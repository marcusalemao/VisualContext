package io.alema.visualcontext

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import io.alema.qrcore.QrParser
import io.alema.qrcore.QrResult

/**
 * QR mode (07/11): with the ▣ QR button on, a code held steady in the window for a moment makes the lens ask what to do
 * ("copiar o código Pix?"). Nothing happens without a tap or a "sim". In memory only.
 */
class QrLook {
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
    private var raw: String? = null
    private var since = 0L
    private var lastSeen = 0L
    private val done = HashMap<String, Long>()
    /** The code waiting for a yes, or null. */
    var offer: QrResult? = null; private set
    var offerRaw: String? = null; private set

    /** Returns true when the offer changed (show or hide the card). */
    fun feed(bmp: Bitmap, now: Long): Boolean {
        val text = decode(bmp)
        if (text != null) { if (text != raw) { raw = text; since = now }; lastSeen = now }
        val before = offerRaw
        if (offer == null && raw != null && now - since >= DWELL && now - lastSeen < 800 && (done[raw!!] ?: 0) < now - 60_000) {
            offer = QrParser.parse(raw!!).takeIf { it !is QrResult.Invalid }; offerRaw = if (offer != null) raw else null
        }
        if (offer != null && now - lastSeen > 4000) { offer = null; offerRaw = null }
        if (text == null && now - lastSeen > 1500) raw = null
        return before != offerRaw
    }

    fun consume(now: Long): Pair<QrResult, String>? {
        val o = offer ?: return null; val r = offerRaw ?: return null
        done[r] = now; offer = null; offerRaw = null
        return o to r
    }

    fun dismiss(now: Long) { offerRaw?.let { done[it] = now }; offer = null; offerRaw = null }

    private fun decode(bmp: Bitmap): String? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val src = RGBLuminanceSource(w, h, px)
        return attempt(src) ?: attempt(src.invert()) ?: if (src.isCropSupported) attempt(src.crop(w / 4, h / 4, w / 2, h / 2)) else null
    }
    private fun attempt(src: LuminanceSource): String? = read(BinaryBitmap(HybridBinarizer(src))) ?: read(BinaryBitmap(GlobalHistogramBinarizer(src)))
    private fun read(b: BinaryBitmap): String? = try { MultiFormatReader().decode(b, hints).text } catch (_: Exception) { null }

    companion object {
        const val DWELL = 300L
        /** Title and lines for the lens card. Pix names are typed by whoever made the code: "o código diz". */
        fun card(r: QrResult): Pair<String, List<String>> = when (r) {
            is QrResult.Pix -> "Pix" to listOfNotNull(
                r.merchantName?.let { "o código diz: $it" + (r.merchantCity?.let { c -> " · $c" } ?: "") },
                if (r.isDynamic) "Pix dinâmico: valor no banco" else r.amount?.let { "R$ " + it.replace('.', ',') } ?: "valor: você digita no banco",
                "copiar o código Pix? toque ou diga \"sim\"")
            is QrResult.Link -> if (MenuRead.looksLikeMenu(r.url)) "Cardápio" to listOf(r.host, "ver as categorias? toque ou diga \"sim\"")
                else "Link" to listOf(r.host, "guardar nas notas? toque ou diga \"sim\"")
            is QrResult.Wifi -> "Wi-Fi" to listOf(r.ssid, "conectar o celular? toque ou diga \"sim\"")
            is QrResult.Contact -> "Contato" to listOfNotNull(r.name, r.org, "guardar na memória? toque ou diga \"sim\"")
            is QrResult.Text -> "Texto" to listOf(r.text.take(60), "copiar? toque ou diga \"sim\"")
            is QrResult.Invalid -> "QR" to listOf(r.reason)
        }
    }
}
