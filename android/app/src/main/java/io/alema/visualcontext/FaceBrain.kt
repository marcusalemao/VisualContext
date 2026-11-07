package io.alema.visualcontext

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import io.alema.facecore.ArcFaceEngine
import io.alema.facecore.Face
import io.alema.facecore.FaceMatch
import io.alema.facecore.LocalFaces
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Who is in the frame. ArcFace on the phone (nothing uploaded for recognition), matched against the
 * VisualContext memory (face_embedding) and the faces taught on this phone. A face with no name is
 * never "pessoa 1": it gets a short neutral description (Describe) and the occasion.
 * Blocking: call from a worker thread.
 */
class FaceBrain(private val ctx: Context) {
    /** One person seen, with where the face is in the analysed bitmap (for the label beside the head). */
    data class Seen(
        val label: String, val lines: List<String>, val sure: Boolean, val known: Boolean,
        val x: Float, val y: Float, val w: Float, val h: Float,
        val descriptor: FloatArray, val thumb: String, val localId: String?,
        /** Set for a face not stored yet: it is only kept after the countdown (or when its name is said). */
        val pendingId: String? = null, val pendingSince: Long = 0,
    )

    /** A new face, in memory only. Nobody becomes a record for passing by (privacy spec, section 7). */
    class Pending(val id: String, var descriptor: FloatArray, var thumb: String, val since: Long, var lastSeen: Long, var desc: String? = null, var face: Face? = null)
    private val pending = java.util.concurrent.CopyOnWriteArrayList<Pending>()
    /**
     * Who was where a moment ago. A recognised person keeps the name while the face stays in about the same
     * place (turned, half hidden, bad light), for up to [HOLD_MS] since the last sure match.
     */
    private class Track(val seen: Seen, var cx: Float, var cy: Float, var w: Float, var sureAt: Long, var lastAt: Long)
    private val tracks = java.util.concurrent.CopyOnWriteArrayList<Track>()
    private val HOLD_MS = 12_000L

    private fun near(t: Track, f: Face): Boolean {
        val cx = f.x + f.w / 2; val cy = f.y + f.h / 2
        val d = kotlin.math.hypot((cx - t.cx).toDouble(), (cy - t.cy).toDouble())
        return d < maxOf(t.w, f.w) * 1.2 && f.w > t.w * 0.5 && f.w < t.w * 2
    }

    /** Faces the user cancelled in this session: not proposed again. */
    private val ignored = java.util.concurrent.CopyOnWriteArrayList<FloatArray>()

    private var engine: ArcFaceEngine? = null
    @Volatile var people: List<Backend.Person> = emptyList()
    private var peopleAt = 0L
    /** Faces seen in the last minute, so "essa é a Ana" and the card can refer to them. */
    @Volatile var last: List<Seen> = emptyList()
    @Volatile var lastAt = 0L
    private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    fun engine(): ArcFaceEngine? = engine ?: try { ArcFaceEngine(ctx).also { engine = it } } catch (_: Throwable) { null }

    fun refreshPeople(force: Boolean = false) {
        if (!force && SystemClock.elapsedRealtime() - peopleAt < 5 * 60_000 && people.isNotEmpty()) return
        Backend.peopleFull(ctx)?.let { people = it; peopleAt = SystemClock.elapsedRealtime() }
    }

    /** Every face in the picture, biggest first. [describeUnknown] costs a Gemini call per new face. */
    fun look(bmp: Bitmap, describeUnknown: Boolean): List<Seen> {
        val e = engine() ?: return emptyList()
        refreshPeople()
        val local = LocalFaces.all(ctx)
        val now = System.currentTimeMillis()
        tracks.removeAll { now - it.lastAt > 5000 || now - it.sureAt > HOLD_MS }
        val out = e.describe(bmp).take(4).map { f ->
            val s = identify(f, bmp, local, describeUnknown)
            val t = tracks.firstOrNull { near(it, f) }
            val result = if (s.known && s.sure) s
                else if (t != null) t.seen.copy(x = f.x, y = f.y, w = f.w, h = f.h, sure = true).also { held ->
                    // the pending buffer of a misread profile is not a new person
                    s.pendingId?.let { pid -> pending.removeAll { it.id == pid } }
                } else s
            if (t != null) { t.cx = f.x + f.w / 2; t.cy = f.y + f.h / 2; t.w = f.w; t.lastAt = now; if (s.known && s.sure) t.sureAt = now }
            else if (result.known && result.sure) tracks += Track(result, f.x + f.w / 2, f.y + f.h / 2, f.w, now, now)
            result
        }
        if (out.isNotEmpty()) { last = out; lastAt = System.currentTimeMillis() }
        return out
    }

