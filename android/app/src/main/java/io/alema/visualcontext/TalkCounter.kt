package io.alema.visualcontext

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Who you talk to most, without Zap (Marcus, 07/11). Counts messages that arrive from WhatsApp, WhatsApp
 * Business, Telegram, Signal and SMS by sender, per day, for 30 days. **Only the count: the text is never
 * read into storage.** Groups are left out. Off until the user turns on notification access in Settings.
 */
class TalkCounter : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in APPS) return
        val ex = sbn.notification.extras
        if (ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val who = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim() ?: return
        // "Ana (3 mensagens)", "Grupo: Ana" and similar are not one person.
        if (who.isEmpty() || who.contains(':') || who.contains('(') || who.equals("WhatsApp", true)) return
        add(this, who)
    }

    companion object {
        private val APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.google.android.apps.messaging", "com.samsung.android.messaging")
        private const val FILE = "conversas_contagem.json"

        fun enabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, TalkCounter::class.java).flattenToString()
            return (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "").split(':').any { it.equals(me, true) }
        }

        @Synchronized private fun add(ctx: Context, who: String) {
            val f = java.io.File(ctx.filesDir, FILE)
            val j = try { JSONObject(f.readText()) } catch (_: Exception) { JSONObject() }
            val day = (System.currentTimeMillis() / 86_400_000).toString()
            val p = j.optJSONObject(who) ?: JSONObject()
            p.put(day, p.optInt(day) + 1)
            // keep 30 days
            val keys = p.keys().asSequence().toList()
            keys.filter { (it.toLongOrNull() ?: 0) < day.toLong() - 30 }.forEach { p.remove(it) }
            j.put(who, p)
            f.writeText(j.toString())
        }

        /** name (lowercase) → messages in the last 30 days. */
        fun counts(ctx: Context): Map<String, Int> {
            val j = try { JSONObject(java.io.File(ctx.filesDir, FILE).readText()) } catch (_: Exception) { return emptyMap() }
            val today = System.currentTimeMillis() / 86_400_000
            val out = HashMap<String, Int>()
            for (who in j.keys()) { val p = j.optJSONObject(who) ?: continue; var n = 0; for (d in p.keys()) if ((d.toLongOrNull() ?: 0) >= today - 30) n += p.optInt(d); if (n > 0) out[who.lowercase()] = n }
            return out
        }

        fun clear(ctx: Context) { java.io.File(ctx.filesDir, FILE).delete() }
    }
}
