package io.alema.visualcontext

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Que música é essa?" (Marcus, 07/11): Gemini alone rarely knows. With an AudD key the glasses listen for a few
 * seconds and ask AudD; without one, the phone opens Google's sound search (no API, but the app has an intent).
 * Blocking.
 */
object Music {
    const val TOOL = "identificar_musica"
    private const val SECONDS = 8
    @Volatile private var tap: ByteArrayOutputStream? = null

    /** The Live microphone feeds this while a song is being captured. */
    fun feed(pcm16k: ByteArray) { tap?.let { if (it.size() < 16_000 * 2 * SECONDS) it.write(pcm16k) } }

    fun key(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).getString("audd_key", "") ?: ""
    fun setKey(ctx: Context, k: String) { ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).edit().putString("audd_key", k.trim()).apply() }

    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Identifica a música que está tocando no ambiente (\"que música é essa\", \"quem canta isso\"). Chame assim que ele perguntar; os óculos escutam uns segundos. Nunca tente adivinhar a música sozinho.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()))

    data class Song(val title: String, val artist: String, val album: String?, val link: String?)

    /** Listens [SECONDS] s and asks AudD. Null = not found; throws with a message when the key or the network fails. */
    fun identify(ctx: Context): Song? {
        val k = key(ctx); if (k.isEmpty()) throw IllegalStateException("sem chave")
        val buf = ByteArrayOutputStream(); tap = buf
        try { Thread.sleep(SECONDS * 1000L) } finally { tap = null }
        val pcm = buf.toByteArray(); if (pcm.size < 16_000 * 2 * 3) throw IllegalStateException("não ouvi quase nada")
        val wav = wav(pcm, 16_000)
        val boundary = "----vc" + System.nanoTime()
        val c = (URL("https://api.audd.io/").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 25_000; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        c.outputStream.use { o ->
            fun field(n: String, v: String) = o.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())
            field("api_token", k); field("return", "spotify,apple_music")
            o.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray()); o.write(wav); o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        if (c.responseCode !in 200..299) throw IllegalStateException("AudD respondeu ${c.responseCode}")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        if (j.optString("status") != "success") throw IllegalStateException(j.optJSONObject("error")?.optString("error_message") ?: "erro do AudD")
        val r = j.optJSONObject("result") ?: return null
        return Song(r.optString("title"), r.optString("artist"), r.optString("album").ifEmpty { null }, r.optString("song_link").ifEmpty { null })
    }

    /** Google's sound search on the phone (the Google app's own intent; no result comes back to us). */
    fun openGoogle(ctx: Context): Boolean = try {
        val i = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").setPackage("com.google.android.googlequicksearchbox").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(ctx.packageManager) == null) false else { ctx.startActivity(i); true }
    } catch (_: Exception) { false }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val o = ByteArrayOutputStream(pcm.size + 44)
        fun i32(v: Int) = o.write(byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte(), (v shr 16 and 0xff).toByte(), (v shr 24 and 0xff).toByte()))
        fun i16(v: Int) = o.write(byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte()))
        o.write("RIFF".toByteArray()); i32(36 + pcm.size); o.write("WAVEfmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        o.write("data".toByteArray()); i32(pcm.size); o.write(pcm)
        return o.toByteArray()
    }
}
