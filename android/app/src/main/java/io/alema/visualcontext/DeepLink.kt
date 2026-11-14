package io.alema.visualcontext

import android.net.Uri

/** visualcontext://conectar?t=vc_… — opened by the Manager's "Conectar óculos" button. */
object DeepLink {
    const val MANAGER = "https://visualcontext.alema.io"
    private val TOKEN = Regex("^vc_[A-Za-z0-9]{16,128}$")

    fun token(uri: Uri?): String? {
        if (uri == null || uri.scheme != "visualcontext" || uri.host != "conectar") return null
        return parse(uri.getQueryParameter("t"))
    }

    fun parse(raw: String?): String? = raw?.trim()?.takeIf { TOKEN.matches(it) }
}
