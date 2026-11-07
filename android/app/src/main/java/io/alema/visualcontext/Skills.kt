package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spec 1r: our own skills, learned instead of programmed. A skill is a recipe with a name, when to use it,
 * and steps from a CLOSED menu (never code): web, ler (GET an https page), memoria, ia, claude.
 * Each skill becomes a Gemini Live tool. Nothing here sends, buys or deletes. Blocking calls.
 */
object Skills {
    class Skill(val slug: String, val name: String, val description: String, val triggers: List<String>, val params: JSONObject, val steps: JSONArray) {
        val tool get() = "skill_" + slug.replace('-', '_')
        fun json(): JSONObject = JSONObject().put("slug", slug).put("name", name).put("description", description)
            .put("triggers", JSONArray(triggers)).put("params", params).put("steps", steps)
    }
    class Result(val title: String, val lines: List<String>, val spoken: String)

    private val MENU = setOf("web", "ler", "memoria", "ia", "claude")
    private const val FILE = "habilidades.json"
    const val TOOL_LEARN = "criar_habilidade"

    // ---------- storage: local cache + the memory (save_skill) ----------
    fun all(ctx: Context): List<Skill> = try { parseList(JSONArray(File(ctx.filesDir, FILE).readText())) } catch (_: Exception) { emptyList() }

