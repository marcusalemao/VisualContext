package io.alema.zap.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What Zap offers to an assistant through Nexus Skills: the wearer's own AI
 * (Anezium's Assistant, with the wearer's key) asks, Zap answers from the inbox
 * on the phone. Zap needs no AI of its own for this.
 *
 * Pure Kotlin: answers are plain maps and lists (JSON-shaped) that the plugin
 * turns into the SDK's types, so every rule here is unit-tested.
 *
 * Sending follows Zap's one rule: nothing goes without the wearer choosing
 * Send. The first [SEND] call only returns a "Send" choice; the assistant shows
 * it as a chip on the glasses, and only a second call carrying that choice
 * (signed, for this exact text and chat, valid a few minutes) sends.
 */
class ZapSkills(
    private val inbox: () -> List<ZapConversation>,
    private val directory: () -> List<ZapContact> = { emptyList() },
    private val secret: ByteArray,
    private val text: ZapText = ZapText(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val defaultCountryCode: String = PhoneNumbers.BRAZIL,
) {
    /** An answer for one invocation. */
    sealed interface Answer {
        data class Done(val data: Map<String, Any?>) : Answer

        /** Nothing happened; the wearer picks one (a person, or Send). */
        data class Choose(val reason: String, val prompt: String, val choices: List<Choice>) : Answer

        data class Failed(val code: String) : Answer

        /** Confirmed by the wearer: the plugin sends, then reports what happened. */
        data class Send(val recipient: Recipient, val body: String) : Answer
    }

    data class Choice(val label: String, val detail: String?, val refType: String, val ref: String)

    fun run(operation: String, args: Map<String, Any?>): Answer = when (operation) {
        LIST_CHATS -> listChats(args.str("filter"))
        READ_CHAT -> readChat(args)
        RECENT -> recent((args["hours"] as? Number)?.toInt() ?: DEFAULT_HOURS)
        SEND -> send(args)
        else -> Answer.Failed(UNSUPPORTED)
    }

    // --- reads ---------------------------------------------------------------

    private fun listChats(filter: String?): Answer {
        val all = inbox().sortedByDescending { it.lastTimeMs }
        val kept = if (filter.isNullOrBlank()) all else {
            val wanted = Text.tokens(filter)
            all.filter { chat -> Text.tokens(chat.title).let { words -> wanted.all { w -> words.any { it.startsWith(w) } } } }
        }
        val today = today()
        return Answer.Done(
            mapOf(
                "chats" to kept.take(MAX_CHATS).map { chat ->
                    val last = chat.last
                    mapOf(
                        "chat" to chat.key,
                        "name" to chat.title.take(80),
                        "group" to chat.isGroup,
                        "last_from" to last?.let { who(it) }?.take(80),
                        "last_text" to last?.let { said(it) }?.take(160),
                        "last_time" to last?.let { clock(it.timeMs, today) },
                        "minutes_ago" to last?.let { minutesAgo(it.timeMs) },
                        "can_reply" to chat.canReply,
                    ).filterValues { it != null }
                },
                "total" to kept.size.coerceAtMost(1000),
            ),
        )
    }

    private fun readChat(args: Map<String, Any?>): Answer {
        val chat = when (val found = findChat(args)) {
            is Found.One -> found.chat
            is Found.Many -> return Answer.Choose(AMBIGUOUS, text.skillWhichChat, found.chats.map(::chatChoice))
            Found.None -> return Answer.Failed(NO_CHAT)
            Found.Missing -> return Answer.Failed(INVALID_ARGUMENTS)
        }
        val count = ((args["count"] as? Number)?.toInt() ?: DEFAULT_READ).coerceIn(1, MAX_READ)
        val today = today()
        // Newest messages that fit, then oldest first so the model reads them in order.
        var chars = 0
        val picked = ArrayList<ZapMessage>()
        for (message in chat.messages.sortedByDescending { it.timeMs }) {
            if (picked.size >= count) break
            val length = said(message).length.coerceAtMost(MAX_MESSAGE_CHARS)
            if (chars + length > MAX_READ_CHARS && picked.isNotEmpty()) break
            chars += length
            picked += message
        }
        return Answer.Done(
            mapOf(
                "chat" to chat.key,
                "name" to chat.title.take(80),
                "group" to chat.isGroup,
                "messages" to picked.reversed().map { message ->
                    mapOf(
                        "from" to who(message).take(80),
                        "time" to clock(message.timeMs, today),
                        "text" to said(message).take(MAX_MESSAGE_CHARS),
                        "photo" to (message.image != null),
                    )
                },
                "older_left_out" to (picked.size < chat.messages.size),
            ),
        )
    }

    private fun recent(hours: Int): Answer {
        val since = now() - hours.coerceIn(1, MAX_HOURS) * HOUR_MS
        val today = today()
        val all = inbox().flatMap { chat ->
            chat.messages.filter { it.timeMs >= since && it.sender != ZapMessage.ME }.map { chat to it }
        }.sortedByDescending { it.second.timeMs }
        return Answer.Done(
            mapOf(
                "messages" to all.take(MAX_RECENT).map { (chat, message) ->
                    mapOf(
                        "chat" to chat.key,
                        "name" to chat.title.take(80),
                        "from" to who(message).take(80),
                        "time" to clock(message.timeMs, today),
                        "text" to said(message).take(MAX_RECENT_CHARS),
                    )
                },
                "truncated" to (all.size > MAX_RECENT),
            ),
        )
    }

    // --- send ----------------------------------------------------------------

    private fun send(args: Map<String, Any?>): Answer {
        val body = args.str("text")?.trim()?.take(MAX_SEND_CHARS)
        if (body.isNullOrEmpty()) return Answer.Failed(INVALID_ARGUMENTS)
        val recipient = when (val target = findTarget(args)) {
            is Target.One -> target.recipient
            is Target.Many -> return Answer.Choose(AMBIGUOUS, text.skillWhoToSend, target.choices)
            Target.None -> return Answer.Failed(NO_CHAT)
            Target.Missing -> return Answer.Failed(INVALID_ARGUMENTS)
        }
        val live = recipient.conversationKey?.let { key -> inbox().any { it.key == key && it.canReplyLive } } == true
        if (recipient.digits == null && !live) return Answer.Failed(REPLY_ON_PHONE)

        val confirm = args.str("confirm")
        if (confirm == null) {
            return Answer.Choose(
                CONFIRM,
                "${text.to(recipient.name)}: “${body.take(100)}”".take(MAX_PROMPT),
                listOf(Choice(text.send, body.take(MAX_DETAIL), CONFIRM_REF, token(recipient, body))),
            )
        }
        if (!valid(confirm, recipient, body)) return Answer.Failed(STALE_REFERENCE)
        return Answer.Send(recipient, body)
    }

    /** A signed "Send" for exactly this chat and text, valid [CONFIRM_TTL_MS]. Nothing stored. */
    fun token(recipient: Recipient, body: String): String {
        val expires = now() + CONFIRM_TTL_MS
        return "$expires.${mac("$expires|${routeOf(recipient)}|$body")}"
    }

    private fun valid(token: String, recipient: Recipient, body: String): Boolean {
        val expires = token.substringBefore('.').toLongOrNull() ?: return false
        if (expires < now() || expires > now() + CONFIRM_TTL_MS) return false
        return constantEquals(token.substringAfter('.'), mac("$expires|${routeOf(recipient)}|$body"))
    }

    private fun routeOf(recipient: Recipient) = recipient.conversationKey ?: recipient.digits ?: recipient.name

    private fun mac(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(value.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    private fun constantEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    // --- finding who ---------------------------------------------------------

    private sealed interface Found {
        data class One(val chat: ZapConversation) : Found
        data class Many(val chats: List<ZapConversation>) : Found
        object None : Found
        object Missing : Found
    }

    private fun findChat(args: Map<String, Any?>): Found {
        val chats = inbox()
        args.str("chat")?.let { key -> return chats.firstOrNull { it.key == key }?.let { Found.One(it) } ?: Found.None }
        val name = args.str("name") ?: return Found.Missing
        val asContacts = chats.map { ZapContact(id = it.key, name = it.title, conversationKey = it.key) }
        val matcher = ContactMatcher(asContacts)
        val matches = matcher.match(name, SkillChoices)
        if (matches.isEmpty()) return Found.None
        if (matches.size == 1 || matcher.isConfident(matches)) {
            return Found.One(chats.first { it.key == matches.first().contact.id })
        }
        return Found.Many(matches.map { m -> chats.first { it.key == m.contact.id } })
    }

    private sealed interface Target {
        data class One(val recipient: Recipient) : Target
        data class Many(val choices: List<Choice>) : Target
        object None : Target
        object Missing : Target
    }

    /** A chat from the inbox, or anyone Zap can reach (people learned, imports, address book when allowed). */
    private fun findTarget(args: Map<String, Any?>): Target {
        val chats = inbox()
        args.str("chat")?.let { key ->
            return chats.firstOrNull { it.key == key }?.let { Target.One(it.recipient()) } ?: Target.None
        }
        args.str("contact")?.let { id ->
            return directory().firstOrNull { it.id == id }?.let { Target.One(Recipient.of(it, defaultCountryCode)) } ?: Target.None
        }
        val name = args.str("name") ?: return Target.Missing
        val people = RecentPeople.withDirectory(
            chats.map { ZapContact(id = CHAT_PREFIX + it.key, name = it.title, waIds = listOfNotNull(it.waDigits), conversationKey = it.key) },
            directory(),
            defaultCountryCode,
        )
        val matcher = ContactMatcher(people)
        val matches = matcher.match(name, SkillChoices)
        if (matches.isEmpty()) return Target.None
        fun recipientOf(contact: ZapContact): Recipient =
            contact.conversationKey?.let { key -> chats.firstOrNull { it.key == key }?.recipient() }
                ?: Recipient.of(contact, defaultCountryCode)
        if (matches.size == 1 || matcher.isConfident(matches)) return Target.One(recipientOf(matches.first().contact))
        return Target.Many(
            matches.map { m ->
                val contact = m.contact
                val chatKey = contact.conversationKey
                if (chatKey != null) {
                    Choice(contact.name.take(MAX_LABEL), text.numberHint(contact.target(defaultCountryCode)), CHAT_REF, chatKey)
                } else {
                    Choice(contact.name.take(MAX_LABEL), text.numberHint(contact.target(defaultCountryCode)), CONTACT_REF, contact.id)
                }
            },
        )
    }

    private fun chatChoice(chat: ZapConversation) =
        Choice(chat.title.take(MAX_LABEL), chat.last?.let { said(it) }?.take(MAX_DETAIL), CHAT_REF, chat.key)

    // --- wording -------------------------------------------------------------

    private fun who(message: ZapMessage) = if (message.sender == ZapMessage.ME) text.me else message.sender

    /** What was said, with a photo in words (an assistant reading "📷 Foto" aloud says the emoji). */
    private fun said(message: ZapMessage): String = when {
        message.image != null && (message.text.isBlank() || message.text == ZapMessage.PHOTO) -> "[${text.photoSaid}]"
        message.image != null -> "[${text.photoSaid}] ${message.text}"
        else -> message.text
    }

    private fun today(): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(now()), zone)

    private fun clock(timeMs: Long, today: LocalDate): String {
        val at = Instant.ofEpochMilli(timeMs).atZone(zone)
        return if (at.toLocalDate() == today) at.format(HOUR) else at.format(DAY_HOUR)
    }

    private fun minutesAgo(timeMs: Long) = ((now() - timeMs) / 60_000).toInt().coerceIn(0, MAX_MINUTES)

    private fun Map<String, Any?>.str(key: String) = (this[key] as? String)?.takeIf { it.isNotBlank() }

    companion object {
        const val LIST_CHATS = "list_chats"
        const val READ_CHAT = "read_chat"
        const val RECENT = "recent_messages"
        const val SEND = "send_message"

        const val CHAT_REF = "chat"
        const val CONTACT_REF = "contact"
        const val CONFIRM_REF = "send_confirmation"

        // Hub error codes, and Zap's own (lowercase, matching the hub's provider-code rule).
        const val UNSUPPORTED = "unsupported_operation"
        const val INVALID_ARGUMENTS = "invalid_arguments"
        const val STALE_REFERENCE = "stale_reference"
        const val NO_CHAT = "no_such_chat"
        const val REPLY_ON_PHONE = "reply_on_phone_only"

        const val AMBIGUOUS = "ambiguous_name"
        const val CONFIRM = "confirm_send"

        const val CONFIRM_TTL_MS = 5 * 60_000L
        const val DEFAULT_HOURS = 12
        const val MAX_HOURS = 48
        const val MAX_CHATS = 12
        const val DEFAULT_READ = 15
        const val MAX_READ = 30
        const val MAX_MESSAGE_CHARS = 800
        const val MAX_READ_CHARS = 9_000
        const val MAX_RECENT = 30
        const val MAX_RECENT_CHARS = 300
        const val MAX_SEND_CHARS = 1_000
        const val MAX_MINUTES = 20_160
        const val MAX_PROMPT = 160
        const val MAX_LABEL = 60
        const val MAX_DETAIL = 80
        private const val SkillChoices = 5
        private const val CHAT_PREFIX = "chat:"
        private const val HOUR_MS = 60 * 60_000L
        private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_HOUR = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    }
}
