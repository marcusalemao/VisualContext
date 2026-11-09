package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A menu behind a QR code (07/11): read it into categories so the lens shows them and Gemini Live can walk
 * the user through ("tem massas e peixes, quer ver alguma?"). Reading is help, not memory: nothing is stored.
 */
object MenuRead {
    private val MENU_HINT = Regex("(?i)(card[aá]pio|menu|goomer|anota\\.ai|ifood|cardapioweb|menudino|instadelivery|qrmenu|pedido|delivery|consumer\\.|abrahao|saipos|cardapio\\.|neemo|getin|tagme|olaclick)")
    fun looksLikeMenu(url: String) = MENU_HINT.containsMatchIn(url)

    class Menu(val place: String, val categories: List<String>, val json: String)

    fun read(ctx: Context, url: String): Menu? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = Describe.textModel(ctx, key) ?: return null
        val page = pageText(url)
        val ask = """Isto é o cardápio de um restaurante, lido de $url. Responda em JSON:
{"restaurante": "", "categorias": [{"nome": "Massas", "itens": [{"nome": "", "preco": "", "descricao": "curta"}]}]}
Agrupe em categorias como Entradas, Massas, Carnes, Aves, Peixes, Vegetarianos, Sobremesas, Bebidas (use as do cardápio quando houver). Não invente pratos nem preços.
""" + if (page.length > 300) "\nTEXTO DA PÁGINA:\n" + page.take(30000) else "\nLeia a página do link."
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ask)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 8000).put("temperature", 0.1).apply { if (page.length > 300) put("responseMimeType", "application/json") })
        if (page.length <= 300) body.put("tools", JSONArray().put(JSONObject().put("url_context", JSONObject())))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 60_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            val s = txt.substring(txt.indexOf('{').coerceAtLeast(0), (txt.lastIndexOf('}') + 1).coerceAtLeast(0))
            val j = JSONObject(s)
            val cats = j.optJSONArray("categorias") ?: return null
            val names = (0 until cats.length()).mapNotNull { cats.optJSONObject(it)?.optString("nome")?.takeIf { n -> n.isNotBlank() } }
            if (names.isEmpty()) null else Menu(j.optString("restaurante"), names, j.toString())
        } catch (_: Exception) { null }
    }

    /** The page as plain text (many digital menus are server-rendered; the rest go through Gemini's own reader). */
    private fun pageText(url: String): String = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12_000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) VisualContext") }
        if (c.responseCode !in 200..299) "" else c.inputStream.bufferedReader().use { it.readText() }.take(600_000)
            .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ").replace(Regex("(?s)<[^>]+>"), " ")
            .replace(Regex("&nbsp;|&#160;"), " ").replace(Regex("&amp;"), "&").replace(Regex("\\s+"), " ").trim()
    } catch (_: Exception) { "" }
}
