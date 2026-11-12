package io.alema.zap.core

import java.net.URLEncoder

/**
 * Links that open a WhatsApp chat with the text already typed in.
 *
 * Opening the chat never sends anything by itself; sending is a separate,
 * deliberate step (the wearer's tap, then the phone's accessibility helper or
 * the wearer's own thumb).
 */
object WhatsAppLink {
    const val PACKAGE = "com.whatsapp"
    const val BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /** Handled by the WhatsApp app directly, no browser hop. */
    fun appUri(digits: String, text: String?): String {
        require(digits.isNotEmpty() && digits.all(Char::isDigit)) { "digits only" }
        val base = "whatsapp://send?phone=$digits"
        return if (text.isNullOrBlank()) base else "$base&text=${encode(text)}"
    }

    /** Universal fallback, works from any browser. */
    fun webUri(digits: String, text: String?): String {
        require(digits.isNotEmpty() && digits.all(Char::isDigit)) { "digits only" }
        val base = "https://wa.me/$digits"
        return if (text.isNullOrBlank()) base else "$base?text=${encode(text)}"
    }

    // URLEncoder writes spaces as "+", which WhatsApp shows literally.
    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
