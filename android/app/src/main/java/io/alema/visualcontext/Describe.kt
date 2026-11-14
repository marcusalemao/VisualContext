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
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { if (c.responseCode in listOf(400, 403, 404)) Backend.markBadModel(ctx, model); return null }
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
                .firstOrNull { !it.optBoolean("thought") && it.optString("text").isNotBlank() }
                ?.optString("text")?.trim()?.lines()?.first()?.trim('.', '"', ' ')?.lowercase()?.take(48)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    /** One question about one photo, plain text answer (first non-thought part). Blocking; null on failure. */
    fun vision(ctx: Context, jpeg: ByteArray, prompt: String, maxTokens: Int = 700): String? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.2))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { if (c.responseCode in listOf(400, 403, 404)) Backend.markBadModel(ctx, model); return null }
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }.trim().ifBlank { null }
        } catch (_: Exception) { null }
    }

    /**
     * Marcus, 09/11: "só o endereço não é suficiente: preciso saber se deixei na mesa, no sofá, embaixo do travesseiro".
     * Where the object is in this photo, with what is around it as a reference (for a motorbike, the street side, the shop).
     */
    fun whereIs(ctx: Context, jpeg: ByteArray, name: String): String? =
        vision(ctx, jpeg, "Nesta foto, onde está \"$name\"? Responda em português, até 12 palavras, só o lugar concreto e uma referência ao lado, " +
            "como alguém diria para outra pessoa achar: \"na mesa de cabeceira, ao lado do abajur\", \"no sofá, em cima da almofada cinza\", " +
            "\"na calçada em frente à padaria, perto do poste\". Se estiver na mão de alguém, diga \"na mão\". Se o objeto não aparecer, responda só: não vejo. Sem ponto final.")
            ?.lines()?.first()?.trim('.', '"', ' ')?.lowercase()?.take(90)?.takeIf { it.isNotBlank() && !it.startsWith("não vejo") }

    /**
     * Which of the user's registered objects are in view, and where (the automatic "last seen"). Returns name → place;
     * empty when none. Objects in someone's hand are skipped (they are moving, not left somewhere).
     */
    fun spotThings(ctx: Context, jpeg: ByteArray, names: List<String>): Map<String, String> {
        val txt = vision(ctx, jpeg, "O usuário tem estes objetos: ${names.joinToString(", ")}. Quais deles aparecem NESTA foto, parados em algum lugar (não na mão de alguém)? " +
            "Responda só JSON, uma lista: [{\"nome\": \"<nome exatamente como na lista>\", \"onde\": \"<lugar concreto e referência ao lado, até 12 palavras>\"}]. " +
            "Só inclua se tiver certeza de que é o objeto. Se nenhum, responda []. Em português.") ?: return emptyMap()
        return try {
            val a = JSONArray(txt.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o -> val n = o.optString("nome"); val w = o.optString("onde").trim('.', ' ').lowercase()
                if (n.isBlank() || w.isBlank() || names.none { it.equals(n, true) }) null else names.first { it.equals(n, true) } to w.take(90) }.toMap()
        } catch (_: Exception) { emptyMap() }
    }

    /** Translation of a text the glasses read (Marcus, 09/11). Returns (source language, translation); null on failure. */
    fun translate(ctx: Context, text: String, target: String): Pair<String, String>? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val prompt = "Traduza para $target o texto abaixo, fiel e natural, mantendo as quebras de linha. Responda só JSON: {\"idioma_original\": \"<língua do texto, em português>\", \"traducao\": \"...\"}.\n\n" + text.take(6000)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4000).put("temperature", 0.2).put("responseMimeType", "application/json"))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { if (c.responseCode in listOf(400, 403, 404)) Backend.markBadModel(ctx, model); return null }
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val j = JSONObject((0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }.trim().removePrefix("```json").removeSuffix("```"))
            val t = j.optString("traducao").trim(); if (t.isBlank()) null else j.optString("idioma_original") to t
        } catch (_: Exception) { null }
    }

    /**
     * The memory version of a conversation with the assistant (07/11): reading for the user is help, not memory.
     * Keeps the user's words and decisions; long readings (menu, sign, leaflet, book page) become one line.
     * Returns (one-sentence summary, condensed conversation). Blocking; null on failure (then the raw text is kept).
     */
    fun memoryVersion(ctx: Context, conversation: String, tries: Int = 0, at: Long = System.currentTimeMillis()): Pair<String, String>? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val prompt = "Abaixo está uma conversa entre o usuário e o assistente dos óculos. Prepare a versão que vai para a memória dele, em português, e responda em JSON " +
            "{\"resumo\": \"o assunto em até 7 palavras, começando pelo tema (\\\"Gastos a pagar até sexta\\\", \\\"Música do bar: Material Girl\\\", \\\"Agenda de seg 09/11\\\"); nunca \\\"O usuário…\\\", \\\"Você pediu…\\\" nem verbo de pedir\", \"conversa\": \"a conversa condensada\"}.\n" +
            "A conversa aconteceu em " + java.text.SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(at)) + ": datas relativas (hoje, amanhã, sexta) no resumo viram datas reais a partir desse dia.\n" +
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
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) {
                // A model Google retired for this key ("no longer available to new users"): skip it and pick the next one.
                if (c.responseCode in listOf(400, 403, 404) && tries < 2) { Backend.markBadModel(ctx, model); return memoryVersion(ctx, conversation, tries + 1, at) }
                return null
            }
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val txt = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            val j = JSONObject(txt.trim().removePrefix("```json").removeSuffix("```"))
            val r = j.optString("resumo").trim().take(160); val conv = j.optString("conversa").trim()
            if (conv.isBlank()) null else r to conv
        } catch (_: Exception) { null }
    }

    /**
     * Marcus, 09/11: he set the languages he speaks, and still the lens showed "Ki múzika sta tukandu". Google's live
     * transcription only takes them as a hint (its docs: "bias", not a lock). Common words of his languages are
     * checked first (free); only a line with none of them goes to a quick text call that rewrites it.
     */
    private val COMMON = mapOf(
        "pt" to "que o a os as de do da dos das e é em um uma no na nos nas pra para por com não sim eu você ele ela isso esse essa está tá estou qual quem onde quando como meu minha tem ter me se mais muito aqui agora hoje música tocando",
        "en" to "the a an is are was what who where when how i you he she it this that my your to of in on for with and not yes do does can please",
        "de" to "der die das ist und nicht ich du er sie es was wer wo wann wie ein eine mein dein mit für auf ja nein bitte",
        "es" to "el la los las es que de y en un una no sí yo tú qué quién dónde cuándo cómo mi está",
        "fr" to "le la les est que de et en un une ne pas oui je tu il elle quoi qui où quand comment mon",
        "it" to "il la gli è che di e in un una non sì io tu cosa chi dove quando come mio")
    fun plausible(text: String, langs: List<String>): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.size < 3) return true
        val common = langs.map { it.take(2) }.flatMap { (COMMON[it] ?: "").split(" ") }.toSet()
        return words.any { it in common }
    }
    /** What the user probably said, in one of his languages; null on failure. Blocking. */
    fun fixHeard(ctx: Context, heard: String): String? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return null
        val model = textModel(ctx, key) ?: return null
        val prompt = "Uma transcrição automática escreveu a fala de uma pessoa assim: \"" + heard.take(300) + "\". Ela só fala " + Backend.languageNames(ctx).joinToString(", ") +
            ". A transcrição errou a língua e escreveu pelo som. Escreva o que ela provavelmente disse, numa dessas línguas, com a grafia certa. Responda só a frase, sem aspas."
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 200).put("temperature", 0.1).put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        return try {
            val c = (Gem.url(key, "$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 8_000; readTimeout = 15_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }.trim().trim('"').takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    /** A cheap text+vision model this key can use, from ListModels (cached). */
    fun textModel(ctx: Context, key: String): String? {
        Backend.prefsText(ctx)?.let { return it }
        val json = try {
            val c = (Gem.url(key, "models?pageSize=200").openConnection() as HttpURLConnection).apply { Gem.auth(this, key); connectTimeout = 10_000 }
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
        } catch (_: Exception) { null } ?: return null
        val models = JSONObject(json).optJSONArray("models") ?: return null
        val ok = (0 until models.length()).mapNotNull { i ->
            val m = models.optJSONObject(i) ?: return@mapNotNull null
            val methods = m.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
            val n = m.optString("name")
            if ((0 until methods.length()).any { methods.optString(it) == "generateContent" } && "flash" in n && listOf("live", "image", "tts", "audio", "think", "interaction", "robotics", "computer").none { it in n }) n else null
        }
        fun score(n: String) = (if (n.endsWith("flash-latest")) 2 else 0) + (if ("lite" in n) -1 else 0) + (if ("preview" in n || "exp" in n) 0 else 1)
        val bad = Backend.badModels(ctx)
        val pick = ok.filter { it !in bad }.sortedWith(compareByDescending<String> { score(it) }.thenByDescending { it }).firstOrNull() ?: return null
        Backend.saveTextModel(ctx, pick)
        return pick
    }
}
