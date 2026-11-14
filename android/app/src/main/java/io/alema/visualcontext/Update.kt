package io.alema.visualcontext

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * "New version" notice (Marcus, 14/11/2026). The beta APK is installed by hand, so nothing updates it by itself.
 * On opening the app (at most every 6 hours) this reads https://vc.alema.io/app-version.json:
 *   { "versionCode": 93, "versionName": "0.19.8", "url": "https://…/VisualContext.apk",
 *     "notes": { "pt": "…", "en": "…", "de": "…" } }
 * and, when versionCode is higher than the installed one, the phone screen shows a card with a download button.
 * Android still asks the user to confirm the install; the same signing key keeps the data. No network, no card.
 */
object Update {
    private const val FEED = "https://vc.alema.io/app-version.json"
    private const val EVERY_MS = 6 * 3600_000L
    private val io = Executors.newSingleThreadExecutor()

    data class Info(val code: Int, val name: String, val url: String, val notes: String)

    private fun prefs(c: Context) = c.getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
    private fun installed(c: Context): Int = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode.toInt() }.getOrDefault(0)

    /** The newer version this phone already knows about and the user has not dismissed, or null. */
    fun pending(c: Context): Info? {
        val p = prefs(c); val code = p.getInt("upd_code", 0)
        if (code <= installed(c) || code == p.getInt("upd_skip", 0)) return null
        val url = p.getString("upd_url", null) ?: return null
        return Info(code, p.getString("upd_name", "") ?: "", url, p.getString("upd_notes", "") ?: "")
    }

    fun dismiss(c: Context, info: Info) { prefs(c).edit().putInt("upd_skip", info.code).apply() }

    /** Asks the feed in the background; [changed] runs on a worker thread, so post to the UI yourself. */
    fun check(c: Context, force: Boolean = false, changed: () -> Unit) {
        val p = prefs(c)
        if (!force && System.currentTimeMillis() - p.getLong("upd_at", 0) < EVERY_MS) return
        val app = c.applicationContext
        io.execute {
            runCatching {
                val cn = URL(FEED).openConnection() as HttpURLConnection
                cn.connectTimeout = 6000; cn.readTimeout = 6000; cn.setRequestProperty("Cache-Control", "no-cache")
                val j = JSONObject(cn.inputStream.bufferedReader().use { it.readText() })
                val lang = Locale.getDefault().language
                val n = j.optJSONObject("notes")
                val notes = n?.optString(if (n.has(lang)) lang else "en", "")?.ifBlank { n.optString("pt", "") } ?: ""
                val before = pending(app)?.code
                prefs(app).edit().putLong("upd_at", System.currentTimeMillis())
                    .putInt("upd_code", j.optInt("versionCode", 0)).putString("upd_name", j.optString("versionName", ""))
                    .putString("upd_url", j.optString("url", "")).putString("upd_notes", notes).apply()
                if (pending(app)?.code != before) changed()
            }
        }
    }
}
