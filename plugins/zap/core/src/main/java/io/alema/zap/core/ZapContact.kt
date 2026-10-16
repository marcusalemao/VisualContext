package io.alema.zap.core

/**
 * A person the wearer can message.
 *
 * [waIds] are WhatsApp ids as digits (country code first, no "+"), taken from
 * the contact rows WhatsApp itself syncs into the phone's address book. They
 * are the most reliable target: WhatsApp already confirmed the number is on
 * WhatsApp. [phones] are the raw numbers from the address book, used only
 * when no WhatsApp id is known.
 */
data class ZapContact(
    val id: String,
    val name: String,
    val waIds: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val starred: Boolean = false,
    /** Last time the wearer messaged this person through Zap, epoch ms; 0 if never. */
    val lastUsedMs: Long = 0L,
    /** The inbox chat with this person, when there is one: lets Zap answer through it even without a number. */
    val conversationKey: String? = null,
) {
    val onWhatsApp: Boolean get() = waIds.isNotEmpty()

    /** The number Zap would open a chat with, or null when nothing usable is known. */
    fun target(defaultCountryCode: String = PhoneNumbers.BRAZIL): String? =
        waIds.firstOrNull() ?: phones.firstNotNullOfOrNull { PhoneNumbers.toWhatsAppDigits(it, defaultCountryCode) }
}
