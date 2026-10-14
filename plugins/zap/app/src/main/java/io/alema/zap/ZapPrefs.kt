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

    /** A short notice on the glasses when a WhatsApp message arrives. On by default. */
    fun announceArrivals(): Boolean = prefs.getBoolean(KEY_ANNOUNCE, true)

    fun setAnnounceArrivals(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCE, enabled).apply()
    }

    /** Off by default: the people list starts from who messaged you, no contacts permission. */
    fun importAddressBook(): Boolean = prefs.getBoolean(KEY_IMPORT, false)

    fun setImportAddressBook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT, enabled).apply()
    }

    private companion object {
        const val KEY_IMPORT = "import_address_book"
        const val KEY_ANNOUNCE = "announce_arrivals"
        const val KEY_KEEP_DAYS = "keep_days"
        const val KEY_MAX_CHATS = "max_conversations"
    }
}
