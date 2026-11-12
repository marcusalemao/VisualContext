package io.alema.zap

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import java.util.Locale

/**
 * "Zap" in Quick Settings (Samsung: Painel rápido): pauses automatic sending.
 *
 * Some banking apps refuse to open while another app's Accessibility service is
 * on (they fight remote-control scams). One tap turns Zap's off right away;
 * Android only lets the wearer turn it back on in its own Accessibility screen,
 * so the next tap opens it there.
 */
class ZapSendTile : TileService() {
    private val pt get() = Locale.getDefault().language == "pt"

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (ZapSendService.isEnabled(this) && ZapSendService.pause()) {
            Toast.makeText(
                this,
                if (pt) "Envio automático pausado. Toque de novo para religar." else "Automatic sending paused. Tap again to turn it back on.",
                Toast.LENGTH_LONG,
            ).show()
            refresh(paused = true)
            return
        }
        val settings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, settings, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(settings)
        }
    }

    private fun refresh(paused: Boolean = !ZapSendService.isEnabled(this)) {
        val tile = qsTile ?: return
        tile.label = "Zap"
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                paused && pt -> "Envio pausado"
                paused -> "Sending paused"
                pt -> "Envio automático"
                else -> "Auto send"
            }
        }
        tile.updateTile()
    }
}
