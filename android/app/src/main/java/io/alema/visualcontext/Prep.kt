package io.alema.visualcontext

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Spec 1o: prep before a conversation. Memory first, then the web (labelled, kept apart).
 * - on request (Live tool "preparar_conversa");
 * - 10 minutes before a calendar event with someone from the memory: a notification that opens the
 *   brief in the Manager (phone calendar, read only). Blocking calls: use a worker thread.
 */
object Prep {
    class Brief(val name: String, val lines: List<String>, val spoken: String)

    fun forPerson(ctx: Context, name: String): Brief? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = Describe.textModel(ctx, key) ?: return null
        val people = Backend.peopleFull(ctx) ?: emptyList()
        val first = norm(name).substringBefore(' ')
        val p = people.firstOrNull { norm(it.name) == norm(name) } ?: people.firstOrNull { norm(it.name).startsWith(first) && first.length > 2 }
        val encs = try { JSONArray(Backend.post(ctx, JSONObject().put("action", "list").put("entity", "encounter").put("limit", 300)) ?: "[]") } catch (_: Exception) { JSONArray() }
        val mine = JSONArray()
        for (i in 0 until encs.length()) {
            val e = encs.optJSONObject(i) ?: continue
            if ((p != null && e.optString("person_id") == p.id) || (first.length > 2 && norm(e.optString("transcript")).contains(first)))
                mine.put(JSONObject().put("quando", e.optString("at")).put("lugar", MyPlaces.label(e)).put("resumo", e.optString("summary")).put("pendencias", e.opt("follow_ups")).put("trecho", e.optString("transcript").take(800)))
            if (mine.length() >= 10) break
        }
        val mem = JSONObject().put("pessoa", if (p == null) JSONObject().put("nome", name) else JSONObject().put("nome", p.name).put("relacao", p.relation).put("empresa", p.company).put("ultima_vez", p.lastSeenDate).put("onde", p.lastSeenContext)).put("encontros", mine)
        val prompt = "Prepare o usuário dos óculos para conversar com ${p?.name ?: name}. Use primeiro a memória dele; depois a web, com cuidado com homônimos (confirme por empresa/cargo; se não tiver certeza, diga). " +
            "Responda só JSON: {\"linhas\": [\"até 5 linhas curtas para a lente: quem é, último encontro, pendência, para não errar, da web (comece com 'web:')\"], \"fala\": \"2 frases para falar baixinho\"}. Nunca invente.\nMEMÓRIA: $mem"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 3000).put("temperature", 0.2))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            val j = JSONObject(txt.substring(txt.indexOf('{'), txt.lastIndexOf('}') + 1))
            val l = j.optJSONArray("linhas") ?: JSONArray()
            Brief(p?.name ?: name, (0 until l.length()).map { l.optString(it) }.filter { it.isNotBlank() }.take(5), j.optString("fala"))
        } catch (_: Exception) { null }
    }

    // ---------- 10 minutes before a calendar event ----------
    private const val CH = "prep"

    /** Checks the calendar every ~15 min (inexact, battery-friendly). Needs READ_CALENDAR. */
    fun schedule(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, Check::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi)
    }

    class Check : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent?) {
            val pending = goAsync()
            Thread { try { check(ctx.applicationContext) } catch (_: Throwable) {} finally { pending.finish() } }.start()
        }
    }

    /** Events starting in 5–25 min whose attendees or title name someone from the memory. */
    fun check(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val people = Backend.peopleFull(ctx) ?: return
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, now + 5 * 60_000); android.content.ContentUris.appendId(it, now + 25 * 60_000) }.build()
        val prefs = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
        ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(0); val title = cur.getString(1) ?: ""; val begin = cur.getLong(2)
                val tag = "prep_${id}_$begin"
                if (prefs.getBoolean(tag, false)) continue
                val guests = mutableListOf<Pair<String, String>>()
                ctx.contentResolver.query(CalendarContract.Attendees.CONTENT_URI, arrayOf(CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL),
                    "${CalendarContract.Attendees.EVENT_ID}=?", arrayOf(id.toString()), null)?.use { a -> while (a.moveToNext()) guests += (a.getString(0) ?: "") to (a.getString(1) ?: "") }
                val hay = norm(title + " " + guests.joinToString(" ") { it.first + " " + it.second })
                val who = people.filter { p -> val n = norm(p.name); n.length > 3 && (hay.contains(n) || n.split(' ').size > 1 && hay.contains(n.substringBefore(' ') + " " + n.substringAfterLast(' '))) }.take(3)
                if (who.isEmpty()) continue
                prefs.edit().putBoolean(tag, true).apply()
                notify(ctx, title, begin, who)
            }
        }
    }

    private fun notify(ctx: Context, title: String, begin: Long, who: List<Backend.Person>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Preparação antes de compromissos", NotificationManager.IMPORTANCE_HIGH))
        val mins = ((begin - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
        val link = Backend.managerLink(ctx) + "&p=" + who[0].id + "&prep=1"
        val pi = PendingIntent.getActivity(ctx, title.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val names = who.joinToString(", ") { it.name }
        try {
            nm.notify(title.hashCode(), Notification.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle("Em $mins min: $title").setContentText("✦ Preparação para $names · toque para ver")
                .setContentIntent(pi).setAutoCancel(true).build())
        } catch (_: SecurityException) {}
    }

    fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9@. ]"), " ").replace(Regex("\\s+"), " ").trim()
}
