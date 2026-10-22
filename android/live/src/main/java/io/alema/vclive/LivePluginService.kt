package io.alema.vclive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusAudioCallbacks
import com.anezium.rokidbus.client.plugin.NexusAudioFormat
import com.anezium.rokidbus.client.plugin.NexusAudioSession
import com.anezium.rokidbus.client.plugin.NexusAudioStopReason
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSnapshotCallbacks
import com.anezium.rokidbus.client.plugin.NexusSnapshotError
import com.anezium.rokidbus.client.plugin.NexusSnapshotSession
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.client.plugin.NexusTtsCallbacks
import com.anezium.rokidbus.client.plugin.NexusTtsDoneReason
import com.anezium.rokidbus.client.plugin.NexusTtsSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import io.alema.livecore.LiveProtocol
import io.alema.livecore.LiveSession
import io.alema.livecore.Transcript
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * VisualContext Live (test) — Gemini Live inside a Nexus plugin.
 * Tap: ask · tap while it talks: interrupt · tap while listening: send a photo · Back: end / close.
 * The card shows where the phone's audio goes, which is what this test is about.
 */
class LivePluginService : NexusPluginService(), LiveSession.Listener {
    private enum class State { IDLE, CONNECTING, OPEN }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var surface: NexusSurfaceSession? = null
    private var audio: NexusAudioSession? = null
    private var snapshot: NexusSnapshotSession? = null
    private var tts: NexusTtsSession? = null
    private val speaker = Speaker()
    private val live = LiveSession(this)
    private val transcript = Transcript()
    private var state = State.IDLE
    private var turnDone = true
    private var lastActivity = 0L
    private var revision = 0
    private var resumeHandle: String? = null
    private val silence = ByteArray(3200)

    override fun onNexusOpen() {
        surface = nexusSurfaceSession(SURFACE_ID)
        idle()
    }

    override fun onNexusClose() = end(close = true)

