package io.alema.qrreader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import io.alema.qrcore.QrResult

/**
 * The glasses only show; the phone acts. A tap on the glasses posts one notification on the phone,
 * and tapping that notification does the action (join Wi-Fi, open link, copy Pix, save contact).
 * Two taps by a person, never zero: nothing happens on its own.
 */
object PhoneHandoff {
    private const val CHANNEL = "leitor_qr"
    private const val NOTIFICATION_ID = 7001

    const val EXTRA_KIND = "kind"
    const val EXTRA_TEXT = "text"
    const val EXTRA_SSID = "ssid"
    const val EXTRA_PASS = "pass"
    const val EXTRA_SEC = "sec"
    const val EXTRA_HIDDEN = "hidden"
    const val EXTRA_NAME = "name"
    const val EXTRA_PHONE = "phone"
    const val EXTRA_EMAIL = "email"
    const val EXTRA_ORG = "org"
    const val EXTRA_TITLE = "title"
    const val EXTRA_URL = "url"

    fun canNotify(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Returns false when the phone can't show the notification (permission not granted yet). */
    fun send(ctx: Context, action: PhoneAction, summary: String): Boolean {
        if (!canNotify(ctx)) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Leitor QR", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "O que você leu com os óculos, para usar no celular"
            },
        )
        val intent = Intent(ctx, ActionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtras(action)
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(action.label)
            .setContentText(summary)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        return true
    }

    private fun Intent.putExtras(a: PhoneAction) {
        when (a) {
            is PhoneAction.JoinWifi -> {
                putExtra(EXTRA_KIND, "wifi")
                putExtra(EXTRA_SSID, a.wifi.ssid)
                putExtra(EXTRA_PASS, a.wifi.password)
                putExtra(EXTRA_SEC, a.wifi.security.name)
                putExtra(EXTRA_HIDDEN, a.wifi.hidden)
            }
            is PhoneAction.OpenLink -> { putExtra(EXTRA_KIND, "link"); putExtra(EXTRA_URL, a.url) }
            is PhoneAction.CopyText -> { putExtra(EXTRA_KIND, "copy"); putExtra(EXTRA_TEXT, a.text); putExtra(EXTRA_TITLE, a.label) }
            is PhoneAction.SaveContact -> {
                putExtra(EXTRA_KIND, "contact")
                putExtra(EXTRA_NAME, a.contact.name)
                putExtra(EXTRA_PHONE, a.contact.phones.firstOrNull())
                putExtra(EXTRA_EMAIL, a.contact.emails.firstOrNull())
                putExtra(EXTRA_ORG, a.contact.org)
                putExtra(EXTRA_TITLE, a.contact.title)
                putExtra(EXTRA_URL, a.contact.urls.firstOrNull())
            }
        }
    }

    fun securityOf(name: String?): QrResult.Wifi.Security =
        runCatching { QrResult.Wifi.Security.valueOf(name ?: "") }.getOrDefault(QrResult.Wifi.Security.WPA)
}
