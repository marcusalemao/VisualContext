package io.alema.zap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.anezium.rokidbus.client.PluginRegistrationResult
import com.anezium.rokidbus.client.plugin.NexusNotice
import com.anezium.rokidbus.client.plugin.NexusPluginCallbacks
import com.anezium.rokidbus.client.plugin.NexusPluginClient
import com.anezium.rokidbus.client.plugin.NexusTtsCallbacks
import com.anezium.rokidbus.client.plugin.NexusTtsDoneReason
import com.anezium.rokidbus.client.plugin.NexusTtsSession
import com.anezium.rokidbus.client.plugin.ttsSession
import io.alema.zap.core.ZapText
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import com.anezium.rokidbus.shared.plugin.PluginCapability

/**
 * What the glasses do when a message arrives while Zap is closed, as the
 * wearer chose in settings:
 * - notice: who wrote and the start of the message, a few seconds, no buttons;
 * - notice + voice: the whole message over a dark backdrop, read aloud at
 *   once (Nexus speaks only on the glasses or earbuds, never the phone speaker).
 * Either way the full chat waits in the inbox.
 *
 * Plugins are dormant unless opened, so this borrows Relay's pattern: the
 * notification listener keeps the process alive, a bus client connects to say
 * one thing and is closed again after a short idle. Only one client per plugin
 * id may be on the bus, so nothing is announced while Zap itself is open on
 * the glasses (its inbox refreshes live instead).
 */
internal object ZapNotifier : NexusPluginCallbacks {
    private const val TAG = "ZapNotifier"
    private const val PLUGIN_ID = "zap"
    private const val NOTICE_TTL_MS = 4_000L
    private const val IDLE_CLOSE_MS = 30_000L
    private const val TITLE_CHARS = 32
    private const val BODY_CHARS = 90
    private const val FULL_BODY_CHARS = 1_024
    /** Long enough to be read aloud at an ordinary voice speed; Nexus caps notices at 45 s. */
    private const val READ_MS_PER_CHAR = 80L
    private const val READ_BASE_MS = 3_000L
    private const val READ_MAX_MS = 45_000L

    private val main = Handler(Looper.getMainLooper())
    private var client: NexusPluginClient? = null
    private var pending: Arrival? = null
    private var tts: NexusTtsSession? = null
    private var spoken = 0
    private val text = ZapText()

    private data class Arrival(val sender: String, val text: String, val readAloud: Boolean)

    @Volatile
    var inboxOpen = false

    fun announce(context: Context, sender: String, text: String) = main.post {
        val mode = ZapSettings(context).arrivalMode()
        if (inboxOpen || mode == ZapSettings.ARRIVAL_OFF) return@post
        pending = Arrival(sender, text, readAloud = mode == ZapSettings.ARRIVAL_READ)
        if (client == null) {
            client = NexusPluginClient.create(context.applicationContext, PLUGIN_ID, this).also(NexusPluginClient::connect)
        }
        tryShow()
        main.removeCallbacks(closeIdle)
        main.postDelayed(closeIdle, IDLE_CLOSE_MS)
    }

    /** Zap opened on the glasses: step aside so its own session is the only client. */
    fun release() = main.post {
        pending = null
        main.removeCallbacks(closeIdle)
        closeIdle.run()
    }

    private val closeIdle = Runnable {
        tts?.close()
        tts = null
        client?.close()
        client = null
    }

    private fun tryShow() {
        val arrival = pending ?: return
        val current = client ?: return
        if (!current.isApproved || !current.hasCapability(PluginCapability.SURFACES) || !current.supportsNoticeSurface) return
        val body = arrival.text.ifBlank { " " }
        val notice = if (arrival.readAloud) {
            val shown = cut(body, FULL_BODY_CHARS)
            NexusNotice(
                title = arrival.sender.take(TITLE_CHARS),
                body = shown,
                footer = "Zap",
                ttlMs = (READ_BASE_MS + READ_MS_PER_CHAR * shown.length).coerceAtMost(READ_MAX_MS),
                wakeDisplay = true,
                backdrop = true, // over whatever is on the lens: this one may be urgent
            )
        } else {
            NexusNotice(
                title = arrival.sender.take(TITLE_CHARS),
                body = cut(body, BODY_CHARS),
                footer = "Zap",
                ttlMs = NOTICE_TTL_MS,
                wakeDisplay = true,
            )
        }
        val result = current.showNotice(notice)
        Log.i(TAG, "arrival notice result=$result read=${arrival.readAloud}")
        pending = null
        if (arrival.readAloud) speak(current, arrival)
    }

    /** One voice: a newer message cuts off the one being read, like a person would. */
    private fun speak(current: NexusPluginClient, arrival: Arrival) {
        if (!current.hasCapability(PluginCapability.TTS)) return
        val session = tts ?: current.ttsSession(ttsCallbacks).also { tts = it }
        val sentence = cut(text.heard(arrival.sender.replace(" · ", ", "), arrival.text), FULL_BODY_CHARS)
        val result = session.speak(sentence, "zap-arrival-${++spoken}")
        Log.i(TAG, "read aloud result=$result")
        // Keep the bus client until the voice has had time to finish.
        main.removeCallbacks(closeIdle)
        main.postDelayed(closeIdle, IDLE_CLOSE_MS + READ_MAX_MS)
    }

    private val ttsCallbacks = object : NexusTtsCallbacks {
        override fun onTtsStarted(utteranceId: String) = Unit
        override fun onTtsDone(utteranceId: String, reason: NexusTtsDoneReason) {
            if (reason == NexusTtsDoneReason.UNAVAILABLE) Log.w(TAG, "read aloud unavailable: no glasses or earbuds, or voice engine failed")
        }
    }

    private fun cut(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"

    override fun onRegistrationState(result: Int) {
        main.post {
            when (result) {
                // Not approved for good (wearer denied Zap, bad descriptor…): stop holding a client.
                PluginRegistrationResult.DENIED,
                PluginRegistrationResult.INVALID_DESCRIPTOR,
                PluginRegistrationResult.IDENTITY_MISMATCH,
                PluginRegistrationResult.UNSUPPORTED_API -> {
                    Log.w(TAG, "registration terminal result=$result")
                    pending = null
                    closeIdle.run()
                }
                else -> tryShow()
            }
        }
    }

    override fun onLinkState(state: Int) {
        main.post { tryShow() }
    }

    override fun onOpen() = Unit
    override fun onClose() = Unit
    override fun onInput(event: NexusInputEvent) = Unit
}