    override fun onDestroy() {
        end(close = true)
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when (state) {
                State.IDLE -> start()
                State.CONNECTING -> Unit
                State.OPEN -> if (speaker.speaking) { speaker.flush(); tts?.stop(); footer("interrompido · pode falar") } else look(null)
            }
            KeyEvent.KEYCODE_BACK -> if (state == State.IDLE) end(close = true) else { end(close = false); idle("até já") }
        }
    }

    private fun idle(note: String? = null) {
        state = State.IDLE
        val lines = if (transcript.text.isNotEmpty()) transcript.lines() else listOfNotNull(note, "toque e pergunte", LiveConfig.route(this))
        show(lines, "toque: perguntar · voltar: fechar")
    }

    private fun start() {
        val key = LiveConfig.key(this)
        if (key.length < 20) { show(listOf("falta a chave do Gemini", "no celular: VisualContext Live"), "voltar: fechar"); return }
        state = State.CONNECTING
        transcript.clear(); turnDone = true
        show(listOf("conectando…", LiveConfig.route(this)), "voltar: cancelar")
        if (LiveConfig.voice(this) == LiveConfig.Voice.GEMINI) speaker.start()
        io.execute {
            val model = LiveConfig.model(this) ?: (live.listModels(key)?.let { LiveProtocol.pickLiveModel(it) } ?: LiveProtocol.MODEL)
                .also { LiveConfig.saveModel(this, it) }
            main.post { if (state == State.CONNECTING) live.connect(key, LiveConfig.voiceName(this), resumeHandle, model) }
        }
    }

    private fun end(close: Boolean) {
        live.close()
        audio?.stop(); audio = null
        snapshot?.cancel(); snapshot = null
        tts?.stop()
        speaker.stop()
        main.removeCallbacksAndMessages(null)
        resumeHandle = null
        state = State.IDLE
        if (close) { surface?.hide(); surface = null }
    }

    private fun startMic(): Boolean {
        val s = nexusAudioSession(object : NexusAudioCallbacks {
            override fun onAudioStarted(format: NexusAudioFormat) = Unit
            override fun onAudioFrame(pcm: ByteArray, sequence: Long, timestamp: Long) {
                if (state != State.OPEN) return
                // Half-duplex while Gemini talks, so it doesn't hear itself.
                val data = if (speaker.speaking) ByteArray(pcm.size) else pcm
                live.send(LiveProtocol.audio(Base64.encodeToString(data, Base64.NO_WRAP)))
            }
            override fun onAudioStopped(reason: NexusAudioStopReason) {
                main.post { if (state == State.OPEN) footer("microfone parou: ${reason.name.lowercase()}") }
            }
        })
        audio = s
        return s?.start() == NexusSdkResult.SENT
    }

    private val silenceCheck = object : Runnable {
        override fun run() {
            if (state != State.OPEN) return
            if (!speaker.speaking && SystemClock.elapsedRealtime() - lastActivity > SILENCE_MS) { end(close = false); idle("até já"); return }
            main.postDelayed(this, 1000)
        }
    }

    override fun onEvents(events: List<LiveProtocol.Event>) {
        for (e in events) {
            if (e is LiveProtocol.Event.Audio) {
                if (LiveConfig.voice(this) == LiveConfig.Voice.GEMINI) speaker.play(Base64.decode(e.base64Pcm24k, Base64.DEFAULT))
                continue
            }
            main.post { handle(e) }
        }
    }

    private fun handle(e: LiveProtocol.Event) {
        when (e) {
            is LiveProtocol.Event.SetupComplete -> {
                if (state != State.CONNECTING && state != State.OPEN) return
                state = State.OPEN
                lastActivity = SystemClock.elapsedRealtime()
                if (!startMic()) { show(listOf("o Nexus não liberou o microfone", "Plugins → VisualContext Live"), "voltar: fechar"); end(close = false); return }
                show(listOf("pode falar", LiveConfig.route(this)), "toque: olhar · voltar: encerrar")
                main.postDelayed(silenceCheck, 1000)
            }
            is LiveProtocol.Event.UserText -> lastActivity = SystemClock.elapsedRealtime()
            is LiveProtocol.Event.ModelText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (turnDone) { transcript.clear(); turnDone = false }
                transcript.add(e.text)
                show(transcript.lines(), "toque: interromper · voltar: encerrar")
            }
            is LiveProtocol.Event.Interrupted -> { speaker.flush(); tts?.stop() }
            is LiveProtocol.Event.TurnComplete -> {
                lastActivity = SystemClock.elapsedRealtime()
                turnDone = true
                if (LiveConfig.voice(this) == LiveConfig.Voice.NEXUS && transcript.text.isNotBlank()) speakWithNexus(transcript.text)
            }
            is LiveProtocol.Event.ToolCall -> tool(e)
            is LiveProtocol.Event.Resumption -> resumeHandle = e.handle
            is LiveProtocol.Event.GoAway -> Unit // test plugin: the 25 s silence close comes first in practice
            is LiveProtocol.Event.Error -> show(listOf(e.message.take(60)), "voltar: encerrar")
            is LiveProtocol.Event.Audio -> Unit
        }
    }

    override fun onClosed(reason: String, byUs: Boolean) {
        main.post {
            if (byUs || state == State.IDLE) return@post
            LiveConfig.saveModel(this, null) // ask Google for the model again next time
            end(close = false)
            idle(reason)
        }
    }

    private fun speakWithNexus(text: String) {
        val t = tts ?: nexusTtsSession(object : NexusTtsCallbacks {
            override fun onTtsStarted(utteranceId: String) = Unit
            override fun onTtsDone(utteranceId: String, reason: NexusTtsDoneReason) { lastActivity = SystemClock.elapsedRealtime() }
        })?.also { tts = it }
        t?.speak(text.take(1000))
    }

    private fun tool(call: LiveProtocol.Event.ToolCall) {
        lastActivity = SystemClock.elapsedRealtime()
        when (call.name) {
            LiveProtocol.TOOL_LOOK -> look(call)
            LiveProtocol.TOOL_SAVE -> io.execute {
                val dir = File(filesDir, "notas").apply { mkdirs() }
                File(dir, "notas.jsonl").appendText(JSONObject().put("quando", System.currentTimeMillis()).put("texto", call.args.optString("texto")).toString() + "\n")
                live.send(LiveProtocol.toolResponse(call.id, call.name, JSONObject().put("guardado", true)))
            }
            else -> live.send(LiveProtocol.toolResponse(call.id, call.name, JSONObject().put("erro", "não disponível neste teste")))
        }
    }

    /** One photo from the glasses through Nexus, sent to Gemini as a Live image frame. */
    private fun look(call: LiveProtocol.Event.ToolCall?) {
        footer("olhando…")
        val s = nexusSnapshotSession(object : NexusSnapshotCallbacks {
            override fun onSnapshotCaptured(jpeg: ByteArray) {
                io.execute {
                    val small = shrink(jpeg)
                    if (small != null) live.send(LiveProtocol.image(Base64.encodeToString(small, Base64.NO_WRAP)))
                    call?.let { live.send(LiveProtocol.toolResponse(it.id, it.name, JSONObject().put("ok", small != null))) }
                    main.post { footer(if (small != null) "foto enviada" else "foto ilegível") }
                }
            }
            override fun onSnapshotError(error: NexusSnapshotError) {
                call?.let { live.send(LiveProtocol.toolResponse(it.id, it.name, JSONObject().put("erro", "a câmera não respondeu"))) }
                main.post { footer("câmera: ${error.name.lowercase()}") }
            }
        })
        snapshot = s
        if (s?.capture() != NexusSdkResult.SENT) {
            call?.let { live.send(LiveProtocol.toolResponse(it.id, it.name, JSONObject().put("erro", "câmera não liberada no Nexus"))) }
            footer("câmera não liberada no Nexus")
        }
    }

    private fun shrink(jpeg: ByteArray): ByteArray? = try {
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val scale = minOf(1f, 768f / maxOf(full.width, full.height))
        val small = Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
        val out = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (small !== full) small.recycle(); full.recycle()
        out.toByteArray()
    } catch (_: Exception) { null }

    private var lastLines: List<String> = emptyList()

    private fun footer(text: String) = show(lastLines, text)

    private fun show(lines: List<String>, footer: String) {
        lastLines = lines
        val s = surface ?: return
        revision++
        s.showCard(NexusCard(title = "VisualContext Live", lines = lines, footer = footer, contentKey = "live-$revision", handlesBack = true))
    }

    companion object {
        private const val SURFACE_ID = "vc-live"
        private const val SILENCE_MS = 25_000L
    }
}
