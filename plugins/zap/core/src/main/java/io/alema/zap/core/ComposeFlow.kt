package io.alema.zap.core

/** Where a send ended up. Shared by the phone sender and the flow. */
enum class SendOutcome { SENT, OPENED_FOR_THUMB, WAITING_FOR_UNLOCK, NOT_INSTALLED, FAILED }

/** Why the flow stopped somewhere other than a sent message. */
enum class Note { NO_SPEECH, SPEECH_UNAVAILABLE, NO_CONTACT, NO_NUMBER, REPLY_ON_PHONE }

sealed interface Step {
    /** Home: "New message" first, then the conversations, newest first. */
    data class Inbox(val conversations: List<ZapConversation>) : Step

    /** One conversation open: its last messages, then Reply · Listen again (· See photo, when it has one). */
    data class Thread(val conversation: ZapConversation) : Step

    /** A photo from the conversation, full screen. Swipe for the others; back to the conversation. */
    data class Photo(val conversation: ZapConversation, val index: Int) : Step {
        val message: ZapMessage get() = conversation.photos[index]
    }

    /** "Say the name" first, then the people the wearer wrote to or heard from lately. */
    data class NewMessage(val recent: List<ZapContact>) : Step

    /**
     * Two separate questions, never one: with no recipient yet it listens for
     * the name only; with a recipient it listens for the message only.
     */
    data class Listening(val recipient: Recipient?, val partial: String = "") : Step

    /** The name fits more than one person, or fits one only loosely: the wearer picks, or says it again (last row). */
    data class Pick(val spoken: String, val choices: List<ZapContact>) : Step

    /** Send · Listen first · Say it again. */
    data class Review(val recipient: Recipient, val body: String) : Step

    data class Sending(val recipient: Recipient, val body: String) : Step

    data class Done(
        val recipient: Recipient?,
        val body: String?,
        val outcome: SendOutcome? = null,
        val note: Note? = null,
        val spoken: String? = null,
    ) : Step {
        /** Nothing reached anyone and asking again could fix it: the one row listens again. */
        val canRetry: Boolean get() = outcome == null && note in RETRYABLE
    }
}

private val RETRYABLE = setOf(Note.NO_SPEECH, Note.NO_CONTACT, Note.SPEECH_UNAVAILABLE)

sealed interface Effect {
    object StartListening : Effect
    object StopListening : Effect

    /** Read a draft back before sending: "To Bruno: on my way". */
    data class SpeakDraft(val recipient: Recipient, val body: String) : Effect

    /** Read a received message aloud: "Bruno: on my way". */
    data class SpeakMessage(val message: ZapMessage) : Effect
    object StopSpeaking : Effect

    /** Send through WhatsApp's live reply when [Recipient.conversationKey] still has one, else open the chat. */
    data class Send(val recipient: Recipient, val body: String) : Effect
    object Close : Effect
}

/**
 * Everything the wearer does in Zap on the glasses, as a pure state machine.
 * The plugin feeds it gestures, speech and fresh inbox data, draws [step] and
 * runs the returned [Effect]s. No Android here, so every path is unit-tested.
 *
 * Rules: the inbox is home; Reply is always offered for a chat Zap can reach
 * (live reply or a known number, the wearer never sees which); nothing is
 * sent without choosing Send on the review card.
 */
