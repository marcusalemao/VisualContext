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
        /** Neutral description while there is no name ("homem de barba, jaqueta azul"). Never shown as a name. */
        var desc: String? = null,
        /** The sighting before the current one: what "última vez" shows. */
        var prevSeen: Long = 0,
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
                    d, o.optString("thumb"), o.optLong("created"), o.optLong("seen"), o.optString("desc").ifEmpty { null }, o.optLong("prev"))
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun save(ctx: Context, list: List<Entry>) = synchronized(lock) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name ?: "").put("pid", it.personId ?: "")
                .put("d", FaceMatch.format(it.descriptor)).put("thumb", it.thumb).put("created", it.created).put("seen", it.lastSeen).put("desc", it.desc ?: "").put("prev", it.prevSeen))
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    /**
     * The same photo stored twice (contacts import + Google Photos of the same picture) adds nothing and
     * clutters the list: a new sample for [name] whose signature is almost identical to a stored one is a duplicate.
     */
    fun isDuplicate(ctx: Context, name: String?, descriptor: FloatArray, minCos: Float = 0.93f): Boolean {
        if (name == null) return false
        return all(ctx).any { e -> e.name.equals(name, ignoreCase = true) && e.descriptor.size == descriptor.size && cos(e.descriptor, descriptor) >= minCos }
    }
    private fun cos(a: FloatArray, b: FloatArray): Float { var d = 0f; var na = 0f; var nb = 0f; for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }; return if (na == 0f || nb == 0f) 0f else d / kotlin.math.sqrt(na * nb) }

    /** Removes near-identical samples of the same person (keeps the oldest). Returns how many went. */
    fun dedupe(ctx: Context, minCos: Float = 0.93f): Int {
        val list = all(ctx).sortedBy { it.created }; val keep = ArrayList<Entry>()
        for (e in list) if (e.name == null || keep.none { k -> k.name.equals(e.name, true) && k.descriptor.size == e.descriptor.size && cos(k.descriptor, e.descriptor) >= minCos }) keep += e
        if (keep.size != list.size) save(ctx, keep)
        return list.size - keep.size
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

    /** A new encounter starts after 30 min apart: the old one becomes "última vez". Writes at most once a minute. */
    fun seen(ctx: Context, ids: Collection<String>, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val l = all(ctx); var changed = false
        for (e in l) if (e.id in ids && now - e.lastSeen > 60_000) {
            if (now - e.lastSeen > 30 * 60_000 && e.lastSeen > 0) e.prevSeen = e.lastSeen
            e.lastSeen = now; changed = true
        }
        if (changed) save(ctx, l)
    }

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.id == id })
}
