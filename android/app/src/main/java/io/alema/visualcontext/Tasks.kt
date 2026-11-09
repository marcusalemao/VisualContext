package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * To-dos for forgetful people (Marcus, 09/11): "o que eu tenho para fazer?" — what was agreed and not done, what
 * someone asked, "me lembra amanhã de…", "depois eu escrevo para ela". Each one carries the action that does it
 * (agenda, e-mail, WhatsApp, ligar, comprar…). Kept as notes of kind "tarefa"; done = a "tarefa_feita" note that
 * points to it (no server change, and it works the same in the app and in the Manager).
 */
object Tasks {
    const val TOOL_ADD = "anotar_tarefa"
    const val TOOL_LIST = "minhas_tarefas"
    const val TOOL_DONE = "concluir_tarefa"
    val ACTIONS = listOf("whatsapp", "email", "ligar", "agenda", "comprar", "lembrete", "outro")

    fun tools(): List<JSONObject> = listOf(
        JSONObject().put("name", TOOL_ADD).put("description", "Anota uma tarefa dele: \"me lembra de…\", \"preciso mandar um e-mail pro Bruno\", \"depois eu escrevo para ela\", algo que alguém pediu e ele aceitou fazer.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("texto", JSONObject().put("type", "STRING").put("description", "o que fazer, curto: Mandar o orçamento para o Bruno"))
                .put("acao", JSONObject().put("type", "STRING").put("enum", JSONArray(ACTIONS)))
                .put("pessoa", JSONObject().put("type", "STRING").put("description", "para quem ou quem pediu"))
                .put("quando", JSONObject().put("type", "STRING").put("description", "prazo AAAA-MM-DDTHH:MM, se dito"))
                .put("detalhes", JSONObject().put("type", "STRING").put("description", "o que mais foi dito: assunto, endereço, o que escrever"))).put("required", JSONArray().put("texto"))),
        JSONObject().put("name", TOOL_LIST).put("description", "As tarefas abertas dele (\"o que eu tenho para fazer?\", \"minhas tarefas\", \"o que ficou pendente?\"). Leia curto, uma por uma, com a ação sugerida."),
        JSONObject().put("name", TOOL_DONE).put("description", "Marca uma tarefa como feita (\"já mandei o e-mail pro Bruno\", \"pode tirar essa\").")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("texto", JSONObject().put("type", "STRING").put("description", "a tarefa, como ele disse"))).put("required", JSONArray().put("texto"))),
    )

    data class Task(val id: String, val text: String, val action: String, val person: String?, val due: LocalDateTime?, val details: String?, val from: String?) {
        fun line(): String = listOfNotNull(icon(action) + " " + text, due?.format(DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale("pt", "BR")))?.let { "até $it" }).joinToString(" · ")
        fun suggestion(): String = when (action) {
            "whatsapp" -> "mandar WhatsApp" + (person?.let { " para $it" } ?: ""); "email" -> "escrever o e-mail" + (person?.let { " para $it" } ?: "")
            "ligar" -> "ligar" + (person?.let { " para $it" } ?: ""); "agenda" -> "pôr na agenda"; "comprar" -> "comprar"; else -> "fazer"
        }
    }
    fun icon(a: String) = when (a) { "whatsapp" -> "💬"; "email" -> "✉"; "ligar" -> "☏"; "agenda" -> "🗓"; "comprar" -> "🛒"; "lembrete" -> "⏰"; else -> "✓" }

    fun add(ctx: Context, text: String, action: String?, person: String?, due: String?, details: String?, from: String?) {
        val a = action?.takeIf { it in ACTIONS } ?: "outro"
        val f = JSONObject().put("acao", a).put("pessoa", person ?: "").put("quando", due ?: "").put("detalhes", details ?: "").put("de", from ?: "")
        Moments.send(ctx, JSONObject().put("action", "note").put("kind", "tarefa").put("title", text.take(120))
            .put("text", listOfNotNull(text, person?.let { "($it)" }, details).joinToString(" ")).put("fields", f).put("source", from?.let { "conversa" } ?: "voz"))
    }

    /** Open tasks, soonest first (no date last). Blocking. */
    fun open(ctx: Context): List<Task> {
        val a = runCatching { JSONArray(Backend.post(ctx, JSONObject().put("action", "list").put("entity", "note").put("limit", 800)) ?: "[]") }.getOrDefault(JSONArray())
        val all = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        val done = all.filter { it.optString("kind") == "tarefa_feita" }.mapNotNull { it.optJSONObject("fields")?.optString("ref") }.toSet()
        return all.filter { it.optString("kind") == "tarefa" && it.optString("id") !in done }.map { n ->
            val f = n.optJSONObject("fields") ?: JSONObject()
            Task(n.optString("id"), n.optString("title").ifBlank { n.optString("text") }, f.optString("acao", "outro"), f.optString("pessoa").ifBlank { null },
                runCatching { LocalDateTime.parse(f.optString("quando").take(16)) }.getOrNull(), f.optString("detalhes").ifBlank { null }, f.optString("de").ifBlank { null })
        }.sortedWith(compareBy({ it.due == null }, { it.due }))
    }

    fun done(ctx: Context, t: Task) = Moments.send(ctx, JSONObject().put("action", "note").put("kind", "tarefa_feita").put("title", "Feito: " + t.text.take(100)).put("text", "Feito: " + t.text).put("fields", JSONObject().put("ref", t.id)).put("source", "voz"))

    /** The open task that best matches what he said ("o e-mail do Bruno"). */
    fun match(list: List<Task>, said: String): Task? {
        val w = said.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        return list.maxByOrNull { t -> (t.text + " " + (t.person ?: "")).lowercase().split(Regex("\\W+")).count { it in w } }?.takeIf { t -> (t.text + " " + (t.person ?: "")).lowercase().split(Regex("\\W+")).any { it in w } }
    }

    /** As story chapters for the lens: one task per card, with what to do. */
    fun chapters(list: List<Task>): List<Story.Chapter> =
        listOf(Story.Chapter("✓ Para fazer", listOf("${list.size} ${if (list.size == 1) "tarefa" else "tarefas"}") + list.take(3).map { "· " + it.text })) +
            list.take(12).map { t -> Story.Chapter(icon(t.action) + " " + t.text, listOfNotNull(t.due?.let { "até " + it.format(DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale("pt", "BR"))) }, t.from?.let { "de uma conversa com $it" }, t.details?.take(140), "→ " + t.suggestion())) }
}
