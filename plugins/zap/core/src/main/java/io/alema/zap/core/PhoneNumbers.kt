package io.alema.zap.core

/**
 * Turns whatever the address book holds into the digits WhatsApp expects:
 * country code + area + number, no "+", no spaces.
 */
object PhoneNumbers {
    const val BRAZIL = "55"

    private val NON_DIGITS = Regex("\\D")

    /** "5511987654321@s.whatsapp.net" -> "5511987654321"; anything else -> null. */
    fun fromWhatsAppJid(jid: String): String? {
        val user = jid.substringBefore('@', missingDelimiterValue = "")
        if (!jid.endsWith("@s.whatsapp.net") || user.isEmpty() || !user.all(Char::isDigit)) return null
        return user.takeIf { it.length in 8..15 }
    }

    /**
     * Returns null when the number cannot be placed in a country (a local
     * number without area code): guessing would message a stranger.
     */
    fun toWhatsAppDigits(raw: String, defaultCountryCode: String = BRAZIL): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        var digits = trimmed.replace(NON_DIGITS, "")
        if (digits.isEmpty()) return null

        if (trimmed.startsWith("+")) return digits.takeIf { it.length in 8..15 }
        if (digits.startsWith("00")) return digits.drop(2).takeIf { it.length in 8..15 }

        if (defaultCountryCode == BRAZIL) {
            // Brazilian long-distance prefix: 0 + 2-digit carrier + area + number
            // ("0 15 11 98765-4321": 14 digits, 13 for a landline), or a bare
            // 0 + area + number ("011 98765-4321": 12, 11 for a landline).
            if (digits.startsWith("0")) {
                digits = when (digits.length) {
                    14, 13 -> digits.drop(3)
                    12, 11 -> digits.drop(1)
                    else -> return null
                }
            }
            return when (digits.length) {
                10, 11 -> BRAZIL + digits // area + number
                12, 13 -> digits.takeIf { it.startsWith(BRAZIL) } // already has 55
                else -> null // 8 or 9 digits: no area code, cannot place it
            }
        }

        if (digits.startsWith(defaultCountryCode) && digits.length > 10) return digits.takeIf { it.length <= 15 }
        return (defaultCountryCode + digits.trimStart('0')).takeIf { it.length in 8..15 }
    }
}