    private fun identify(f: Face, bmp: Bitmap, local: List<LocalFaces.Entry>, describeUnknown: Boolean): Seen {
        val size = f.descriptor.size
        val known = (people.mapNotNull { p -> p.embedding?.let { FaceMatch.Known("p:${p.id}", it) } } +
            local.map { FaceMatch.Known(it.personId?.let { id -> "p:$id" } ?: "l:${it.id}", it.descriptor) })
            .filter { it.descriptor.size == size }
        val thumb = crop(bmp, f)
        return when (val r = FaceMatch.best(f.descriptor, known)) {
            is FaceMatch.Result.Match -> card(r.key, f, local, thumb, sure = true)
            is FaceMatch.Result.Unsure -> card(r.key, f, local, thumb, sure = false)
            FaceMatch.Result.Unknown -> {
                // New face: kept in memory only, described neutrally, never "pessoa 1".
                val now = System.currentTimeMillis()
                if (ignored.any { FaceMatch.distance(it, f.descriptor) < FaceMatch.rule(size).accept }) {
                    return Seen("", emptyList(), true, false, f.x, f.y, f.w, f.h, f.descriptor, thumb, null)
                }
                pending.removeAll { now - it.lastSeen > 15_000 }
                val pd = pending.minByOrNull { FaceMatch.distance(it.descriptor, f.descriptor) }
                    ?.takeIf { FaceMatch.distance(it.descriptor, f.descriptor) < FaceMatch.rule(size).reject }
                    ?: Pending(java.util.UUID.randomUUID().toString(), f.descriptor, thumb, now, now).also { pending += it }
                pd.lastSeen = now; pd.descriptor = f.descriptor; pd.thumb = thumb; pd.face = f
                if (describeUnknown && pd.desc == null) pd.desc = Describe.person(ctx, faceJpeg(bmp, f))
                Seen(pd.desc ?: "…", listOf("primeira vez · aqui, agora"), true, false, f.x, f.y, f.w, f.h, f.descriptor, thumb, null, pd.id, pd.since)
            }
        }
    }

    private fun card(key: String, f: Face, local: List<LocalFaces.Entry>, thumb: String, sure: Boolean): Seen {
        val p = if (key.startsWith("p:")) people.find { it.id == key.removePrefix("p:") } else null
        val l = local.filter { "p:${it.personId}" == key || "l:${it.id}" == key }
        val named = p != null || l.any { it.name != null }
        val name = p?.name ?: l.firstOrNull { it.name != null }?.name ?: l.firstOrNull { it.desc != null }?.desc ?: "…"
        val how = p?.let { listOf(it.relation, it.company).filter { s -> s.isNotBlank() }.joinToString(" · ") }.orEmpty()
        val prev = l.maxOfOrNull { it.prevSeen }?.takeIf { it > 0 }
        val lastLine = when {
            p != null && p.lastSeenContext.isNotBlank() -> "última vez: " + listOf(shortDate(p.lastSeenDate), p.lastSeenContext).filter { it.isNotBlank() }.joinToString(", ")
            prev != null -> "última vez: " + fmt.format(Date(prev))
            named -> "primeiro encontro registrado"
            else -> ""
        }
        LocalFaces.seen(ctx, l.map { it.id })
        val lines = listOfNotNull(how.takeIf { it.isNotBlank() }, lastLine.takeIf { it.isNotBlank() }).ifEmpty { listOf(if (named) "na sua memória" else "ainda sem nome") }
        return Seen(name, lines, sure, named, f.x, f.y, f.w, f.h, f.descriptor, thumb, l.firstOrNull()?.id)
    }

    /** Writes the neutral description for a face that has none yet (one Gemini call). Returns the new label. */
    fun describe(s: Seen, bmp: Bitmap): String? {
        s.pendingId?.let { pid ->
            val pd = pending.firstOrNull { it.id == pid } ?: return null
            if (pd.desc != null) return null
            pd.desc = Describe.person(ctx, faceJpeg(bmp, Face(s.descriptor, s.x, s.y, s.w, s.h, 1f)))
            return pd.desc
        }
        val id = s.localId ?: return null
        if (LocalFaces.all(ctx).any { it.id == id && (it.name != null || it.desc != null) }) return null
        val f = Face(s.descriptor, s.x, s.y, s.w, s.h, 1f)
        val desc = Describe.person(ctx, faceJpeg(bmp, f)) ?: return null
        LocalFaces.update(ctx, id) { it.desc = desc }
        return desc
    }

