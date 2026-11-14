package io.alema.visualcontext

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** "Ouvir": a sample sentence in a Gemini voice (the same voices the Live uses). Blocking; cached per voice. */
object VoicePreview {
    private const val MODEL = "gemini-2.5-flash-preview-tts"
    private val cache = HashMap<String, ByteArray>()
    private var out: SpeakerOut? = null

    /** Returns null when it played, otherwise why not. */
    fun play(ctx: Context, voice: String): String? {
        val key = Backend.geminiKey(ctx); if (key.length < 20) return "Salve a chave do Gemini primeiro."
        val pcm = cache[voice] ?: fetch(key, voice)?.also { cache[voice] = it } ?: return "Não deu para gerar a amostra."
        val s = out ?: SpeakerOut().also { out = it; it.start() }
        s.flush(); s.play(pcm)
        return null
    }

    private fun fetch(key: String, voice: String): ByteArray? = try {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Oi. Eu sou a voz $voice. É assim que eu vou falar com você pelos óculos.")))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
        val c = (Gem.url(key, "models/$MODEL:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); Gem.auth(this, key)
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) null
        else {
            val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val data = (0 until (parts?.length() ?: 0)).mapNotNull { parts!!.optJSONObject(it)?.optJSONObject("inlineData")?.optString("data") }.firstOrNull { it.isNotEmpty() }
            data?.let { Base64.decode(it, Base64.DEFAULT) } // PCM 16-bit, 24 kHz, mono: what SpeakerOut plays
        }
    } catch (_: Exception) { null }
}
