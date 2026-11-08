package io.alema.visualcontext

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * The glasses on your phone, in any orientation. The phone camera (back or front) is the glasses camera,
 * its mic and speaker the glasses audio, its GPS and compass the phone's.
 *
 * People are always on: a known face gets its card beside the head by itself; a new face is described
 * neutrally (never "pessoa 1") and, if it stays in front of you, a 5-second countdown keeps it — tap to cancel.
 * Saying the name ("essa é a Ana", "meu nome é Pedro") names it, with Falar on.
 */
class EmulatorActivity : ComponentActivity(), LiveBrain.Host, SensorEventListener {
    private lateinit var preview: PreviewView
    private lateinit var hud: HudOverlay
    private lateinit var talk: Button
    private lateinit var placesBtn: Button
    private lateinit var facesBtn: Button
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private val faces by lazy { FaceBrain(this) }
    private val live by lazy { LiveBrain(this, this) }
    /**
     * The old engine (face-api.js), only in the background: when the new engine sees a stranger, the old one checks
     * the faces registered with it long ago. A match is moved to the new engine without the user doing anything.
     */
    private var legacy: FaceEngine? = null
    private val legacyChecked = HashSet<String>()
    private var legacyBusy = false
    @Volatile private var lastJpeg: ByteArray? = null
    @Volatile private var facesOn = true
    @Volatile private var scanning = false
    private var lastFaceScan = 0L
    private var placesOn = true // Marcus, 08/11: "quero mostrar a experiência completa"
    private var placesAt = 0L
    private var heading = 0f
    private var sx = 0f; private var sy = 1f
    private var liveOn = false
    private var lastDescribeAt = 0L
    private var front = false
    /** Countdown for the new face in front: pending id → when it started. */
    private var countdownId: String? = null
    private var countdownAt = 0L
    private var lastSeen: List<FaceBrain.Seen> = emptyList()
    private var lastBw = 1; private var lastBh = 1
    /** QR codes you keep looking at (Pix etc.). Read on their own thread, never stored. */
    private val qr = QrLook()
    private val qrWork = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var qrBusy = false
    private var lastQrScan = 0L
    /** Spec 1h: the conversation in front of you is transcribed by the phone while the assistant is off. */
    private lateinit var ears: Ears
    private var hearing = false
    /** Spec 1i: output = screen, screen + voice, or voice only (the glasses without a display). */
    private lateinit var voice: Voice
    private var offlineSaidAt = 0L
    private var output = "tela"
    private val spoken = HashMap<String, Long>()
    private val speaks get() = output != "tela"
    private lateinit var outBtn: Button
    /** QR mode (07/11): only while the ▣ QR button is on, with a window to aim; closes by itself after 25 s. */
    private var qrOn = false
    private var qrAt = 0L
    private lateinit var qrBtn: Button

