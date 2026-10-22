package io.alema.vcsolo

import io.alema.qrcore.QrHint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * VisualContext Solo — runs on the glasses, no phone and no Nexus.
 * Tap: ask (opens a Gemini Live session) · tap while it talks: interrupt · tap while listening: look now
 * Back / swipe: end the session (Back again closes the app) · Menu: set up again.
 * It stays quiet until asked, never asks "anything else?", and closes itself after 25 s of silence.
 */
class SoloActivity : ComponentActivity(), LiveSession.Listener {
    private enum class State { SETUP, IDLE, CONNECTING, OPEN }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var hud: HudView
    private lateinit var camera: Camera
    private val speaker = SpeakerOut()
    private val mic = MicIn { pcm -> onMic(pcm) }
    private val live = LiveSession(this)
    private val transcript = Transcript()

    private var state = State.IDLE
    private var config: SoloConfig? = null
    private var memory: Memory? = null
    private var resumeHandle: String? = null
    private var lastActivity = 0L
    private var question = StringBuilder()
    private var turnDone = true
    private var lastPhoto: ByteArray? = null
    private var reconnecting = false
    private val silence = ByteArray(3200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hud = HudView(this)
        setContentView(hud)
        camera = Camera(this, this)
        config = loadConfig()
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        if (config == null) startSetup() else showIdle()
    }

    @Deprecated("ComponentActivity still delivers it; the Activity Result API is overkill for two permissions.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (config == null) startSetup() else showIdle()
    }

    // ---- setup by QR -------------------------------------------------------------------------

    private var scanView: android.widget.FrameLayout? = null
    private var overlay: ScanOverlay? = null
    private var lastHintAt = 0L

    /**
     * Reading the setup QR: the camera is shown live on the glasses, zoomed 2×, with corner marks,
     * a sweeping line and one hint at the top ("chegue mais perto", "fique parado…").
     * Swipe = zoom in/out. The wide lens and the camera on the left temple make blind aiming impossible.
     */
    private fun startSetup() {
        state = State.SETUP
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showHud()
            hud.set("Configurar", listOf("falta liberar a câmera", "adb shell pm grant", "io.alema.vcsolo …CAMERA"), "voltar: sair"); return
        }
        val preview = androidx.camera.view.PreviewView(this).apply {
            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
        val ov = ScanOverlay(this).apply { detail = "no computador: visualcontext.alema.io/solo" }
        overlay = ov
        scanView = android.widget.FrameLayout(this).apply { addView(preview); addView(ov) }
        setContentView(scanView)
        camera.start(preview) { ok ->
            if (!ok) { showHud(); hud.set("Configurar", listOf("a câmera não abriu"), "voltar: sair"); return@start }
            val z = camera.zoomTo(2f)
            ov.set(QrHint.State.POINT.text, "zoom ${"%.1f".format(z)}× · deslize: zoom · voltar: sair")
            camera.scanQr(status = { st ->
                main.post {
                    // Hints change at most twice a second, so the text doesn't flicker.
                    val now = SystemClock.elapsedRealtime()
                    if (state == State.SETUP && now - lastHintAt > 500) { lastHintAt = now; ov.set(st.text) }
                }
            }) { raw ->
                val c = SetupCode.parse(raw)
                main.post {
                    if (state != State.SETUP) return@post
                    if (c == null) { ov.set("esse QR não é do VisualContext"); return@post }
                    saveConfig(c)
                    config = c
                    camera.stop()
                    showHud()
                    showIdle("✓ configurado")
                }
            }
        }
    }

    private fun zoomStep(delta: Float) {
        val z = camera.zoomTo(camera.zoom + delta)
        overlay?.set(overlay?.hint ?: "", "zoom ${"%.1f".format(z)}× · deslize: zoom · voltar: sair")
    }

    private fun showHud() {
        if (scanView != null) { scanView = null; overlay = null; setContentView(hud) }
    }

    // ---- session --------------------------------------------------------------------------

    private fun showIdle(note: String? = null) {
        state = State.IDLE
        hud.live = false
        val c = config ?: return startSetup()
        memory = Memory(this, c.vcToken)
        val lines = listOfNotNull(note, "toque e pergunte", "o que quiser, ou \"o que é isso?\"")
        hud.set("VisualContext", if (transcript.text.isNotEmpty()) transcript.lines() else lines,
            "toque: perguntar · voltar: sair")
    }

    private fun startSession() {
        val c = config ?: return startSetup()
        if (!online()) {
            hud.set("Sem internet", listOf("conecte o Wi-Fi dos óculos", "ou o hotspot do relógio"), "toque: tentar de novo"); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            hud.set("Microfone", listOf("falta liberar o microfone", "adb shell pm grant", "io.alema.vcsolo …RECORD_AUDIO"), "voltar: sair"); return
        }
        state = State.CONNECTING
        transcript.clear(); question.setLength(0); turnDone = true; lastPhoto = null
        hud.set("VisualContext", listOf("conectando…"), "voltar: cancelar")
        speaker.start()
        io.execute {
            val model = model(c)
            main.post { if (state == State.CONNECTING) live.connect(c.geminiKey, c.voice, resumeHandle, model) }
        }
    }

