package io.alema.qrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * Finds a QR code in one camera snapshot. Runs on the phone, in memory only:
 * the JPEG is never written to disk and never sent anywhere.
 */
object QrDecoder {
    private const val MAX_SIDE = 1600

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    fun decode(jpeg: ByteArray): String? {
        val bmp = load(jpeg) ?: return null
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            return decodePixels(px, w, h)
        } finally {
            bmp.recycle()
        }
    }

    /** ARGB pixels → QR text. No Android types, so it is tested on the JVM. */
    fun decodePixels(px: IntArray, w: Int, h: Int): String? {
        val src = RGBLuminanceSource(w, h, px)
        // Several passes, cheapest first: menus and café tables have glare, low light and inverted codes.
        return attempt(src) ?: attempt(src.invert()) ?: attemptCentre(src)
    }

    private fun load(jpeg: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun attempt(src: LuminanceSource): String? =
        read(BinaryBitmap(HybridBinarizer(src))) ?: read(BinaryBitmap(GlobalHistogramBinarizer(src)))

    /** A small code far away: zoom into the middle, where the wearer is looking. */
    private fun attemptCentre(src: LuminanceSource): String? {
        if (!src.isCropSupported) return null
        val cw = src.width / 2
        val ch = src.height / 2
        return attempt(src.crop(src.width / 4, src.height / 4, cw, ch))
    }

    private fun read(bitmap: BinaryBitmap): String? = try {
        MultiFormatReader().decode(bitmap, hints).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}
