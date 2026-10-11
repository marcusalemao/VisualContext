package io.alema.qrcore

/** What a decoded QR string means. Pure data: no Android, no I/O. */
sealed interface QrResult {

    data class Wifi(
        val ssid: String,
        val password: String?,
        val security: Security,
        val hidden: Boolean,
    ) : QrResult {
        enum class Security { OPEN, WEP, WPA, SAE, EAP }
    }

    data class Link(val url: String, val host: String) : QrResult

    /**
     * A Pix BR Code. [merchantName] and [merchantCity] are typed by whoever made the code
     * (static codes), so the UI must present them as "the code says", never as verified.
     */
    data class Pix(
        val key: String?,
        val dynamicUrl: String?,
        val amount: String?,
        val merchantName: String?,
        val merchantCity: String?,
        val txid: String?,
        val description: String?,
        val raw: String,
    ) : QrResult {
        val isDynamic: Boolean get() = dynamicUrl != null
    }

    data class Contact(
        val name: String?,
        val phones: List<String>,
        val emails: List<String>,
        val org: String?,
        val title: String?,
        val urls: List<String>,
        val source: Source,
    ) : QrResult {
        enum class Source { VCARD, MECARD, LINKEDIN, WHATSAPP }
    }

    data class Text(val text: String) : QrResult

    /** Looked like a known type but failed validation (bad Pix CRC, Wi-Fi without SSID…). */
    data class Invalid(val kind: String, val reason: String, val raw: String) : QrResult
}
