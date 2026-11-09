package io.alema.visualcontext

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spec 1n: "lembra disso" with a paper in view. One frame is read by Gemini into a note with a title and
 * fields (business card, badge, prescription, form, any paper); the frame is then dropped. Blocking.
 */
object Papers {
    private const val PROMPT = """Olhe a foto. Se houver um papel, cartão de visita, crachá, receita médica, formulário, documento, cardápio ou tela com texto bem visível, leia e responda em JSON:
{"papel": true, "tipo": "cartao"|"cracha"|"receita"|"formulario"|"cardapio"|"outro", "titulo": "curto, ex.: Receita do Dr. Paulo, Cartão de Ana Ribeiro", "resumo": "uma frase", "texto": "o texto todo, como está",
 "pessoa": {"nome": "", "empresa": "", "cargo": "", "telefone": "", "email": "", "site": ""},
 "receita": {"medico": "", "data": "", "itens": [{"remedio": "", "dose": "", "quando": ""}]}}
Preencha "pessoa" só para cartão ou crachá e "receita" só para receita. Não invente: campo que não estiver escrito fica vazio.
Se não houver papel com texto legível, responda {"papel": false}."""

    class Paper(val kind: String, val title: String, val summary: String, val text: String, val fields: JSONObject) {
        val sensitive get() = kind == "receita"
    }

    fun read(ctx: Context, jpeg: ByteArray): Paper? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = Describe.textModel(ctx, key) ?: return null
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", PROMPT))
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096).put("temperature", 0.1).put("responseMimeType", "application/json"))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            val j = JSONObject(txt.trim().removePrefix("```json").removeSuffix("```"))
            if (!j.optBoolean("papel")) return null
            val kind = j.optString("tipo", "outro")
            val fields = JSONObject()
            j.optJSONObject("pessoa")?.takeIf { kind == "cartao" || kind == "cracha" }?.let { fields.put("pessoa", it) }
            j.optJSONObject("receita")?.takeIf { kind == "receita" }?.let { fields.put("receita", it) }
            Paper(kind, j.optString("titulo").ifBlank { "Papel" }, j.optString("resumo"), j.optString("texto"), fields)
        } catch (_: Exception) { null }
    }

    /** Stores the paper in the memory; a business card or badge also creates or completes the person. */
    fun save(ctx: Context, p: Paper, place: String?) {
        val note = JSONObject().put("action", "note").put("text", listOf(p.summary, p.text).filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { p.title })
            .put("kind", p.kind).put("title", p.title).put("fields", p.fields).put("sensitive", p.sensitive).put("source", "paper")
        if (place != null) note.put("place", place)
        Moments.send(ctx, note)
        p.fields.optJSONObject("pessoa")?.let { o ->
            val name = o.optString("nome").trim()
            if (name.length > 1) Moments.send(ctx, JSONObject().put("action", "add_people").put("people", JSONArray().put(JSONObject()
                .put("name", name).put("company", o.optString("empresa")).put("role", o.optString("cargo"))
                .put("contact_phone", o.optString("telefone")).put("contact_email", o.optString("email")).put("source", if (p.kind == "cracha") "cracha" else "cartao"))))
        }
        NotesOut.offer(ctx, p.title, plain(p, place), p.sensitive)
    }

    fun plain(p: Paper, place: String?): String {
        val b = StringBuilder(p.title).append("\n\n")
        if (p.summary.isNotBlank()) b.append(p.summary).append("\n\n")
        p.fields.optJSONObject("receita")?.optJSONArray("itens")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { b.append("• ").append(listOf(it.optString("remedio"), it.optString("dose"), it.optString("quando")).filter { s -> s.isNotBlank() }.joinToString(" · ")).append("\n") }
            b.append("\n")
        }
        if (p.text.isNotBlank()) b.append(p.text).append("\n\n")
        b.append("— VisualContext, ").append(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date()))
        if (!place.isNullOrBlank()) b.append(", ").append(place)
        return b.toString()
    }
}
