package io.alema.zap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.anezium.rokidbus.client.PluginRegistrationResult
import com.anezium.rokidbus.client.plugin.NexusNotice
import com.anezium.rokidbus.client.plugin.NexusPluginCallbacks
import com.anezium.rokidbus.client.plugin.NexusPluginClient
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import com.anezium.rokidbus.shared.plugin.PluginCapability

/**
 * The discreet arrival notice: who wrote and the start of the message, for a
 * few seconds, no buttons. The full message waits in the inbox.
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

    private val main = Handler(Looper.getMainLooper())
    private var client: NexusPluginClient? = null
    private var pending: Pair<String, String>? = null

    @Volatile
    var inboxOpen = false

    fun announce(context: Context, sender: String, text: String) = main.post {
        if (inboxOpen || !ZapSettings(context).announceArrivals()) return@post
        pending = sender to text
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
        client?.close()
        client = null
    }

    private fun tryShow() {
        val (sender, text) = pending ?: return
        val current = client ?: return
        if (!current.isApproved || !current.hasCapability(PluginCapability.SURFACES) || !current.supportsNoticeSurface) return
        val result = current.showNotice(
            NexusNotice(
                title = sender.take(TITLE_CHARS),
                body = if (text.length <= BODY_CHARS) text else text.take(BODY_CHARS - 1) + "…",
                footer = "Zap",
                ttlMs = NOTICE_TTL_MS,
                wakeDisplay = true,
            ),
        )
        Log.i(TAG, "arrival notice result=$result")
        pending = null
    }

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
