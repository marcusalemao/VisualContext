package io.alema.visualcontext

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * The lens, drawn over the phone camera: the HUD grammar (green only, 2 px strokes, no fills,
 * useful area = top 70 %). Labels sit beside heads; one card at the top left (≤ 70 % width);
 * the transcript and the footer at the bottom of the useful area.
 */
class HudOverlay(ctx: Context) : View(ctx) {
    data class Label(val text: String, val cx: Float, val cy: Float, val w: Float, val h: Float, val dashed: Boolean = false)
    data class Pin(val title: String, val sub: String, val bearingDeg: Float, val hidden: Boolean)

    var labels: List<Label> = emptyList()
    var cardTitle: String? = null
    var cardLines: List<String> = emptyList()
    var transcript: List<String> = emptyList()
    var footer: String = ""
    var arrow: String? = null
    var arrowText: String = ""
    var pins: List<Pin> = emptyList()
    var heading: Float = 0f
    var listening = false
    var speaking = false

    private val green = Color.rgb(0, 255, 136)
    private val soft = Color.rgb(143, 223, 181)
    private val mono = Typeface.MONOSPACE
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val pDash = Paint(pStroke).apply { pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; typeface = Typeface.create(mono, Typeface.BOLD) }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; typeface = mono }
    private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val useful = h * 0.70f
        // standby dot (top right)
        pFill.color = if (listening) soft else green
        c.drawCircle(w - dp(18f), dp(18f), dp(if (listening) 7f else 5f), pFill)
        // place pins: cards pinned to a bearing inside a 24° window around the heading
        pins.forEach { p ->
            var d = (p.bearingDeg - heading + 540) % 360 - 180
            if (kotlin.math.abs(d) > 13f) return@forEach
            val cx = w / 2 + d / 12f * (w / 2)
            val cw = dp(150f); val ch = dp(46f); val top = dp(60f)
            val r = RectF(cx - cw / 2, top, cx + cw / 2, top + ch)
            c.drawRoundRect(r, dp(10f), dp(10f), pShadow)
            c.drawRoundRect(r, dp(10f), dp(10f), if (p.hidden) pDash else pStroke)
            pTitle.textSize = sp(13f); pText.textSize = sp(10f)
            c.drawText(p.title, r.left + dp(10f), r.top + dp(18f), pTitle)
            c.drawText(p.sub, r.left + dp(10f), r.top + dp(36f), pText)
            c.drawLine(cx, r.bottom, cx, r.bottom + dp(14f), if (p.hidden) pDash else pStroke)
        }
        // labels beside heads
        pTitle.textSize = sp(14f)
        labels.forEach { l ->
            val x = l.cx + l.w / 2 + dp(10f); val y = l.cy - l.h / 4
            c.drawLine(l.cx + l.w / 2, y - dp(5f), x - dp(2f), y - dp(5f), if (l.dashed) pDash else pStroke)
            val tw = pTitle.measureText(l.text)
            c.drawRoundRect(RectF(x - dp(4f), y - sp(14f), x + tw + dp(4f), y + dp(6f)), dp(4f), dp(4f), pShadow)
            c.drawText(l.text, x, y, pTitle)
        }
        // card (top left, ≤ 70 % width)
        cardTitle?.let { t ->
            pTitle.textSize = sp(18f); pText.textSize = sp(13f)
            val cw = minOf(w * 0.7f, dp(340f)); val pad = dp(12f)
            val lines = cardLines.map { fit(it, pText, cw - 2 * pad) }
            val ch = pad + sp(20f) + dp(8f) + lines.size * sp(17f) + pad
            val r = RectF(dp(16f), dp(16f), dp(16f) + cw, dp(16f) + ch)
            c.drawRoundRect(r, dp(12f), dp(12f), pShadow)
            c.drawRoundRect(r, dp(12f), dp(12f), pStroke)
            c.drawText(fit(t.uppercase(), pTitle, cw - 2 * pad), r.left + pad, r.top + pad + sp(16f), pTitle)
            c.drawLine(r.left + pad, r.top + pad + sp(22f), r.right - pad, r.top + pad + sp(22f), pStroke)
            lines.forEachIndexed { i, s -> c.drawText(s, r.left + pad, r.top + pad + sp(22f) + dp(6f) + (i + 1) * sp(17f) - dp(3f), pText) }
        }
        // arrow (walking guidance)
        arrow?.let { a ->
            pTitle.textSize = sp(56f)
            c.drawText(a, dp(20f), useful - dp(40f), pTitle)
            pTitle.textSize = sp(20f)
            c.drawText(arrowText, dp(90f), useful - dp(60f), pTitle)
        }
        // transcript (bottom of the useful area)
        pText.textSize = sp(14f)
        val base = useful - dp(12f)
        transcript.takeLast(3).reversed().forEachIndexed { i, s ->
            val y = base - i * sp(19f)
            c.drawRect(dp(12f), y - sp(14f), dp(12f) + pText.measureText(s) + dp(8f), y + dp(4f), pShadow)
            c.drawText(s, dp(16f), y, pText)
        }
        // footer
        if (footer.isNotEmpty()) {
            pText.textSize = sp(11f); pText.color = green
            c.drawText(footer, dp(16f), h - dp(14f), pText)
            pText.color = soft
        }
        // the 70 % line, faint
        pDash.alpha = 60; c.drawLine(0f, useful, w, useful, pDash); pDash.alpha = 255
    }

    private fun fit(t: String, p: Paint, max: Float): String {
        if (p.measureText(t) <= max) return t
        var s = t
        while (s.length > 1 && p.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }

    fun clearCard() { cardTitle = null; cardLines = emptyList(); labels = emptyList(); arrow = null; invalidate() }
}
