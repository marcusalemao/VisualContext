package io.alema.zap.core

/**
 * The "recent" list on the New message card: strictly the people the wearer
 * exchanged messages with lately, newest first. No favourites, no frequency
 * ranking: a starred contact the wearer has not talked to in years must not
 * sit at the top.
 */
object RecentPeople {

    /**
     * [conversations] are the inbox chats (who wrote lately); [messaged] are
     * people with a known last-contact time (written to through Zap, or heard
     * from before the inbox forgot them). The same person found both ways
     * appears once, with the newest time and the inbox chat attached.
     */
    fun build(
        conversations: List<ZapConversation>,
        messaged: List<ZapContact>,
        limit: Int,
        defaultCountryCode: String = PhoneNumbers.BRAZIL,
    ): List<ZapContact> {
        val fromInbox = conversations
            .filter { !it.isGroup }
            .map { chat ->
                ZapContact(
                    id = chat.waDigits?.let { "wa:$it" } ?: "chat:${chat.key}",
                    name = chat.title,
                    waIds = listOfNotNull(chat.waDigits),
                    lastUsedMs = chat.lastTimeMs,
                    conversationKey = chat.key,
                )
            }
        val byPerson = linkedMapOf<String, ZapContact>()
        (fromInbox + messaged.filter { it.lastUsedMs > 0 }).forEach { contact ->
            val key = identity(contact, defaultCountryCode)
            val seen = byPerson[key]
            byPerson[key] = if (seen == null) contact else merge(seen, contact)
        }
        return byPerson.values.sortedByDescending { it.lastUsedMs }.take(limit)
    }

    /** Everyone Zap can find by name: recent people first, then the rest of the directory, each person once. */
    fun withDirectory(
        recent: List<ZapContact>,
        directory: List<ZapContact>,
        defaultCountryCode: String = PhoneNumbers.BRAZIL,
    ): List<ZapContact> {
        val byPerson = linkedMapOf<String, ZapContact>()
        (recent + directory).forEach { contact ->
            val key = identity(contact, defaultCountryCode)
            val seen = byPerson[key]
            byPerson[key] = if (seen == null) contact else merge(seen, contact)
        }
        return byPerson.values.toList()
    }

    private fun identity(contact: ZapContact, cc: String): String =
        contact.target(cc)?.let { "n:$it" } ?: "id:${contact.id}"

    /** Keep the first-seen name (inbox or address book), the newest time, and any route either side knows. */
    private fun merge(a: ZapContact, b: ZapContact) = a.copy(
        waIds = (a.waIds + b.waIds).distinct(),
        phones = (a.phones + b.phones).distinct(),
        lastUsedMs = maxOf(a.lastUsedMs, b.lastUsedMs),
        conversationKey = a.conversationKey ?: b.conversationKey,
        starred = a.starred || b.starred,
    )
}
