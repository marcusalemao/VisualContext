package io.alema.zap

import android.app.Notification
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
 * Watches WhatsApp notifications only to learn who wrote (see [ZapPeople]).
 *
 * WhatsApp gives each chat a conversation shortcut whose id is the WhatsApp id
 * ("5511…@s.whatsapp.net"); group chats end in "@g.us" and are skipped, since a
 * group cannot be opened from a link. To confirm on the device: if a WhatsApp
 * build stops exposing the id there, this learns nothing and the address-book
 * import still works.
 */
class ZapNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != WhatsAppLink.PACKAGE && sbn.packageName != WhatsAppLink.BUSINESS_PACKAGE) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return
        val digits = notification.shortcutId?.let(PhoneNumbers::fromWhatsAppJid) ?: return
        val name = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return
        ZapPeople(this).remember(digits, name)
    }
}
