package io.alema.wifiadb

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Draws one Instalar-pelo-Wi-Fi card as a single PNG for the Nexus image surface.
 * HUD grammar (decided with Marcus): monochrome green, black = transparent on the waveguide,
 * NO fill (11/10: any fill reads as an opaque green block on the lens), every stroke 2 px.
 * The image is taller than the card and mostly black; the card sits at the TOP of it.
 * Tested on the glasses (12/10): a card at the bottom is cut in half — the wearer sees the upper part.
 */
object Hud {
    const val WIDTH = 480
    const val HEIGHT = 512          // image surface limit
    private const val CARD_H = 236  // title + 4 lines

    private val GREEN = Color.rgb(0, 255, 136)
    private val GREEN_SOFT = Color.rgb(143, 223, 181)
    private val STROKE = Color.rgb(0, 153, 82)     // #00FF88 at 60 % over black

    fun render(title: String, lines: List<String>, footer: String?): ByteArray {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        val top = 2f
        val box = RectF(2f, top, WIDTH - 2f, top + CARD_H)
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE; style = Paint.Style.STROKE; strokeWidth = 2f
        })
        val mono = Typeface.MONOSPACE
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; textSize = 26f; typeface = Typeface.create(mono, Typeface.BOLD) }
        val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN_SOFT; textSize = 17f; typeface = mono }
        val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; textSize = 16f; typeface = mono }
        var y = top + 40f
        c.drawText(ellipsize(title.uppercase(), titleP, WIDTH - 40f), 20f, y, titleP)
        y += 14f
        c.drawLine(20f, y, WIDTH - 20f, y, Paint().apply { color = STROKE; strokeWidth = 2f })
        y += 32f
        for (line in lines.take(4)) {
            c.drawText(ellipsize(line, lineP, WIDTH - 40f), 20f, y, lineP)
            y += 28f
        }
        if (!footer.isNullOrBlank()) c.drawText(ellipsize(footer, footP, WIDTH - 40f), 20f, top + CARD_H - 16f, footP)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun ellipsize(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
