package io.alema.visualcontext

import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini, either straight to Google with his own key or through our server (beta testers, Marcus 09/11: "a chave
 * nunca vai para o celular deles"). The server answers in Google's own format, so callers do not care which.
 */
object Gem {
    const val PROXY = "vcproxy:"
    private const val BASE = "https://vzqjwhlpwvqrosoiwiin.supabase.co/functions/v1/gem/v1beta/"
    fun proxied(key: String) = key.startsWith(PROXY)
    /** [path] like "models/gemini-flash-latest:generateContent" or "models?pageSize=200". */
    fun url(key: String, path: String) = URL(if (proxied(key)) BASE + path else "https://generativelanguage.googleapis.com/v1beta/$path")
    fun auth(c: HttpURLConnection, key: String) { if (proxied(key)) c.setRequestProperty("x-vc-token", key.removePrefix(PROXY)) else c.setRequestProperty("x-goog-api-key", key) }
}
