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

    /**
     * Photos, not files (Marcus, 07/11: the file picker only offered Drive). The Android photo picker shows the
     * gallery and, when the Google Photos app is set as its cloud source, Google Photos with search by name.
     * Older phones get the system "pick an image" chooser, where Google Photos is one of the apps.
     */
    fun pickPhotos(max: Int = 12): android.content.Intent =
        if (android.os.Build.VERSION.SDK_INT >= 33) android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).apply {
            type = "image/*"
            putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, max.coerceAtMost(android.provider.MediaStore.getPickImagesMaxLimit()))
        } else android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*"; putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true) }

    /**
     * Work in progress the user must not walk away from (Marcus, 07/11: reading photos takes ~10 s and looked done).
     * A sheet that cannot be dismissed, with a bar and "3 de 7". Call [Progress.step] from any thread; [Progress.done] closes it.
     */
    class Progress(act: android.app.Activity, title: String, private val total: Int) {
        private val bar = android.widget.ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = maxOf(total, 1); isIndeterminate = total <= 0; progressTintList = android.content.res.ColorStateList.valueOf(green); indeterminateTintList = android.content.res.ColorStateList.valueOf(green) }
        private val line = android.widget.TextView(act).apply { textSize = 14f; setTextColor(soft); setPadding(0, dp(act, 10), 0, 0) }
        private val dlg = android.app.AlertDialog.Builder(act).setCancelable(false).setView(android.widget.LinearLayout(act).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(act, 24), dp(act, 20), dp(act, 24), dp(act, 20)); setBackgroundColor(Color.rgb(8, 22, 14))
            addView(android.widget.TextView(act).apply { text = title; textSize = 17f; setTextColor(green); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(act, 12)) })
            addView(bar); addView(line)
            addView(android.widget.TextView(act).apply { text = "Não saia desta tela até terminar."; textSize = 12f; setTextColor(soft); setPadding(0, dp(act, 8), 0, 0) })
        }).create().also { it.show() }
        private val act = act
        fun step(i: Int, what: String = "") = act.runOnUiThread { bar.progress = i; line.text = (if (total > 0) "$i de $total" else "") + (if (what.isNotEmpty()) " · $what" else "") }
        fun done() = act.runOnUiThread { runCatching { dlg.dismiss() } }
    }

    private val navOf = java.util.WeakHashMap<View, Int>()
    /** Height of the Android navigation bar under this view (0 until the system tells us). */
    fun nav(v: View) = navOf[v] ?: 0

    /** A small outline chip for secondary actions in a row ("＋ Fotos", "Ver todas", "Apagar"); same height, same spacing. */
    fun chip(ctx: Context, text: String, color: Int = green, onClick: () -> Unit): Button = Button(ctx).apply {
        this.text = text; isAllCaps = false; textSize = 13f; setTextColor(color); stateListAnimator = null
        minHeight = dp(ctx, 36); minimumHeight = dp(ctx, 36); minWidth = 0; minimumWidth = 0; setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
        background = GradientDrawable().apply { cornerRadius = dp(ctx, 12).toFloat(); setColor(Color.TRANSPARENT); setStroke(dp(ctx, 1), color) }
        setOnClickListener { onClick() }
    }

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
