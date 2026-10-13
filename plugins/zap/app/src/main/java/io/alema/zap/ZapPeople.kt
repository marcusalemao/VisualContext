package io.alema.zap

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.alema.zap.core.PhoneNumbers
import io.alema.zap.core.WhatsAppLink
import io.alema.zap.core.ZapContact
import org.json.JSONObject

/**
 * People who messaged the wearer on WhatsApp, learned from WhatsApp's own
 * notifications: a name and a number, nothing else. Message text is never
 * stored. This is the contact list Zap works from when the wearer has not
 * imported the address book.
 */
internal class ZapPeople(context: Context) {
    private val prefs = context.getSharedPreferences("zap_people", Context.MODE_PRIVATE)

    fun remember(waDigits: String, name: String, nowMs: Long = System.currentTimeMillis()) {
        val clean = cleanName(name)
        if (clean.isEmpty() || waDigits.isEmpty()) return
        prefs.edit().putString(waDigits, JSONObject().put("name", clean).put("seen", nowMs).toString()).apply()
        trim()
    }

    fun forget(waDigits: String) {
        prefs.edit().remove(waDigits).apply()
    }

    fun forgetAll() {
        prefs.edit().clear().apply()
    }

    fun all(recents: ZapRecents): List<ZapContact> = prefs.all.mapNotNull { (digits, raw) ->
        val json = (raw as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
        val id = idFor(digits)
        ZapContact(
            id = id,
            name = json.optString("name"),
            waIds = listOf(digits),
            lastUsedMs = maxOf(recents.lastUsed(id), json.optLong("seen")),
        )
    }

    private fun trim() {
        if (prefs.all.size <= MAX) return
        val oldest = prefs.all.mapNotNull { (k, v) ->
            (v as? String)?.let { runCatching { JSONObject(it).optLong("seen") }.getOrNull() }?.let { k to it }
        }.sortedBy { it.second }
        val editor = prefs.edit()
        oldest.take(prefs.all.size - MAX).forEach { editor.remove(it.first) }
        editor.apply()
    }

    companion object {
        private const val MAX = 300
        private val COUNT_SUFFIX = Regex("\\s*\\(\\d+[^)]*\\)\\s*$")

        fun idFor(waDigits: String) = "wa:$waDigits"

        /** "Bruno Costa (3 mensagens)" -> "Bruno Costa". */
        fun cleanName(name: String): String = name.replace(COUNT_SUFFIX, "").trim()
    }
}

/**
 * Watches WhatsApp notifications (and only WhatsApp's) to build the inbox
 * and learn who wrote.
 *
 * WhatsApp gives each chat a conversation shortcut whose id is the WhatsApp id
 * ("5511…@s.whatsapp.net"; groups end in "@g.us"). One-to-one chats feed the
 * people list with name and number; their messages go to the encrypted
 * inbox. To confirm on the device: if a WhatsApp build stops exposing that
 * id, chats are keyed by title and Reply works only while the notification
 * is live.
 */
class ZapNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        // After a restart the live reply tokens are gone; rebuild them from what is still showing.
        runCatching { activeNotifications }.getOrNull()?.forEach { capture(it) }
    }

    override fun onListenerDisconnected() {
        ZapInbox.releaseAll()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        capture(sbn ?: return)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        ZapInbox.released(sbn ?: return)
    }

    private fun capture(sbn: StatusBarNotification) {
        if (sbn.packageName != WhatsAppLink.PACKAGE && sbn.packageName != WhatsAppLink.BUSINESS_PACKAGE) return
        val health = ZapHealth(this)
        health.sawWhatsApp()
        val chat = try {
            ZapInbox.capture(this, sbn)
        } catch (e: RuntimeException) {
            // Never let one odd notification take the listener down; keep the reason for the Check card.
            health.failed("${e.javaClass.simpleName}: ${e.message}")
            return
        } ?: return health.failed("unreadable notification (${sbn.notification?.extras?.keySet()?.sorted()?.joinToString()})")
        health.captured()
        if (!chat.isGroup) chat.waDigits?.let { ZapPeople(this).remember(it, chat.title) }
    }
}
