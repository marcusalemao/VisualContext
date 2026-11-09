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
    const val SECONDS_MS = SECONDS * 1000L
    @Volatile private var tap: ByteArrayOutputStream? = null

    /** The Live microphone feeds this while a song is being captured. */
    fun feed(pcm16k: ByteArray) { tap?.let { if (it.size() < 16_000 * 2 * SECONDS) it.write(pcm16k) } }

    fun key(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).getString("audd_key", "") ?: ""
    fun setKey(ctx: Context, k: String) { ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).edit().putString("audd_key", k.trim()).apply() }

    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Identifica a música que está tocando no ambiente (\"que música é essa\", \"quem canta isso\"). Chame assim que ele perguntar; os óculos escutam uns segundos. Nunca tente adivinhar a música sozinho.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()))

    data class Song(val title: String, val artist: String, val album: String?, val link: String?, val timecode: String? = null)

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
        return Song(r.optString("title"), r.optString("artist"), r.optString("album").ifEmpty { null }, r.optString("song_link").ifEmpty { null }, r.optString("timecode").ifEmpty { null })
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

/**
 * Things (Marcus, 07/11): "essas são minhas chaves" keeps a picture of the object and where it was; "onde deixei as chaves?"
 * answers from memory before looking. Blocking.
 */
object Things {
    const val TOOL_KEEP = "guardar_objeto"
    const val TOOL_FIND = "onde_deixei"

    fun tools(): List<JSONObject> = listOf(
        JSONObject().put("name", TOOL_KEEP)
            .put("description", "Guarda um objeto do usuário na memória visual, com foto e lugar: \"essas são minhas chaves\", \"guarda onde deixei o carregador\", \"lembra que o óculos ficou aqui\". Chame na hora; a foto é a câmera agora, então peça para ele olhar para o objeto se não estiver vendo.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("nome", JSONObject().put("type", "STRING").put("description", "o objeto, curto: chaves, carteira, carregador"))
                .put("onde", JSONObject().put("type", "STRING").put("description", "onde está, se ele disse ou se dá para ver: mesa da cozinha, bolso da mochila")))
                .put("required", JSONArray().put("nome"))),
        JSONObject().put("name", TOOL_FIND)
            .put("description", "Onde o usuário deixou um objeto, pela memória visual: \"onde deixei as chaves?\", \"cadê meu carregador?\". Chame ANTES de olhar a câmera; responda com o lugar e quando foi.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("objeto", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("objeto"))),
    )
    const val SYSTEM = " OBJETOS: quando ele mostrar ou nomear um objeto seu (\"essas são minhas chaves\"), chame guardar_objeto; nunca transforme isso em nota. Quando perguntar onde deixou algo, chame onde_deixei primeiro e só depois, se não houver registro, olhe a câmera. A resposta de onde_deixei traz o lugar exato e a foto de quando foi visto já aparece na lente: diga o lugar e \"a foto está na lente\"; nunca diga que não pode mostrar o passado."

    /** Current frame as a small JPEG (base64), or null. */
    fun snapshot(jpeg: ByteArray?): String? = jpeg?.let {
        val b = android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) ?: return null
        val s = 480f / maxOf(b.width, b.height); val small = if (s < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
        val o = ByteArrayOutputStream(); small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, o)
        android.util.Base64.encodeToString(o.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /**
     * Saves where an object is now. [where] is what the user said; when empty, the photo is asked where the object is
     * (Marcus, 09/11: "na mesa, no sofá, embaixo do travesseiro" — the address alone is useless). Returns the place saved.
     */
    fun keep(ctx: Context, name: String, where: String?, jpeg: ByteArray?, place: String?): Pair<Boolean, String?> {
        val loc = Where.location(ctx)
        val seen = if (where.isNullOrBlank() && jpeg != null) Frames.shrink(jpeg, 1024)?.let { Describe.whereIs(ctx, it, name) } else null
        val spot = where?.takeIf { it.isNotBlank() } ?: seen
        val body = JSONObject().put("action", "thing").put("name", name).put("location_description", listOfNotNull(spot, place).joinToString(" · ").ifEmpty { null })
            .put("snapshot", snapshot(jpeg)).apply { if (loc != null) put("lat", loc.latitude).put("lng", loc.longitude) }
        val r = Backend.post(ctx, body)
        if (r == null) { Moments.send(ctx, body); return false to spot } // offline: queued, goes later
        return true to spot
    }

    // ── Automatic "last seen" (Marcus, 09/11: "isso devia ser automático") ──
    /** Names of the user's objects (cached for 10 min), so the camera can look for them. */
    @Volatile private var namesAt = 0L; @Volatile private var namesCache: List<String> = emptyList()
    fun names(ctx: Context): List<String> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - namesAt < 600_000) return namesCache
        namesAt = now
        val r = Backend.post(ctx, JSONObject().put("action", "list").put("entity", "thing").put("limit", 200)) ?: return namesCache
        val a = runCatching { JSONArray(r) }.getOrNull() ?: return namesCache
        namesCache = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.trim()?.lowercase()?.takeIf { n -> n.isNotBlank() } }.distinct().take(30)
        return namesCache
    }
    fun forgetNames() { namesAt = 0L }
    /** The same object in the same spot is not written twice within 15 min. */
    private val lastSpot = HashMap<String, Pair<String, Long>>()
    fun worthSaving(name: String, where: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime(); val last = lastSpot[name]
        val same = last != null && now - last.second < 900_000 && (last.first == where || last.first.take(12) == where.take(12))
        if (!same) lastSpot[name] = where to now
        return !same
    }

    const val TOOL_SEEN = "vi_objeto"
    fun seenTool(names: List<String>): JSONObject = JSONObject().put("name", TOOL_SEEN)
        .put("description", "Chame, sem dizer nada, quando vir na câmera um dos objetos do usuário parado em algum lugar (não na mão): ${names.joinToString(", ")}. Assim ele sabe depois onde deixou.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("nome", JSONObject().put("type", "STRING").put("description", "o objeto, com o nome da lista"))
            .put("onde", JSONObject().put("type", "STRING").put("description", "lugar concreto e referência ao lado: na mesa da sala, ao lado do controle"))).put("required", JSONArray().put("nome").put("onde")))

    data class Found(val name: String, val where: String?, val at: String, val snapshot: String?)
    fun find(ctx: Context, name: String): List<Found> {
        val r = Backend.post(ctx, JSONObject().put("action", "find_thing").put("name", name)) ?: return emptyList()
        val a = runCatching { JSONArray(r) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Found(it.optString("name"), it.optString("location_description").ifEmpty { null }, it.optString("seen_at"), it.optString("snapshot_url").ifEmpty { null }) }
    }
}
