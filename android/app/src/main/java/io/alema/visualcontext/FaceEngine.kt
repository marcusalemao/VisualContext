package io.alema.visualcontext

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Face detection + 128-d descriptors on the phone, with face-api.js inside an off-screen WebView
 * (assets/face). The photo never leaves the phone for recognition.
 * All calls on the main thread; answers come back on the main thread.
 */
class FaceEngine(ctx: Context) {
    data class Face(val descriptor: FloatArray, val x: Float, val y: Float, val w: Float, val h: Float, val score: Float)
    sealed class Result {
        data class Faces(val faces: List<Face>, val width: Int, val height: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val ids = AtomicInteger()
    private var loaded = false
    private val queue = ArrayList<() -> Unit>()

    private val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    private val web = WebView(ctx.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        addJavascriptInterface(Bridge(), "VC")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
            override fun onPageFinished(view: WebView, url: String) {
                loaded = true
                val q = ArrayList(queue); queue.clear(); q.forEach { it() }
            }
        }
        loadUrl("https://appassets.androidplatform.net/assets/face/index.html")
    }

    private inner class Bridge {
        @JavascriptInterface fun result(id: String, json: String) {
            val cb = pending.remove(id) ?: return
            val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject().put("ok", false).put("error", "json") }
            main.post { cb(obj) }
        }
    }

    private fun call(fn: String, arg: String?, cb: (JSONObject) -> Unit) {
        val id = "c" + ids.incrementAndGet()
        pending[id] = cb
        val js = if (arg == null) "$fn('$id')" else "$fn('$id','$arg')"
        val run = { web.evaluateJavascript(js, null) }
        if (loaded) run() else queue += run
        main.postDelayed({ pending.remove(id)?.invoke(JSONObject().put("ok", false).put("error", "demorou demais")) }, 45_000)
    }

    /** Load the models ahead of time (first load takes a few seconds). */
    fun warm(cb: (String) -> Unit = {}) = call("warm", null) { cb(it.optString("backend", it.optString("error"))) }

    /** jpeg → faces, biggest first. Base64 has no quotes, so it is safe inside the JS string. */
    fun describe(jpegBase64: String, cb: (Result) -> Unit) = call("describe", jpegBase64) { o ->
        if (!o.optBoolean("ok")) { cb(Result.Failed(o.optString("error", "erro"))); return@call }
        val arr = o.optJSONArray("faces")
        val faces = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val f = arr!!.optJSONObject(i) ?: return@mapNotNull null
            val d = f.optJSONArray("descriptor") ?: return@mapNotNull null
            if (d.length() != 128) return@mapNotNull null
            val b = f.optJSONObject("box") ?: JSONObject()
            Face(FloatArray(128) { d.optDouble(it).toFloat() }, b.optDouble("x").toFloat(), b.optDouble("y").toFloat(),
                b.optDouble("w").toFloat(), b.optDouble("h").toFloat(), f.optDouble("score").toFloat())
        }
        cb(Result.Faces(faces, o.optInt("w"), o.optInt("h")))
    }

    fun destroy() { pending.clear(); web.destroy() }
}