    /** End of the countdown: the new face is stored (with its description, still without a name). */
    fun keep(pendingId: String, name: String? = null): String? {
        val pd = pending.firstOrNull { it.id == pendingId } ?: return null
        pending.remove(pd)
        val e = LocalFaces.add(ctx, pd.descriptor, pd.thumb)
        val match = name?.let { n -> people.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        LocalFaces.update(ctx, e.id) { it.desc = pd.desc; it.name = match?.name ?: name; it.personId = match?.id }
        return e.id
    }

    /** People in the memory whose only face record is an old face-api one (128-d). */
    fun legacyPeople(): List<Backend.Person> = people.filter { it.embedding?.size == 128 } .filter { p -> LocalFaces.all(ctx).none { it.personId == p.id } }

    /** The old engine recognised a new face as someone in the memory: store it under that person, silently. */
    fun adopt(pendingId: String, personName: String): Boolean = keep(pendingId, personName) != null

    /** "Cancelar" during the countdown: nothing stored, and this face is not proposed again in this session. */
    fun ignore(pendingId: String) {
        pending.firstOrNull { it.id == pendingId }?.let { ignored += it.descriptor; pending.remove(it) }
    }

    /** "Essa é a Ana": names the biggest face seen in the last minute (or links it to a person in the memory). */
    fun name(nameSaid: String): JSONObject {
        val n = nameSaid.trim().ifEmpty { return JSONObject().put("erro", "sem nome") }
        if (System.currentTimeMillis() - lastAt > 60_000 || last.isEmpty()) return JSONObject().put("erro", "não vi um rosto agora há pouco; olhe para a pessoa e peça de novo")
        val s = last.first { it.label.isNotEmpty() || it.pendingId != null || it.localId != null }
        val match = people.firstOrNull { it.name.equals(n, ignoreCase = true) }
        // Saying the name is consent enough: a pending face is stored right away.
        val id = s.localId ?: s.pendingId?.let { keep(it, n) } ?: LocalFaces.add(ctx, s.descriptor, s.thumb).id
        LocalFaces.update(ctx, id) { it.name = match?.name ?: n; it.personId = match?.id }
        val count = LocalFaces.all(ctx).count { (it.name ?: "").equals(match?.name ?: n, ignoreCase = true) }
        return JSONObject().put("guardado", true).put("nome", match?.name ?: n).put("na_memoria", match != null).put("fotos", count)
    }

    fun asJson(seen: List<Seen>): JSONObject {
        val arr = org.json.JSONArray()
        seen.forEach { s ->
            val o = JSONObject().put("conhecido", s.known).put("nome_ou_descricao", s.label).put("certeza", if (s.sure) "alta" else "baixa").put("detalhes", s.lines.joinToString("; "))
            people.firstOrNull { it.name == s.label }?.let { p -> o.put("relacao", p.relation).put("empresa", p.company).put("ultima_vez", p.lastSeenContext) }
            arr.put(o)
        }
        return JSONObject().put("rostos", arr.length()).put("pessoas", arr)
    }

    private fun shortDate(iso: String) = if (iso.length >= 10) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else iso

    private fun faceJpeg(bmp: Bitmap, f: Face): ByteArray {
        val pad = f.w * 0.6f
        val x = (f.x - pad).toInt().coerceIn(0, bmp.width - 1); val y = (f.y - pad).toInt().coerceIn(0, bmp.height - 1)
        val w = (f.w + 2 * pad).toInt().coerceAtMost(bmp.width - x); val h = (f.h + 2.5f * pad).toInt().coerceAtMost(bmp.height - y)
        val c = Bitmap.createBitmap(bmp, x, y, maxOf(w, 1), maxOf(h, 1))
        return ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    private fun crop(bmp: Bitmap, f: Face): String = try {
        val pad = f.w * 0.25f
        val x = (f.x - pad).toInt().coerceIn(0, bmp.width - 1); val y = (f.y - pad).toInt().coerceIn(0, bmp.height - 1)
        val w = (f.w + 2 * pad).toInt().coerceAtMost(bmp.width - x); val h = (f.h + 2 * pad).toInt().coerceAtMost(bmp.height - y)
        val c = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, x, y, maxOf(w, 1), maxOf(h, 1)), 128, 128 * h / maxOf(w, 1), true)
        Base64.encodeToString(ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { "" }

    fun close() { engine?.close(); engine = null }
}
