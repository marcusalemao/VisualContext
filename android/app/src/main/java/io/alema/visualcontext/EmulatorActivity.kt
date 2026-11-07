package io.alema.visualcontext

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    @Volatile private var lastJpeg: ByteArray? = null
    @Volatile private var facesOn = true
    @Volatile private var scanning = false
    private var lastFaceScan = 0L
    private var placesOn = false
    private var placesAt = 0L
    private var heading = 0f
    private var liveOn = false
    private var lastDescribeAt = 0L
    private var front = false
    /** Countdown for the new face in front: pending id → when it started. */
    private var countdownId: String? = null
    private var countdownAt = 0L
    private var lastSeen: List<FaceBrain.Seen> = emptyList()
    private var lastBw = 1; private var lastBh = 1

    private val green = Color.rgb(0, 255, 136)
    private val dim = Color.rgb(90, 120, 104)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        hud = HudOverlay(this).apply {
            // Tap anywhere on the lens during the countdown = cancel (the swipe on the glasses).
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP && countdownId != null) { cancelCountdown(); true } else false }
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
        hud.footer = listOf(if (facesOn) "pessoas: ligado" else "pessoas: desligado", if (placesOn) "lugares: ligado" else "lugares: desligado").joinToString(" · ")
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
        } finally { scanning = false }
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
        // Countdown: a new face that stays in front of you for 7 s; then 5…0 and it is kept.
        val pid = primary?.pendingId
        // The buffer runs from the first second (in memory); at 7 s the countdown starts; cancelling discards it all.
        if (pid != null && countdownId == null && System.currentTimeMillis() - primary.pendingSince > 7000) { countdownId = pid; countdownAt = SystemClock.elapsedRealtime() }
        if (countdownId != null && seen.none { it.pendingId == countdownId }) countdownId = null
        val left = countdownId?.let { 5 - ((SystemClock.elapsedRealtime() - countdownAt) / 1000).toInt() } ?: 0
        // Known person: records at once, no countdown (spec 10/10). New person: countdown, then recording.
        hud.faces = seen.filter { it.label.isNotEmpty() }.map {
            val cd = if (it.pendingId != null && it.pendingId == countdownId) maxOf(left, 1) else 0
            toTag(it, bw, bh, it === primary, cd).copy(recording = it === primary && it.known && cd == 0)
        }
        hud.invalidate()
    }

    private fun countdownTick() {
        val id = countdownId ?: return
        if (SystemClock.elapsedRealtime() - countdownAt >= 5000) {
            countdownId = null
            work.execute { faces.keep(id); main.post { toast("encontro guardado — diga o nome da pessoa") } }
        } else onFaces(lastSeen, lastBw, lastBh)
    }

    private fun cancelCountdown() {
        val id = countdownId ?: return
        countdownId = null
        work.execute { faces.ignore(id) }
        toast("descartado: nada foi guardado")
    }

    override fun onNamed(name: String) { toast("guardado: $name") }

    private fun toggleLive() {
        if (liveOn) { if (live.speaking) live.interrupt() else { live.stop(); liveOn = false } }
        else { liveOn = true; live.start() }
    }

    override fun frame(): ByteArray? = lastJpeg
    override fun faces(): FaceBrain = faces
    override fun onState(state: String) {
        hud.listening = state == "listening"; hud.speaking = state == "speaking"
        talk.text = when { state == "connecting" -> "… conectando"; state == "speaking" -> "■ Interromper"; state == "listening" -> "■ Encerrar"; else -> "🎙 Falar" }
        if (state.startsWith("error")) { toast(state.removePrefix("error: ")); liveOn = false; talk.text = "🎙 Falar" }
        hud.invalidate()
    }
    override fun onTranscript(lines: List<String>, userLine: String?) {
        hud.transcript = if (userLine != null && lines.isEmpty()) listOf("🎙 " + userLine.takeLast(34)) else lines
        hud.invalidate()
    }

    private fun refreshPlaces(force: Boolean) {
        if (!placesOn) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - placesAt < 60_000) return
        placesAt = now
        work.execute {
            val loc = Where.location(this)
            if (loc == null) { main.post { toast("sem GPS ainda") }; return@execute }
            val near = Where.nearby(loc, 300, 6)
            main.post {
                if (near.isEmpty()) toast("nenhum lugar conhecido num raio de 300 m")
                // "hidden" = farther than 150 m (approximation of "behind a building" until we have building outlines).
                hud.pins = near.map { HudOverlay.Pin(it.name, "${it.kind} · ${it.dist} m", it.bearing.toFloat(), hidden = it.dist > 150) }
                hud.invalidate()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            countdownTick()
            if (liveOn && live.open && !live.speaking && SystemClock.elapsedRealtime() - live.lastActivity > 40_000) { live.stop(); liveOn = false; toast("Gemini Live encerrado: 40 s em silêncio") }
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
        val h = ((Math.toDegrees(kotlin.math.atan2(east.toDouble(), north.toDouble())).toFloat()) + 360) % 360
        if (kotlin.math.abs(h - heading) > 1f) { heading = h; hud.heading = h; if (placesOn) hud.invalidate() }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    override fun onDestroy() { main.removeCallbacksAndMessages(null); live.destroy(); faces.close(); work.shutdownNow(); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