    /** The QR's model if the user set one; otherwise the Live model Google lists for this key (asked once, then cached). */
    private fun model(c: SoloConfig): String {
        if (c.model != LiveProtocol.MODEL) return c.model
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("resolved_model", null)?.let { return it }
        val picked = live.listModels(c.geminiKey)?.let { LiveProtocol.pickLiveModel(it) } ?: return LiveProtocol.MODEL
        prefs.edit().putString("resolved_model", picked).apply()
        return picked
    }

    private fun endSession(note: String? = null) {
        live.close()
        mic.stop()
        speaker.stop()
        camera.stop()
        main.removeCallbacks(silenceCheck)
        resumeHandle = null
        state = State.IDLE
        hud.live = false
        val lines = if (transcript.text.isNotEmpty()) transcript.lines() else listOfNotNull(note, "toque e pergunte")
        hud.set("VisualContext", lines, (note?.let { "$it · " } ?: "") + "toque: perguntar")
    }

    private fun onMic(pcm: ByteArray) {
        if (state != State.OPEN) return
        // Half-duplex while it talks: the glasses speaker is right next to the mic.
        val data = if (speaker.speaking) silence else pcm
        live.send(LiveProtocol.audio(Base64.encodeToString(data, Base64.NO_WRAP)))
    }

    private val silenceCheck = object : Runnable {
        override fun run() {
            if (state != State.OPEN) return
            if (!speaker.speaking && SystemClock.elapsedRealtime() - lastActivity > SILENCE_MS) { endSession("até já"); return }
            main.postDelayed(this, 1000)
        }
    }

    override fun onEvents(events: List<LiveProtocol.Event>) {
        for (e in events) {
            // Audio is played from the network thread to keep latency low; everything else on the main thread.
            if (e is LiveProtocol.Event.Audio) { speaker.play(Base64.decode(e.base64Pcm24k, Base64.DEFAULT)); continue }
            main.post { handle(e) }
        }
    }

