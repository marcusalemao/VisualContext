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
    /** One person: the face, and every name read under it (the most frequent one wins). */
    class Found(var name: String, val descriptor: FloatArray, var thumb: String, var size: Float, var cut: Boolean) {
        val names = HashMap<String, Int>()
        fun vote(n: String) { names[n] = (names[n] ?: 0) + 1; name = names.maxByOrNull { it.value }!!.key; cut = name.endsWith("…") || name.endsWith("...") }
    }

    private val engine = ArcFaceEngine(ctx)
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    /** Everything found so far. */
    val people = mutableListOf<Found>()
    val found: List<Found> get() = people

    /** Words of the app's own interface, never a person's name. */
    private val ui = Regex("(?i)^(pessoas|people|animais|pets|adicionar|add|nome|name|mostrar|show|ocultar|hide|pesquisar|search|cole[cç][oõ]es|collections|fotos|photos|mais|more|ver tudo|see all|lugares|places|coisas|things|documentos|selecionar|select|e)$")

    private class Word(val text: String, val box: Rect)

    fun read(bmp: Bitmap): Int {
        // Word by word: two names on the same row of the grid come as one OCR line, so lines can't be trusted.
        val words = try {
            Tasks.await(ocr.process(InputImage.fromBitmap(bmp, 0))).textBlocks.flatMap { it.lines }.flatMap { it.elements }
                .mapNotNull { e -> e.boundingBox?.let { Word(e.text.trim(), it) } }
        } catch (_: Exception) { return 0 }
        if (words.isEmpty()) return 0
        var added = 0
        val faces = engine.describe(bmp)
        for (f in faces) {
            // The name sits under the face (app) or over the bottom of the tile (web), inside its own column;
            // skip faces whose name could be off screen.
            if (f.y + f.h * 1.9f > bmp.height || f.y < 0) continue
            // Column = halfway to the neighbours on the same row, so long names ("Leonardo de Pinho Vieira") fit
            // but never take a word from the next tile.
            val cx = f.x + f.w / 2; val cy = f.y + f.h / 2
            val row = faces.filter { it !== f && kotlin.math.abs((it.y + it.h / 2) - cy) < f.h }
            val leftN = row.map { it.x + it.w / 2 }.filter { it < cx }.maxOrNull()
            val rightN = row.map { it.x + it.w / 2 }.filter { it > cx }.minOrNull()
            val left = leftN?.let { (it + cx) / 2 } ?: (cx - f.w * 1.1f)
            val right = rightN?.let { (it + cx) / 2 } ?: (cx + f.w * 1.1f)
            val mine = words.filter { w ->
                val wx = w.box.exactCenterX()
                wx in left..right && w.box.left >= left - f.w * 0.1f && w.box.right <= right + f.w * 0.1f && w.box.top >= f.y + f.h * 0.85f && w.box.top <= f.y + f.h * 1.9f
            }.sortedWith(compareBy<Word> { it.box.top / maxOf(1, it.box.height()) }.thenBy { it.box.left })
            if (mine.isEmpty()) { keepNameless(bmp, f); continue }
            // At most the two rows right under the face.
            val firstTop = mine.minOf { it.box.top }; val rowH = mine.first().box.height().coerceAtLeast(1)
            val name = mine.filter { it.box.top < firstTop + rowH * 2.6f }.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', ' ')
            if (name.length < 2 || name.any { it.isDigit() } || ui.matches(name)) continue
            // Same face as one already found (the grid repeats while scrolling): add a vote for this name.
            val same = people.maxByOrNull { dot(it.descriptor, f.descriptor) }?.takeIf { dot(it.descriptor, f.descriptor) > SAME }
            if (same != null) {
                same.vote(name)
                if (f.w > same.size) { same.thumb = crop(bmp, f.x, f.y, f.w, f.h); same.size = f.w }
                continue
            }
            people += Found(name, f.descriptor, crop(bmp, f.x, f.y, f.w, f.h), f.w, false).also { it.vote(name) }
            added++
        }
        return added
    }

    /** Faces with no name in Google Photos: shown apart, so the user can name the ones he knows. */
    val nameless = mutableListOf<Found>()
    private fun keepNameless(bmp: Bitmap, f: io.alema.facecore.Face) {
        if (f.w < bmp.width * 0.12f) return // only grid faces, not tiny ones in a banner
        if (people.any { dot(it.descriptor, f.descriptor) > SAME }) return
        val same = nameless.maxByOrNull { dot(it.descriptor, f.descriptor) }?.takeIf { dot(it.descriptor, f.descriptor) > SAME }
        if (same != null) { if (f.w > same.size) { same.thumb = crop(bmp, f.x, f.y, f.w, f.h); same.size = f.w }; return }
        nameless += Found("", f.descriptor, crop(bmp, f.x, f.y, f.w, f.h), f.w, false)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    private fun crop(bmp: Bitmap, x: Float, y: Float, w: Float, h: Float): String = try {
        val pad = w * 0.15f
        val r = Rect((x - pad).toInt().coerceAtLeast(0), (y - pad).toInt().coerceAtLeast(0), (x + w + pad).toInt().coerceAtMost(bmp.width), (y + h + pad).toInt().coerceAtMost(bmp.height))
        val c = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()), 128, 128, true)
        Base64.encodeToString(ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { "" }

    fun close() { engine.close(); ocr.close() }

    companion object {
        /** Cosine similarity above this = the same person (ArcFace unit vectors; matches FaceMatch's accept line). */
        private const val SAME = 0.45f
        fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
        /** What the last capture found, for the pick screen. */
        @Volatile var last: List<Found> = emptyList()
        @Volatile var lastNameless: List<Found> = emptyList()
    }
}
