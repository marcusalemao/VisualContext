package io.alema.zap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import io.alema.zap.core.PhoneNumbers
import io.alema.zap.core.ZapContact

/**
 * Reads the phone's address book into [ZapContact]s.
 *
 * WhatsApp syncs its own rows into the address book (account type
 * `com.whatsapp`), one per contact that is on WhatsApp, with the WhatsApp id
 * in `data1`. Reading those tells Zap who is reachable and with which number,
 * without touching WhatsApp's own storage. Plain phone numbers fill in for
 * contacts WhatsApp has not synced yet.
 */
internal class ZapContacts(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Everyone the wearer can message: people learned from WhatsApp
     * notifications, plus the address book when the wearer chose to import it.
     * The same person found both ways appears once, under the address-book name.
     */
    fun directory(): List<ZapContact> {
        val recents = ZapRecents(context)
        val learned = ZapPeople(context).all(recents)
        if (!ZapSettings(context).importAddressBook()) return learned
        val book = loadAddressBook()
        val known = book.flatMap { it.waIds }.toSet()
        return book + learned.filter { contact -> contact.waIds.none { it in known } }
    }

    fun loadAddressBook(): List<ZapContact> {
        if (!hasPermission()) return emptyList()
        val byId = linkedMapOf<String, Builder>()
        val resolver = context.contentResolver

        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.STARRED,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
            ),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val builder = byId.getOrPut(id) { Builder(id, name) }
                builder.starred = builder.starred || cursor.getInt(2) == 1
                val value = cursor.getString(4) ?: continue
                when (cursor.getString(3)) {
                    WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE ->
                        PhoneNumbers.fromWhatsAppJid(value)?.let(builder.waIds::add)
                    else -> builder.phones += value
                }
            }
        }

        val recents = ZapRecents(context)
        return byId.values.map { it.build(recents.lastUsed(it.id)) }
    }

    /** Recently messaged first, then starred, then everyone else on WhatsApp — the home list. */
    fun shortlist(all: List<ZapContact>, limit: Int): List<ZapContact> = all
        .filter { it.onWhatsApp || it.target() != null }
        .sortedWith(
            compareByDescending<ZapContact> { it.lastUsedMs }
                .thenByDescending { it.starred }
                .thenBy { it.name.lowercase() },
        )
        .filter { it.lastUsedMs > 0 || it.starred }
        .take(limit)

    private class Builder(val id: String, val name: String) {
        var starred = false
        val waIds = linkedSetOf<String>()
        val phones = mutableListOf<String>()
        fun build(lastUsed: Long) = ZapContact(id, name, waIds.toList(), phones.toList(), starred, lastUsed)
    }

    companion object {
        const val WHATSAPP_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        const val WHATSAPP_BUSINESS_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"
    }
}

/** Who the wearer messaged through Zap, and when. Ids and times only — never message text. */
internal class ZapRecents(context: Context) {
    private val prefs = context.getSharedPreferences("zap_recents", Context.MODE_PRIVATE)

    fun lastUsed(contactId: String): Long = prefs.getLong(contactId, 0L)

    fun touch(contactId: String, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(contactId, nowMs).apply()
        trim()
    }

    private fun trim() {
        val all = prefs.all.entries.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }
        if (all.size <= MAX) return
        val editor = prefs.edit()
        all.sortedBy { it.second }.take(all.size - MAX).forEach { editor.remove(it.first) }
        editor.apply()
    }

    private companion object {
        const val MAX = 30
    }
}
