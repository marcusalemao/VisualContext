package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * "Reviver o dia" on the lens (Marcus, 09/11): the day in chapters, one per place, then the objects and the songs.
 * The temple moves forward and back, two taps leave. Built only from what is recorded (same rules as the Manager).
 */
object Story {
    const val TOOL = "reviver_dia"
    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Mostra na lente o dia dele em capítulos (lugares, pessoas, o que foi dito, objetos, músicas), navegável pela haste. Use para \"reviver o dia\", \"como foi meu dia\", \"me conta meu dia\", \"e ontem?\".")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("dias_atras", JSONObject().put("type", "INTEGER").put("description", "0 = hoje, 1 = ontem"))))

    data class Chapter(val title: String, val lines: List<String>)

    private fun list(ctx: Context, entity: String) = runCatching { JSONArray(Backend.post(ctx, JSONObject().put("action", "list").put("entity", entity).put("limit", 300)) ?: "[]") }.getOrDefault(JSONArray())
    private fun local(s: String?) = runCatching { OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
    private fun hm(s: String?) = local(s)?.let { "%02d:%02d".format(it.hour, it.minute) } ?: ""

    /** Blocking (reads the memory). Empty when nothing was recorded that day. */
    fun build(ctx: Context, daysAgo: Int): List<Chapter> {
        val day = LocalDate.now().minusDays(daysAgo.toLong())
        fun on(s: String?) = local(s)?.toLocalDate() == day
        val me = Backend.meName(ctx).lowercase()
        val enc = list(ctx, "encounter").let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
            .filter { on(it.optString("at")) && it.optString("person_name").lowercase().let { n -> n.isEmpty() || n != me } }.sortedBy { it.optString("at") }
        val things = list(ctx, "thing").let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.filter { on(it.optString("seen_at")) }.sortedBy { it.optString("seen_at") }
        val notes = list(ctx, "note").let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.filter { on(it.optString("created_at").ifEmpty { it.optString("at") }) }
        if (enc.isEmpty() && things.isEmpty() && notes.isEmpty()) return emptyList()
        // Stops: consecutive moments at the same place become one chapter.
        data class Stop(val place: String, val from: String, var to: String, val items: MutableList<JSONObject>)
        val stops = ArrayList<Stop>()
        for (e in enc) {
            val place = MyPlaces.label(e).substringBefore(" (").substringBefore(" · ").ifBlank { "—" }
            val last = stops.lastOrNull()
            if (last != null && last.place == place) { last.to = e.optString("at"); last.items += e } else stops += Stop(place, e.optString("at"), e.optString("at"), mutableListOf(e))
        }
        val people = enc.mapNotNull { it.optString("person_name").ifBlank { null } }.distinct()
        val label = if (daysAgo == 0) "Hoje" else if (daysAgo == 1) "Ontem" else day.toString()
        val out = ArrayList<Chapter>()
        out += Chapter("✦ $label", listOf("${stops.size} ${if (stops.size == 1) "lugar" else "lugares"} · ${people.size} ${if (people.size == 1) "pessoa" else "pessoas"}", people.take(4).joinToString(", ")).filter { it.isNotBlank() })
        stops.forEachIndexed { i, s ->
            val who = s.items.mapNotNull { it.optString("person_name").ifBlank { null } }.distinct()
            val said = s.items.mapNotNull { e -> e.optString("summary").ifBlank { null } ?: e.optString("transcript").lines().firstOrNull { it.startsWith("Você:") }?.removePrefix("Você:")?.trim() }.take(2)
            out += Chapter("⌖ ${s.place}", listOf("${i + 1}/${stops.size} · ${hm(s.from)}${if (s.to != s.from) "–" + hm(s.to) else ""}") +
                (if (who.isNotEmpty()) listOf("com " + who.joinToString(", ")) else if (s.items.any { it.optString("capture_mode") == "live" }) listOf("conversa com o assistente") else emptyList()) +
                said.map { "“$it”" })
        }
        if (things.isNotEmpty()) out += Chapter("◈ Objetos", things.map { "${it.optString("name")}: ${it.optString("location_description").substringBefore(" · ")} · ${hm(it.optString("seen_at"))}" })
        val songs = notes.filter { it.optString("kind") == "musica" }
        if (songs.isNotEmpty()) out += Chapter("♪ Trilha do dia", songs.map { it.optString("title").substringBefore(" · ") })
        return out
    }
}
