package io.alema.zap

import android.content.Context

/**
 * Two timestamps that tell apart "Android never hands Zap a WhatsApp
 * notification" (access off, or restricted) from "it does, but Zap could not
 * read it" — the first question when the inbox stays empty.
 */
internal class ZapHealth(context: Context) {
    private val prefs = context.getSharedPreferences("zap_health", Context.MODE_PRIVATE)

    fun sawWhatsApp() = prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
    fun captured() = prefs.edit().putLong("captured", System.currentTimeMillis()).apply()
    fun lastSeen(): Long = prefs.getLong("seen", 0L)
    fun lastCaptured(): Long = prefs.getLong("captured", 0L)
    fun failed(reason: String) = prefs.edit().putString("error", reason.take(300)).apply()
    fun lastError(): String? = prefs.getString("error", null)

    /** How WhatsApp identified the last chat ("phone", "lid", "title"…): tells why a number is missing. */
    fun sawIdKind(kind: String) = prefs.edit().putString("id_kind", kind).apply()
    fun lastIdKind(): String? = prefs.getString("id_kind", null)
}

internal class ZapSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zap_settings", Context.MODE_PRIVATE)

    /** How long the inbox keeps messages on this phone. */
    fun keepDays(): Int = prefs.getInt(KEY_KEEP_DAYS, io.alema.zap.core.InboxRules.DEFAULT_KEEP_DAYS)

    fun setKeepDays(days: Int) {
        prefs.edit().putInt(KEY_KEEP_DAYS, days).apply()
    }

    /** How many conversations the inbox lists. */
    fun maxConversations(): Int = prefs.getInt(KEY_MAX_CHATS, io.alema.zap.core.InboxRules.DEFAULT_MAX_CONVERSATIONS)

    fun setMaxConversations(count: Int) {
        prefs.edit().putInt(KEY_MAX_CHATS, count).apply()
    }

    /**
     * What the glasses do when a WhatsApp message arrives while Zap is closed:
     * nothing, a short notice (default), or the whole message shown over a
     * dark backdrop and read aloud at once — for when messages can be urgent.
     */
    fun arrivalMode(): Int = prefs.getInt(
        KEY_ARRIVAL,
        // Before 0.4.2 this was an on/off switch.
        if (prefs.getBoolean(KEY_ANNOUNCE, true)) ARRIVAL_NOTICE else ARRIVAL_OFF,
    )

    fun setArrivalMode(mode: Int) {
        prefs.edit().putInt(KEY_ARRIVAL, mode.coerceIn(ARRIVAL_OFF, ARRIVAL_READ)).apply()
    }

    /** On by default: a photo taken with the glasses' button is offered for sending, once. */
    fun offerNewPhotos(): Boolean = prefs.getBoolean(KEY_OFFER_PHOTOS, true)

    fun setOfferNewPhotos(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFER_PHOTOS, enabled).apply()
    }

    /** Off by default: the people list starts from who messaged you, no contacts permission. */
    fun importAddressBook(): Boolean = prefs.getBoolean(KEY_IMPORT, false)

    fun setImportAddressBook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT, enabled).apply()
    }

    companion object {
        const val ARRIVAL_OFF = 0
        const val ARRIVAL_NOTICE = 1
        const val ARRIVAL_READ = 2
        private const val KEY_ARRIVAL = "arrival_mode"
        private const val KEY_IMPORT = "import_address_book"
        private const val KEY_OFFER_PHOTOS = "offer_new_photos"
        private const val KEY_ANNOUNCE = "announce_arrivals"
        private const val KEY_KEEP_DAYS = "keep_days"
        private const val KEY_MAX_CHATS = "max_conversations"
    }
}
