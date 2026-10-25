package io.alema.zap

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import io.alema.zap.core.Recipient
import io.alema.zap.core.SuggestedEvent
import io.alema.zap.core.WhatsAppLink
import io.alema.zap.core.ZapConversation
import io.alema.zap.core.ZapText
import java.util.TimeZone

/**
 * What a tap on the summary card does on the phone, without the wearer ever
 * taking the phone out: an event straight into the phone's own calendar
 * (whatever account it syncs with — Google, Samsung — no login in Zap), and a
 * reminder that comes back on the glasses with the reply chip ready.
 */
internal object ZapCalendar {
    private const val TAG = "ZapCalendar"
    private const val DEFAULT_LENGTH_MS = 60 * 60_000L
    private const val ALERT_MINUTES = 15

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** True when the event is in the calendar. Without the permission nothing is written and false comes back. */
    fun add(context: Context, event: SuggestedEvent, chatTitle: String): Boolean {
        if (!allowed(context)) return false
        return runCatching {
            val calendarId = primaryCalendar(context) ?: return false
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, event.startMs)
                put(CalendarContract.Events.DTEND, event.endMs ?: (event.startMs + DEFAULT_LENGTH_MS))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                event.place?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                put(CalendarContract.Events.DESCRIPTION, "WhatsApp · $chatTitle (Zap)")
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return false
            val id = uri.lastPathSegment?.toLongOrNull() ?: return true
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, ALERT_MINUTES)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
            true
        }.onFailure { Log.w(TAG, "calendar insert failed", it) }.getOrDefault(false)
    }

    /** The calendar the phone treats as main and visible, else the first one that can take events. */
    private fun primaryCalendar(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            var fallback: Long? = null
            while (c.moveToNext()) {
                val writable = c.getInt(2) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (!writable || c.getInt(3) != 1) continue
                if (c.getInt(1) == 1) return c.getLong(0)
                if (fallback == null) fallback = c.getLong(0)
            }
            return fallback
        }
        return null
    }
}

/** "Remind me to reply in 30 min": comes back on the glasses (with Reply ready) and on the phone. */
internal object ZapReminders {
    private const val CHANNEL = "zap_reminders"
    const val ACTION = "io.alema.zap.REMIND_REPLY"

    fun schedule(context: Context, chat: ZapConversation, minutes: Int, summary: String?): Boolean = runCatching {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, ZapReminderReceiver::class.java).setAction(ACTION)
            .putExtra("key", chat.key)
            .putExtra("title", chat.title)
            .putExtra("digits", chat.waDigits)
            .putExtra("summary", summary)
        val pending = PendingIntent.getBroadcast(
            context, chat.key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Inexact is fine for "in half an hour" and needs no special permission; a few minutes late at most.
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        true
    }.getOrDefault(false)

    fun fire(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val title = intent.getStringExtra("title") ?: return
        val digits = intent.getStringExtra("digits")
        val summary = intent.getStringExtra("summary")
        val text = ZapText()
        val chat = ZapInbox.list(context).firstOrNull { it.key == key }
        val recipient = chat?.takeIf { it.canReply }?.recipient() ?: digits?.let { Recipient(title, it, key) }
        // On the glasses: the band, with Reply ready, even when arrivals are switched off — the wearer asked for this one.
        ZapNotifier.announce(context, "⏰ ${text.remindNow(title)}", text.remindWhy(summary), recipient, always = true)
        phone(context, title, text, summary, digits)
    }

    private fun phone(context: Context, title: String, text: ZapText, summary: String?, digits: String?) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, text.remindNow("…").removeSuffix(" …"), NotificationManager.IMPORTANCE_HIGH))
        val open = digits?.let {
            PendingIntent.getActivity(
                context, title.hashCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLink.appUri(it, null))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.nexus_glyph_zap)
            .setContentTitle(text.remindNow(title))
            .setContentText(text.remindWhy(summary))
            .setStyle(android.app.Notification.BigTextStyle().bigText(text.remindWhy(summary)))
            .setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        manager.notify(title.hashCode(), notification)
    }
}

class ZapReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ZapReminders.ACTION) ZapReminders.fire(context, intent)
    }
}
