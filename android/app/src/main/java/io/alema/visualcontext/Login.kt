package io.alema.visualcontext

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Entrar com seu e-mail": Supabase Auth sends an e-mail with a link (and, once we have our own SMTP,
 * a 6-digit code). The link opens the Manager, which hands a device code back to the app through
 * visualcontext://conectar; the code is typed here. Either way every other call uses that vc_ code.
 * Blocking — call it off the main thread.
 */
object Login {
    private const val AUTH = "https://vzqjwhlpwvqrosoiwiin.supabase.co/auth/v1"
    /** Public by design (Supabase "publishable" key); it only lets the app ask for a code. */
    private const val KEY = "sb_publishable_EA_WlXKNQ9aiCQjknR8b3A_Z1qMuRxp"

    sealed class Out {
        data class Ok(val value: String) : Out()
        data class Fail(val why: String) : Out()
    }

    private fun call(url: String, body: JSONObject, bearer: String? = null): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12_000; readTimeout = 20_000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("apikey", KEY)
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    fun sendCode(email: String): Out = try {
        val (c, t) = call("$AUTH/otp?redirect_to=" + java.net.URLEncoder.encode(Backend.MANAGER_URL + "?app=1", "UTF-8"), JSONObject().put("email", email.trim().lowercase()).put("create_user", true))
        when {
            c in 200..299 -> Out.Ok("")
            c == 429 || "rate" in t -> Out.Fail("Muitos pedidos. Espere um minuto e tente de novo.")
            "email" in t && "invalid" in t -> Out.Fail("E-mail inválido.")
            else -> Out.Fail("Não deu para enviar o código ($c).")
        }
    } catch (_: Exception) { Out.Fail("Sem internet.") }

    /** Code → device token for this phone. */
    fun verify(email: String, code: String, deviceName: String): Out = try {
        val (c, t) = call("$AUTH/verify", JSONObject().put("type", "email").put("email", email.trim().lowercase()).put("token", code.trim()))
        if (c !in 200..299) Out.Fail(if ("expired" in t || "invalid" in t) "Código errado ou vencido." else "Não deu para entrar ($c).")
        else {
            val access = JSONObject(t).optString("access_token")
            val (c2, t2) = call(Backend.DEFAULT_URL, JSONObject().put("action", "register_device").put("name", deviceName), access)
            val tok = runCatching { JSONObject(t2).optString("token") }.getOrDefault("")
            if (c2 in 200..299 && tok.startsWith("vc_")) Out.Ok(tok) else Out.Fail("Entrou, mas não deu para registrar este celular ($c2).")
        }
    } catch (_: Exception) { Out.Fail("Sem internet.") }

    /** Invite code from the approval message (VC-XXXX-XXXX) → device token, no e-mail needed (Marcus, 09/11). Returns token and e-mail. */
    fun redeem(code: String, deviceName: String): Pair<Out, String> = try {
        val (c, t) = call(Backend.DEFAULT_URL, JSONObject().put("action", "redeem_code").put("code", code.trim()).put("name", deviceName))
        val j = runCatching { JSONObject(t) }.getOrDefault(JSONObject())
        val tok = j.optString("token")
        if (c in 200..299 && tok.startsWith("vc_")) Out.Ok(tok) to j.optString("email")
        else Out.Fail(j.optString("error").ifBlank { "Não deu para entrar ($c)." }) to ""
    } catch (_: Exception) { Out.Fail("Sem internet.") to "" }
}