    private fun parseList(a: JSONArray) = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parse) }

    fun parse(j: JSONObject): Skill? {
        val steps = j.optJSONArray("steps") ?: return null
        for (i in 0 until steps.length()) if (steps.optJSONObject(i)?.optString("tipo") !in MENU) return null
        val slug = j.optString("slug").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        if (slug.isEmpty() || j.optString("name").isBlank()) return null
        val tr = j.optJSONArray("triggers") ?: JSONArray()
        return Skill(slug, j.optString("name"), j.optString("description"), (0 until tr.length()).map { tr.optString(it) }, j.optJSONObject("params") ?: JSONObject(), steps)
    }

    fun put(ctx: Context, s: Skill) {
        val list = all(ctx).filter { it.slug != s.slug } + s
        File(ctx.filesDir, FILE).writeText(JSONArray(list.map { it.json() }).toString())
        Moments.send(ctx, JSONObject().put("action", "save_skill").put("skill", s.json()))
    }

    fun remove(ctx: Context, slug: String) {
        File(ctx.filesDir, FILE).writeText(JSONArray(all(ctx).filter { it.slug != slug }.map { it.json() }).toString())
        Moments.send(ctx, JSONObject().put("action", "delete_skill").put("slug", slug))
    }

    /** Pulls the skills saved from other devices (the glasses, another phone). */
    fun refresh(ctx: Context) {
        val r = Backend.post(ctx, JSONObject().put("action", "skills")) ?: return
        val list = try { parseList(JSONArray(r)) } catch (_: Exception) { return }
        if (list.isNotEmpty()) File(ctx.filesDir, FILE).writeText(JSONArray(list.map { it.json() }).toString())
    }

    // ---------- Live: one tool per skill, plus "criar_habilidade" ----------
    fun tools(ctx: Context): JSONArray {
        val out = JSONArray()
        for (s in all(ctx)) out.put(JSONObject().put("name", s.tool).put("description", "Habilidade do usuário \"${s.name}\": ${s.description}. Use quando ele disser coisas como: ${s.triggers.joinToString("; ")}.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("pedido", JSONObject().put("type", "STRING").put("description", "o que o usuário pediu, com detalhes")))))
        out.put(JSONObject().put("name", TOOL_LEARN)
            .put("description", "Cria uma habilidade nova do usuário (ex.: minha carteira de ações, falar com o Claude, notícias de um assunto). Chame só depois que ele aceitar. Antes, pergunte o que faltar (quais ações, qual assunto).")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("nome", JSONObject().put("type", "STRING")).put("o_que_faz", JSONObject().put("type", "STRING"))
                .put("detalhes", JSONObject().put("type", "STRING").put("description", "o que o usuário informou: ações, assuntos, sites")))
                .put("required", JSONArray().put("nome").put("o_que_faz"))))
        return out
    }

    fun system(ctx: Context): String {
        val names = all(ctx).map { it.name }
        return "HABILIDADES: o usuário tem ${if (names.isEmpty()) "nenhuma habilidade ainda" else "estas habilidades: " + names.joinToString(", ")}. " +
            "Quando ele pedir algo pela segunda vez nesta conversa, ou disser \"aprende isso\" / \"cria uma habilidade\", ofereça em uma frase criar uma habilidade; se aceitar, pergunte o que faltar e chame criar_habilidade. " +
            "Para conversar com o Claude, crie (ou use) a habilidade \"Falar com o Claude\"."
    }

    // ---------- learning ----------
    /** The AI writes the recipe from what the user asked; only steps from the closed menu survive. */
    fun learn(ctx: Context, name: String, what: String, details: String): Skill? {
        val prompt = """Escreva uma habilidade (receita) para o assistente dos óculos do usuário. Responda SÓ JSON:
{"slug":"minha-carteira","name":"$name","description":"1 frase","triggers":["3 a 5 jeitos de pedir"],"params":{"nome": "valor informado pelo usuário"},
 "steps":[{"tipo":"web|ler|memoria|ia|claude", ...}]}
Tipos de passo (só estes):
- {"tipo":"web","pergunta":"..."}: busca no Google e resume (use para cotações, notícias, horários, fatos atuais).
- {"tipo":"ler","url":"https://..."}: lê uma página pública.
- {"tipo":"memoria","busca":"..."}: procura na memória do usuário (pessoas, encontros, notas).
- {"tipo":"ia","pedido":"..."}: pede algo a uma IA com o resultado anterior.
- {"tipo":"claude","pedido":"..."}: manda a pergunta a outro assistente (Claude, GPT ou o modelo que o usuário escolheu) — para "falar com o Claude", "pergunta pro GPT".
Nos textos, use {{pedido}} (o que o usuário pediu agora), {{anterior}} (resultado do passo anterior), {{hoje}} e {{nome_do_param}}.
Nada que envie, compre ou apague. No máximo 3 passos.
HABILIDADE: $name — $what. DETALHES DO USUÁRIO: $details"""
        val txt = Gemini.text(ctx, prompt, json = true, search = false) ?: return null
        return try { parse(JSONObject(txt.substring(txt.indexOf('{'), txt.lastIndexOf('}') + 1)))?.also { put(ctx, it) } } catch (_: Exception) { null }
    }

    // ---------- running ----------
    fun run(ctx: Context, s: Skill, ask: String): Result {
        var prev = ""
        val vars = HashMap<String, String>().apply {
            put("pedido", ask); put("hoje", java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date()))
            s.params.keys().forEach { k -> put(k, s.params.optString(k)) }
        }
        fun fill(t: String) = Regex("\\{\\{(\\w+)\\}\\}").replace(t) { m -> if (m.groupValues[1] == "anterior") prev else vars[m.groupValues[1]] ?: "" }
        for (i in 0 until s.steps.length()) {
            val st = s.steps.optJSONObject(i) ?: continue
            prev = when (st.optString("tipo")) {
                "web" -> Gemini.text(ctx, fill(st.optString("pergunta")) + "\nResponda em português, curto, com números e datas.", json = false, search = true)
                "ler" -> read(fill(st.optString("url")))
                "memoria" -> memory(ctx, fill(st.optString("busca")))
                "ia" -> Gemini.text(ctx, fill(st.optString("pedido")), json = false, search = false)
                "claude" -> Claude.ask(ctx, fill(st.optString("pedido")).ifBlank { ask })
                else -> null
            } ?: return Result(s.name, listOf("não consegui agora"), "Não consegui fazer ${s.name} agora.")
        }
        // The lens card and the spoken sentence, from whatever the steps found.
        val card = Gemini.text(ctx, "Transforme em um card curto para a lente dos óculos e uma frase falada. Responda SÓ JSON {\"titulo\":\"\",\"linhas\":[\"até 5 linhas curtas\"],\"fala\":\"1 ou 2 frases\"}. Pedido: $ask. Conteúdo: ${prev.take(6000)}", json = true, search = false)
        return try {
            val j = JSONObject(card!!.substring(card.indexOf('{'), card.lastIndexOf('}') + 1))
            val l = j.optJSONArray("linhas") ?: JSONArray()
            Result(j.optString("titulo").ifBlank { s.name }, (0 until l.length()).map { l.optString(it) }.take(5), j.optString("fala").ifBlank { prev.take(200) })
        } catch (_: Exception) { Result(s.name, prev.lines().filter { it.isNotBlank() }.take(5), prev.take(300)) }
    }

    private fun read(url: String): String? {
        if (!url.startsWith("https://")) return null
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12_000; setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) VisualContext") }
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }.take(400_000)
                .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ").replace(Regex("(?s)<[^>]+>"), " ").replace(Regex("\\s+"), " ").take(12_000)
        } catch (_: Exception) { null }
    }

    private fun memory(ctx: Context, q: String): String {
        val words = q.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        val out = JSONArray()
        for (entity in listOf("person", "encounter", "note")) {
            val a = try { JSONArray(Backend.post(ctx, JSONObject().put("action", "list").put("entity", entity).put("limit", 300)) ?: "[]") } catch (_: Exception) { JSONArray() }
            for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; val t = o.toString().lowercase(); if (words.isEmpty() || words.any { t.contains(it) }) out.put(o.apply { remove("face_embedding"); remove("transcript") }); if (out.length() > 25) break }
        }
        return out.toString().take(8000)
    }
}

