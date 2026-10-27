package io.alema.vcsolo

import io.alema.qrcore.QrHint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View

/**
 * Drawn over the live camera view while reading a QR: only the four corner marks of the target square,
 * a line sweeping up and down inside it, and one short hint at the TOP (REGRAS-HUD: nothing important at the bottom).
 * No fill anywhere: the camera does not see the overlay, and a fill would only block the wearer's view.
 */
class ScanOverlay(ctx: Context) : View(ctx) {
    var title = "Configurar"
    var hint = QrHint.State.POINT.text
    var detail = ""

    private val green = Color.rgb(0, 255, 136)
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; strokeWidth = 3f }
    private val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 24f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 26f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val detailP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; textSize = 16f; typeface = Typeface.MONOSPACE }
    // Dark halo behind text so it reads over the camera image without a filled box.
    private val shadow = Color.argb(220, 0, 0, 0)

    init {
        listOf(titleP, hintP, detailP).forEach { it.setShadowLayer(6f, 0f, 0f, shadow) }
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun set(hint: String, detail: String = this.detail) {
        this.hint = hint; this.detail = detail
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // Text block at the top.
        c.drawText(title.uppercase(), 20f, 34f, titleP)
        c.drawText(hint, 20f, 72f, hintP)
        if (detail.isNotBlank()) c.drawText(detail, 20f, 100f, detailP)
        // Target square under the text, as large as fits.
        val top = 116f
        val side = minOf(w - 60f, h - top - 20f)
        val left = (w - side) / 2f
        val right = left + side; val bottom = top + side
        val k = side * 0.16f
        c.drawLine(left, top, left + k, top, corner); c.drawLine(left, top, left, top + k, corner)
        c.drawLine(right, top, right - k, top, corner); c.drawLine(right, top, right, top + k, corner)
        c.drawLine(left, bottom, left + k, bottom, corner); c.drawLine(left, bottom, left, bottom - k, corner)
        c.drawLine(right, bottom, right - k, bottom, corner); c.drawLine(right, bottom, right, bottom - k, corner)
        // Sweeping line: 1.6 s down, 1.6 s up.
        val t = (SystemClock.uptimeMillis() % 3200L) / 1600f
        val f = if (t <= 1f) t else 2f - t
        val y = top + 12f + f * (side - 24f)
        c.drawLine(left + 14f, y, right - 14f, y, line)
        postInvalidateOnAnimation()
    }
}
