package io.alema.zap.core

/** One message as WhatsApp's notification showed it. */
data class ZapMessage(
    val sender: String,
    val text: String,
    val timeMs: Long,
    /** Key of a photo stored on the phone (encrypted), when the message carries one. */
    val image: String? = null,
) {
    companion object {
        /** "You" as a sender: WhatsApp leaves the sender empty on the wearer's own messages. */
        const val ME = ""

        /** Text for a photo sent without a caption. */
        const val PHOTO = "📷 Foto"
    }
}

/**
 * A conversation in the inbox, rebuilt from WhatsApp notifications.
 *
 * [key] is stable per chat (the WhatsApp id when known, otherwise the chat
 * title). [waDigits] is the person's number for one-to-one chats; groups have
 * none, so a group can only be answered while its notification is still live.
 */
data class ZapConversation(
    val key: String,
    val title: String,
    val waDigits: String?,
    val isGroup: Boolean,
    val messages: List<ZapMessage>,
    /** True while WhatsApp's own reply action for this chat is still alive on the phone. */
    val canReplyLive: Boolean = false,
) {
    val lastTimeMs: Long get() = messages.maxOfOrNull { it.timeMs } ?: 0L
    val last: ZapMessage? get() = messages.maxByOrNull { it.timeMs }

    /** Messages with a photo, oldest first. */
    val photos: List<ZapMessage> get() = messages.filter { it.image != null }

    /** Something to reply to by voice: live reply, or a number to open a fresh chat with. */
    val canReply: Boolean get() = canReplyLive || waDigits != null

    fun recipient(): Recipient = Recipient(name = title, digits = waDigits, conversationKey = key)
}

/** Who a message goes to: a person or chat, with whatever route is known. */
data class Recipient(
    val name: String,
    val digits: String?,
    val conversationKey: String? = null,
    val contactId: String? = null,
) {
    companion object {
        fun of(contact: ZapContact, defaultCountryCode: String = PhoneNumbers.BRAZIL) =
            Recipient(contact.name, contact.target(defaultCountryCode), contact.conversationKey, contact.id)
    }
}

/** Pure inbox bookkeeping: merging a fresh notification into what is stored, and forgetting the old. */
object InboxRules {
    const val DEFAULT_KEEP_DAYS = 7
    const val DEFAULT_MAX_CONVERSATIONS = 20
    const val MAX_MESSAGES = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * A notification shows only the latest few messages, so each one is
     * merged into the stored chat instead of replacing it. Repeats (the same
     * sender, text and minute) count once: WhatsApp reposts its notification
     * whenever it changes.
     */
    fun merge(stored: ZapConversation?, incoming: ZapConversation, maxMessages: Int = MAX_MESSAGES): ZapConversation {
        val all = (stored?.messages.orEmpty() + incoming.messages)
            .distinctBy { listOf(it.sender, it.text, it.timeMs / 60_000, it.image) }
            .sortedBy { it.timeMs }
            .takeLast(maxMessages)
        return incoming.copy(
            waDigits = incoming.waDigits ?: stored?.waDigits,
            messages = all,
        )
    }

    /** Newest first, nothing older than [keepDays], at most [maxConversations]. */
    fun prune(
        conversations: Collection<ZapConversation>,
        nowMs: Long,
        keepDays: Int = DEFAULT_KEEP_DAYS,
        maxConversations: Int = DEFAULT_MAX_CONVERSATIONS,
    ): List<ZapConversation> {
        val cutoff = nowMs - keepDays * DAY_MS
        return conversations
            .map { c -> c.copy(messages = c.messages.filter { it.timeMs >= cutoff }) }
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.lastTimeMs }
            .take(maxConversations)
    }
}