/** Plain Gemini calls for skills (text, optional Google Search, optional JSON). Blocking. */
object Gemini {
    fun text(ctx: Context, prompt: String, json: Boolean, search: Boolean): String? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = Describe.textModel(ctx, key) ?: return null
        val gen = JSONObject().put("maxOutputTokens", 6000).put("temperature", 0.2)
        if (json && !search) gen.put("responseMimeType", "application/json")
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).put("generationConfig", gen)
        if (search) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        return try {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 60_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }.ifBlank { null }
        } catch (_: Exception) { null }
    }
}

/**
 * "Falar com outro assistente": the user's own key. OpenRouter (sk-or-…, any model), OpenAI (sk-…),
 * or Anthropic (sk-ant-…). The key and the model id are settings, so a new model needs no new build.
 * Blocking. No project memory travels with it: it is a plain question to that model.
 */
object Claude {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
    fun key(ctx: Context) = prefs(ctx).getString("claude_key", "") ?: ""
    fun setKey(ctx: Context, k: String) { prefs(ctx).edit().putString("claude_key", k.trim()).apply() }
    fun provider(k: String) = when { k.startsWith("sk-or-") -> "openrouter"; k.startsWith("sk-ant-") -> "anthropic"; k.startsWith("sk-") -> "openai"; else -> "" }
    fun providerName(k: String) = when (provider(k)) { "openrouter" -> "OpenRouter"; "anthropic" -> "Anthropic"; "openai" -> "OpenAI"; else -> "" }

    fun ask(ctx: Context, q: String): String? = call(ctx, q).first

    /** Settings → "Testar": null when it works, otherwise what went wrong, in words. */
    fun test(ctx: Context): String? {
        val k = key(ctx); if (provider(k).isEmpty()) return "Chave não reconhecida: precisa começar com sk-or-, sk-ant- ou sk-."
        val (a, why) = call(ctx, "Responda só: ok")
        return if (a != null) null else why
    }

    private fun call(ctx: Context, q: String): Pair<String?, String> {
        val k = key(ctx); val prov = provider(k)
        if (prov.isEmpty()) return null to "Falta a chave do assistente nos Ajustes do VisualContext."
        val sys = "Você responde pelos óculos do usuário: em português, curto (até 4 frases), sem markdown."
        return try {
            val (url, headers, body) = when (prov) {
                "anthropic" -> Triple("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"),
                    JSONObject().put("model", model(ctx)).put("max_tokens", 800).put("system", sys).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q))))
                else -> Triple(if (prov == "openrouter") "https://openrouter.ai/api/v1/chat/completions" else "https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer $k"),
                    JSONObject().put("model", model(ctx)).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", sys)).put(JSONObject().put("role", "user").put("content", q))))
            }
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 60_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); headers.forEach { (h, v) -> setRequestProperty(h, v) }
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { JSONObject(c.errorStream.bufferedReader().readText()).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                return null to when (code) { 401, 403 -> "Chave recusada pela ${providerName(k)}."; 402 -> "Sem créditos na ${providerName(k)}."; 404 -> "Modelo \"${model(ctx)}\" não existe na ${providerName(k)}."; 429 -> "Limite de uso atingido. Tente daqui a pouco."; else -> "Erro $code${if (err.isNotEmpty()) ": " + err.take(120) else ""}" }
            }
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val text = if (prov == "anthropic") j.optJSONArray("content")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") } }
                else j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            if (text.isNullOrBlank()) null to "Resposta vazia." else text to ""
        } catch (e: Exception) { null to "Sem internet (${e.javaClass.simpleName})." }
    }

    /** Model id for the key's provider: the saved one, or a sensible default. */
    fun model(ctx: Context): String {
        val m = prefs(ctx).getString("claude_model", "")?.trim().orEmpty()
        if (m.isNotEmpty()) return m
        return defaultModel(key(ctx))
    }
    fun defaultModel(k: String) = when (provider(k)) { "openrouter" -> "anthropic/claude-sonnet-5.5"; "openai" -> "gpt-5-mini"; else -> "claude-sonnet-5-5" }
    fun setModel(ctx: Context, m: String) { prefs(ctx).edit().putString("claude_model", m.trim()).apply() }
}
