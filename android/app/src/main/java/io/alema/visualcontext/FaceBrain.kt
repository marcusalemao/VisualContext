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
    )

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
        val out = e.describe(bmp).take(4).map { f -> identify(f, bmp, local, describeUnknown) }
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
                // New face: keep it (no name yet) with a neutral description, never "pessoa 1".
                val desc = if (describeUnknown) Describe.person(ctx, faceJpeg(bmp, f)) else null
                val entry = LocalFaces.add(ctx, f.descriptor, thumb)
                LocalFaces.update(ctx, entry.id) { it.name = desc }
                Seen(desc ?: "alguém novo", listOf("primeira vez · aqui, agora"), true, false, f.x, f.y, f.w, f.h, f.descriptor, thumb, entry.id)
            }
        }
    }

    private fun card(key: String, f: Face, local: List<LocalFaces.Entry>, thumb: String, sure: Boolean): Seen {
        val p = if (key.startsWith("p:")) people.find { it.id == key.removePrefix("p:") } else null
        val l = local.filter { "p:${it.personId}" == key || "l:${it.id}" == key }
        val name = p?.name ?: l.firstOrNull { it.name != null }?.name ?: "alguém"
        val how = p?.let { listOf(it.relation, it.company).filter { s -> s.isNotBlank() }.joinToString(" · ") }.orEmpty()
        val seen = l.maxOfOrNull { it.lastSeen }?.takeIf { it > 0 }
        val lastLine = when {
            p != null && p.lastSeenContext.isNotBlank() -> "última vez: " + listOf(shortDate(p.lastSeenDate), p.lastSeenContext).filter { it.isNotBlank() }.joinToString(", ")
            seen != null -> "visto em " + fmt.format(Date(seen))
            else -> ""
        }
        val now = System.currentTimeMillis()
        l.forEach { x -> LocalFaces.update(ctx, x.id) { it.lastSeen = now } }
        val lines = listOfNotNull(how.takeIf { it.isNotBlank() }, lastLine.takeIf { it.isNotBlank() }).ifEmpty { listOf("na sua memória") }
        return Seen(name, lines, sure, p != null || l.any { it.personId != null } || l.any { it.name != null }, f.x, f.y, f.w, f.h, f.descriptor, thumb, l.firstOrNull()?.id)
    }

    /** "Essa é a Ana": names the biggest face seen in the last minute (or links it to a person in the memory). */
    fun name(nameSaid: String): JSONObject {
        val n = nameSaid.trim().ifEmpty { return JSONObject().put("erro", "sem nome") }
        if (System.currentTimeMillis() - lastAt > 60_000 || last.isEmpty()) return JSONObject().put("erro", "não vi um rosto agora há pouco; olhe para a pessoa e peça de novo")
        val s = last.first()
        val match = people.firstOrNull { it.name.equals(n, ignoreCase = true) }
        val id = s.localId ?: LocalFaces.add(ctx, s.descriptor, s.thumb).id
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