class ComposeFlow(
    private val directory: () -> List<ZapContact>,
    private val recentPeople: () -> List<ZapContact> = { emptyList() },
    private val defaultCountryCode: String = PhoneNumbers.BRAZIL,
) {
    var step: Step = Step.Inbox(emptyList())
        private set
    var selected: Int = 0
        private set

    private var conversations: List<ZapConversation> = emptyList()

    /** The browsing screen to return to after a reply or a new message. */
    private var base: Step = Step.Inbox(emptyList())

    fun open(inbox: List<ZapConversation>): List<Effect> {
        conversations = inbox
        go(Step.Inbox(inbox), asBase = true)
        return emptyList()
    }

    /** New notifications while Zap is open: refresh what is on screen without moving the wearer. */
    fun refresh(inbox: List<ZapConversation>) {
        conversations = inbox
        when (val s = step) {
            is Step.Inbox -> {
                val keep = selected
                step = Step.Inbox(inbox)
                selected = keep.coerceIn(0, maxOf(0, choiceCount() - 1))
                base = step
            }
            is Step.Thread -> inbox.firstOrNull { it.key == s.conversation.key }?.let {
                step = Step.Thread(it)
                base = step
            }
            is Step.Photo -> inbox.firstOrNull { it.key == s.conversation.key }?.let { fresh ->
                // Keep showing the same photo even if newer ones arrived.
                val index = fresh.photos.indexOfFirst { it.image == s.message.image }
                if (index >= 0) step = Step.Photo(fresh, index)
            }
            else -> Unit
        }
    }

    fun choiceCount(): Int = when (val s = step) {
        is Step.Inbox -> 1 + s.conversations.size
        is Step.Thread -> if (s.conversation.photos.isEmpty()) THREAD_CHOICES else THREAD_CHOICES + 1
        is Step.Photo -> s.conversation.photos.size
        is Step.NewMessage -> 1 + s.recent.size
        is Step.Pick -> s.choices.size + 1
        is Step.Review -> REVIEW_CHOICES
        is Step.Done -> 1
        is Step.Listening, is Step.Sending -> 0
    }

    fun move(delta: Int) {
        (step as? Step.Photo)?.let { s ->
            // Photos don't wrap around: the ends are where the conversation's photos end.
            val next = (s.index + delta).coerceIn(0, s.conversation.photos.size - 1)
            step = s.copy(index = next)
            return
        }
        val count = choiceCount()
        if (count > 0) selected = Math.floorMod(selected + delta, count)
    }

    fun tap(): List<Effect> = when (val s = step) {
        is Step.Inbox -> if (selected == 0) {
            go(Step.NewMessage(recentPeople()), asBase = true)
            emptyList()
        } else {
            go(Step.Thread(s.conversations[selected - 1]), asBase = true)
            emptyList()
        }
        is Step.Thread -> when (selected) {
            THREAD_REPLY -> reply(s.conversation)
            THREAD_PHOTO -> {
                go(Step.Photo(s.conversation, s.conversation.photos.size - 1))
                listOf(Effect.StopSpeaking)
            }
            else -> s.conversation.last?.let { listOf(Effect.SpeakMessage(it)) } ?: emptyList()
        }
        is Step.NewMessage -> if (selected == 0) {
            listen(null)
        } else {
            choose(s.recent[selected - 1])
        }
        is Step.Pick -> if (selected < s.choices.size) choose(s.choices[selected]) else listen(null)
        is Step.Review -> when (selected) {
            REVIEW_SEND -> send(s.recipient, s.body)
            REVIEW_LISTEN -> listOf(Effect.SpeakDraft(s.recipient, s.body))
            else -> listOf(Effect.StopSpeaking) + listen(s.recipient)
        }
        is Step.Photo -> {
            // Tapping a photo reads its caption, if any, like Listen again.
            if (s.message.text.isNotBlank()) listOf(Effect.SpeakMessage(s.message)) else emptyList()
        }
        is Step.Done -> if (s.canRetry) {
            listen(s.recipient)
        } else {
            go(Step.Inbox(conversations), asBase = true)
            emptyList()
        }
        is Step.Listening, is Step.Sending -> emptyList()
    }

    fun back(): List<Effect> = when (val s = step) {
        is Step.Inbox -> listOf(Effect.Close)
        is Step.Photo -> {
            step = Step.Thread(s.conversation)
            selected = THREAD_PHOTO
            listOf(Effect.StopSpeaking)
        }
        is Step.Thread, is Step.NewMessage -> {
            go(Step.Inbox(conversations), asBase = true)
            listOf(Effect.StopSpeaking)
        }
        is Step.Listening -> {
            go(base)
            listOf(Effect.StopListening)
        }
        is Step.Sending -> emptyList()
        is Step.Review -> {
            go(base)
            listOf(Effect.StopSpeaking)
        }
        is Step.Pick, is Step.Done -> {
            go(base)
            emptyList()
        }
    }

    fun onSpeechPartial(text: String) {
        val s = step as? Step.Listening ?: return
        step = s.copy(partial = text)
    }

    fun onSpeechFinal(text: String): List<Effect> {
        val s = step as? Step.Listening ?: return emptyList()
        if (text.isBlank()) return done(Step.Done(s.recipient, null, note = Note.NO_SPEECH))
        val recipient = s.recipient
        if (recipient != null) return review(recipient, ComposeParser.tidyBody(text))

        // Name step: only a name is expected; greetings and trailing words are tolerated.
        val matcher = ContactMatcher(RecentPeople.withDirectory(recentPeople(), directory(), defaultCountryCode))
        val (spoken, matches) = matcher.findName(text)
        return when {
            matches.isEmpty() -> done(Step.Done(null, null, note = Note.NO_CONTACT, spoken = text.trim()))
            // One person, clearly: straight to "say the message". Anything else is shown to compare.
            matches.size == 1 && matcher.isStrong(matches.first()) -> choose(matches.first().contact)
            else -> {
                go(Step.Pick(spoken, matches.map { it.contact }))
                emptyList()
            }
        }
    }

    fun onSpeechFailed(note: Note): List<Effect> {
        val s = step as? Step.Listening ?: return emptyList()
        return done(Step.Done(s.recipient, null, note = note))
    }

    fun onSendOutcome(outcome: SendOutcome) {
        when (val s = step) {
            is Step.Sending -> go(Step.Done(s.recipient, s.body, outcome = outcome))
            // A locked phone reports "waiting" first and "sent" once unlocked.
            is Step.Done -> if (s.outcome == SendOutcome.WAITING_FOR_UNLOCK) step = s.copy(outcome = outcome)
            else -> Unit
        }
    }

    /** Reply is one button; whether it goes through the live notification or a reopened chat is decided at send. */
    private fun reply(conversation: ZapConversation): List<Effect> {
        if (!conversation.canReply) {
            return done(Step.Done(conversation.recipient(), null, note = Note.REPLY_ON_PHONE))
        }
        return listOf(Effect.StopSpeaking) + listen(conversation.recipient())
    }

    private fun choose(contact: ZapContact): List<Effect> {
        val recipient = Recipient.of(contact, defaultCountryCode)
        // Without a number, only a chat whose WhatsApp reply is still live on the phone can be reached.
        val live = recipient.conversationKey?.let { key -> conversations.any { it.key == key && it.canReplyLive } } == true
        if (recipient.digits == null && !live) return done(Step.Done(recipient, null, note = Note.NO_NUMBER))
        return listen(recipient)
    }

    private fun listen(recipient: Recipient?): List<Effect> {
        go(Step.Listening(recipient))
        return listOf(Effect.StartListening)
    }

    private fun review(recipient: Recipient, body: String): List<Effect> {
        go(Step.Review(recipient, body))
        return emptyList()
    }

    private fun send(recipient: Recipient, body: String): List<Effect> {
        go(Step.Sending(recipient, body))
        return listOf(Effect.StopSpeaking, Effect.Send(recipient, body))
    }

    private fun done(next: Step.Done): List<Effect> {
        go(next)
        return emptyList()
    }

    private fun go(next: Step, asBase: Boolean = false) {
        step = next
        selected = 0
        if (asBase) base = next
    }

    companion object {
        const val THREAD_REPLY = 0
        const val THREAD_LISTEN = 1
        const val THREAD_PHOTO = 2
        private const val THREAD_CHOICES = 2
        const val REVIEW_SEND = 0
        const val REVIEW_LISTEN = 1
        const val REVIEW_REDO = 2
        private const val REVIEW_CHOICES = 3
    }
}
