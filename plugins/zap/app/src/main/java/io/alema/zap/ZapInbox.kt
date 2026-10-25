package io.alema.zap

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.service.notification.StatusBarNotification
import android.util.Log
import io.alema.zap.core.InboxRules
import io.alema.zap.core.PhoneNumbers
import io.alema.zap.core.WhatsAppLink
import io.alema.zap.core.ZapConversation
import io.alema.zap.core.ZapMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The WhatsApp inbox, rebuilt from WhatsApp's own notifications.
 *
 * Messages are kept on this phone only, encrypted with a key that lives in
 * the Android Keystore (it never leaves the phone and is not in backups), for
 * as many days as the wearer chose (7 by default). WhatsApp's live reply
 * actions are kept in memory only: they cannot be stored, and they die when
 * the notification goes away — then Reply falls back to reopening the chat.
 */
internal object ZapInbox {
    private const val TAG = "ZapInbox"
    private const val FILE = "inbox.bin"
    private const val KEY_ALIAS = "zap_inbox"
    private const val FRESH_MS = 2 * 60_000L

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var loaded = false
    private val chats = linkedMapOf<String, ZapConversation>()
    private val liveReplies = mutableMapOf<String, Notification.Action>()
    private val listeners = mutableSetOf<() -> Unit>()

    /** "You" as a sender: WhatsApp leaves the sender empty on the wearer's own messages. */
    const val ME = ZapMessage.ME

    /** Text for a photo sent without a caption. */
    const val PHOTO = ZapMessage.PHOTO

    private val NOT_CHATS = setOf(
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_STATUS,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL,
    )

    /** A chat keyed by the app's own name has no WhatsApp id: it is a status line, not a person. */
    private val APP_NAMES = setOf("WhatsApp", "WhatsApp Business")

    fun list(context: Context): List<ZapConversation> = synchronized(lock) {
        ensureLoaded(context)
        val settings = ZapSettings(context)
        InboxRules.prune(chats.values, System.currentTimeMillis(), settings.keepDays(), settings.maxConversations())
            .map { it.copy(canReplyLive = liveReplies.containsKey(it.key)) }
    }

