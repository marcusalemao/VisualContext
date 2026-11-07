package io.alema.visualcontext

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button

/** The one look for every page (Marcus, 07/11: grey buttons read as disabled; the Android bar covered the end of pages). */
object Look {
    val ink: Int = Color.rgb(3, 12, 6)
    val green: Int = Color.rgb(0, 255, 136)
    val soft: Int = Color.rgb(143, 223, 181)
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Primary = filled green; otherwise green outline. Disabled stays readable (dimmed, not grey). */
    fun button(b: Button, primary: Boolean) = b.apply {
        isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
        setTextColor(if (primary) ink else green); minHeight = dp(context, 48)
        background = GradientDrawable().apply { cornerRadius = dp(context, 14).toFloat(); if (primary) setColor(green) else { setColor(Color.TRANSPARENT); setStroke(dp(context, 2), green) } }
        alpha = if (isEnabled) 1f else 0.45f
    }

    private val navOf = java.util.WeakHashMap<View, Int>()
    /** Height of the Android navigation bar under this view (0 until the system tells us). */
    fun nav(v: View) = navOf[v] ?: 0

    /** Keep the page's end above the Android navigation bar (gesture or buttons). Pages that reset their padding add [nav]. */
    fun insets(v: View) {
        val l = v.paddingLeft; val t = v.paddingTop; val r = v.paddingRight; val b = v.paddingBottom
        v.setOnApplyWindowInsetsListener { view, ins ->
            val nav = if (android.os.Build.VERSION.SDK_INT >= 30) ins.getInsets(android.view.WindowInsets.Type.systemBars()).bottom else @Suppress("DEPRECATION") ins.systemWindowInsetBottom
            navOf[view] = nav
            view.setPadding(view.paddingLeft.takeIf { it != l } ?: l, view.paddingTop.takeIf { it != t } ?: t, view.paddingRight.takeIf { it != r } ?: r, maxOf(b, view.paddingBottom - nav) + nav); ins
        }
        v.requestApplyInsets()
    }
}
