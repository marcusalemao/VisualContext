package io.alema.visualcontext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The VisualContext backend (fcProxy on Base44). Every call carries the device token
 * in the x-vc-token header; without it the backend answers 401.
 * The token lives only in this phone's private app storage, never in the code.
 */
object Backend {
    const val DEFAULT_URL = "https://base44.app/api/apps/6a11083db49430b410a8c066/functions/fcProxy"
    private const val PREFS = "visualcontext"

    fun url(ctx: Context): String = prefs(ctx).getString("backend_url", DEFAULT_URL) ?: DEFAULT_URL
    fun token(ctx: Context): String = prefs(ctx).getString("device_token", "") ?: ""
    fun save(ctx: Context, url: String, token: String) {
        prefs(ctx).edit().putString("backend_url", url.trim().ifEmpty { DEFAULT_URL }).putString("device_token", token.trim()).apply()
    }

    sealed class Result {
        data class People(val count: Int, val newest: List<String>) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Blocking — call it off the main thread. */
    fun people(ctx: Context): Result {
        val token = token(ctx)
        if (token.isEmpty()) return Result.Failed("sem token")
        return try {
            val conn = (URL(url(ctx)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-vc-token", token)
            }
            val body = JSONObject().put("action", "list").put("entity", "FaceContextPerson").put("limit", 1000)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code == 401) return Result.Failed("token recusado")
            if (code !in 200..299) return Result.Failed("erro $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val list = JSONArray(text)
            val names = (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() && n != "null" } }
            Result.People(list.length(), names.take(3))
        } catch (e: Exception) {
            Result.Failed("sem conexão")
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
