package io.alema.qrcore

/**
 * What to tell the wearer while aiming at a QR. Plain Kotlin, unit-tested.
 * The Rokid camera has a wide lens and sits on the left temple, so codes look smaller and off-centre:
 * the live view + these short hints replace guessing.
 */
object QrHint {
    enum class State(val text: String) {
        POINT("aponte para o QR code"),
        CLOSER("chegue mais perto"),
        HOLD("fique parado…"),
    }

    /** A code smaller than this share of the frame is usually too small to read. */
    const val MIN_SHARE = 0.22f

    /**
     * @param points the QR finder pattern centres ZXing located (x, y pairs), or empty when none were found
     * @param frameW frame width, @param frameH frame height
     */
    fun of(points: List<Pair<Float, Float>>, frameW: Int, frameH: Int): State {
        if (points.size < 3 || frameW <= 0 || frameH <= 0) return State.POINT
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val side = maxOf(xs.max() - xs.min(), ys.max() - ys.min())
        return if (side / minOf(frameW, frameH) < MIN_SHARE) State.CLOSER else State.HOLD
    }
}