    fun observe(listener: () -> Unit): () -> Unit {
        synchronized(lock) { listeners += listener }
        return { synchronized(lock) { listeners -= listener } }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            chats.clear()
            liveReplies.clear()
            File(context.filesDir, FILE).delete()
            ZapPhotos.clear(context)
        }
        notifyChanged()
    }

    // --- capture --------------------------------------------------------------

    /** What a notification changed: the chat, and the newest message from someone else if it is new. */
    data class Captured(val chat: ZapConversation, val fresh: ZapMessage?)

    /** Called for every WhatsApp notification. Returns what it updated, if anything. */
    fun capture(context: Context, sbn: StatusBarNotification): Captured? {
        val parsed = parse(context, sbn) ?: return null
        // No number in WhatsApp's id (private "@lid" ids): ask the notification's sender link and the address book.
        val incoming = if (parsed.isGroup || parsed.waDigits != null) parsed else {
            parsed.copy(waDigits = ZapContacts(context).digitsFor(senderUri(sbn.notification.extras), parsed.title))
        }
        val action = replyAction(sbn.notification)
        var fresh: ZapMessage? = null
        synchronized(lock) {
            ensureLoaded(context)
            val before = chats[incoming.key]?.lastTimeMs ?: 0L
            // "New" = from someone else, newer than what was stored, and recent:
            // a listener reconnect replays old notifications that must not ring again.
            fresh = incoming.messages
                .filter { it.sender != ME && it.timeMs > before && System.currentTimeMillis() - it.timeMs < FRESH_MS }
                .maxByOrNull { it.timeMs }
            val merged = InboxRules.merge(chats[incoming.key], incoming)
            chats.remove(incoming.key)
            chats[incoming.key] = merged
            if (action != null) liveReplies[incoming.key] = action
            save(context)
        }
        notifyChanged()
        return Captured(incoming, fresh)
    }

    /** The notification is gone (read on the phone, dismissed): Reply falls back to reopening the chat. */
    fun released(context: Context, sbn: StatusBarNotification) {
        val key = keyOf(sbn) ?: return
        val removed = synchronized(lock) { liveReplies.remove(key) != null }
        // WhatsApp clears its notification once the chat is read on the phone: read here too.
        val read = markSeen(context, key, notify = false)
        if (removed || read) notifyChanged()
    }

    /** The wearer saw this chat (opened it in Zap): it stops counting as unread. */
    fun markRead(context: Context, key: String) {
        if (markSeen(context, key, notify = false)) notifyChanged()
    }

    private fun markSeen(context: Context, key: String, notify: Boolean): Boolean {
        val changed = synchronized(lock) {
            ensureLoaded(context)
            val chat = chats[key] ?: return@synchronized false
            val seen = chat.seen()
            if (seen === chat) return@synchronized false
            chats[key] = seen
            save(context)
            true
        }
        if (changed && notify) notifyChanged()
        return changed
    }

    /** Listener lost: every live reply token is unusable now. */
    fun releaseAll() {
        synchronized(lock) { liveReplies.clear() }
        notifyChanged()
    }

    // --- replying -------------------------------------------------------------

    /**
     * Sends [text] through WhatsApp's own reply action for [key], exactly like
     * replying from the notification shade. Returns false when no live action
     * exists (the caller then reopens the chat instead).
     */
    /** WhatsApp's own reply action for this chat is still alive on the phone. */
    fun canReplyLive(key: String): Boolean = synchronized(lock) { liveReplies.containsKey(key) }

    fun replyLive(context: Context, key: String, text: String): Boolean {
        val action = synchronized(lock) { liveReplies[key] } ?: return false
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput } ?: return false
        if (inputs.isEmpty()) return false
        val intent = Intent()
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, results)
        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            action.actionIntent.send(context, 0, intent)
            synchronized(lock) {
                chats[key]?.let { chat ->
                    chats[key] = InboxRules.merge(chat, chat.copy(messages = listOf(ZapMessage(ME, text, System.currentTimeMillis())))).seen()
                }
                save(context)
            }
            notifyChanged()
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "live reply cancelled", e)
            synchronized(lock) { liveReplies.remove(key) }
            false
        }
    }

    /** After a reply sent by reopening the chat, show it in the thread too. */
    fun recordSent(context: Context, key: String?, text: String, title: String? = null, digits: String? = null) {
        key ?: return
        synchronized(lock) {
            ensureLoaded(context)
            // A first message to someone who never wrote: the chat starts here, keyed like WhatsApp's own (the number).
            val chat = chats[key] ?: title?.let { ZapConversation(key, it, digits, isGroup = false, messages = emptyList()) } ?: return
            chats[key] = InboxRules.merge(chat, chat.copy(messages = listOf(ZapMessage(ME, text, System.currentTimeMillis())))).seen()
            save(context)
        }
        notifyChanged()
    }

    // --- parsing WhatsApp notifications ----------------------------------------

    private fun isWhatsApp(pkg: String) = pkg == WhatsAppLink.PACKAGE || pkg == WhatsAppLink.BUSINESS_PACKAGE

    private fun keyOf(sbn: StatusBarNotification): String? {
        if (!isWhatsApp(sbn.packageName)) return null
        val n = sbn.notification ?: return null
        val shortcut = n.shortcutId
        return shortcut?.let { PhoneNumbers.fromWhatsAppJid(it) ?: it }
            ?: titleOf(n)
    }

    private fun titleOf(n: Notification): String? {
        val extras = n.extras ?: return null
        val raw = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return null
        return ZapPeople.cleanName(raw).ifBlank { null }
    }

    private fun parse(context: Context, sbn: StatusBarNotification): ZapConversation? {
        if (!isWhatsApp(sbn.packageName)) return null
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        // WhatsApp's own status lines ("Checking for new messages", backups, calls in
        // progress) are ongoing/service notifications, never a chat.
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null
        if (n.category in NOT_CHATS) return null
        val extras = n.extras ?: return null
        val key = keyOf(sbn) ?: return null
        val title = titleOf(n) ?: return null
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            n.shortcutId?.endsWith("@g.us") == true
        val digits = if (isGroup) null else n.shortcutId?.let(PhoneNumbers::fromWhatsAppJid)

        var messages = messagingStyle(context, extras, fallbackTimeMs = sbn.postTime)
            .ifEmpty {
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (text.isEmpty()) emptyList() else listOf(ZapMessage(title, text, sbn.postTime))
            }
        // Some builds attach the photo as a big picture instead: it belongs to the newest message.
        if (messages.isNotEmpty() && messages.none { it.image != null }) {
            ZapPhotos.fromBigPicture(context, extras)?.let { key ->
                val newest = messages.maxBy { it.timeMs }
                messages = messages.map { if (it === newest) it.copy(image = key) else it }
            }
        }
        if (messages.isEmpty()) return null
        return ZapConversation(key, title, digits, isGroup, messages)
    }

    /** MessagingStyle messages, read from the raw bundles so no support library is needed. */
    private fun messagingStyle(context: Context, extras: Bundle, fallbackTimeMs: Long): List<ZapMessage> {
        @Suppress("DEPRECATION")
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return bundles.mapNotNull { item ->
            val b = item as? Bundle ?: return@mapNotNull null
            val image = ZapPhotos.fromMessage(context, b)
            val text = b.getCharSequence("text")?.toString()?.trim().orEmpty().ifEmpty { if (image != null) PHOTO else "" }
            if (text.isEmpty()) return@mapNotNull null
            @Suppress("DEPRECATION")
            val person = b.getParcelable<Person>("sender_person")
            val sender = person?.name?.toString() ?: b.getCharSequence("sender")?.toString()
            ZapMessage(
                // No sender at all means the wearer wrote it.
                sender = sender?.let(ZapPeople::cleanName)?.ifBlank { null } ?: ME,
                text = text,
                // A missing time would read as 1970 and be pruned at once.
                timeMs = b.getLong("time").takeIf { it > 0 } ?: fallbackTimeMs,
                image = image,
            )
        }
    }

    /** The sender's link, when WhatsApp attaches one ("tel:…" or an address-book contact). */
    private fun senderUri(extras: Bundle?): String? {
        extras ?: return null
        @Suppress("DEPRECATION")
        val people = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST).orEmpty()
        people.firstNotNullOfOrNull { it.uri }?.let { return it }
        @Suppress("DEPRECATION")
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        return bundles.asList().asReversed().firstNotNullOfOrNull { item ->
            @Suppress("DEPRECATION")
            (item as? Bundle)?.getParcelable<Person>("sender_person")?.uri
        }
    }

    private fun replyAction(n: Notification): Notification.Action? = n.actions?.firstOrNull { action ->
        action.actionIntent != null && action.remoteInputs?.any { it.allowFreeFormInput } == true
    }

    // --- storage, encrypted with a Keystore key ---------------------------------

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return
        runCatching {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            for (i in 0 until json.length()) fromJson(json.getJSONObject(i))?.let { chats[it.key] = it }
            // Before 0.5.1 WhatsApp's "Checking for new messages" could land as a chat named after the app.
            APP_NAMES.forEach { chats.remove(it) }
        }.onFailure { Log.w(TAG, "inbox unreadable, starting empty", it) }
    }

    private fun save(context: Context) {
        val settings = ZapSettings(context)
        val kept = InboxRules.prune(chats.values, System.currentTimeMillis(), settings.keepDays(), settings.maxConversations())
        chats.clear()
        kept.asReversed().forEach { chats[it.key] = it }
        ZapPhotos.keepOnly(context, kept.flatMap { c -> c.messages.mapNotNull { it.image } }.toSet())
        runCatching {
            val plain = JSONArray().apply { kept.forEach { put(toJson(it)) } }.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val out = cipher.iv + cipher.doFinal(plain)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeBytes(out)
            tmp.renameTo(File(context.filesDir, FILE))
        }.onFailure { Log.w(TAG, "could not save inbox", it) }
    }

    /** The inbox key (Android Keystore). Also encrypts stored photos. */
    internal fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun toJson(c: ZapConversation) = JSONObject()
        .put("key", c.key).put("title", c.title).put("digits", c.waDigits ?: JSONObject.NULL).put("group", c.isGroup)
        .put("read", c.readUpToMs)
        .put("messages", JSONArray().apply {
            c.messages.forEach { m ->
                put(JSONObject().put("s", m.sender).put("t", m.text).put("at", m.timeMs).apply { m.image?.let { put("img", it) } })
            }
        })

    private fun fromJson(o: JSONObject): ZapConversation? = runCatching {
        val messages = o.getJSONArray("messages").let { a ->
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let { ZapMessage(it.optString("s"), it.optString("t"), it.optLong("at"), it.optString("img").ifEmpty { null }) }
            }
        }
        ZapConversation(
            key = o.getString("key"),
            title = o.getString("title"),
            waDigits = if (o.isNull("digits")) null else o.optString("digits"),
            isGroup = o.optBoolean("group"),
            messages = messages,
            // Chats stored before 0.12 had no read mark: count them as read, not as a wall of unread.
            readUpToMs = if (o.has("read")) o.optLong("read") else messages.maxOfOrNull { it.timeMs } ?: 0L,
        )
    }.getOrNull()

    private fun notifyChanged() {
        val snapshot = synchronized(lock) { listeners.toList() }
        main.post { snapshot.forEach { it() } }
    }
}
