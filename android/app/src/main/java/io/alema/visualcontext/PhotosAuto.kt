package io.alema.visualcontext

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Spec 5b, automatic: while the screen capture runs, scrolls photos.google.com/people in Chrome by itself,
 * a screen at a time, until the page stops changing; then brings the user back to the pick screen.
 * Acts only on Chrome and only while an import is running.
 */
class PhotosAuto : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var lastSig = ""
    private var same = 0
    private var steps = 0

    override fun onServiceConnected() { me = this }
    override fun onDestroy() { if (me === this) me = null; super.onDestroy() }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    private fun begin() { lastSig = ""; same = 0; steps = 0; h.removeCallbacksAndMessages(null); h.postDelayed(::step, 5000) }

    private fun step() {
        if (!running || !PhotosCaptureService.running) { running = false; return }
        val root = rootInActiveWindow
        if (root?.packageName != "com.android.chrome") { h.postDelayed(::step, 1500); return }
        val sig = signature(root)
        if (sig == lastSig) same++ else { same = 0; lastSig = sig }
        if (same >= 3 || steps > 400) { finish(); return }
        steps++
        val m = resources.displayMetrics
        val x = m.widthPixels * 0.5f
        // Scroll a little less than a screen: every face is seen at least once with its name.
        val p = Path().apply { moveTo(x, m.heightPixels * 0.78f); lineTo(x, m.heightPixels * 0.30f) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 450)).build(), null, null)
        // Time for the page to load the next tiles and for the capture to read them.
        h.postDelayed(::step, 1600)
    }

    /** What the page shows now: the names in view. When it stops changing, the end was reached. */
    private fun signature(n: AccessibilityNodeInfo): String {
        val out = StringBuilder()
        fun walk(x: AccessibilityNodeInfo?, d: Int) {
            if (x == null || d > 40 || out.length > 4000) return
            x.text?.let { out.append(it).append('|') }
            for (i in 0 until x.childCount) walk(x.getChild(i), d + 1)
        }
        walk(n, 0)
        return out.toString()
    }

    private fun finish() {
        running = false
        PhotosCaptureService.stop(this)
        h.postDelayed({ startActivity(Intent(this, PhotosPickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, 900)
    }

    companion object {
        @Volatile private var me: PhotosAuto? = null
        @Volatile var running = false; private set
        fun enabled(ctx: Context): Boolean {
            val want = ComponentName(ctx, PhotosAuto::class.java).flattenToString()
            return (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").split(':').any { it.equals(want, true) }
        }
        fun start(): Boolean { val s = me ?: return false; running = true; s.begin(); return true }
        fun stop() { running = false }
    }
}
