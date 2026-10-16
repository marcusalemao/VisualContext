package io.alema.zap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
     * notifications, an imported contacts file, and the address book when
     * the wearer turned it on.
     * The same person found both ways appears once, under the address-book name.
     */
    fun directory(): List<ZapContact> {
        val recents = ZapRecents(context)
        val learned = ZapPeople(context).all(recents)
        val imported = ZapImported(context).all(recents)
        val book = if (ZapSettings(context).importAddressBook()) loadAddressBook() else emptyList()
        // Address book first (its names are the wearer's own), then people learned, then imports; each number once.
        val known = mutableSetOf<String>()
        return (book + learned + imported).filter { contact ->
            val ids = contact.waIds.ifEmpty { listOfNotNull(contact.target()) }
            if (ids.any { it in known }) false else { known += ids; true }
        }
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

    /**
     * The number behind a chat WhatsApp identified without one (newer
     * WhatsApp builds use private "@lid" ids). Tries the sender's own link
     * in the notification, then — only with contacts permission — the one
     * address-book contact with exactly this name. Never guesses between two.
     */
    fun digitsFor(personUri: String?, name: String): String? {
        if (personUri != null && personUri.startsWith("tel:")) {
            PhoneNumbers.toWhatsAppDigits(Uri.decode(personUri.removePrefix("tel:")))?.let { return it }
        }
        if (!hasPermission()) return null
        val resolver = context.contentResolver
        val contactId: String? = personUri?.takeIf { it.startsWith("content://") }?.let { uri ->
            runCatching {
                resolver.query(Uri.parse(uri), arrayOf(ContactsContract.Contacts._ID), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
        }
        val (selection, args) = if (contactId != null) {
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)" to
                arrayOf(contactId, WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        } else {
            "${ContactsContract.Data.DISPLAY_NAME} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)" to
                arrayOf(name, WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        }
        val found = mutableMapOf<String, MutableList<String>>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                selection, args, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val value = c.getString(2) ?: continue
                    val digits = when (c.getString(1)) {
                        WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE -> PhoneNumbers.fromWhatsAppJid(value)
                        else -> PhoneNumbers.toWhatsAppDigits(value)
                    } ?: continue
                    // WhatsApp's own row first: it is a number WhatsApp confirmed.
                    val list = found.getOrPut(id) { mutableListOf() }
                    if (c.getString(1) == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) list += digits else list.add(0, digits)
                }
            }
        }
        return found.values.singleOrNull()?.firstOrNull()
    }

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
