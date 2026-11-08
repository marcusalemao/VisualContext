package io.alema.visualcontext

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-shot Gemini calls (generateContent), used for the short neutral description of a person
 * who has no name yet. Never "pessoa 1": "homem de barba, jaqueta azul". Blocking.
 */
object Describe {
    private const val PROMPT = "Descreva a pessoa principal desta foto em no máximo 6 palavras, em português, de forma neutra e respeitosa, " +
        "só o que ajuda a reconhecê-la de novo (cabelo, barba, óculos, roupa). Sem corpo, idade, etnia ou julgamentos. " +
        "Sem ponto final. Exemplo: homem de barba, jaqueta azul"

    fun person(ctx: Context, jpeg: ByteArray): String? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", PROMPT))
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            // Room for the model's own thinking before the 6 words (newer Flash models think by default).
            .put("generationConfig", JSONObject().put("maxOutputTokens", 600).put("temperature", 0.2))
        return try {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { if (model != "models/gemini-2.5-flash") Backend.saveTextModel(ctx, "models/gemini-2.5-flash"); return null }
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
                .firstOrNull { !it.optBoolean("thought") && it.optString("text").isNotBlank() }
                ?.optString("text")?.trim()?.lines()?.first()?.trim('.', '"', ' ')?.lowercase()?.take(48)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    /**
     * The memory version of a conversation with the assistant (07/11): reading for the user is help, not memory.
     * Keeps the user's words and decisions; long readings (menu, sign, leaflet, book page) become one line.
     * Returns (one-sentence summary, condensed conversation). Blocking; null on failure (then the raw text is kept).
     */
    fun memoryVersion(ctx: Context, conversation: String): Pair<String, String>? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val prompt = "Abaixo está uma conversa entre o usuário e o assistente dos óculos. Prepare a versão que vai para a memória dele, em português, e responda em JSON " +
            "{\"resumo\": \"uma frase (até 15 palavras) com o assunto ou o que ele decidiu\", \"conversa\": \"a conversa condensada\"}.\n" +
            "Regras da conversa condensada: mantenha as falas do usuário e tudo o que ele decidiu, pediu, comprou, combinou ou comeu. " +
            "Quando o assistente apenas leu algo para ele (cardápio, placa, bula, página, tela), troque a leitura por uma linha só, por exemplo \"[o assistente leu o cardápio]\". " +
            "Respostas curtas do assistente ficam. Mantenha o formato \"Você: …\" / \"Assistente: …\". " +
            "Se houver algo íntimo ou sensível (nudez, sexo, saúde de alguém), o resumo é uma frase neutra, sem detalhes.\n" +
            "IDIOMAS: o usuário só fala " + Backend.languageNames(ctx).joinToString(", ") + ". A transcrição automática às vezes escreve a fala dele em outra língua ou em letras sem sentido: " +
            "reescreva essas falas \"Você: …\" no que ele provavelmente disse, numa das línguas dele, usando a resposta do assistente como pista; se não der para saber, \"Você: [fala não entendida]\". " +
            "Se for claramente outra pessoa falando outra língua (o assistente traduziu ou respondeu a ela), escreva \"Outra pessoa (língua): original — tradução: …\", guardando o original e a tradução em português.\n\n" + conversation.take(12000)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 6000).put("temperature", 0.2).put("responseMimeType", "application/json"))
        return try {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) {
                if (model != "models/gemini-2.5-flash") { Backend.saveTextModel(ctx, "models/gemini-2.5-flash"); return memoryVersion(ctx, conversation) }
                return null
            }
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            val j = JSONObject(txt.trim().removePrefix("```json").removeSuffix("```"))
            val r = j.optString("resumo").trim().take(160); val conv = j.optString("conversa").trim()
            if (conv.isBlank()) null else r to conv
        } catch (_: Exception) { null }
    }

    /** A cheap text+vision model this key can use, from ListModels (cached). */
    fun textModel(ctx: Context, key: String): String? {
        Backend.prefsText(ctx)?.let { return it }
        val json = try {
            val c = (URL(io.alema.livecore.LiveProtocol.MODELS_URL).openConnection() as HttpURLConnection).apply { setRequestProperty("x-goog-api-key", key); connectTimeout = 10_000 }
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
        } catch (_: Exception) { null } ?: return null
        val models = JSONObject(json).optJSONArray("models") ?: return null
        val ok = (0 until models.length()).mapNotNull { i ->
            val m = models.optJSONObject(i) ?: return@mapNotNull null
            val methods = m.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
            val n = m.optString("name")
            if ((0 until methods.length()).any { methods.optString(it) == "generateContent" } && "flash" in n && listOf("live", "image", "tts", "audio", "think", "interaction", "robotics", "computer").none { it in n }) n else null
        }
        fun score(n: String) = (if ("lite" in n) -1 else 0) + (if ("preview" in n || "exp" in n) 0 else 1)
        val pick = ok.sortedWith(compareByDescending<String> { score(it) }.thenByDescending { it }).firstOrNull() ?: return null
        Backend.saveTextModel(ctx, pick)
        return pick
    }
}
