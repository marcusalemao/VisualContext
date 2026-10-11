package io.alema.visualcontext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Draws one VisualContext card as a single PNG for the Nexus image surface.
 * HUD grammar (decided with Marcus): monochrome green, black = transparent on the waveguide,
 * card fill = green at 20 %, every stroke 2 px, information in the top 70 %.
 */
object HudCard {
    const val WIDTH = 480
    const val HEIGHT = 320

    private val GREEN = Color.rgb(0, 255, 136)
    private val GREEN_SOFT = Color.rgb(143, 223, 181)
    private val FILL = Color.rgb(0, 51, 27)        // #00FF88 at 20 % over black
    private val STROKE = Color.rgb(0, 153, 82)     // #00FF88 at 60 % over black

    fun render(title: String, lines: List<String>, footer: String?): ByteArray {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        val box = RectF(2f, 2f, WIDTH - 2f, HEIGHT - 2f)
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FILL })
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE; style = Paint.Style.STROKE; strokeWidth = 2f
        })
        val mono = Typeface.MONOSPACE
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; textSize = 30f; typeface = Typeface.create(mono, Typeface.BOLD) }
        val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN_SOFT; textSize = 20f; typeface = mono }
        val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; textSize = 16f; typeface = mono }
        var y = 52f
        c.drawText(ellipsize(title.uppercase(), titleP, WIDTH - 40f), 20f, y, titleP)
        y += 16f
        c.drawLine(20f, y, WIDTH - 20f, y, Paint().apply { color = STROKE; strokeWidth = 2f })
        y += 36f
        for (line in lines.take(5)) {
            c.drawText(ellipsize(line, lineP, WIDTH - 40f), 20f, y, lineP)
            y += 32f
        }
        if (!footer.isNullOrBlank()) c.drawText(ellipsize(footer, footP, WIDTH - 40f), 20f, HEIGHT - 20f, footP)
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
