package io.alema.visualcontext

import io.alema.facecore.FaceMatch
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The VisualContext backend (the `vc` function on Supabase). Every call carries the device token
 * in the x-vc-token header; without it the backend answers 401.
 * The token lives only in this phone's private app storage, never in the code.
 */
object Backend {
    /** The VisualContext memory (Supabase, São Paulo). Base44 (until 08/11/2026) is gone; an old saved address moves here by itself. */
    const val DEFAULT_URL = "https://vzqjwhlpwvqrosoiwiin.supabase.co/functions/v1/vc"
    private const val PREFS = "visualcontext"

    fun url(ctx: Context): String = (prefs(ctx).getString("backend_url", DEFAULT_URL) ?: DEFAULT_URL).let { if ("base44.app" in it) DEFAULT_URL else it }
    /** Face engine: "arc" (ArcFace, default from 0.3) or "faceapi" (old, compatible with the 7 stored faces). */
    fun engine(ctx: Context): String = prefs(ctx).getString("face_engine", "arc") ?: "arc"
    fun setEngine(ctx: Context, e: String) { prefs(ctx).edit().putString("face_engine", e).apply() }
    fun geminiKey(ctx: Context): String = prefs(ctx).getString("gemini_key", "") ?: ""
    fun voiceName(ctx: Context): String = prefs(ctx).getString("voice", "Kore") ?: "Kore"
    fun saveGemini(ctx: Context, key: String, voice: String) { prefs(ctx).edit().putString("gemini_key", key.trim()).putString("voice", voice).remove("live_model").remove("text_model").apply() }
    /** The wearer's own name in the faces list (Marcus, 07/11): that face is never an encounter, even in a mirror or on a friend's phone. */
    fun meName(ctx: Context): String = prefs(ctx).getString("me_name", "") ?: ""
    fun setMeName(ctx: Context, n: String) {
        prefs(ctx).edit().putString("me_name", n.trim()).apply()
        Moments.send(ctx, org.json.JSONObject().put("action", "set_me").put("name", n.trim())) // the Manager shows "★ você" too
    }
    /** Pull the account's "me" (set in the Manager, or on another phone). Blocking. */
    fun syncMe(ctx: Context) {
        val r = post(ctx, org.json.JSONObject().put("action", "me")) ?: return
        val n = runCatching { org.json.JSONObject(r).optString("me_name") }.getOrDefault("")
        if (n != "null") prefs(ctx).edit().putString("me_name", n).apply()
    }
    /** Only the phone: no Gemini Live at all (recognise, transcribe and warn offline). */
    fun offlineOnly(ctx: Context) = prefs(ctx).getBoolean("offline_only", false)
    fun setOfflineOnly(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("offline_only", on).apply() }
    /** Standby: between encounters, listen (offline) for questions to the memory and calls to the assistant. */
    fun standby(ctx: Context) = prefs(ctx).getBoolean("standby", false)
    fun setStandby(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("standby", on).apply() }
    fun saveVoice(ctx: Context, voice: String) { prefs(ctx).edit().putString("voice", voice).apply() }
    fun prefsText(ctx: Context): String? = prefs(ctx).getString("text_model", null)
    fun saveTextModel(ctx: Context, m: String?) { prefs(ctx).edit().putString("text_model", m).apply() }
    fun liveModel(ctx: Context): String? = prefs(ctx).getString("live_model", null)
    fun saveLiveModel(ctx: Context, m: String?) { prefs(ctx).edit().putString("live_model", m).apply() }
    fun token(ctx: Context): String = prefs(ctx).getString("device_token", "") ?: ""
    /** The e-mail this phone signed in with (shown in Settings). */
    fun email(ctx: Context): String = prefs(ctx).getString("account_email", "") ?: ""
    fun saveEmail(ctx: Context, e: String) { prefs(ctx).edit().putString("account_email", e.trim().lowercase()).apply() }
    fun save(ctx: Context, url: String, token: String) {
        prefs(ctx).edit().putString("backend_url", url.trim().ifEmpty { DEFAULT_URL }).putString("device_token", token.trim()).apply()
    }

    sealed class Result {
        data class People(val count: Int, val newest: List<String>) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Blocking. Any vc action; null when offline, no token or an error. */
    fun post(ctx: Context, body: JSONObject): String? {
        val token = token(ctx); if (token.isEmpty()) return null
        return try {
            val c = (URL(url(ctx)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 20_000; doOutput = true
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-vc-token", token)
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) { null }
    }

    /** The Manager in the browser, already signed in with this phone (fragment: never sent to a server). */
    const val MANAGER_URL = "https://web-visualcontext-site.visualcontext.pages.dev/manager"
    fun managerLink(ctx: Context): String {
        val g = geminiKey(ctx).takeIf { it.length > 20 }?.let { "&g=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        return "$MANAGER_URL#t=${token(ctx)}$g"
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

    /** One person from the memory, only the fields the glasses card needs. */
    data class Person(
        val id: String, val name: String, val relation: String, val company: String,
        val lastSeenContext: String, val lastSeenDate: String, val embedding: FloatArray?,
    )

    /** Blocking. The whole people list with face embeddings, or null when offline / no token. */
    fun peopleFull(ctx: Context): List<Person>? {
        val token = token(ctx)
        if (token.isEmpty()) return null
        return try {
            val conn = (URL(url(ctx)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-vc-token", token)
            }
            val body = JSONObject().put("action", "list").put("entity", "FaceContextPerson").put("limit", 1000)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val list = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                fun f(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" } ?: ""
                val name = f("name").ifEmpty { return@mapNotNull null }
                Person(f("id"), name, f("relation"), f("company"), f("last_seen_context"), f("last_seen_date"), FaceMatch.parse(f("face_embedding")))
            }
        } catch (_: Exception) { null }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
