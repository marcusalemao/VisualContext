package io.alema.visualcontext

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import java.text.Normalizer

/**
 * Spec 5b: the imported face also goes to the phone's contacts (which usually sync to Google Contacts).
 * Only existing contacts, only when the full name matches exactly one contact, only contacts without a photo.
 * Never creates a contact. Blocking.
 */
object ContactPhotos {
    enum class Result { SET, HAS_PHOTO, SAME_NAME, NOT_FOUND, FAILED }

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    fun put(ctx: Context, name: String, jpeg: ByteArray): Result {
        val cr = ctx.contentResolver
        val want = norm(name)
        if (want.split(' ').size < 2) return Result.SAME_NAME // a first name alone is never enough
        val hits = mutableListOf<Pair<Long, Boolean>>() // contact id, has photo
        cr.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_ID), null, null, null)?.use { c ->
            while (c.moveToNext()) if (norm(c.getString(1) ?: "") == want) hits += c.getLong(0) to !c.isNull(2)
        }
        if (hits.isEmpty()) return Result.NOT_FOUND
        if (hits.size > 1) return Result.SAME_NAME
        val (cid, has) = hits[0]
        if (has) return Result.HAS_PHOTO
        // The raw contact to write to: prefer a Google account one, so the photo syncs.
        var raw = -1L
        cr.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(cid.toString()), null)?.use { c ->
            while (c.moveToNext()) { if (raw < 0 || c.getString(1) == "com.google") raw = c.getLong(0) }
        }
        if (raw < 0) return Result.FAILED
        return try {
            val ops = arrayListOf(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, raw)
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, jpeg).build())
            cr.applyBatch(ContactsContract.AUTHORITY, ops)
            Result.SET
        } catch (_: Exception) { Result.FAILED }
    }
}
