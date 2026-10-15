package io.alema.wifiadb

import android.content.Context

/** Last commands, so the phone screen can show them with copy buttons. */
object Store {
    private const val PREFS = "instalar_wifi"

    fun save(ctx: Context, i: AdbInfo) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pair", AdbCommands.pair(i))
            .putString("connect", AdbCommands.connect(i))
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    fun pair(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pair", null)
    fun connect(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("connect", null)
}
