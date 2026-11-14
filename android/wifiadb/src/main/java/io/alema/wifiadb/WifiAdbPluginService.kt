package io.alema.wifiadb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusImage
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.BusPaths
import com.anezium.rokidbus.shared.ImageSurfaceContract
import com.anezium.rokidbus.shared.WirelessAdbAction
import com.anezium.rokidbus.shared.WirelessAdbContract
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import org.json.JSONObject
import java.util.UUID

/**
 * "Instalar pelo Wi-Fi": asks Nexus to turn on wireless debugging on the glasses and shows,
 * on the HUD and on the phone, the two adb commands to type on the computer.
 * Tap = new pairing code · swipe = turn debugging off · Back = close (debugging stays on so `adb connect` works).
 */
class WifiAdbPluginService : NexusPluginService() {
    private val main = Handler(Looper.getMainLooper())
    private var surface: NexusSurfaceSession? = null
    private var revision = 0
    private var last: AdbInfo? = null

    override fun onNexusOpen() {
        surface = nexusSurfaceSession(SURFACE_ID)
        show("Instalar pelo Wi-Fi", listOf("ligando a depuração sem fio…"), "voltar: fechar")
        request(WirelessAdbAction.ENABLE)
    }

    override fun onNexusClose() {
        main.removeCallbacksAndMessages(null)
        surface?.hide()
        surface = null
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> request(WirelessAdbAction.START_PAIRING)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                request(WirelessAdbAction.DISABLE)
                show("Instalar pelo Wi-Fi", listOf("depuração sem fio desligada", "toque: ligar de novo"), "voltar: fechar")
            }
            KeyEvent.KEYCODE_BACK -> onNexusClose()
        }
    }

    private fun request(action: WirelessAdbAction) {
        val client = nexusClient
        if (client == null) {
            show("Instalar pelo Wi-Fi", listOf("o Nexus ainda não aprovou", "este plugin"), "voltar: fechar"); return
        }
        val sent = client.send(BusPaths.WIRELESS_ADB_REQUEST, UUID.randomUUID().toString(), WirelessAdbContract.request(action))
        if (!sent) show("Instalar pelo Wi-Fi", listOf("falta liberar a permissão", "Nexus → Plugins →", "Instalar pelo Wi-Fi"), "voltar: fechar")
    }

    override fun onNexusMessage(path: String, id: String, payload: JSONObject) {
        if (path != BusPaths.WIRELESS_ADB_REPLY) return
        val r = WirelessAdbContract.parseReply(payload) ?: return
        main.post { onReply(r.action, r.success, r.wifiConnected, r.enabled, r.host, r.connectPort, r.pairingPort, r.pairingCode, r.errorCode ?: r.message) }
    }

    private fun onReply(action: WirelessAdbAction, success: Boolean, wifi: Boolean, enabled: Boolean, host: String?,
                        connectPort: Int?, pairingPort: Int?, code: String?, error: String?) {
        if (action == WirelessAdbAction.DISABLE) return
        val info = AdbInfo(host, connectPort, pairingPort, code ?: last?.pairingCode, wifi, enabled, if (success) null else (error ?: "erro"))
        last = info
        // Debugging on but no pairing code yet: ask for one, so the first install works without a second tap.
        if (success && enabled && action == WirelessAdbAction.ENABLE && code == null) {
            request(WirelessAdbAction.START_PAIRING)
        }
        show("Instalar pelo Wi-Fi", AdbCommands.hudLines(info), "toque: novo código · deslize: desligar")
        Store.save(this, info)
        if (success && enabled) notifyPhone(info)
    }

    private fun notifyPhone(i: AdbInfo) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Instalar pelo Wi-Fi", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(this, 1, Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Comandos prontos para o computador")
            .setContentText(AdbCommands.connect(i) ?: "toque para ver")
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    private fun show(title: String, lines: List<String>, footer: String) {
        val s = surface ?: return
        revision++
        if (nexusClient?.supportsImageSurface == true) {
            val png = Hud.render(title, lines, footer)
            val img = NexusImage(contentKey = "adb-$revision", mimeType = ImageSurfaceContract.MIME_PNG,
                pixelWidth = Hud.WIDTH, pixelHeight = Hud.HEIGHT, handlesBack = true)
            if (s.showImage(img, png) == NexusSdkResult.SENT) return
        }
        s.showCard(NexusCard(title = title, lines = lines, footer = footer, contentKey = "adb-text-$revision", handlesBack = true))
    }

    companion object {
        private const val SURFACE_ID = "instalar-wifi"
        private const val CHANNEL = "instalar_wifi"
    }
}
