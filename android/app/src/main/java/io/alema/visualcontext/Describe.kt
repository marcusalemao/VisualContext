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
            if (c.responseCode !in 200..299) return null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
                .firstOrNull { !it.optBoolean("thought") && it.optString("text").isNotBlank() }
                ?.optString("text")?.trim()?.lines()?.first()?.trim('.', '"', ' ')?.lowercase()?.take(48)?.takeIf { it.isNotBlank() }
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
            if ((0 until methods.length()).any { methods.optString(it) == "generateContent" } && "flash" in n && "live" !in n && "image" !in n && "tts" !in n && "audio" !in n) n else null
        }
        fun score(n: String) = (if ("lite" in n) -1 else 0) + (if ("preview" in n || "exp" in n) 0 else 1)
        val pick = ok.sortedWith(compareByDescending<String> { score(it) }.thenByDescending { it }).firstOrNull() ?: return null
        Backend.saveTextModel(ctx, pick)
        return pick
    }
}
