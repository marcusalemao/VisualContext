package io.alema.visualcontext

import android.app.Activity
import android.content.Context
import android.view.View
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig

/**
 * Microsoft Clarity in the app (Marcus, 09/11: "mapa de calor… vamos ver se funciona"). Off until he turns it on in
 * Settings. What could be personal never goes: the lens (camera video and HUD) and the key fields are masked.
 */
object Heat {
    private const val PROJECT = "zb1tjyx4v2"
    private var started = false
    fun on(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).getBoolean("heat_on", false)
    fun set(ctx: Context, v: Boolean) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).edit().putBoolean("heat_on", v).apply()
    fun start(a: Activity) {
        if (started || !on(a)) return
        started = runCatching { Clarity.initialize(a, ClarityConfig(PROJECT)) }.getOrDefault(false)
    }
    fun mask(v: View) { runCatching { Clarity.maskView(v) } }
}
