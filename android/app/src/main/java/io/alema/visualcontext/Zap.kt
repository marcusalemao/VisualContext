package io.alema.visualcontext

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talk to someone from the memory (Marcus, 09/11): the Zap opens the conversation on the glasses; without it, WhatsApp
 * on the phone; without either, the link to install the Zap. Contract the Zap answers from 0.16: zap://chat?phone=&name=.
 */
object Zap {
    const val PKG = "io.alema.zap"
    const val SITE = "https://z.alema.io"
    const val TOOL = "conversar_com"
    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Abre a conversa (Zap/WhatsApp) com uma pessoa da memória: \"manda mensagem pro Bruno\", \"abre a conversa com a Ana\", \"fala com o Pedro\".")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("nome", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("nome")))

    fun installed(ctx: Context) = NotesOut.installed(ctx, PKG)

    /** "zap" | "whatsapp" | "sem_numero" | "sem_pessoa" | "sem_app". Blocking (reads the memory). */
    /** 55 + DDD + number; an old 8-digit mobile gets its 9; foreign or incomplete numbers go as typed (same rule as the Manager). */
    fun brPhone(raw: String): String {
        var d = raw.filter { it.isDigit() }.removePrefix("00")
        if (Regex("^0\\d{12,13}$").matches(d)) d = d.drop(3) else if (Regex("^0\\d{10,11}$").matches(d)) d = d.drop(1)
        if (Regex("^55\\d{10,11}$").matches(d)) d = d.drop(2)
        if (!Regex("^\\d{10,11}$").matches(d)) return d
        var n = d.drop(2)
        if (n.length == 8 && n[0] in "6789") n = "9$n"
        return "55" + d.take(2) + n
    }

    fun open(ctx: Context, name: String): String {
        val r = Backend.post(ctx, JSONObject().put("action", "list").put("entity", "person").put("limit", 1000)) ?: return "sem_pessoa"
        val a = runCatching { JSONArray(r) }.getOrNull() ?: return "sem_pessoa"
        val key = name.trim().lowercase()
        val p = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("name").lowercase() == key }
            ?: (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("name").lowercase().split(" ").contains(key) || it.optString("nickname").lowercase() == key }
            ?: return "sem_pessoa"
        val phone = brPhone(p.optString("contact_whatsapp").ifBlank { p.optString("contact_phone") })
        if (phone.isEmpty()) return "sem_numero"
        val full = p.optString("name")
        if (installed(ctx)) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("zap://chat?phone=$phone&name=" + Uri.encode(full))).setPackage(PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(i); true }.getOrDefault(false)) return "zap"
        }
        // The WhatsApp app itself (a plain wa.me link can open the web version).
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) if (NotesOut.installed(ctx, pkg)) {
            val wa = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(wa); true }.getOrDefault(false)) return "whatsapp"
        }
        NotesOut.offer(ctx, "Instalar o Zap", "O VisualContext queria abrir a conversa com $full nos óculos. Isso precisa do Zap: $SITE", sensitive = false)
        return "sem_app"
    }
}