    private val green = Color.rgb(0, 255, 136)
    private val dim = Color.rgb(90, 120, 104)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Where.loadKey(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        val taps = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { if (countdownId != null) { cancelCountdown(); return true }; return false }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { // tap on the top card closes it
                val r = hud.cardRect ?: return false
                if (!r.contains(e.x, e.y) || hud.buttonRects.any { it.contains(e.x, e.y) }) return false
                hud.cardTitle = null; hud.cardLines = emptyList(); hud.invalidate(); return true
            }
        })
        hud = HudOverlay(this).apply {
            // Tap anywhere on the lens during the countdown = cancel (the swipe on the glasses).
            setOnTouchListener { _, e ->
                if (taps.onTouchEvent(e)) true
                else if (e.action != MotionEvent.ACTION_UP) false
                else if (hud.buttonRects.indexOfFirst { it.contains(e.x, e.y) }.let { it >= 0 && cardAction != null }) { cardAction?.invoke(hud.buttonRects.indexOfFirst { it.contains(e.x, e.y) }); true }
                else if (hud.pinRects.entries.firstOrNull { it.value.contains(e.x, e.y) }?.key?.let { openPin(it); true } == true) true
                else if (countdownId != null) { cancelCountdown(); true }
                else if (hud.cardTitle != null && hud.cardTitle != "Câmera") { hud.cardTitle = null; hud.cardLines = emptyList(); hud.invalidate(); true } else false
            }
        }
        // Lens on top, buttons in their own strip below it (never under the Android navigation bar).
        val lens = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); addView(preview); addView(hud) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(lens, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(170, 0, 0, 0)); setPadding(4, 4, 4, 4)
        }
        fun btn(t: String, cb: () -> Unit) = Button(this).apply {
            text = t; textSize = 12f; isAllCaps = false; setTextColor(green); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { cb() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        talk = btn("🎙 Falar") { toggleLive() }; bar.addView(talk)
        facesBtn = btn("") { facesOn = !facesOn; if (!facesOn) { hud.faces = emptyList(); cancelCountdown() }; paintToggles() }; bar.addView(facesBtn)
        placesBtn = btn("") { placesOn = !placesOn; if (!placesOn) hud.pins = emptyList() else refreshPlaces(true); paintToggles() }; bar.addView(placesBtn)
        qrBtn = btn("▣ QR") { setQr(!qrOn) }; bar.addView(qrBtn)
        outBtn = btn("") { setOutput(when (output) { "tela" -> "tela_voz"; "tela_voz" -> "voz"; else -> "tela" }) }; bar.addView(outBtn)
        bar.addView(btn("⟲ Câmera") { front = !front; startCamera() })
        bar.addView(btn("✕ Fechar") { finish() })
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // Android 15+ draws apps under the system bars: the whole screen steps in by the bars' size.
        // Fallback to the system's own navigation-bar height if the insets come back empty.
        val navFallback = resources.getIdentifier("navigation_bar_height", "dimen", "android").let { if (it > 0) resources.getDimensionPixelSize(it) else 0 }
        val statusFallback = resources.getIdentifier("status_bar_height", "dimen", "android").let { if (it > 0) resources.getDimensionPixelSize(it) else 0 }
        root.setPadding(0, statusFallback, 0, navFallback)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(sys.left, maxOf(sys.top, 0), sys.right, if (sys.bottom > 0) sys.bottom else navFallback)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        voice = Voice(this)
        output = Voice.output(this)
        ears = Ears(this, onName = { n -> work.execute {
                val r = faces.name(n)
                if (r.optBoolean("guardado")) main.post { onNamed(r.optString("nome", n)) }
            } }, onState = { h -> val was = hearing; hearing = h; main.post { paintToggles(); if (speaks && h != was) { if (h) voice.recStart() else voice.recEnd() } } },
            onQuestion = { q -> standbyQuestion(q) })
        ears.standby = Backend.standby(this) // off by default (Marcus, 08/11: "aperto o Falar"); a setting for who wants it
        applyOutput()
        paintToggles()
        val need = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) startCamera() else requestPermissions(need.toTypedArray(), 1)
        work.execute { faces.refreshPeople(true); faces.engine() }
        main.postDelayed(ticker, 250)
    }

    /** Toggles say their state in words and colour: "● Pessoas: ligado" / "○ Lugares: desligado". */
    private fun paintToggles() {
        facesBtn.text = if (facesOn) "● Pessoas" else "○ Pessoas"
        facesBtn.setTextColor(if (facesOn) green else dim)
        placesBtn.text = if (placesOn) "● Lugares" else "○ Lugares"
        placesBtn.setTextColor(if (placesOn) green else dim)
        hud.footer = listOfNotNull(if (facesOn) "pessoas: ligado" else "pessoas: desligado", if (placesOn) "lugares: ligado" else "lugares: desligado", if (::ears.isInitialized && hearing) "🎙 ouvindo a conversa" else null).joinToString(" · ")
        hud.invalidate()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else { hud.cardTitle = "Câmera"; hud.cardLines = listOf("libere a câmera para o emulador"); hud.invalidate() }
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val p = f.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val an = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            an.setAnalyzer(work) { img ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    // Faces as often as the phone can (≈ 2–4 per second), so the card follows the head.
                    if (now - lastFaceScan > 350) {
                        lastJpeg = Frames.jpeg(img, 80) ?: lastJpeg
                        if (facesOn && !scanning) { lastFaceScan = now; scan() }
                        if (qrOn && now - lastQrScan > 300 && !qrBusy) { lastQrScan = now; qrScan() }
                    }
                } finally { img.close() }
            }
            p.unbindAll()
            p.bindToLifecycle(this, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, prev, an)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun scan() {
        val j = lastJpeg ?: return
        scanning = true
        try {
            val b = Frames.bitmap(j, 640) ?: return
            var seen = faces.look(b, describeUnknown = false)
            // A new face gets its neutral description once (one Gemini call, at most every 5 s).
            val nameless = seen.firstOrNull { !it.known && it.label == "…" }
            if (nameless != null && SystemClock.elapsedRealtime() - lastDescribeAt > 5000) {
                lastDescribeAt = SystemClock.elapsedRealtime()
                if (faces.describe(nameless, b) != null) seen = faces.look(b, describeUnknown = false)
            }
            val bw = b.width; val bh = b.height
            main.post { if (facesOn) onFaces(seen, bw, bh) }
            seen.firstOrNull { it.pendingId != null }?.let { p -> legacyCheck(p, j) }
        } finally { scanning = false }
    }

    private fun qrScan() {
        val j = lastJpeg ?: return
        qrBusy = true
        qrWork.execute {
            try {
                val full = Frames.bitmap(j, 1280) ?: return@execute
                // The window shows (and reads) the centre of what the camera sees.
                val side = (minOf(full.width, full.height) * 0.62f).toInt()
                val b = android.graphics.Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
                val view = if (front) android.graphics.Bitmap.createBitmap(b, 0, 0, side, side, android.graphics.Matrix().apply { preScale(-1f, 1f) }, false) else b
                val changed = qr.feed(b, SystemClock.elapsedRealtime())
                main.post { if (qrOn) { hud.qrView = view; if (changed) paintQr() else hud.invalidate() } }
            } catch (_: Throwable) { } finally { qrBusy = false }
        }
    }

    private fun setQr(on: Boolean) {
        qrOn = on; qrAt = SystemClock.elapsedRealtime()
        hud.qrWindow = on; hud.qrHint = "encaixe o QR aqui"
        if (!on) { hud.qrView = null; qr.dismiss(SystemClock.elapsedRealtime()) }
        qrBtn.setTextColor(if (on) green else dim); qrBtn.text = if (on) "● QR" else "▣ QR"
        paintQr()
        if (on) main.postDelayed({ if (qrOn && SystemClock.elapsedRealtime() - qrAt >= 25_000 && qr.offer == null) setQr(false) }, 25_000)
    }

    private fun paintQr() {
        val o = qr.offer
        if (o == null) { if (hud.cardTitle != null && hud.cardTitle != "Câmera") { hud.cardTitle = null; hud.cardLines = emptyList() } }
        else {
            val (t, l) = QrLook.card(o)
            // One good read is enough (Marcus, 08/11): the window closes, the card stays with Sim / Não on screen.
            if (qrOn) stopQrScan()
            showCard(t, l.map { it.replace(Regex("""\s*toque ou diga "sim"""") , "") }, listOf("✓ Sim", "✕ Não")) { i -> if (i == 0) acceptQr() else { qr.dismiss(SystemClock.elapsedRealtime()); closeCard() } }
            if (speaks) voice.say("$t. " + l.joinToString(". "), interrupt = true)
        }
        hud.invalidate()
    }

    /** Stops looking for codes but keeps the one found (and its card). */
    private fun stopQrScan() {
        qrOn = false; hud.qrWindow = false; hud.qrView = null
        qrBtn.setTextColor(dim); qrBtn.text = "▣ QR"
    }

    // ── Cards with buttons (QR, places) ──
    private var cardAction: ((Int) -> Unit)? = null
    private fun showCard(title: String, lines: List<String>, buttons: List<String>, action: (Int) -> Unit) {
        hud.cardTitle = title; hud.cardLines = lines; hud.cardButtons = buttons; hud.buttonFocus = 0; cardAction = action; hud.invalidate()
        briefClear?.let { main.removeCallbacks(it) }
        briefClear = Runnable { if (hud.cardTitle == title) closeCard() }.also { main.postDelayed(it, 30_000L) }
    }
    private fun closeCard() { hud.cardTitle = null; hud.cardLines = emptyList(); cardAction = null; hud.pinFocus = -1; hud.invalidate() }

    /** A place pin chosen (tap or temple): its card with what to do (Marcus, 08/11: "caminhar, navegar até lá, ou mais detalhes"). */
    private fun openPin(i: Int) {
        val p = hud.pins.getOrNull(i) ?: return
        hud.pinFocus = i
        showCard("⌖ " + p.title, listOf(listOf(p.kind, if (p.dist > 0) "${p.dist} m" else "").filter { it.isNotBlank() }.joinToString(" · ")), listOf("▶ Ir a pé", "ⓘ Detalhes", "✕")) { b ->
            when (b) {
                0 -> { closeCard(); goTo(p) }
                1 -> pinDetails(p)
                else -> closeCard()
            }
        }
        hud.pinFocus = i; hud.invalidate()
    }
    private fun goTo(p: HudOverlay.Pin) {
        val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=${p.lat},${p.lon}&mode=w")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(packageManager) == null) i.data = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=${p.lat},${p.lon}")
        try { startActivity(i) } catch (_: Exception) { toast("não consegui abrir o mapa") }
    }
    private fun pinDetails(p: HudOverlay.Pin) {
        if (liveOn) { live.context("Ele escolheu na lente o lugar \"${p.title}\" (${p.kind}, a ${p.dist} m, coordenadas ${p.lat},${p.lon}). Diga em duas frases o que é, se está aberto agora e o que vale saber; use a busca do Google."); closeCard() }
        else {
            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${p.lat},${p.lon}?q=" + android.net.Uri.encode(p.title))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(i) } catch (_: Exception) { toast("sem app de mapas") }
            closeCard()
        }
    }

    /** The temple (D-pad on the glasses, arrows on a keyboard): moves between pins or card buttons; centre = choose. */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        if (e.action != android.view.KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        val code = e.keyCode
        val prev = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT
        val next = code == android.view.KeyEvent.KEYCODE_DPAD_DOWN || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        val ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER
        if (hud.cardButtons.isNotEmpty() && cardAction != null) {
            when {
                prev -> { hud.buttonFocus = (hud.buttonFocus - 1 + hud.cardButtons.size) % hud.cardButtons.size; hud.invalidate(); return true }
                next -> { hud.buttonFocus = (hud.buttonFocus + 1) % hud.cardButtons.size; hud.invalidate(); return true }
                ok -> { cardAction?.invoke(hud.buttonFocus); return true }
                code == android.view.KeyEvent.KEYCODE_BACK -> { closeCard(); return true }
            }
        }
        if ((prev || next) && hud.pins.isNotEmpty()) {
            // Only the pins in view, nearest to the centre first, in left-to-right order.
            val seen = hud.pins.indices.filter { kotlin.math.abs((hud.pins[it].bearingDeg - hud.heading + 540) % 360 - 180) <= 30f }
                .sortedBy { (hud.pins[it].bearingDeg - hud.heading + 540) % 360 - 180 }
            if (seen.isEmpty()) return super.dispatchKeyEvent(e)
            val at = seen.indexOf(hud.pinFocus)
            hud.pinFocus = seen[if (at < 0) (if (next) 0 else seen.size - 1) else (at + (if (next) 1 else -1) + seen.size) % seen.size]; hud.invalidate(); return true
        }
        if (ok && hud.pinFocus >= 0) { openPin(hud.pinFocus); return true }
        if (code == android.view.KeyEvent.KEYCODE_BACK && hud.pinFocus >= 0) { hud.pinFocus = -1; hud.invalidate(); return true }
        return super.dispatchKeyEvent(e)
    }

    /** The tap (or "sim") on a QR card. Pix: copy the code; the bank app (or the wallet) offers to pay. Never pays by itself. */
    private fun acceptQr() {
        val (r, raw) = qr.consume(SystemClock.elapsedRealtime()) ?: return
        setQr(false)
        val clip = getSystemService(android.content.ClipboardManager::class.java)
        when (r) {
            is io.alema.qrcore.QrResult.Pix -> {
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Pix copia e cola", raw))
                val title = "Pix" + (r.merchantName?.let { ": $it" } ?: "") + (r.amount?.let { " · R$ " + it.replace('.', ',') } ?: "")
                work.execute {
                    val place = Where.location(this)?.let { Where.placeLabel(this, it) }
                    Moments.send(this, org.json.JSONObject().put("action", "note").put("kind", "pix").put("title", title).put("source", "qr")
                        .put("text", "Código Pix copiado" + (r.merchantName?.let { " (o código diz: $it${r.merchantCity?.let { c -> ", $c" } ?: ""})" } ?: "") + ".")
                        .put("fields", org.json.JSONObject().put("pix", org.json.JSONObject().put("nome", r.merchantName ?: "").put("cidade", r.merchantCity ?: "")
                            .put("valor", r.amount ?: "").put("dinamico", r.isDynamic).put("descricao", r.description ?: "")))
                        .apply { if (place != null) put("place", place) })
                }
                toast("Código Pix copiado. Abra o banco: ele oferece pagar.")
            }
            // Sites are not opened (no browser in the lens): a menu is read for you, any other link goes to the notes.
            is io.alema.qrcore.QrResult.Link -> if (MenuRead.looksLikeMenu(r.url)) openMenu(r.url) else {
                work.execute { Moments.send(this, org.json.JSONObject().put("action", "note").put("kind", "link").put("title", "Link: " + r.host).put("text", r.url).put("source", "qr")) }
                NotesOut.offer(this, "Link: " + r.host, r.url, sensitive = false)
                toast("link guardado nas notas: ${r.host}")
            }
            is io.alema.qrcore.QrResult.Wifi -> {
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Wi-Fi", r.password ?: ""))
                try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
                toast("Senha do Wi-Fi ${r.ssid} copiada")
            }
            is io.alema.qrcore.QrResult.Contact -> {
                val name = r.name
                if (name.isNullOrBlank()) toast("contato sem nome")
                else { work.execute { Moments.send(this, org.json.JSONObject().put("action", "add_people").put("people", org.json.JSONArray().put(org.json.JSONObject()
                    .put("name", name).put("company", r.org ?: "").put("role", r.title ?: "").put("contact_phone", r.phones.firstOrNull() ?: "").put("contact_email", r.emails.firstOrNull() ?: "").put("source", "qr")))) }
                    toast("guardado: $name") }
            }
            is io.alema.qrcore.QrResult.Text -> { clip.setPrimaryClip(android.content.ClipData.newPlainText("QR", r.text)); NotesOut.offer(this, "QR: " + r.text.take(30), r.text, sensitive = false); toast("texto copiado") }
            else -> Unit
        }
    }

    private fun openMenu(url: String) {
        hud.cardTitle = "Cardápio"; hud.cardLines = listOf("lendo o cardápio…"); hud.invalidate()
        work.execute {
            val m = MenuRead.read(this, url)
            main.post {
                if (m == null) { hud.cardTitle = "Cardápio"; hud.cardLines = listOf("não consegui ler este cardápio", "o link ficou nas notas"); hud.invalidate()
                    work.execute { Moments.send(this, org.json.JSONObject().put("action", "note").put("kind", "link").put("title", "Cardápio: " + (android.net.Uri.parse(url).host ?: url)).put("text", url).put("source", "qr")) }
                    return@post }
                hud.cardTitle = m.place.ifBlank { "Cardápio" }; hud.cardLines = m.categories.take(6) + listOf("diga uma categoria"); hud.invalidate()
                // Gemini Live walks the user through it; the menu itself is not memory.
                live.context("(contexto, não é fala do usuário) O usuário escaneou o cardápio do restaurante${if (m.place.isNotBlank()) " " + m.place else ""}. " +
                    "Apresente as categorias numa frase curta e pergunte qual ele quer ver; depois diga os pratos e preços da categoria escolhida, poucos por vez. " +
                    "Não guarde o cardápio na memória. CARDÁPIO: ${m.json.take(20000)}")
                if (!liveOn) { liveOn = true; live.start() }
            }
        }
    }

    /**
     * Spec 1i: a person's card said once, softly, before the conversation gets going; never repeated by itself
     * (again only after 15 min apart). Unknown faces stay silent until the countdown asks "Guardar?".
     */
    private fun announce(s: FaceBrain.Seen) {
        if (!s.known || !s.sure || s.label.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - (spoken[s.label] ?: -1_000_000L) < 15 * 60_000) return
        spoken[s.label] = now
        voice.soft()
        voice.say((listOf(s.label) + s.lines.filter { !it.startsWith("diga o nome") }).joinToString(". ").replace("última vez:", "Última vez,"))
    }

    private fun setOutput(v: String) { output = v; Voice.setOutput(this, v); applyOutput(); toast(when (v) { "tela" -> "saída: só tela"; "tela_voz" -> "saída: tela + voz"; else -> "saída: só voz (óculos sem tela)" }) }

    /** Voice only = the lens shows nothing (as glasses without a display); the camera keeps working. */
    private fun applyOutput() {
        outBtn.text = when (output) { "tela" -> "▭ Tela"; "tela_voz" -> "▭+🔊"; else -> "🔊 Voz" }
        outBtn.setTextColor(if (output == "tela") dim else green)
        hud.alpha = if (output == "voz") 0f else 1f
    }

    override fun onYes(): Boolean = if (qr.offer != null) { main.post { acceptQr() }; true } else false

    private fun legacyCheck(p: FaceBrain.Seen, jpeg: ByteArray) {
        val pid = p.pendingId ?: return
        val old = faces.legacyPeople(); if (old.isEmpty()) return
        main.post {
            if (legacyBusy || !legacyChecked.add(pid)) return@post
            legacyBusy = true
            val e = legacy ?: FaceEngine(applicationContext).also { legacy = it }
            val small = Frames.shrink(jpeg, 640) ?: jpeg
            e.describe(android.util.Base64.encodeToString(small, android.util.Base64.NO_WRAP)) { r ->
                legacyBusy = false
                val f = (r as? FaceEngine.Result.Faces)?.faces?.firstOrNull() ?: return@describe
                val known = old.mapNotNull { pp -> pp.embedding?.let { io.alema.facecore.FaceMatch.Known(pp.name, it) } }
                val m = io.alema.facecore.FaceMatch.best(f.descriptor, known) as? io.alema.facecore.FaceMatch.Result.Match ?: return@describe
                work.execute { if (faces.adopt(pid, m.key)) main.post { toast("reconhecido pela memória antiga: ${m.key}") } }
            }
        }
    }

    /** Analysis bitmap → screen, as the preview shows it (FILL_CENTER, mirrored for the front camera). */
    private fun toTag(s: FaceBrain.Seen, bw: Int, bh: Int, primary: Boolean, countdown: Int): HudOverlay.FaceTag {
        val k = maxOf(hud.width.toFloat() / bw, hud.height.toFloat() / bh)
        val ox = (hud.width - bw * k) / 2; val oy = (hud.height - bh * k) / 2
        val cxImg = if (front) bw - (s.x + s.w / 2) else s.x + s.w / 2
        val key = s.localId ?: s.pendingId ?: s.label
        val title = when { s.label.isEmpty() -> ""; s.sure -> s.label; else -> s.label + "?" }
        val lines = if (!s.known) s.lines + listOf("diga o nome: \"essa é a Ana\"") else s.lines
        return HudOverlay.FaceTag(key, title, lines, ox + cxImg * k, oy + (s.y + s.h / 2) * k, s.w * k, s.h * k, primary, s.known, countdown)
    }

    override fun onFaces(seen: List<FaceBrain.Seen>, bw: Int, bh: Int) {
        lastSeen = seen; lastBw = bw; lastBh = bh
        val primary = seen.firstOrNull { it.label.isNotEmpty() }
        if (primary != null && !primary.isMe && ::ears.isInitialized && !liveOn) ears.faceSeen(if (primary.known) primary.label else null)
        // It was me all along (the encounter had started while the face was still unknown): drop it.
        if (primary != null && primary.isMe && ::ears.isInitialized && ears.who == null) ears.discard()
        if (primary != null && !primary.isMe && speaks) announce(primary)
        // Countdown: a new face that stays in front of you for 7 s; then 5…0 and it is kept.
        val pid = primary?.pendingId
        // The buffer runs from the first second (in memory); at 7 s the countdown starts; cancelling discards it all.
        if (pid != null && countdownId == null && System.currentTimeMillis() - primary.pendingSince > 7000) {
            countdownId = pid; countdownAt = SystemClock.elapsedRealtime()
            if (speaks) voice.say("Pessoa nova. Guardar? Dois toques cancelam.")
        }
        if (countdownId != null && seen.none { it.pendingId == countdownId }) countdownId = null
        val left = countdownId?.let { 5 - ((SystemClock.elapsedRealtime() - countdownAt) / 1000).toInt() } ?: 0
        // Known person: records at once, no countdown (spec 10/10). New person: countdown, then recording.
        hud.faces = seen.filter { it.label.isNotEmpty() }.map {
            val cd = if (it.pendingId != null && it.pendingId == countdownId) maxOf(left, 1) else 0
            toTag(it, bw, bh, it === primary, cd).copy(recording = it === primary && it.known && !it.isMe && cd == 0)
        }
        hud.invalidate()
    }

    private fun countdownTick() {
        val id = countdownId ?: return
        if (SystemClock.elapsedRealtime() - countdownAt >= 5000) {
            countdownId = null
            work.execute { faces.keep(id); main.post { toast("encontro guardado — diga o nome da pessoa"); if (speaks) voice.tick() } }
        } else onFaces(lastSeen, lastBw, lastBh)
    }

    private fun cancelCountdown() {
        val id = countdownId ?: return
        countdownId = null
        work.execute { faces.ignore(id) }
        ears.discard()
        toast("descartado: nada foi guardado")
    }

    override fun onNamed(name: String) { toast("guardado: $name"); if (speaks) { voice.tick(); voice.say(name) } }
    override fun onSaved(title: String) { toast("guardado: $title"); if (speaks) { voice.tick(); voice.say("Guardado: $title") } }
    override fun onBrief(name: String, lines: List<String>) {
        val t = name.trim().let { if (it.firstOrNull()?.let { c -> !c.isLetterOrDigit() } == true) it else "✦ $it" }
        hud.cardTitle = t; hud.cardLines = lines.filter { it.isNotBlank() }; hud.invalidate()
        briefClear?.let { main.removeCallbacks(it) }
        // "Lembrando…" waits for the answer; any card goes away by itself (Marcus, 08/11: it stayed forever).
        briefClear = Runnable { if (hud.cardTitle == t) { hud.cardTitle = null; hud.cardLines = emptyList(); hud.invalidate() } }.also { main.postDelayed(it, if ("…" in t) 30_000L else 20_000L) }
    }
    private var briefClear: Runnable? = null

    override fun onLyric(title: String, lines: List<String>) {
        hud.cardTitle = title; hud.cardLines = lines; hud.invalidate()
        briefClear?.let { main.removeCallbacks(it) }
        briefClear = Runnable { if (hud.cardTitle == title) { hud.cardTitle = null; hud.cardLines = emptyList(); hud.invalidate() } }.also { main.postDelayed(it, 15_000L) }
    }

    /** The music app came to the front to start playing: back to the glasses view. */
    override fun comeBack() {
        try { startActivity(Intent(this, EmulatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    private fun toggleLive() {
        if (!liveOn && Backend.offlineOnly(this)) { toast("O Live está desligado em Ajustes → Assistente (\"Desligar o Live\"). Ligue lá para falar com o assistente."); return }
        if (liveOn) { if (live.speaking) live.interrupt() else { live.stop(); liveOn = false; ears.resume() } }
        else { liveOn = true; ears.pause(); live.start() }
    }

    /**
     * A question heard between encounters (standby): memory questions are answered offline-first (one text call
     * to Gemini, phone voice, HUD card); anything else addressed to the assistant opens the Live with that sentence.
     */
    private fun standbyQuestion(q: String) {
        if (liveOn) return
        val low = q.lowercase()
        val addressed = Regex("""^(ei|ô|oi|olha|escuta)?[,\s]*(assistente|visual\s?context|visualcontext|óculos)\b""").find(low)
        val body = if (addressed != null) q.substring(addressed.range.last + 1).trim().trimStart(',', ' ') else q
        val lb = body.lowercase()
        val memory = Regex("""o que (eu )?(fiz|anotei|combinei|guardei)|quem (eu )?(encontrei|vi)|quando (foi que )?(eu )?(vi|encontrei|falei)|última vez que""").containsMatchIn(lb)
        val thing = Regex("""onde (eu )?deixei (o |a |as |os |meu |minha )?(.+)|cad[eê] (meu |minha |o |a |as |os )?(.+)""").find(lb)
        when {
            Regex("que horas são").containsMatchIn(lb) -> { val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(System.currentTimeMillis()); say("São $t.", "✦ Agora", listOf(t)) }
            thing != null -> work.execute {
                val name = (thing.groupValues[3].ifEmpty { thing.groupValues[5] }).trim().trimEnd('?', '.')
                val f = Things.find(this, name)
                main.post { if (f.isEmpty()) say("Não tenho registro de onde você deixou $name.", "⌖ $name", listOf("sem registro")) else { val t = f[0]; val w = runCatching { java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale("pt", "BR")).format(java.time.OffsetDateTime.parse(t.at).toInstant().toEpochMilli()) }.getOrDefault(""); say("Você deixou ${t.name}" + (t.where?.let { " em $it" } ?: "") + (if (w.isNotEmpty()) ", $w" else "") + ".", "⌖ ${t.name}", listOf(t.where ?: "lugar não anotado", w)) } }
            }
            memory -> {
                main.post { hud.cardTitle = "✦ Lembrando…"; hud.cardLines = listOf(body.take(38)); hud.invalidate() }
                val days = when { Regex("hoje").containsMatchIn(lb) -> 1; Regex("ontem").containsMatchIn(lb) -> 2; Regex("semana").containsMatchIn(lb) -> 7; Regex("m[eê]s").containsMatchIn(lb) -> 31; else -> 30 }
                work.execute { val a = Recall.ask(this, body, days); main.post { if (a == null) say("Não consegui consultar a memória agora.", "✦ Memória", listOf("sem resposta")) else say(a.say, a.title, a.lines) } }
            }
            addressed != null && body.length > 3 -> { live.context(body); toggleLive() } // "assistente, …": the Live opens and answers that sentence
        }
    }
    private var cardClear: Runnable? = null
    private fun say(text: String, title: String, lines: List<String>) {
        hud.cardTitle = title; hud.cardLines = lines; hud.invalidate()
        if (speaks || Voice.output(this) != "tela") voice.say(text, interrupt = true)
        cardClear?.let { main.removeCallbacks(it) }
        cardClear = Runnable { if (hud.cardTitle == title) hud.clearCard() }.also { main.postDelayed(it, 25_000) }
    }

    override fun placeName(): String? = Where.location(this)?.let { Where.placeLabel(this, it) }
    override fun frame(): ByteArray? = lastJpeg
    override fun faces(): FaceBrain = faces
    override fun onState(state: String) {
        hud.listening = state == "listening"; hud.speaking = state == "speaking"
        talk.text = when { state == "connecting" -> "… conectando"; state == "speaking" -> "■ Interromper"; state == "listening" -> "■ Encerrar"; else -> "🎙 Falar" }
        if (state.startsWith("error")) {
            val why = state.removePrefix("error: "); toast(why); liveOn = false; talk.text = "🎙 Falar"; ears.resume()
            val now = android.os.SystemClock.elapsedRealtime()
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val offline = cm.activeNetwork == null
            if (offline && speaks && now - offlineSaidAt > 5 * 60_000) { offlineSaidAt = now; voice.say("Sem internet para o assistente. Continuo avisando pelo celular.", interrupt = true) }
        }
        hud.invalidate()
    }
    override fun onTranscript(lines: List<String>, userLine: String?) {
        hud.transcript = if (userLine != null && lines.isEmpty()) listOf("🎙 " + userLine.takeLast(34)) else lines
        hud.invalidate()
        // The answer leaves the lens by itself (Marcus, 07/11: it stayed fixed on screen).
        main.removeCallbacks(clearTranscript); main.postDelayed(clearTranscript, 9000)
    }
    private val clearTranscript = Runnable { if (!live.speaking) { hud.transcript = emptyList(); hud.invalidate() } else main.postDelayed(this::clearLater, 3000) }
    private fun clearLater() { main.removeCallbacks(clearTranscript); main.postDelayed(clearTranscript, 3000) }

    private fun refreshPlaces(force: Boolean) {
        if (!placesOn) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - placesAt < 60_000) return
        placesAt = now
        work.execute {
            val loc = Where.location(this)
            if (loc == null) { main.post { toast("sem GPS ainda") }; return@execute }
            val near = Where.nearby(loc, 400, 12)
            // Nothing named around (or Overpass down): at least say where we are (Marcus, 07/11).
            val addr = if (near.isEmpty()) Where.address(this, loc) else null
            main.post {
                if (near.isEmpty() && !addr.isNullOrBlank()) toast("📍 $addr")
                // "hidden" = farther than 150 m (approximation of "behind a building" until we have building outlines).
                hud.pins = near.map { HudOverlay.Pin(it.name, "${it.kind} · ${it.dist} m", it.bearing.toFloat(), hidden = it.dist > 150, lat = it.lat, lon = it.lon, kind = it.kind, dist = it.dist) }; hud.pinFocus = -1
                hud.invalidate()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            countdownTick()
            if (liveOn && live.open && !live.speaking && SystemClock.elapsedRealtime() - live.lastActivity > 40_000) { live.stop(); liveOn = false; ears.resume(); toast("Gemini Live encerrado: 40 s em silêncio") }
            refreshPlaces(false)
            main.postDelayed(this, 250)
        }
    }

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onPause() { super.onPause(); (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this) }
    /** Heading of where the back camera points, whatever the phone's orientation (the device's −Z axis on the map). */
    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9); SensorManager.getRotationMatrixFromVector(r, e.values)
        val east = -r[2]; val north = -r[5]
        if (east * east + north * north < 0.05f) return // phone lying flat: no direction
        // Low-pass on the direction vector + a 2° dead band: pins stay put while the head barely moves (Marcus, 08/11).
        val n = kotlin.math.sqrt(east * east + north * north); sx += 0.12f * (east / n - sx); sy += 0.12f * (north / n - sy)
        val h = ((Math.toDegrees(kotlin.math.atan2(sx.toDouble(), sy.toDouble())).toFloat()) + 360) % 360
        val diff = kotlin.math.abs((h - heading + 540) % 360 - 180)
        if (diff > 2f) { heading = h; hud.heading = h; if (placesOn) hud.invalidate() }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    override fun onDestroy() { ears.finish(); ears.close(); voice.close(); main.removeCallbacksAndMessages(null); live.destroy(); faces.close(); legacy?.destroy(); work.shutdownNow(); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
