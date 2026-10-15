package io.alema.vcsolo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * The whole screen. HUD rules (REGRAS-HUD): black = transparent on the lens, monochrome green,
 * no fills, 2 px strokes, card at the TOP (a card at the bottom is cut in half on the real glasses).
 */
class HudView(ctx: Context) : View(ctx) {
    var title = "VisualContext"
    var lines: List<String> = emptyList()
    var footer = ""
    var live = false          // small dot when a Gemini session is open

    private val green = Color.rgb(0, 255, 136)
    private val soft = Color.rgb(143, 223, 181)
    private val stroke = Color.rgb(0, 153, 82)
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 26f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 22f; typeface = Typeface.MONOSPACE }
    private val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 16f; typeface = Typeface.MONOSPACE }

    fun set(title: String, lines: List<String>, footer: String) {
        this.title = title; this.lines = lines; this.footer = footer
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = 60f + 32f * 3 + 34f
        c.drawRoundRect(RectF(2f, 2f, w - 2f, h), 14f, 14f, box)
        var y = 40f
        c.drawText(fit(title.uppercase(), titleP, w - 70f), 20f, y, titleP)
        if (live) c.drawCircle(w - 30f, 31f, 8f, dot)
        y += 12f
        c.drawLine(20f, y, w - 20f, y, box)
        y += 34f
        for (l in lines.take(3)) { c.drawText(fit(l, lineP, w - 40f), 20f, y, lineP); y += 32f }
        if (footer.isNotBlank()) c.drawText(fit(footer, footP, w - 40f), 20f, h - 14f, footP)
    }

    private fun fit(t: String, p: Paint, max: Float): String {
        if (p.measureText(t) <= max) return t
        var s = t
        while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }
}
