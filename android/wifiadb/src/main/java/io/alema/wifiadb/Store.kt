package io.alema.wifiadb

import android.content.Context

/**
 * Last commands, so the phone screen can show them with copy buttons.
 * Nexus rule: pairing codes expire after two minutes and must never be persisted or logged,
 * so the pairing command lives only in memory; the connect command (no secret) is saved.
 */
object Store {
    private const val PREFS = "instalar_wifi"
    private const val CODE_TTL_MS = 2 * 60_000L
    @Volatile private var pairCmd: String? = null
    @Volatile private var pairAt = 0L

    fun save(ctx: Context, i: AdbInfo) {
        AdbCommands.pair(i)?.let { pairCmd = it; pairAt = System.currentTimeMillis() }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("connect", AdbCommands.connect(i)).remove("pair").apply()
    }

    fun pair(@Suppress("UNUSED_PARAMETER") ctx: Context): String? =
        pairCmd?.takeIf { System.currentTimeMillis() - pairAt < CODE_TTL_MS }

    fun connect(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("connect", null)
}
