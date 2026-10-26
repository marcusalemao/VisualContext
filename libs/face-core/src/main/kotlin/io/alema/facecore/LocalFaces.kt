package io.alema.facecore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Faces taught on this phone (MVP): descriptor + a small crop, in the app's private storage.
 * A face is "new" until it gets a name on the phone screen. If the name is someone already
 * in the memory, it links to that person (personId) so the card shows how you know them.
 * Syncing these to the Manager is Beto's next step (an enroll action in the backend).
 */
object LocalFaces {
    data class Entry(
        val id: String, var name: String?, var personId: String?, val descriptor: FloatArray,
        val thumb: String, val created: Long, var lastSeen: Long,
    )

    private const val FILE = "rostos.json"
    private val lock = Any()

    fun all(ctx: Context): MutableList<Entry> = synchronized(lock) {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return mutableListOf()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val d = FaceMatch.parse(o.optString("d")) ?: return@mapNotNull null
                Entry(o.optString("id"), o.optString("name").ifEmpty { null }, o.optString("pid").ifEmpty { null },
                    d, o.optString("thumb"), o.optLong("created"), o.optLong("seen"))
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun save(ctx: Context, list: List<Entry>) = synchronized(lock) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name ?: "").put("pid", it.personId ?: "")
                .put("d", FaceMatch.format(it.descriptor)).put("thumb", it.thumb).put("created", it.created).put("seen", it.lastSeen))
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    fun add(ctx: Context, descriptor: FloatArray, thumb: String): Entry {
        val now = System.currentTimeMillis()
        val e = Entry(UUID.randomUUID().toString(), null, null, descriptor, thumb, now, now)
        save(ctx, all(ctx).apply { add(e) })
        return e
    }

    fun update(ctx: Context, id: String, change: (Entry) -> Unit) {
        val l = all(ctx); l.find { it.id == id }?.let(change); save(ctx, l)
    }

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.id == id })
}
