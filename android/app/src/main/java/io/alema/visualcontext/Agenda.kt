package io.alema.visualcontext

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * "O que eu tenho para hoje?" (Marcus, 08/11: "não tenho acesso à sua agenda"): the phone's calendars (Google Calendar
 * included, through Android's calendar provider), read on the phone. Needs the calendar permission given in Settings.
 */
object Agenda {
    const val TOOL = "agenda"
    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Compromissos da agenda do usuário (Google Agenda e outras do celular): \"o que eu tenho para hoje\", \"minha agenda amanhã\", \"quando é a reunião com a Ana\". Chame em vez de dizer que não tem acesso.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("de", JSONObject().put("type", "STRING").put("description", "hoje, amanha ou uma data AAAA-MM-DD"))
            .put("dias", JSONObject().put("type", "INTEGER").put("description", "quantos dias a partir dali (1 = só esse dia)"))
            .put("busca", JSONObject().put("type", "STRING").put("description", "texto para procurar no título, opcional"))))

    data class Ev(val title: String, val begin: Long, val end: Long, val where: String?, val allDay: Boolean)

    fun read(ctx: Context, fromDay: Long, days: Int, query: String?): List<Ev>? {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val end = fromDay + days.coerceIn(1, 31) * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, fromDay); android.content.ContentUris.appendId(it, end) }.build()
        val out = ArrayList<Ev>()
        ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
            null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val t = c.getString(0) ?: continue
                if (!query.isNullOrBlank() && !t.contains(query, ignoreCase = true)) continue
                out += Ev(t, c.getLong(1), c.getLong(2), c.getString(3)?.takeIf { it.isNotBlank() }, c.getInt(4) == 1)
            }
        }
        return out.take(30)
    }

    fun call(ctx: Context, args: JSONObject): Pair<JSONObject, List<String>> {
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        when (val de = args.optString("de").lowercase().trim()) {
            "", "hoje", "today" -> {}
            "amanha", "amanhã", "tomorrow" -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            else -> runCatching { java.time.LocalDate.parse(de) }.getOrNull()?.let { cal.set(it.year, it.monthValue - 1, it.dayOfMonth) }
        }
        val days = args.optInt("dias", 1).let { if (it <= 0) 1 else it }
        val evs = read(ctx, cal.timeInMillis, days, args.optString("busca").ifBlank { null })
            ?: return JSONObject().put("erro", "sem permissão para ler a agenda").put("diga", "Preciso da permissão da agenda: Ajustes do VisualContext, Preparação, Ligar.") to listOf("permita a agenda nos Ajustes")
        val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")); val dm = java.text.SimpleDateFormat("EEE dd/MM", java.util.Locale("pt", "BR"))
        val arr = JSONArray(); val lines = ArrayList<String>()
        for (e in evs) {
            val whenTxt = (if (days > 1) dm.format(e.begin) + " " else "") + (if (e.allDay) "dia todo" else hm.format(e.begin) + "–" + hm.format(e.end))
            arr.put(JSONObject().put("titulo", e.title).put("quando", whenTxt).put("onde", e.where ?: ""))
            lines += "$whenTxt ${e.title}"
        }
        return JSONObject().put("compromissos", arr).put("total", evs.size) to (if (lines.isEmpty()) listOf("nada marcado") else lines.take(5))
    }
}
