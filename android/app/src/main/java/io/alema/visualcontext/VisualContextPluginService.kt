package io.alema.visualcontext

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusImage
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.ImageSurfaceContract
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import java.util.concurrent.Executors

/**
 * VisualContext as a headless Nexus plugin — v0.1 "hello glasses":
 * opens a card on the HUD that proves the whole chain works
 * (glasses ↔ Nexus ↔ this phone app ↔ VisualContext backend with the device token).
 * Tap = refresh · Back = close. Camera, microphone and faces come next.
 */
class VisualContextPluginService : NexusPluginService() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var surface: NexusSurfaceSession? = null
    private var revision = 0

    override fun onNexusOpen() {
        surface = nexusSurfaceSession(SURFACE_ID)
        show("VisualContext", listOf("conectando à memória…"), "toque: atualizar · voltar: fechar")
        refresh()
    }

    override fun onNexusClose() {
        surface?.hide()
        surface = null
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> refresh()
            KeyEvent.KEYCODE_BACK -> { surface?.hide(); surface = null }
        }
    }

    private fun refresh() {
        io.execute {
            val r = Backend.people(applicationContext)
            main.post {
                when (r) {
                    is Backend.Result.People -> show(
                        "VisualContext",
                        listOf("● conectado", "${r.count} pessoas na memória") + r.newest.map { "· $it" },
                        "toque: atualizar · voltar: fechar",
                    )
                    is Backend.Result.Failed -> show(
                        "VisualContext",
                        listOf("○ ${r.reason}", "abra os ajustes do plugin", "no celular e cole o token"),
                        "voltar: fechar",
                    )
                }
            }
        }
    }

    /** Whole card as one image when the glasses support it (sharpest result); text card otherwise. */
    private fun show(title: String, lines: List<String>, footer: String) {
        val s = surface ?: return
        revision++
        if (nexusClient?.supportsImageSurface == true) {
            val png = HudCard.render(title, lines, footer)
            val image = NexusImage(
                contentKey = "vc-card-$revision",
                mimeType = ImageSurfaceContract.MIME_PNG,
                pixelWidth = HudCard.WIDTH,
                pixelHeight = HudCard.HEIGHT,
                handlesBack = true,
            )
            if (s.showImage(image, png) == NexusSdkResult.SENT) return
        }
        s.showCard(NexusCard(title = title, lines = lines, footer = footer, contentKey = "vc-text-$revision", handlesBack = true))
    }

    private companion object {
        const val SURFACE_ID = "main"
    }
}
