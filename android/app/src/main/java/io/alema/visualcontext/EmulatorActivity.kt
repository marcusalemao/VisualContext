package io.alema.visualcontext

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
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
 * The glasses on your phone. The phone camera is the glasses camera, its mic and speaker the
 * glasses audio, its GPS and compass the phone's. The same brains (faces, Live, places) will draw
 * on the real glasses later through the Nexus plugin or the on-glasses app.
 *
 * Buttons stand in for the touchpad: Quem é? (tap) · Falar (long press / AI button) · Lugares · Fechar.
 */
class EmulatorActivity : ComponentActivity(), LiveBrain.Host, SensorEventListener {
    private lateinit var preview: PreviewView
    private lateinit var hud: HudOverlay
    private lateinit var talk: Button
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private val faces by lazy { FaceBrain(this) }
    private val live by lazy { LiveBrain(this, this) }
    @Volatile private var lastJpeg: ByteArray? = null
    @Volatile private var frameW = 1; @Volatile private var frameH = 1
    private var autoFaces = true
    private var lastFaceScan = 0L
    @Volatile private var scanning = false
    private var placesOn = false
    private var placesAt = 0L
    private var heading = 0f
    private var liveOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        hud = HudOverlay(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); addView(preview); addView(hud) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(150, 0, 0, 0)); setPadding(8, 6, 8, 6)
        }
        fun btn(t: String, cb: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.rgb(0, 255, 136)); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { cb() } }
        bar.addView(btn("Quem é?") { who() })
        talk = btn("Falar") { toggleLive() }; bar.addView(talk)
        bar.addView(btn("Lugares") { placesOn = !placesOn; if (!placesOn) { hud.pins = emptyList(); hud.invalidate() } else refreshPlaces(true); toast(if (placesOn) "Lugares ligados" else "Lugares desligados") })
        bar.addView(btn("Limpar") { hud.clearCard(); hud.transcript = emptyList(); hud.footer = ""; hud.invalidate() })
        bar.addView(btn("Fechar") { finish() })
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
        hud.footer = "toque: quem é? · falar: Gemini Live · lugares: cards"
        val need = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) startCamera() else requestPermissions(need.toTypedArray(), 1)
        work.execute { faces.refreshPeople(true); faces.engine() }
        main.postDelayed(ticker, 1000)
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
                    // Keep the latest frame (for olhar / quem é); scan for faces every 1.5 s when idle.
                    if (now - lastFaceScan > 1500 || lastJpeg == null) {
                        val j = Frames.jpeg(img, 80)
                        if (j != null) { lastJpeg = j; val rot = img.imageInfo.rotationDegrees; frameW = if (rot % 180 == 0) img.width else img.height; frameH = if (rot % 180 == 0) img.height else img.width }
                        if (autoFaces && !scanning && now - lastFaceScan > 1500) { lastFaceScan = now; scan() }
                    }
                } finally { img.close() }
            }
            p.unbindAll(); p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, an)
        }, ContextCompat.getMainExecutor(this))
    }

    /** Quiet scan: labels beside heads, no card, no Gemini call (unknown faces are only described on "Quem é?"). */
    private fun scan() {
        val j = lastJpeg ?: return
        scanning = true
        work.execute {
            try {
                val b = Frames.bitmap(j, 640) ?: return@execute
                val seen = faces.look(b, describeUnknown = false)
                main.post { hud.labels = seen.map { toLabel(it, b.width, b.height) }; hud.invalidate() }
            } finally { scanning = false }
        }
    }

    private fun toLabel(s: FaceBrain.Seen, bw: Int, bh: Int): HudOverlay.Label {
        val k = maxOf(hud.width.toFloat() / bw, hud.height.toFloat() / bh)
        val ox = (hud.width - bw * k) / 2; val oy = (hud.height - bh * k) / 2
        return HudOverlay.Label(if (s.sure) s.label else s.label + "?", ox + (s.x + s.w / 2) * k, oy + (s.y + s.h / 2) * k, s.w * k, s.h * k, dashed = !s.known)
    }

    /** Tap: the full short card for the biggest face, with the neutral description for a face without a name. */
    private fun who() {
        val j = lastJpeg ?: return toast("sem imagem ainda")
        hud.footer = "olhando…"; hud.invalidate()
        work.execute {
            val b = Frames.bitmap(j, 960) ?: return@execute
            val seen = faces.look(b, describeUnknown = true)
            main.post { onFaces(seen); hud.labels = seen.map { toLabel(it, b.width, b.height) }; hud.footer = ""; hud.invalidate() }
        }
    }

    override fun onFaces(seen: List<FaceBrain.Seen>) {
        val s = seen.firstOrNull()
        if (s == null) { hud.cardTitle = "ninguém"; hud.cardLines = listOf("não vi rosto"); hud.invalidate(); return }
        hud.cardTitle = if (s.sure) s.label else s.label + "?"
        hud.cardLines = s.lines + (if (!s.known) listOf("diga o nome e o card aprende") else emptyList())
        hud.invalidate()
    }

    private fun toggleLive() {
        if (liveOn) { if (live.speaking) live.interrupt() else { live.stop(); liveOn = false } }
        else { liveOn = true; live.start() }
    }

    override fun frame(): ByteArray? = lastJpeg
    override fun faces(): FaceBrain = faces
    override fun onState(state: String) {
        hud.listening = state == "listening"; hud.speaking = state == "speaking"
        talk.text = when { state == "connecting" -> "…"; state == "speaking" -> "Interromper"; state == "listening" -> "Encerrar"; else -> "Falar" }
        when {
            state.startsWith("error") -> { hud.footer = state.removePrefix("error: "); liveOn = false; talk.text = "Falar" }
            state == "listening" -> hud.footer = "pode falar"
            state == "idle" -> hud.footer = ""
        }
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
                // Two nearest, the rest only as they come into view; "hidden" = farther than 150 m (approximation of "behind a building").
                hud.pins = near.map { HudOverlay.Pin(it.name, "${it.kind} · ${it.dist} m", it.bearing.toFloat(), hidden = it.dist > 150) }
                hud.invalidate()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (liveOn && live.open && !live.speaking && SystemClock.elapsedRealtime() - live.lastActivity > 40_000) { live.stop(); liveOn = false; hud.footer = "até já"; hud.invalidate() }
            refreshPlaces(false)
            main.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onPause() { super.onPause(); (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9); SensorManager.getRotationMatrixFromVector(r, e.values)
        // Landscape: remap so "forward" is the camera's direction.
        val m = FloatArray(9); SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, m)
        val o = FloatArray(3); SensorManager.getOrientation(m, o)
        val h = (Math.toDegrees(o[0].toDouble()).toFloat() + 360) % 360
        if (kotlin.math.abs(h - heading) > 1f) { heading = h; hud.heading = h; if (placesOn) hud.invalidate() }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    override fun onDestroy() { main.removeCallbacksAndMessages(null); live.destroy(); faces.close(); work.shutdownNow(); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
