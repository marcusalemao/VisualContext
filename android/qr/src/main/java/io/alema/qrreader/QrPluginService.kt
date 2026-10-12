package io.alema.qrreader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusImage
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSnapshotCallbacks
import com.anezium.rokidbus.client.plugin.NexusSnapshotError
import com.anezium.rokidbus.client.plugin.NexusSnapshotSession
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.ImageSurfaceContract
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import io.alema.qrcore.QrParser
import java.util.concurrent.Executors

/**
 * Leitor QR — a Rokid Nexus plugin.
 * Open it, look at a QR code: the glasses take still photos for a few seconds, the phone reads them
 * in memory, and the HUD shows what the code says (Wi-Fi, link, Pix, contact).
 * Tap = send it to the phone · Back = read another / close.
 * Photos are never saved and never leave the phone (the app has no internet permission).
 */
class QrPluginService : NexusPluginService() {
    private enum class State { IDLE, SCANNING, RESULT, GAVE_UP }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var surface: NexusSurfaceSession? = null
    private var snapshot: NexusSnapshotSession? = null
    private var state = State.IDLE
    private var scanStartedAt = 0L
    private var attempts = 0
    private var card: Card? = null
    private var revision = 0
    private var generation = 0   // bumps on every new scan, so late results from an old scan are ignored

    override fun onNexusOpen() {
        surface = nexusSurfaceSession(SURFACE_ID)
        startScan()
    }

    override fun onNexusClose() = stopAll()

    override fun onDestroy() {
        stopAll()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> onTap()
            KeyEvent.KEYCODE_BACK -> onBack()
        }
    }

    private fun onTap() {
        when (state) {
            State.RESULT -> {
                val c = card ?: return
                val a = c.action ?: return
                val sent = PhoneHandoff.send(applicationContext, a, c.lines.firstOrNull().orEmpty())
                show(
                    c.title,
                    if (sent) listOf("✓ mandado para o celular", "toque na notificação de lá") else
                        listOf("o celular não deixou avisar", "abra o Leitor QR no celular", "e permita notificações"),
                    "voltar: ler outro",
                )
            }
            State.GAVE_UP, State.IDLE -> startScan()
            State.SCANNING -> Unit
        }
    }

    private fun onBack() {
        when (state) {
            State.RESULT, State.GAVE_UP -> startScan()
            else -> { stopAll() }
        }
    }

    private fun startScan() {
        generation++
        state = State.SCANNING
        card = null
        attempts = 0
        scanStartedAt = SystemClock.elapsedRealtime()
        show("Leitor QR", listOf("olhe para o QR code", "de perto, sem pressa"), "lendo… · voltar: fechar")
        captureNext(generation)
    }

    private fun captureNext(gen: Int) {
        if (gen != generation || state != State.SCANNING) return
        if (SystemClock.elapsedRealtime() - scanStartedAt > SCAN_WINDOW_MS) {
            state = State.GAVE_UP
            show("Leitor QR", listOf("não achei um QR code", "chegue mais perto", "ou melhore a luz"), "toque: tentar de novo · voltar: fechar")
            return
        }
        attempts++
        val s = nexusSnapshotSession(object : NexusSnapshotCallbacks {
            override fun onSnapshotCaptured(jpeg: ByteArray) {
                io.execute {
                    val raw = QrDecoder.decode(jpeg)   // jpeg is dropped right after this line
                    main.post { onDecoded(gen, raw) }
                }
            }

            override fun onSnapshotError(error: NexusSnapshotError) {
                main.post { onSnapshotFailed(gen, error) }
            }
        })
        snapshot = s
        val r = s?.capture() ?: NexusSdkResult.NOT_REGISTERED
        if (r != NexusSdkResult.SENT) onCaptureRefused(r)
    }

    private fun onDecoded(gen: Int, raw: String?) {
        if (gen != generation || state != State.SCANNING) return
        if (raw.isNullOrBlank()) {
            main.postDelayed({ captureNext(gen) }, BETWEEN_SHOTS_MS)
            return
        }
        val c = ResultCard.of(QrParser.parse(raw))
        card = c
        state = State.RESULT
        show(c.title, c.lines, if (c.action == null) "voltar: ler outro" else c.footer)
    }

    private fun onSnapshotFailed(gen: Int, error: NexusSnapshotError) {
        if (gen != generation || state != State.SCANNING) return
        when (error) {
            NexusSnapshotError.BUSY, NexusSnapshotError.TIMEOUT, NexusSnapshotError.CAPTURE_FAILED ->
                main.postDelayed({ captureNext(gen) }, RETRY_MS)
            NexusSnapshotError.LINK_DOWN -> {
                state = State.GAVE_UP
                show("Leitor QR", listOf("os óculos estão sem conexão", "com o celular"), "toque: tentar de novo · voltar: fechar")
            }
            NexusSnapshotError.CANCELLED -> Unit
            NexusSnapshotError.ERROR -> main.postDelayed({ captureNext(gen) }, RETRY_MS)
        }
    }

    private fun onCaptureRefused(r: NexusSdkResult) {
        state = State.GAVE_UP
        val lines = when (r) {
            NexusSdkResult.CAPABILITY_NOT_GRANTED -> listOf("falta liberar a câmera", "no Nexus: Plugins →", "Leitor QR → câmera")
            NexusSdkResult.CAPABILITY_NOT_AVAILABLE -> listOf("este Nexus ainda não tem", "foto da câmera para plugins", "atualize o Nexus")
            else -> listOf("o Nexus ainda não aprovou", "o plugin Leitor QR")
        }
        show("Leitor QR", lines, "toque: tentar de novo · voltar: fechar")
    }

    private fun stopAll() {
        generation++
        state = State.IDLE
        snapshot?.cancel()
        snapshot = null
        main.removeCallbacksAndMessages(null)
        surface?.hide()
        surface = null
    }

    /** Whole card as one image when the glasses support it (sharpest result); text card otherwise. */
    private fun show(title: String, lines: List<String>, footer: String) {
        val s = surface ?: return
        revision++
        if (nexusClient?.supportsImageSurface == true) {
            val png = Hud.render(title, lines, footer)
            val image = NexusImage(
                contentKey = "qr-card-$revision",
                mimeType = ImageSurfaceContract.MIME_PNG,
                pixelWidth = Hud.WIDTH,
                pixelHeight = Hud.HEIGHT,
                handlesBack = true,
            )
            if (s.showImage(image, png) == NexusSdkResult.SENT) return
        }
        s.showCard(NexusCard(title = title, lines = lines, footer = footer, contentKey = "qr-text-$revision", handlesBack = true))
    }

    companion object {
        private const val SURFACE_ID = "leitor-qr"
        private const val SCAN_WINDOW_MS = 25_000L
        private const val BETWEEN_SHOTS_MS = 350L
        private const val RETRY_MS = 800L
    }
}
