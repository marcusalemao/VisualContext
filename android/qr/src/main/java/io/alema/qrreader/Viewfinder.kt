package io.alema.qrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * A "slow video" viewfinder built from the snapshots the plugin already takes (~3 per second):
 * the wearer sees what the camera sees, zoomed, with the four corner marks and one hint at the top.
 *
 * Why not the live camera like Lens? Nexus gives the live camera to ONE plugin per phone, and ours would take
 * Lens's place. Snapshots on the image surface work today without stepping on anyone.
 */
object Viewfinder {
    const val SIZE = 480                    // square, under the image-surface limits (512 px edge, 64 KiB)
    private const val MAX_BYTES = 60_000

    private val green = Color.rgb(0, 255, 136)
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green; textSize = 26f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green; textSize = 16f; typeface = Typeface.MONOSPACE
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    // Grey image: the lens is monochrome green anyway, and grey JPEGs stay small.
    private val grey = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }

    /** Loads a snapshot at a size good enough for both decoding and the viewfinder. */
    fun load(jpeg: ByteArray, maxSide: Int = 1600): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Centre crop by [zoom], hint at the top, corner marks around the area that counts. JPEG ≤ 60 KB. */
    fun render(photo: Bitmap, zoom: Float, hint: String, detail: String): ByteArray {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val side = (minOf(photo.width, photo.height) / zoom.coerceAtLeast(1f)).toInt()
        val cx = photo.width / 2; val cy = photo.height / 2
        val src = Rect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        // Image under the text, so the hint never covers the code.
        val top = 92f
        val img = RectF((SIZE - (SIZE - top)) / 2f, top, (SIZE + (SIZE - top)) / 2f, SIZE.toFloat())
        c.drawBitmap(photo, src, img, grey)
        val k = img.width() * 0.16f
        val l = img.left + 8f; val r = img.right - 8f; val t = img.top + 8f; val b = img.bottom - 8f
        c.drawLine(l, t, l + k, t, corner); c.drawLine(l, t, l, t + k, corner)
        c.drawLine(r, t, r - k, t, corner); c.drawLine(r, t, r, t + k, corner)
        c.drawLine(l, b, l + k, b, corner); c.drawLine(l, b, l, b - k, corner)
        c.drawLine(r, b, r - k, b, corner); c.drawLine(r, b, r, b - k, corner)
        c.drawText(hint, 16f, 36f, hintP)
        c.drawText(detail, 16f, 66f, smallP)
        var q = 70
        var bytes: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, q, bos)
            bytes = bos.toByteArray()
            q -= 12
        } while (bytes.size > MAX_BYTES && q >= 22)
        out.recycle()
        return bytes
    }
}
