package io.alema.vcsolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.alema.facecore.ArcFaceEngine
import io.alema.facecore.Face
import io.alema.facecore.FaceMatch
import io.alema.facecore.LocalFaces
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Faces on the glasses (the same ArcFace engine as the phone plugin). Recognition runs here,
 * on the glasses; faces are kept in this app's private storage. Blocking: call from a worker thread.
 */
class Faces(private val ctx: Context, private val memory: () -> Memory?) {
    private var engine: ArcFaceEngine? = null
    /** The last face seen by quem_e, so "essa é a Ana" right after doesn't need a new photo. */
    private var last: Pair<FloatArray, String>? = null
    private var lastAt = 0L

    fun hasRecent() = last != null && System.currentTimeMillis() - lastAt < 60_000

    private fun engine(): ArcFaceEngine? = engine ?: try { ArcFaceEngine(ctx).also { engine = it } } catch (_: Throwable) { null }

    private fun biggest(jpeg: ByteArray): Pair<Face, Bitmap>? {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val face = engine()?.describe(bmp)?.firstOrNull() ?: return null
        return face to bmp
    }

    /** quem_e: name + how they know each other (from the VisualContext memory when connected). */
    fun who(jpeg: ByteArray?): JSONObject {
        if (jpeg == null) return JSONObject().put("erro", "a câmera não respondeu")
        if (engine() == null) return JSONObject().put("erro", "reconhecimento de rostos indisponível neste óculos")
        val (face, bmp) = biggest(jpeg) ?: return JSONObject().put("rosto", false).put("dica", "não vi rosto; peça para chegar mais perto ou olhar de frente")
        last = face.descriptor to thumb(bmp, face); lastAt = System.currentTimeMillis()
        val local = LocalFaces.all(ctx).filter { it.name != null && it.descriptor.size == face.descriptor.size }
        val known = local.map { FaceMatch.Known(it.name!!.lowercase(), it.descriptor) }
        return when (val r = FaceMatch.best(face.descriptor, known)) {
            is FaceMatch.Result.Match -> person(r.key, local, sure = true)
            is FaceMatch.Result.Unsure -> person(r.key, local, sure = false)
            FaceMatch.Result.Unknown -> JSONObject().put("rosto", true).put("conhecido", false)
        }
    }

    private fun person(key: String, local: List<LocalFaces.Entry>, sure: Boolean): JSONObject {
        val e = local.filter { it.name!!.lowercase() == key }
        val name = e.first().name!!
        val out = JSONObject().put("rosto", true).put("conhecido", true).put("nome", name).put("certeza", if (sure) "alta" else "baixa")
        val seen = e.maxOf { it.lastSeen }
        if (seen > 0) out.put("visto_antes_em", java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(seen)))
        memory()?.findPerson(name)?.optJSONArray("pessoas")?.optJSONObject(0)?.let { out.put("memoria", it) }
        e.forEach { x -> LocalFaces.update(ctx, x.id) { it.lastSeen = System.currentTimeMillis() } }
        return out
    }

    /** guardar_pessoa: uses the face just seen (≤ 60 s), otherwise the photo given now. */
    fun enroll(name: String, jpeg: ByteArray?): JSONObject {
        val n = name.trim().ifEmpty { return JSONObject().put("erro", "sem nome") }
        val recent = last?.takeIf { System.currentTimeMillis() - lastAt < 60_000 }
        val (d, thumb) = recent ?: run {
            val (face, bmp) = jpeg?.let { biggest(it) } ?: return JSONObject().put("erro", "não vi rosto para guardar")
            face.descriptor to thumb(bmp, face)
        }
        val e = LocalFaces.add(ctx, d, thumb)
        LocalFaces.update(ctx, e.id) { it.name = n }
        last = null
        val total = LocalFaces.all(ctx).count { it.name.equals(n, ignoreCase = true) }
        return JSONObject().put("guardado", true).put("nome", n).put("fotos_desta_pessoa", total)
            .put("dica", if (total < 3) "guardar mais 1 ou 2 vezes, de ângulos diferentes, melhora o reconhecimento" else "")
    }

    private fun thumb(bmp: Bitmap, f: Face): String = try {
        val pad = f.w * 0.25f
        val x = (f.x - pad).toInt().coerceIn(0, bmp.width - 1); val y = (f.y - pad).toInt().coerceIn(0, bmp.height - 1)
        val w = (f.w + 2 * pad).toInt().coerceAtMost(bmp.width - x); val h = (f.h + 2 * pad).toInt().coerceAtMost(bmp.height - y)
        val c = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, x, y, w, h), 128, 128 * h / maxOf(w, 1), true)
        Base64.encodeToString(ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { "" }

    fun close() { engine?.close(); engine = null }
}
