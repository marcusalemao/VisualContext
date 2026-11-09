package io.alema.visualcontext

import android.content.Context
import android.graphics.Bitmap
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
    /**
     * A person in view. The primary one (biggest face) gets the short card, anchored beside the head;
     * the others only their name. [countdown] > 0 shows "guardando pessoa nova: N".
     */
    data class FaceTag(val key: String, val title: String, val lines: List<String>, val cx: Float, val cy: Float, val w: Float, val h: Float,
                       val primary: Boolean, val known: Boolean, val countdown: Int = 0, val recording: Boolean = false)

    var faces: List<FaceTag> = emptyList()
    /** Smoothed positions, so the card glides with the head instead of jumping every scan. */
    private val pos = HashMap<String, FloatArray>()
    data class Pin(val title: String, val sub: String, val bearingDeg: Float, val hidden: Boolean, val lat: Double = 0.0, val lon: Double = 0.0, val kind: String = "", val dist: Int = 0)

    var labels: List<Label> = emptyList()
    var cardTitle: String? = null
        set(v) { if (v != field) { cardButtons = emptyList(); buttonFocus = 0; cardImage = null }; field = v }
    /** Where the top card was drawn (to keep pins below it and to close it with a tap). */
    var cardRect: RectF? = null; private set
    var cardLines: List<String> = emptyList()
    /** A photo inside the card (where an object was last seen); cleared with the title. */
    var cardImage: Bitmap? = null
    /** The lyrics card (Marcus, 09/11): cover, title, previous / current / next line with three weights. */
    data class Lyric(val title: String, val artist: String, val prev: String?, val cur: String?, val next: String?, val cover: Bitmap?)
    var lyric: Lyric? = null
    var transcript: List<String> = emptyList()
    var footer: String = ""
    var arrow: String? = null
    var arrowText: String = ""
    var pins: List<Pin> = emptyList()
    /** The pin chosen with the temple or a tap (Marcus, 08/11), drawn filled; -1 = none. Index into [pins]. */
    var pinFocus = -1
    /** Where each visible pin was drawn, by index in [pins] (for taps). */
    val pinRects = HashMap<Int, RectF>()
    /** On-screen buttons of the top card ("Sim", "Não"; "Ir a pé", "Detalhes"), and the one chosen with the temple. */
    var cardButtons: List<String> = emptyList()
    var buttonFocus = 0
    val buttonRects = ArrayList<RectF>()
    var heading: Float = 0f
    /** QR mode: the camera's centre, shown inside a window in the middle of the lens so the code can be aimed (as on the glasses). */
    var qrWindow = false
    var qrView: android.graphics.Bitmap? = null
    var qrHint: String = "encaixe o QR aqui"
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
        // place pins: cards pinned to a bearing inside a ±30° window (Marcus, 08/11: one degree moved them a lot).
        // They start below the top card and never sit on top of each other: an overlapping pin drops to the next row.
        val pinTop = (cardRect?.bottom ?: 0f).coerceAtLeast(dp(44f)) + dp(14f)
        val cw = dp(156f); val ch = dp(46f); val gap = dp(8f)
        val rows = ArrayList<MutableList<RectF>>()
        pinRects.clear()
        // QR mode: the window is the only thing in the middle (pins sat on top of it).
        if (!qrWindow) pins.mapIndexed { i, p -> Triple(i, p, (p.bearingDeg - heading + 540) % 360 - 180) }.filter { kotlin.math.abs(it.third) <= 30f || it.first == pinFocus }
            .sortedBy { kotlin.math.abs(it.third) }.forEach { (idx, p, d0) -> val d = d0.coerceIn(-30f, 30f)
                val cx = (w / 2 + d / 30f * (w / 2 - cw / 2)).coerceIn(cw / 2 + dp(6f), w - cw / 2 - dp(6f))
                var row = 0
                while (row < 3 && rows.getOrNull(row)?.any { it.left < cx + cw / 2 + gap && it.right > cx - cw / 2 - gap } == true) row++
                if (row >= 3) return@forEach
                while (rows.size <= row) rows.add(ArrayList())
                val top = pinTop + row * (ch + gap + dp(10f))
                val r = RectF(cx - cw / 2, top, cx + cw / 2, top + ch); rows[row].add(r); pinRects[idx] = r
                val sel = idx == pinFocus
                c.drawRoundRect(r, dp(10f), dp(10f), pShadow)
                if (sel) { pFill.color = green; c.drawRoundRect(r, dp(10f), dp(10f), pFill) }
                c.drawRoundRect(r, dp(10f), dp(10f), if (p.hidden && !sel) pDash else pStroke)
                pTitle.textSize = sp(13f); pText.textSize = sp(10f)
                if (sel) { pTitle.color = Color.BLACK; pText.color = Color.BLACK }
                c.drawText(fit(p.title, pTitle, cw - dp(20f)), r.left + dp(10f), r.top + dp(18f), pTitle)
                c.drawText(fit(p.sub, pText, cw - dp(20f)), r.left + dp(10f), r.top + dp(36f), pText)
                pTitle.color = green; pText.color = soft
                c.drawLine(cx, r.bottom, cx, r.bottom + dp(10f), if (p.hidden) pDash else pStroke)
            }
        // people: card beside the primary head, names beside the others
        drawFaces(c, w, useful)
        // labels beside heads (legacy, used by the Nexus plugin path)
        pTitle.textSize = sp(14f)
        labels.forEach { l ->
            val x = l.cx + l.w / 2 + dp(10f); val y = l.cy - l.h / 4
            c.drawLine(l.cx + l.w / 2, y - dp(5f), x - dp(2f), y - dp(5f), if (l.dashed) pDash else pStroke)
            val tw = pTitle.measureText(l.text)
            c.drawRoundRect(RectF(x - dp(4f), y - sp(14f), x + tw + dp(4f), y + dp(6f)), dp(4f), dp(4f), pShadow)
            c.drawText(l.text, x, y, pTitle)
        }
        lyric?.let { drawLyric(c, w, it) }
        // card (top left, ≤ 70 % width)
        if (cardTitle == null && cardRect != null) cardRect = null
        if (lyric == null) cardTitle?.let { t ->
            pTitle.textSize = sp(17f); pText.textSize = sp(12.5f)
            val cw = minOf(w * 0.7f, dp(340f)); val pad = dp(12f)
            // Nothing ends in "…" (Marcus, 09/11): the title wraps to 2 rows, each line to 4 rows, 10 rows in all.
            val titleRows = wrap(t.uppercase(), pTitle, cw - 2 * pad, 2)
            val lines = cardLines.flatMap { wrap(it, pText, cw - 2 * pad, 4) }.take(10)
            val img = cardImage; val ih = if (img == null) 0f else minOf(dp(120f), (cw - 2 * pad) * img.height / img.width) + dp(8f)
            val bh = if (cardButtons.isEmpty()) 0f else dp(48f) + dp(8f)
            val th = titleRows.size * sp(20f)
            val ch = pad + th + dp(8f) + ih + lines.size * sp(16f) + pad + bh
            val r = RectF(dp(16f), dp(16f), dp(16f) + cw, dp(16f) + ch)
            if (cardRect != r) { cardRect = r; post { invalidate() } } // pins are laid out from this
            c.drawRoundRect(r, dp(12f), dp(12f), pShadow)
            c.drawRoundRect(r, dp(12f), dp(12f), pStroke)
            titleRows.forEachIndexed { i, row -> c.drawText(row, r.left + pad, r.top + pad + sp(16f) + i * sp(20f), pTitle) }
            val sep = r.top + pad + th + dp(2f)
            c.drawLine(r.left + pad, sep, r.right - pad, sep, pStroke)
            var y = sep + dp(6f)
            if (img != null) {
                val iw = cw - 2 * pad; val hh = ih - dp(8f)
                val dst = RectF(r.left + pad, y, r.left + pad + iw, y + hh)
                val sc = maxOf(iw / img.width, hh / img.height); val sw = iw / sc; val sh = hh / sc
                val src = android.graphics.Rect(((img.width - sw) / 2).toInt(), ((img.height - sh) / 2).toInt(), ((img.width + sw) / 2).toInt(), ((img.height + sh) / 2).toInt())
                c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(dst, dp(8f), dp(8f), android.graphics.Path.Direction.CW) })
                c.drawBitmap(img, src, dst, null); c.restore()
                y += ih
            }
            lines.forEachIndexed { i, s -> c.drawText(s, r.left + pad, y + (i + 1) * sp(16f) - dp(3f), pText) }
            // Buttons on the card itself (Marcus, 08/11: "toque ou diga sim" had no Yes on the screen). 48 dp to tap.
            buttonRects.clear()
            if (cardButtons.isNotEmpty()) {
                val n = cardButtons.size; val g = dp(8f); val bw = (cw - 2 * pad - (n - 1) * g) / n
                val top = r.bottom - pad - dp(48f)
                pTitle.textSize = sp(15f)
                cardButtons.forEachIndexed { i, label ->
                    val b = RectF(r.left + pad + i * (bw + g), top, r.left + pad + i * (bw + g) + bw, top + dp(48f)); buttonRects.add(b)
                    val sel = i == buttonFocus
                    if (sel) { pFill.color = green; c.drawRoundRect(b, dp(10f), dp(10f), pFill) } else c.drawRoundRect(b, dp(10f), dp(10f), pShadow)
                    c.drawRoundRect(b, dp(10f), dp(10f), pStroke)
                    pTitle.color = if (sel) Color.BLACK else green
                    val tx = fit(label, pTitle, bw - dp(12f)); c.drawText(tx, b.centerX() - pTitle.measureText(tx) / 2, b.centerY() + sp(5f), pTitle)
                    pTitle.color = green
                }
            }
        }
        if (cardTitle == null) buttonRects.clear()
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
        if (qrWindow) drawQrWindow(c, w, useful)
        // the 70 % line, faint
        pDash.alpha = 60; c.drawLine(0f, useful, w, useful, pDash); pDash.alpha = 255
    }

    private fun drawFaces(c: Canvas, w: Float, useful: Float) {
        var moving = false
        val keys = faces.map { it.key }.toSet()
        pos.keys.retainAll(keys)
        for (f in faces) {
            val cur = pos.getOrPut(f.key) { floatArrayOf(f.cx, f.cy, f.w, f.h) }
            val tgt = floatArrayOf(f.cx, f.cy, f.w, f.h)
            for (i in 0..3) { val d = tgt[i] - cur[i]; if (kotlin.math.abs(d) > 1f) { cur[i] += d * 0.25f; moving = true } else cur[i] = tgt[i] }
            val (cx, cy, fw, fh) = cur.toList()
            val stroke = if (f.known) pStroke else pDash
            if (!f.primary) {
                if (f.title.isEmpty()) continue
                pTitle.textSize = sp(14f)
                val x = cx + fw / 2 + dp(10f); val y = cy - fh / 4
                val tw = pTitle.measureText(f.title)
                c.drawRoundRect(RectF(x - dp(4f), y - sp(14f), x + tw + dp(4f), y + dp(6f)), dp(4f), dp(4f), pShadow)
                c.drawText(f.title, x, y, pTitle)
                continue
            }
            // The card: right of the head, or left when there is no room; top aligned with the forehead, inside the useful area.
            pTitle.textSize = sp(15f); pText.textSize = sp(11.5f)
            val cw = minOf(w * 0.6f, dp(280f)); val pad = dp(10f)
            // Wrapped, never cut (Marcus, 09/11): title up to 2 rows, each line up to 4.
            val titleRows = wrap(f.title.uppercase(), pTitle, cw - 2 * pad, 2)
            val lines = f.lines.flatMap { wrap(it, pText, cw - 2 * pad, 4) }.take(10)
            val th = titleRows.size * sp(18f)
            val ch = pad + th + dp(6f) + lines.size * sp(15f) + pad
            var left = cx + fw / 2 + dp(14f)
            if (left + cw > w - dp(8f)) left = cx - fw / 2 - dp(14f) - cw
            left = left.coerceIn(dp(8f), w - cw - dp(8f))
            val top = (cy - fh / 2).coerceIn(dp(30f), maxOf(dp(30f), useful - ch - dp(8f)))
            val r = RectF(left, top, left + cw, top + ch)
            // leader line from the card to the head
            val hx = if (left > cx) cx + fw / 2 else cx - fw / 2
            c.drawLine(hx, cy - fh / 4, if (left > cx) r.left else r.right, r.top + ch / 2, stroke)
            // Spec 10/10: the recording line sits outside the card, on its own line above it, as plain text.
            if (f.countdown > 0 || f.recording) {
                pTitle.textSize = sp(14f)
                val t = if (f.countdown > 0) "Gravando encontro: ${f.countdown}" else "● gravando encontro"
                val ty = r.top - dp(8f)
                c.drawRect(r.left, ty - sp(14f), r.left + pTitle.measureText(t) + dp(6f), ty + dp(4f), pShadow)
                c.drawText(t, r.left + dp(2f), ty, pTitle)
                pTitle.textSize = sp(15f)
            }
            c.drawRoundRect(r, dp(10f), dp(10f), pShadow)
            c.drawRoundRect(r, dp(10f), dp(10f), stroke)
            titleRows.forEachIndexed { i, row -> c.drawText(row, r.left + pad, r.top + pad + sp(14f) + i * sp(18f), pTitle) }
            val sep = r.top + pad + th + dp(2f)
            c.drawLine(r.left + pad, sep, r.right - pad, sep, stroke)
            lines.forEachIndexed { i, s -> c.drawText(s, r.left + pad, sep + dp(4f) + (i + 1) * sp(15f) - dp(3f), pText) }
        }
        if (moving) postInvalidateOnAnimation()
    }

    /** Cover on the left, title and artist, then the line before (faint), the current (bold) and the next (half). */
    private fun drawLyric(c: Canvas, w: Float, l: Lyric) {
        val cw = minOf(w * 0.78f, dp(360f)); val pad = dp(12f); val cover = dp(64f)
        val textW = cw - 2 * pad - (if (l.cover != null) cover + dp(10f) else 0f)
        pTitle.textSize = sp(15f); pText.textSize = sp(11f)
        val title = wrap(l.title, pTitle, textW, 2); val artist = wrap(l.artist, pText, textW, 1)
        val head = maxOf(if (l.cover != null) cover else 0f, title.size * sp(18f) + artist.size * sp(14f))
        val pLine = Paint(pText).apply { textSize = sp(14f) }
        val pCur = Paint(pTitle).apply { textSize = sp(16f); isFakeBoldText = true }
        val prev = l.prev?.let { wrap(it, pLine, cw - 2 * pad, 2) } ?: emptyList()
        val cur = l.cur?.let { wrap(it, pCur, cw - 2 * pad, 3) } ?: emptyList()
        val next = l.next?.let { wrap(it, pLine, cw - 2 * pad, 2) } ?: emptyList()
        val body = prev.size * sp(18f) + cur.size * sp(21f) + next.size * sp(18f) + (if (prev.isNotEmpty() || next.isNotEmpty()) dp(8f) else 0f)
        val ch = pad + head + dp(10f) + body + pad
        val r = RectF(dp(16f), dp(16f), dp(16f) + cw, dp(16f) + ch)
        if (cardRect != r) { cardRect = r; post { invalidate() } }
        c.drawRoundRect(r, dp(12f), dp(12f), pShadow); c.drawRoundRect(r, dp(12f), dp(12f), pStroke)
        var x = r.left + pad
        l.cover?.let { b ->
            val dst = RectF(x, r.top + pad, x + cover, r.top + pad + cover)
            c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(dst, dp(8f), dp(8f), android.graphics.Path.Direction.CW) })
            c.drawBitmap(b, null, dst, null); c.restore(); x += cover + dp(10f)
        }
        var y = r.top + pad + sp(14f)
        title.forEach { c.drawText(it, x, y, pTitle); y += sp(18f) }
        artist.forEach { c.drawText(it, x, y - dp(2f), pText); y += sp(14f) }
        y = r.top + pad + head + dp(10f)
        // Three weights (Marcus, 09/11): the line sung now bold and full green, the one before faint, the next in between.
        pLine.color = green; pLine.alpha = 95
        prev.forEach { y += sp(18f); c.drawText(it, r.left + pad, y - dp(4f), pLine) }
        if (prev.isNotEmpty()) y += dp(4f)
        cur.forEach { y += sp(21f); c.drawText(it, r.left + pad, y - dp(4f), pCur) }
        if (next.isNotEmpty()) y += dp(4f)
        pLine.alpha = 170
        next.forEach { y += sp(18f); c.drawText(it, r.left + pad, y - dp(4f), pLine) }
        pText.color = soft
    }

    private fun drawQrWindow(c: Canvas, w: Float, useful: Float) {
        val side = minOf(w, useful) * 0.46f
        val l = (w - side) / 2; val t = (useful - side) / 2 + dp(10f)
        val r = RectF(l, t, l + side, t + side)
        c.drawRoundRect(RectF(r.left - dp(4f), r.top - dp(4f), r.right + dp(4f), r.bottom + dp(4f)), dp(14f), dp(14f), pShadow)
        qrView?.let { b -> c.drawBitmap(b, null, r, null) }
        // corner brackets, the viewfinder grammar
        val k = side * 0.18f
        listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom).forEach { (x, y) ->
            val sx = if (x == r.left) 1 else -1; val sy = if (y == r.top) 1 else -1
            c.drawLine(x, y, x + sx * k, y, pStroke); c.drawLine(x, y, x, y + sy * k, pStroke)
        }
        pTitle.textSize = sp(15f)
        val tw = pTitle.measureText(qrHint)
        c.drawRect((w - tw) / 2 - dp(6f), r.top - dp(30f), (w + tw) / 2 + dp(6f), r.top - dp(8f), pShadow)
        c.drawText(qrHint, (w - tw) / 2, r.top - dp(13f), pTitle)
    }

    private fun wrap(t: String, p: Paint, max: Float, rows: Int = 99): List<String> {
        val out = ArrayList<String>(); var line = ""
        for (word in t.split(Regex("\\s+"))) {
            val tryL = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(tryL) <= max || line.isEmpty()) line = tryL else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        // Over the row limit: the last row carries what is left, and only then an ellipsis.
        if (out.size > rows) { val keep = out.take(rows - 1); return (keep + out.drop(rows - 1).joinToString(" ")).map { fit(it, p, max) } }
        return out.map { fit(it, p, max) }
    }

    private fun fit(t: String, p: Paint, max: Float): String {
        if (p.measureText(t) <= max) return t
        var s = t
        while (s.length > 1 && p.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }

    fun clearCard() { cardTitle = null; cardLines = emptyList(); lyric = null; labels = emptyList(); faces = emptyList(); arrow = null; invalidate() }
}
