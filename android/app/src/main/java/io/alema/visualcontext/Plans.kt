package io.alema.visualcontext

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plans agreed in a conversation go to the calendar (Marcus, 09/11: "a gente combina terça que vem, reunião — já
 * anota na agenda: horário, dia, com quem, endereço, assunto e o que mais a pessoa disser"). The glasses ask first
 * (card with Sim / Não, or "sim" by voice); on yes the event is written straight into the phone's calendar (Google
 * Agenda included), with the person, the place and the notes. Without the calendar permission, the calendar app opens
 * with everything filled in.
 */
object Plans {
    const val TOOL = "marcar_agenda"
    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Propõe pôr na agenda um compromisso combinado ou pedido (\"marca na agenda\", \"terça que vem às 14h reunião com o Bruno\"). O app mostra o cartão e ele confirma; não diga que já marcou.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("titulo", JSONObject().put("type", "STRING").put("description", "curto: Reunião com Bruno — roteiro do curta"))
            .put("inicio", JSONObject().put("type", "STRING").put("description", "data e hora local AAAA-MM-DDTHH:MM"))
            .put("duracao_min", JSONObject().put("type", "INTEGER"))
            .put("dia_inteiro", JSONObject().put("type", "BOOLEAN"))
            .put("local", JSONObject().put("type", "STRING").put("description", "endereço ou nome do lugar, se dito"))
            .put("com", JSONObject().put("type", "STRING").put("description", "com quem; quem convidou"))
            .put("notas", JSONObject().put("type", "STRING").put("description", "tudo o mais que foi dito sobre o compromisso: assunto, o que levar, links, detalhes")))
            .put("required", JSONArray().put("titulo").put("inicio")))

    data class Plan(val title: String, val start: ZonedDateTime, val minutes: Int, val allDay: Boolean, val where: String?, val with: String?, val notes: String?) {
        fun line(): String = start.format(DateTimeFormatter.ofPattern(if (allDay) "EEE dd/MM" else "EEE dd/MM HH:mm", Locale("pt", "BR"))) + (if (!allDay) " · ${minutes} min" else " · dia inteiro")
        fun card(): List<String> = listOfNotNull(line(), with?.let { "com $it" }, where?.let { "⌖ $it" }, notes?.take(160))
    }

    fun fromArgs(a: JSONObject): Plan? = runCatching {
        val start = LocalDateTime.parse(a.optString("inicio").take(16)).atZone(ZoneId.systemDefault())
        Plan(a.optString("titulo").ifBlank { "Compromisso" }, start, a.optInt("duracao_min", 60).coerceIn(5, 24 * 60), a.optBoolean("dia_inteiro"),
            a.optString("local").ifBlank { null }, a.optString("com").ifBlank { null }, a.optString("notas").ifBlank { null })
    }.getOrNull()

    /**
     * Commitments in an encounter's transcript (Gemini, text only). Only plans with a date that is clear enough;
     * relative dates ("terça que vem") are resolved from the encounter's own date. Blocking.
     */
    fun extract(ctx: Context, transcript: String, at: Long, person: String?): List<Plan> {
        val key = Backend.geminiKey(ctx); if (key.length < 20 || transcript.length < 40) return emptyList()
        val model = Describe.textModel(ctx, key) ?: return emptyList()
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), ZoneId.systemDefault())
        val prompt = "Conversa gravada em ${now.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale("pt", "BR")))}" + (person?.let { " com $it" } ?: "") + ".\n" +
            "Liste só compromissos FUTUROS combinados nela (reunião, encontro, consulta, ligação, entrega), com dia definido. Datas relativas (\"terça que vem\", \"amanhã\") a partir da data acima. " +
            "Responda só JSON: [{\"titulo\":\"curto\",\"inicio\":\"AAAA-MM-DDTHH:MM\",\"duracao_min\":60,\"dia_inteiro\":false,\"local\":\"endereço ou lugar, se dito\",\"com\":\"quem\",\"notas\":\"assunto e demais detalhes ditos\"}]. " +
            "Sem hora dita: dia_inteiro true. Nenhum compromisso claro: []. Não invente.\n\n" + transcript.take(12000)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 1500).put("responseMimeType", "application/json"))
        return try {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 40_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { if (c.responseCode in listOf(400, 403, 404)) Backend.markBadModel(ctx, model); return emptyList() }
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return emptyList()
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }.trim().removePrefix("```json").removeSuffix("```")
            val a = JSONArray(txt)
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromArgs) }.filter { it.start.isAfter(ZonedDateTime.now().minusHours(1)) }.take(3)
        } catch (_: Exception) { emptyList() }
    }

    fun canWrite(ctx: Context) = ctx.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Writes the event in the main calendar; returns its Uri when written silently, null when the calendar app was opened. */
    fun save(ctx: Context, p: Plan, openAppIfNoPermission: Boolean = true): android.net.Uri? {
        val desc = listOfNotNull(p.with?.let { "Com: $it" }, p.notes, "— anotado pelo VisualContext").joinToString("\n")
        val startMs = p.start.toInstant().toEpochMilli()
        val endMs = if (p.allDay) startMs + 86_400_000L else startMs + p.minutes * 60_000L
        if (canWrite(ctx)) {
            val cal = ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}", null, null)?.use { c ->
                var best = -1L; while (c.moveToNext()) { if (best < 0) best = c.getLong(0); if (c.getInt(1) == 1) { best = c.getLong(0); break } }; best
            } ?: -1L
            if (cal >= 0) {
                val v = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, cal); put(CalendarContract.Events.TITLE, p.title); put(CalendarContract.Events.DESCRIPTION, desc)
                    put(CalendarContract.Events.DTSTART, if (p.allDay) p.start.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() else startMs)
                    put(CalendarContract.Events.DTEND, if (p.allDay) p.start.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() else endMs)
                    put(CalendarContract.Events.ALL_DAY, if (p.allDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, if (p.allDay) "UTC" else ZoneId.systemDefault().id)
                    p.where?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                }
                runCatching { ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) }.getOrNull()?.let { return it }
            }
        }
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CalendarContract.Events.TITLE, p.title).putExtra(CalendarContract.Events.DESCRIPTION, desc)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs).putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, p.allDay)
        p.where?.let { i.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        if (openAppIfNoPermission) runCatching { ctx.startActivity(i) }
        return null
    }
    fun undo(ctx: Context, uri: android.net.Uri) = runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    /** Is the plan clear enough to go in on its own: a date and a time (not just "um dia desses"). */
    fun certain(p: Plan) = !p.allDay

    /** Kept as a note (kind "evento") so the Manager shows it: "sugerido" waits for him there, "na_agenda" is history. */
    fun remember(ctx: Context, p: Plan, status: String, from: String?) {
        val f = JSONObject().put("inicio", p.start.toLocalDateTime().toString()).put("duracao_min", p.minutes).put("dia_inteiro", p.allDay)
            .put("local", p.where ?: "").put("com", p.with ?: "").put("notas", p.notes ?: "").put("status", status).put("de", from ?: "")
        Moments.send(ctx, JSONObject().put("action", "note").put("kind", "evento").put("title", p.title)
            .put("text", (listOf(p.line()) + listOfNotNull(p.with?.let { "com $it" }, p.where, p.notes)).joinToString("\n")).put("fields", f).put("source", "conversa"))
    }

    fun auto(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).getBoolean("agenda_auto", true)
    fun setAuto(ctx: Context, on: Boolean) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).edit().putBoolean("agenda_auto", on).apply()
}
