package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ask the memory from the glasses (Marcus, 08/11: "o que eu fiz hoje?", "quando vi o Bruno pela última vez?",
 * "o que combinei com a Ana?"), not only in the Manager. The answer is spoken and shown as a HUD card. Blocking.
 */
object Recall {
    const val TOOL = "consultar_memoria"

    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Consulta a memória do usuário (encontros, conversas, lugares, coisas, notas): \"o que eu fiz hoje\", \"quem eu encontrei ontem\", \"quando vi o Bruno\", \"o que combinei com a Ana\", \"o que anotei esta semana\". Chame sempre que a pergunta for sobre o passado dele; nunca responda de cabeça.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("pergunta", JSONObject().put("type", "STRING").put("description", "a pergunta, como ele disse"))
            .put("dias", JSONObject().put("type", "INTEGER").put("description", "quantos dias para trás olhar: 1 = hoje, 2 = desde ontem, 7 = semana; 30 se não souber")))
            .put("required", JSONArray().put("pergunta")))

    data class Answer(val say: String, val title: String, val lines: List<String>)

    fun ask(ctx: Context, question: String, days: Int): Answer? {
        val since = System.currentTimeMillis() - days.coerceIn(1, 365) * 86_400_000L
        val enc = rows(ctx, "encounter", 300).filter { t(it.optString("at")) >= since }
        val notes = rows(ctx, "note", 200).filter { t(it.optString("at").ifEmpty { it.optString("created_at") }) >= since }
        val things = rows(ctx, "thing", 100).filter { t(it.optString("seen_at")) >= since }
        val me = Backend.meName(ctx)
        val fmt = java.text.SimpleDateFormat("EEE dd/MM HH:mm", java.util.Locale("pt", "BR"))
        val sb = StringBuilder()
        for (e in enc.take(120)) {
            val who = e.optString("person_name").ifEmpty { null }; if (who != null && me.isNotEmpty() && who.equals(me, true)) continue
            sb.append("- ").append(fmt.format(t(e.optString("at")))).append(" · ").append(if (e.optString("capture_mode") == "live") "conversa com o assistente" else "encontro" + (who?.let { " com $it" } ?: ""))
            e.optString("place").ifEmpty { null }?.let { sb.append(" · ").append(it) }
            (e.optString("summary").ifEmpty { e.optString("transcript").take(300) }).ifEmpty { null }?.let { sb.append(" · ").append(it.replace("\n", " ")) }
            sb.append("\n")
        }
        for (n in notes.take(60)) sb.append("- nota ").append(fmt.format(t(n.optString("at").ifEmpty { n.optString("created_at") }))).append(" · ").append(n.optString("title").ifEmpty { n.optString("text").take(160) }.replace("\n", " ")).append("\n")
        for (x in things.take(40)) sb.append("- objeto ").append(fmt.format(t(x.optString("seen_at")))).append(" · ").append(x.optString("name")).append(" em ").append(x.optString("location_description")).append("\n")
        if (sb.isEmpty()) return Answer(if (days <= 1) "Hoje ainda não tem nada na memória." else "Não achei nada nesse período.", "✦ Memória", listOf("nada em ${if (days <= 1) "hoje" else "$days dias"}"))
        val prompt = """Você responde pela memória pessoal do usuário (óculos VisualContext). Hoje é ${fmt.format(System.currentTimeMillis())}.
Pergunta: "$question"
Registros (mais recentes primeiro):
$sb
Responda em JSON: {"fala": "resposta falada, em português, 1 a 3 frases, concreta (nomes, lugares, horas)", "titulo": "título curto para a lente, até 24 caracteres", "linhas": ["até 5 linhas curtas para a lente, cada uma até 38 caracteres, começando pela hora quando fizer sentido"]}. Só use o que está nos registros."""
        val raw = Gemini.text(ctx, prompt, json = true, search = false) ?: return null
        val j = runCatching { JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")) }.getOrNull() ?: return null
        val lines = j.optJSONArray("linhas")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return Answer(j.optString("fala"), "✦ " + j.optString("titulo").ifEmpty { "Memória" }, lines.take(5))
    }

    private fun rows(ctx: Context, entity: String, limit: Int): List<JSONObject> {
        val r = Backend.post(ctx, JSONObject().put("action", "list").put("entity", entity).put("limit", limit)) ?: return emptyList()
        val a = runCatching { JSONArray(r) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
    private fun t(iso: String): Long = runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)
}
