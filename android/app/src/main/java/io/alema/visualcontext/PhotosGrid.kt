package io.alema.visualcontext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.alema.facecore.ArcFaceEngine
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * Spec 5b, step 1: reads Google Photos' "People and pets" grid from screen frames.
 * Each face is paired with the name written right under it. Pets mostly have no human face, so they
 * drop out on their own; faces without a name are skipped (nothing to call them). Blocking.
 */
class PhotosGrid(ctx: Context) {
    class Found(val name: String, val descriptor: FloatArray, var thumb: String, var size: Float, val cut: Boolean)

    private val engine = ArcFaceEngine(ctx)
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    /** Everything found so far, by name (normalised). */
    val found = LinkedHashMap<String, Found>()

    /** Words of the app's own interface, never a person's name. */
    private val ui = Regex("(?i)^(pessoas|people|animais|pets|adicionar|add|nome|name|mostrar|show|ocultar|hide|pesquisar|search|cole[cç][oõ]es|collections|fotos|photos|mais|more|ver tudo|see all|lugares|places|coisas|things|documentos|selecionar|select|\\d+.*)")

    fun read(bmp: Bitmap): Int {
        val lines = try {
            Tasks.await(ocr.process(InputImage.fromBitmap(bmp, 0))).textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { l.text.trim() to it } }
        } catch (_: Exception) { return 0 }
        if (lines.isEmpty()) return 0
        var added = 0
        for (f in engine.describe(bmp)) {
            val cx = f.x + f.w / 2
            val below = lines.filter { (t, b) ->
                b.top >= f.y + f.h * 0.85f && b.top <= f.y + f.h * 1.9f && cx in b.left.toFloat()..b.right.toFloat() && t.length in 2..40 && !ui.containsMatchIn(t)
            }.minByOrNull { it.second.top } ?: continue
            val name = below.first.trimEnd('.', '…', ' ').trim()
            if (name.length < 2 || name.any { it.isDigit() }) continue
            val key = norm(name)
            val prev = found[key]
            if (prev != null && prev.size >= f.w) continue
            val thumb = crop(bmp, f.x, f.y, f.w, f.h)
            if (prev != null) { prev.thumb = thumb; prev.size = f.w; continue }
            found[key] = Found(name, f.descriptor, thumb, f.w, below.first.endsWith("…") || below.first.endsWith("..."))
            added++
        }
        return added
    }

    private fun crop(bmp: Bitmap, x: Float, y: Float, w: Float, h: Float): String = try {
        val pad = w * 0.15f
        val r = Rect((x - pad).toInt().coerceAtLeast(0), (y - pad).toInt().coerceAtLeast(0), (x + w + pad).toInt().coerceAtMost(bmp.width), (y + h + pad).toInt().coerceAtMost(bmp.height))
        val c = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()), 128, 128, true)
        Base64.encodeToString(ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { "" }

    fun close() { engine.close(); ocr.close() }

    companion object {
        fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
        /** What the last capture found, for the pick screen. */
        @Volatile var last: List<Found> = emptyList()
    }
}
