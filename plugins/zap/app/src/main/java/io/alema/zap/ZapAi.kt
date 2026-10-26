package io.alema.zap

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import io.alema.zap.core.Brief
import io.alema.zap.core.BriefError
import io.alema.zap.core.BriefPrompt
import io.alema.zap.core.BriefState
import io.alema.zap.core.DigestPrompt
import io.alema.zap.core.DigestState
import io.alema.zap.core.ZapConversation
import io.alema.zap.core.ZapMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Summaries with the wearer's own AI. Zap pays for nobody's AI: in the free
 * version each person brings a key (Gemini's is free in Google AI Studio);
 * the Easy plan will route through alema's server instead.
 *
 * The only reason Zap has the internet permission. Nothing goes out unless the
 * wearer turned summaries on, and then only the chat being opened, straight to
 * the provider they chose — never to alema.
 *
 * Gemini also hears WhatsApp voice notes (sent as audio with the request), so
 * "five voice messages in a row" become one sentence. Other providers get the
 * text only, and the card says how many voice notes were left out.
 */
internal object ZapAi {
    private const val TAG = "ZapAi"
    private const val PREFS = "zap_ai"
    private const val TIMEOUT_MS = 25_000
    /** Voice notes sent along per summary, and their total size: enough for a burst of audios, never a whole archive. */
    private const val MAX_AUDIOS = 5
    private const val MAX_AUDIO_BYTES = 12 * 1024 * 1024

    enum class Provider(val label: String, val defaultModel: String, val hearsAudio: Boolean) {
        GEMINI("Google Gemini", "gemini-flash-latest", true),
        OPENAI("OpenAI", "gpt-4o-mini", false),
        OPENROUTER("OpenRouter", "google/gemini-2.5-flash", false),
    }

    private val worker = Executors.newSingleThreadExecutor()

    // --- settings ----------------------------------------------------------

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("on", on).putBoolean("decided", true).apply()

    /** The wearer has seen the switch and chose, either way: the "new" hint on the glasses goes away. */
    fun decided(context: Context): Boolean = prefs(context).getBoolean("decided", false)

    /** On, with a key: the glasses may open chats on a summary. */
    fun ready(context: Context): Boolean = enabled(context) && key(context) != null

    fun provider(context: Context): Provider =
        runCatching { Provider.valueOf(prefs(context).getString("provider", null) ?: "") }.getOrDefault(Provider.GEMINI)

    fun setProvider(context: Context, provider: Provider) = prefs(context).edit().putString("provider", provider.name).apply()

    fun model(context: Context): String =
        prefs(context).getString("model_${provider(context).name}", null)?.takeIf { it.isNotBlank() } ?: provider(context).defaultModel

    fun setModel(context: Context, model: String) =
        prefs(context).edit().putString("model_${provider(context).name}", model.trim()).apply()

    /** The key, encrypted with the phone's Keystore; it never leaves the phone except to its own provider. */
    fun key(context: Context): String? = prefs(context).getString("key_${provider(context).name}", null)?.let(::decrypt)

    fun setKey(context: Context, key: String?) {
        val name = "key_${provider(context).name}"
        val clean = key?.trim()?.takeIf { it.isNotEmpty() }
        prefs(context).edit().apply { if (clean == null) remove(name) else putString(name, encrypt(clean)) }.apply()
    }

    /** "AIza…3f2c": enough to recognize the key on screen without showing it. */
    fun maskedKey(context: Context): String? = key(context)?.let { if (it.length <= 8) "••••" else it.take(4) + "…" + it.takeLast(4) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ZapInbox.secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ZapInbox.secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    // --- summarizing ----------------------------------------------------------

    /** Off the main thread; [done] is called on the worker thread. */
    fun summarize(context: Context, chat: ZapConversation, messages: List<ZapMessage>, done: (BriefState) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val key = key(app)
            if (!enabled(app) || key == null) {
                done(BriefState.Failed(BriefError.NO_KEY))
                return@execute
            }
            val started = System.currentTimeMillis()
            val result = runCatching { request(app, key, chat, messages) }
                .getOrElse { e -> BriefState.Failed(errorOf(e)).also { Log.w(TAG, "summary failed: ${e.javaClass.simpleName} ${e.message?.take(160)}") } }
            Log.i(TAG, "summary ${result.javaClass.simpleName} in ${System.currentTimeMillis() - started} ms, ${messages.size} messages")
            done(result)
        }
    }

    /**
     * The day's summary across chats ("✦ Resumo do dia" on the glasses). Text only:
     * voice notes go in as "[voice message 12s]", so it is quick and cheap.
     */
    fun digest(context: Context, chats: List<Pair<ZapConversation, List<ZapMessage>>>, done: (DigestState) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val key = key(app)
            if (!enabled(app) || key == null) {
                done(DigestState.Failed(BriefError.NO_KEY))
                return@execute
            }
            val started = System.currentTimeMillis()
            val result = runCatching {
                val pt = Locale.getDefault().language == "pt"
                val user = DigestPrompt.user(chats, System.currentTimeMillis(), ZoneId.systemDefault())
                val answer = ask(app, key, DigestPrompt.system(pt), user, emptyList())
                val digest = DigestPrompt.parse(answer, chats.map { it.first })
                if (digest == null) DigestState.Failed(BriefError.FAILED)
                else DigestState.Ready(digest.copy(chats = chats.size, messages = chats.sumOf { it.second.size }))
            }.getOrElse { e -> DigestState.Failed(errorOf(e)).also { Log.w(TAG, "digest failed: ${e.javaClass.simpleName} ${e.message?.take(160)}") } }
            Log.i(TAG, "digest ${result.javaClass.simpleName} in ${System.currentTimeMillis() - started} ms, ${chats.size} chats")
            done(result)
        }
    }

    /** "Test the key" in settings: a tiny request, the answer as a short line for the card. */
    fun test(context: Context, done: (BriefError?) -> Unit) {
        val probe = ZapConversation("test", "Ana", null, false, listOf(ZapMessage("Ana", "Almoço amanhã às 12h no Lume?", System.currentTimeMillis())))
        summarize(context, probe, probe.messages) { state ->
            done((state as? BriefState.Failed)?.error)
        }
    }

    private class HttpFailure(val code: Int, val body: String) : IOException("HTTP $code")

    private fun errorOf(e: Throwable): BriefError = when (e) {
        is HttpFailure -> when {
            e.code == 401 || e.code == 403 -> BriefError.BAD_KEY
            e.code == 400 && (e.body.contains("API_KEY_INVALID") || e.body.contains("API key not valid")) -> BriefError.BAD_KEY
            e.code == 429 || e.code == 402 -> BriefError.LIMIT
            else -> BriefError.FAILED
        }
        is UnknownHostException -> BriefError.OFFLINE
        is SocketTimeoutException -> BriefError.FAILED
        is IOException -> if (e.message?.contains("unreachable", ignoreCase = true) == true) BriefError.OFFLINE else BriefError.FAILED
        else -> BriefError.FAILED
    }

    private fun request(context: Context, key: String, chat: ZapConversation, messages: List<ZapMessage>): BriefState {
        val provider = provider(context)
        val zone = ZoneId.systemDefault()
        val pt = Locale.getDefault().language == "pt"
        // Voice notes the AI can hear: Gemini only, the newest unheard ones first, within the size cap.
        val audio = if (provider.hearsAudio) voiceAudio(context, chat, messages) else emptyMap()
        val voiceTotal = messages.count { it.voiceSeconds != null && it.sender != ZapMessage.ME && it.timeMs > chat.readUpToMs }
        val user = BriefPrompt.user(chat, messages, System.currentTimeMillis(), zone, audioFor = audio.keys, me = if (pt) "Eu" else "Me")
        val answer = ask(context, key, BriefPrompt.system(pt), user, audio.values.toList())
        val brief: Brief = BriefPrompt.parse(answer, zone) ?: return BriefState.Failed(BriefError.FAILED)
        val heard = audio.keys.count { messages[it].timeMs > chat.readUpToMs }
        return BriefState.Ready(
            brief.copy(
                messages = messages.count { it.sender != ZapMessage.ME && it.timeMs > chat.readUpToMs },
                voiceHeard = heard,
                voiceMissing = (voiceTotal - heard).coerceAtLeast(0),
            ),
        )
    }

    /** One request to the provider the wearer chose; audio only reaches Gemini. */
    private fun ask(context: Context, key: String, system: String, user: String, audio: List<ByteArray>): String =
        when (provider(context)) {
            Provider.GEMINI -> gemini(key, model(context), system, user, audio)
            Provider.OPENAI -> openAiCompatible("https://api.openai.com/v1/chat/completions", key, model(context), system, user, openRouter = false)
            Provider.OPENROUTER -> openAiCompatible("https://openrouter.ai/api/v1/chat/completions", key, model(context), system, user, openRouter = true)
        }

    /** Index in [messages] → the voice note's bytes, found in WhatsApp's folder (needs the folder access set up for "Play"). */
    private fun voiceAudio(context: Context, chat: ZapConversation, messages: List<ZapMessage>): Map<Int, ByteArray> {
        if (!ZapVoiceNotes.hasAccess(context)) return emptyMap()
        val out = linkedMapOf<Int, ByteArray>()
        var total = 0
        val wanted = messages.withIndex()
            .filter { (_, m) -> m.voiceSeconds != null && m.sender != ZapMessage.ME && m.timeMs > chat.readUpToMs }
            .takeLast(MAX_AUDIOS)
        for ((index, message) in wanted) {
            val uri: Uri = ZapVoiceNotes.find(context, message) ?: continue
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: continue
            if (total + bytes.size > MAX_AUDIO_BYTES) break
            total += bytes.size
            out[index] = bytes
        }
        // Back in chat order, so "audio #1" in the text is the first part attached.
        return out.toSortedMap()
    }

    private fun gemini(key: String, model: String, system: String, user: String, audio: List<ByteArray>): String {
        val parts = JSONArray().put(JSONObject().put("text", user))
        audio.forEach { bytes ->
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/ogg").put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.3))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${Uri.encode(model)}:generateContent"
        val json = JSONObject(post(url, body.toString(), mapOf("x-goog-api-key" to key)))
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: throw IOException("no candidate")
        val texts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: throw IOException("no parts")
        return (0 until texts.length()).joinToString("") { texts.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun openAiCompatible(url: String, key: String, model: String, system: String, user: String, openRouter: Boolean): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val headers = buildMap {
            put("Authorization", "Bearer $key")
            if (openRouter) {
                put("HTTP-Referer", "https://alema.io")
                put("X-Title", "Zap")
            }
        }
        val json = JSONObject(post(url, body.toString(), headers))
        return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw IOException("no choices")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw HttpFailure(code, text.take(600))
            return text
        } finally {
            connection.disconnect()
        }
    }
}
