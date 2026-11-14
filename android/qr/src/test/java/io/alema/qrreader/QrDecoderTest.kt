package io.alema.qrreader

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Draws a QR into a bigger "photo" (offset, small, noisy, low contrast) and reads it back. */
class QrDecoderTest {
    private val wifi = "WIFI:T:WPA;S:Café da Paim;P:cafezinho123;;"

    private fun photo(text: String, size: Int, w: Int, h: Int, x0: Int, y0: Int, dark: Int, light: Int, noise: Int, invert: Boolean = false): IntArray {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val rnd = Random(42)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val inQr = x in x0 until x0 + size && y in y0 until y0 + size
            var on = inQr && m[x - x0, y - y0]
            if (invert && inQr) on = !on
            var v = if (on) dark else light
            v = (v + rnd.nextInt(-noise, noise + 1)).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    @Test fun cleanCode() = assertEquals(wifi, QrDecoder.decodePixels(photo(wifi, 300, 800, 600, 250, 150, 0, 255, 0), 800, 600))

    @Test fun smallLowContrastNoisy() =
        assertEquals(wifi, QrDecoder.decodePixels(photo(wifi, 160, 1280, 960, 560, 400, 70, 180, 25), 1280, 960))

    @Test fun invertedCode() =
        assertEquals(wifi, QrDecoder.decodePixels(photo(wifi, 260, 800, 600, 270, 170, 0, 255, 0, invert = true), 800, 600))

    @Test fun noCode() = assertNull(QrDecoder.decodePixels(IntArray(640 * 480) { 0xFF808080.toInt() }, 640, 480))
}
