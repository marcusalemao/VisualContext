package io.alema.vcsolo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * The user's VisualContext memory, straight from the glasses: people (read) and notes (kept on the glasses for now).
 * Uses the same backend and device token as the Nexus plugin. Without a token, the tools answer "não configurado".
 */
class Memory(private val ctx: Context, private val token: String?) {
    private var people: JSONArray? = null

    /** Blocking — call off the main thread. */
    fun findPerson(query: String): JSONObject {
        if (token == null) return JSONObject().put("erro", "memória do VisualContext não configurada neste óculos")
        val list = people ?: fetchPeople() ?: return JSONObject().put("erro", "sem conexão com a memória")
        people = list
        val q = norm(query)
        val hits = JSONArray()
        for (i in 0 until list.length()) {
            val p = list.optJSONObject(i) ?: continue
            val names = listOf(p.optString("name"), p.optString("nickname")).map(::norm)
            if (names.any { it.isNotEmpty() && (it.contains(q) || q.contains(it)) }) hits.put(clean(p))
            if (hits.length() == 3) break
        }
        return JSONObject().put("pessoas", hits).put("encontradas", hits.length())
    }

    private fun fetchPeople(): JSONArray? = try {
        val conn = (URL(BACKEND).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 8_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-vc-token", token)
        }
        conn.outputStream.use { it.write(JSONObject().put("action", "list").put("entity", "FaceContextPerson").put("limit", 1000).toString().toByteArray()) }
        if (conn.responseCode in 200..299) JSONArray(conn.inputStream.bufferedReader().use { it.readText() }) else null
    } catch (_: Exception) { null }

    /** Face signatures and ids never go to Gemini: only what helps answer. */
    private fun clean(p: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = p.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val lower = k.lowercase()
            if (lower.contains("embedding") || lower.contains("descriptor") || lower.contains("photo") || lower.contains("image") ||
                lower == "id" || lower.endsWith("_id") || lower.startsWith("created_by")) continue
            val v = p.get(k)
            if (v is JSONObject) out.put(k, clean(v)) else out.put(k, v)
        }
        return out
    }

    /** Notes stay on the glasses for now (one JSON per line); syncing them to the Manager is Beto's next step. */
    fun saveNote(text: String, question: String? = null, photo: ByteArray? = null): JSONObject {
        val dir = File(ctx.filesDir, "notas").apply { mkdirs() }
        val at = System.currentTimeMillis()
        val photoFile = photo?.let { File(dir, "foto-$at.jpg").apply { writeBytes(it) } }
        val line = JSONObject().put("quando", at).put("texto", text)
            .apply { question?.let { put("pergunta", it) }; photoFile?.let { put("foto", it.name) } }
        File(dir, "notas.jsonl").appendText(line.toString() + "\n")
        return JSONObject().put("guardado", true)
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    companion object {
        const val BACKEND = "https://base44.app/api/apps/6a11083db49430b410a8c066/functions/fcProxy"
    }
}