    private fun handle(e: LiveProtocol.Event) {
        when (e) {
            is LiveProtocol.Event.SetupComplete -> {
                if (state != State.CONNECTING && state != State.OPEN) return
                state = State.OPEN
                reconnecting = false
                hud.live = true
                lastActivity = SystemClock.elapsedRealtime()
                if (!mic.start()) { endSession("microfone indisponível"); return }
                if (transcript.text.isEmpty()) hud.set("VisualContext", listOf("pode falar"), "toque: olhar · voltar: encerrar")
                main.removeCallbacks(silenceCheck); main.postDelayed(silenceCheck, 1000)
            }
            is LiveProtocol.Event.UserText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (turnDone) { question.setLength(0); turnDone = false }
                question.append(e.text)
                hud.footer = "você: " + question.toString().trim().takeLast(40)
                hud.invalidate()
            }
            is LiveProtocol.Event.ModelText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (turnDone) { transcript.clear(); turnDone = false }
                transcript.add(e.text)
                hud.set("VisualContext", transcript.lines(), "toque: interromper · voltar: encerrar")
            }
            is LiveProtocol.Event.Interrupted -> speaker.flush()
            is LiveProtocol.Event.TurnComplete -> {
                lastActivity = SystemClock.elapsedRealtime()
                turnDone = true
                autoSave()
                hud.footer = "toque: olhar · voltar: encerrar"; hud.invalidate()
            }
            is LiveProtocol.Event.ToolCall -> onTool(e)
            is LiveProtocol.Event.Resumption -> resumeHandle = e.handle
            is LiveProtocol.Event.GoAway -> reconnect()
            is LiveProtocol.Event.Error -> hud.set("VisualContext", listOf(e.message.take(60)), "voltar: encerrar")
            is LiveProtocol.Event.Audio -> Unit
        }
    }

    /** The server ends every connection after a few minutes; resume it without the user noticing. */
    private fun reconnect() {
        val c = config ?: return
        if (resumeHandle == null || reconnecting) return
        reconnecting = true
        mic.stop()
        live.close()
        state = State.CONNECTING
        main.postDelayed({ live.connect(c.geminiKey, c.voice, resumeHandle, c.model) }, 300)
    }

    override fun onClosed(reason: String, byUs: Boolean) {
        main.post {
            if (byUs) return@post
            // The resumed connection itself failed: give up cleanly instead of hanging on "conectando".
            if (reconnecting) { reconnecting = false; endSession(reason); return@post }
            if (state == State.OPEN && resumeHandle != null) { reconnect(); return@post }
            // Refused before the session even started: maybe the model changed name; ask Google again next time.
            if (state == State.CONNECTING) getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("resolved_model").apply()
            if (state != State.IDLE && state != State.SETUP) endSession(reason)
        }
    }

    // ---- tools ----------------------------------------------------------------------------

    private fun onTool(call: LiveProtocol.Event.ToolCall) {
        lastActivity = SystemClock.elapsedRealtime()
        when (call.name) {
            LiveProtocol.TOOL_LOOK -> look { jpeg ->
                val r = if (jpeg == null) JSONObject().put("erro", "a câmera não respondeu")
                    else JSONObject().put("ok", true).put("nota", "a foto foi enviada como imagem")
                live.send(LiveProtocol.toolResponse(call.id, call.name, r))
            }
            LiveProtocol.TOOL_PERSON -> io.execute {
                val r = memory?.findPerson(call.args.optString("nome")) ?: JSONObject().put("erro", "sem memória")
                live.send(LiveProtocol.toolResponse(call.id, call.name, r))
            }
            LiveProtocol.TOOL_SAVE -> io.execute {
                val photo = if (config?.saveMode == SaveMode.PHOTO) lastPhoto else null
                val r = memory?.saveNote(call.args.optString("texto"), question.toString().trim().ifEmpty { null }, photo) ?: JSONObject().put("erro", "sem memória")
                live.send(LiveProtocol.toolResponse(call.id, call.name, r))
            }
            else -> live.send(LiveProtocol.toolResponse(call.id, call.name, JSONObject().put("erro", "ferramenta desconhecida")))
        }
    }

    /** One photo, sent as a Live "video" frame. The camera warms up only on the first look of a session. */
    private fun look(done: (ByteArray?) -> Unit) {
        hud.footer = "olhando…"; hud.invalidate()
        camera.start { ok ->
            if (!ok) { done(null); return@start }
            camera.photo { jpeg ->
                if (jpeg != null) {
                    lastPhoto = jpeg
                    live.send(LiveProtocol.image(Base64.encodeToString(jpeg, Base64.NO_WRAP)))
                    main.post { hud.showPhoto(jpeg) }
                }
                main.post { done(jpeg) }
            }
        }
    }

    /** Save mode chosen in setup: nothing, the answer as a note, or answer + photo. */
    private fun autoSave() {
        val mode = config?.saveMode ?: return
        if (mode == SaveMode.NONE) return
        val answer = transcript.text.takeIf { it.isNotBlank() } ?: return
        val q = question.toString().trim().ifEmpty { null }
        val photo = if (mode == SaveMode.PHOTO) lastPhoto else null
        lastPhoto = null
        io.execute { memory?.saveNote(answer, q, photo) }
    }

    // ---- input ----------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> onTap()
            KeyEvent.KEYCODE_BACK -> onBack()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> if (state == State.SETUP) zoomStep(0.5f) else if (state == State.OPEN || state == State.CONNECTING) endSession() else return super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> if (state == State.SETUP) zoomStep(-0.5f) else if (state == State.OPEN || state == State.CONNECTING) endSession() else return super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_MENU -> { endSession(); getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply(); config = null; startSetup() }
            else -> {
                // Unknown key: show its code once, so we learn this glasses' touchpad mapping.
                hud.footer = "tecla $keyCode"; hud.invalidate()
                return super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    private fun onTap() {
        when (state) {
            State.SETUP -> Unit
            State.IDLE -> startSession()
            State.CONNECTING -> Unit
            State.OPEN -> if (speaker.speaking) { speaker.flush(); hud.footer = "interrompido · pode falar"; hud.invalidate() }
                else look { jpeg -> hud.footer = if (jpeg != null) "foto enviada · pergunte" else "a câmera não respondeu"; hud.invalidate() }
        }
    }

    private fun onBack() {
        when (state) {
            State.OPEN, State.CONNECTING -> endSession()
            else -> finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (state == State.OPEN || state == State.CONNECTING) endSession()
        if (state == State.SETUP) camera.stop()
    }

    override fun onResume() {
        super.onResume()
        if (state == State.SETUP && !camera.bound) startSetup()
    }

    override fun onDestroy() {
        endSession()
        camera.shutdown()
        io.shutdown()
        super.onDestroy()
    }

    // ---- storage --------------------------------------------------------------------------

    private fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadConfig(): SoloConfig? {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = p.getString("gemini", null) ?: return null
        return SoloConfig(key, SaveMode.of(p.getString("save", null)), p.getString("voice", null) ?: SoloConfig.DEFAULT_VOICE, p.getString("vc", null), p.getString("model", null) ?: LiveProtocol.MODEL)
    }

    private fun saveConfig(c: SoloConfig) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("gemini", c.geminiKey).putString("save", c.saveMode.wire).putString("voice", c.voice).putString("vc", c.vcToken).putString("model", c.model)
            .apply()
    }

    companion object {
        private const val PREFS = "solo"
        private const val SILENCE_MS = 25_000L
    }
}
