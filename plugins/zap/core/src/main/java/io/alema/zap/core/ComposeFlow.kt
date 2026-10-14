package io.alema.zap.core

/** Where a send ended up. Shared by the phone sender and the flow. */
enum class SendOutcome { SENT, OPENED_FOR_THUMB, WAITING_FOR_UNLOCK, NOT_INSTALLED, FAILED }

/** Why the flow stopped somewhere other than a sent message. */
enum class Note { NO_SPEECH, SPEECH_UNAVAILABLE, NO_CONTACT, NO_NUMBER, REPLY_ON_PHONE, NO_PHOTOS, CAMERA_FAILED }

sealed interface Step {
    /** Home: "New message" first, then the conversations, newest first. */
    data class Inbox(val conversations: List<ZapConversation>) : Step

    /**
     * One conversation open. [reading]: the whole chat as a document the wearer
     * scrolls, every message in full. A tap brings up the choices instead:
     * Reply · Listen again · Send a photo or video (· See photos, when it has some). Back from the
     * choices returns to reading; back from reading returns home.
     */
    data class Thread(val conversation: ZapConversation, val reading: Boolean = true) : Step

    /** A photo from the conversation, full screen. Swipe for the others; back to the conversation. */
    data class Photo(val conversation: ZapConversation, val index: Int) : Step {
        val message: ZapMessage get() = conversation.photos[index]
    }

    /**
     * Pick a photo from the phone to send: newest first, full screen, swipe
     * for older ones, tap to attach. [body] and [previousPhoto] come along from
     * a review card, so back can return to it unchanged.
     */
    data class ChoosePhoto(
        val recipient: Recipient,
        val photos: List<String>,
        val index: Int = 0,
        val body: String = "",
        val previousPhoto: String? = null,
    ) : Step {
        val photo: String get() = photos[index]
    }

    /**
     * Where the attachment comes from: Camera (a photo now, with the glasses)
     * or Gallery (what the glasses and the phone already have). [body] and
     * [previousPhoto] come along from a review card, so back returns to it.
     */
    data class AttachFrom(val recipient: Recipient, val body: String = "", val previousPhoto: String? = null) : Step

    /** The glasses' camera is taking a photo for [recipient]; it shows up to check before attaching. */
    data class Snapping(val recipient: Recipient, val body: String = "", val previousPhoto: String? = null) : Step

    /** "Say the name" first, then the people the wearer wrote to or heard from lately. */
    data class NewMessage(val recent: List<ZapContact>) : Step

    /**
     * Two separate questions, never one: with no recipient yet it listens for
     * the name only; with a recipient it listens for the message only.
     */
    data class Listening(val recipient: Recipient?, val partial: String = "", val photo: String? = null) : Step

    /** The name fits more than one person, or fits one only loosely: the wearer picks, or says it again (last row). */
    data class Pick(val spoken: String, val choices: List<ZapContact>) : Step

    /** Send · Listen first · Say it again · Attach (or change) a photo. [body] may be empty when a photo goes alone. */
    data class Review(val recipient: Recipient, val body: String, val photo: String? = null) : Step

    data class Sending(val recipient: Recipient, val body: String, val photo: String? = null) : Step

    data class Done(
        val recipient: Recipient?,
        val body: String?,
        val outcome: SendOutcome? = null,
        val note: Note? = null,
        val spoken: String? = null,
        /** A photo the wearer had attached: trying again keeps it. */
        val photo: String? = null,
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

    /** Take a photo with the glasses' camera now. */
    object TakeSnapshot : Effect

    /** Ask the glasses to send over what they shot since the last time (it may take a few seconds). */
    object PullGlassesMedia : Effect

    /**
     * Send through WhatsApp's live reply when [Recipient.conversationKey] still
     * has one, else open the chat. With a [photo] (a key from the gallery) it
     * always goes through the chat, since a notification reply carries text only.
     */
    data class Send(val recipient: Recipient, val body: String, val photo: String? = null) : Effect
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
    /** Keys of the phone photos the wearer may attach, newest first (glasses photos when there are any). */
    private val gallery: () -> List<String> = { emptyList() },
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
                step = s.copy(conversation = it)
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
        is Step.Thread -> when {
            s.reading -> 0 // the glasses scroll the document themselves
            s.conversation.photos.isEmpty() -> THREAD_CHOICES
            else -> THREAD_CHOICES + 1
        }
        is Step.Photo -> s.conversation.photos.size
        is Step.ChoosePhoto -> s.photos.size
        is Step.AttachFrom -> ATTACH_CHOICES
        is Step.NewMessage -> 1 + s.recent.size
        is Step.Pick -> s.choices.size + 1
        is Step.Review -> REVIEW_CHOICES
        is Step.Done -> 1
        is Step.Listening, is Step.Sending, is Step.Snapping -> 0
    }

    fun move(delta: Int) {
        (step as? Step.Photo)?.let { s ->
            // Photos don't wrap around: the ends are where the conversation's photos end.
            val next = (s.index + delta).coerceIn(0, s.conversation.photos.size - 1)
            step = s.copy(index = next)
            return
        }
        (step as? Step.ChoosePhoto)?.let { s ->
            step = s.copy(index = (s.index + delta).coerceIn(0, s.photos.size - 1))
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
        is Step.Thread -> if (s.reading) {
            go(s.copy(reading = false), asBase = true)
            emptyList()
        } else when (selected) {
            THREAD_REPLY -> reply(s.conversation)
            THREAD_SEND_PHOTO -> attachFrom(s.conversation.recipient(), body = "", previous = null)
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
            REVIEW_SEND -> send(s.recipient, s.body, s.photo)
            REVIEW_LISTEN -> listOf(Effect.SpeakDraft(s.recipient, s.body))
            REVIEW_PHOTO -> listOf(Effect.StopSpeaking) + attachFrom(s.recipient, s.body, s.photo)
            else -> listOf(Effect.StopSpeaking) + listen(s.recipient, s.photo)
        }
        is Step.ChoosePhoto -> review(s.recipient, s.body, s.photo)
        is Step.AttachFrom -> if (selected == ATTACH_CAMERA) {
            snap(s.recipient, s.body, s.previousPhoto)
        } else {
            choosePhoto(s.recipient, s.body, s.previousPhoto)
        }
        is Step.Photo -> {
            // Tapping a photo reads its caption, if any, like Listen again.
            if (s.message.text.isNotBlank()) listOf(Effect.SpeakMessage(s.message)) else emptyList()
        }
        is Step.Done -> if (s.canRetry) {
            listen(s.recipient, s.photo)
        } else {
            go(Step.Inbox(conversations), asBase = true)
            emptyList()
        }
        is Step.Listening, is Step.Sending, is Step.Snapping -> emptyList()
    }

    fun back(): List<Effect> = when (val s = step) {
        is Step.Inbox -> listOf(Effect.Close)
        is Step.Photo -> {
            step = Step.Thread(s.conversation, reading = false)
            selected = THREAD_PHOTO
            listOf(Effect.StopSpeaking)
        }
        is Step.Thread -> {
            if (s.reading) go(Step.Inbox(conversations), asBase = true) else go(s.copy(reading = true), asBase = true)
            listOf(Effect.StopSpeaking)
        }
        is Step.NewMessage -> {
            go(Step.Inbox(conversations), asBase = true)
            listOf(Effect.StopSpeaking)
        }
        is Step.Listening -> {
            go(base)
            listOf(Effect.StopListening)
        }
        is Step.Sending -> emptyList()
        // A photo that arrives after back is simply dropped.
        is Step.Snapping -> backToSource(s.recipient, s.body, s.previousPhoto, ATTACH_CAMERA)
        is Step.ChoosePhoto -> backToSource(s.recipient, s.body, s.previousPhoto, ATTACH_GALLERY)
        is Step.AttachFrom -> {
            // From a review card: back to it, as it was. From a chat: back to the chat.
            if (s.body.isNotEmpty() || s.previousPhoto != null) review(s.recipient, s.body, s.previousPhoto) else go(base)
            emptyList()
        }
        is Step.Review -> {
            go(base)
            listOf(Effect.StopSpeaking)
        }
        is Step.Pick, is Step.Done -> {
            go(base)
            emptyList()
        }
    }

    /**
     * New photos arrived from the glasses (or the gallery changed) while the
     * wearer is choosing: show the fresh list, staying on the photo they had up.
     */
    fun refreshPhotos(photos: List<String>) {
        val s = step as? Step.ChoosePhoto ?: return
        if (photos.isEmpty() || photos == s.photos) return
        val keep = photos.indexOf(s.photo).takeIf { it >= 0 } ?: 0
        step = s.copy(photos = photos, index = keep)
    }

    /** The glasses' photo is here: show it, a tap attaches it, back drops it. */
    fun onSnapshot(key: String) {
        val s = step as? Step.Snapping ?: return
        go(Step.ChoosePhoto(s.recipient, listOf(key), body = s.body, previousPhoto = s.previousPhoto))
    }

    fun onSnapshotFailed() {
        val s = step as? Step.Snapping ?: return
        go(Step.Done(s.recipient, s.body.ifEmpty { null }, note = Note.CAMERA_FAILED, photo = s.previousPhoto))
    }

    fun onSpeechPartial(text: String) {
        val s = step as? Step.Listening ?: return
        step = s.copy(partial = text)
    }

    fun onSpeechFinal(text: String): List<Effect> {
        val s = step as? Step.Listening ?: return emptyList()
        if (text.isBlank()) return done(Step.Done(s.recipient, null, note = Note.NO_SPEECH, photo = s.photo))
        val recipient = s.recipient
        if (recipient != null) return review(recipient, ComposeParser.tidyBody(text), s.photo)

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
        return done(Step.Done(s.recipient, null, note = note, photo = s.photo))
    }

    fun onSendOutcome(outcome: SendOutcome) {
        when (val s = step) {
            is Step.Sending -> go(Step.Done(s.recipient, s.body.ifEmpty { null }, outcome = outcome))
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

    private fun listen(recipient: Recipient?, photo: String? = null): List<Effect> {
        go(Step.Listening(recipient, photo = photo))
        return listOf(Effect.StartListening)
    }

    private fun review(recipient: Recipient, body: String, photo: String? = null): List<Effect> {
        go(Step.Review(recipient, body, photo))
        return emptyList()
    }

    /** A photo goes through the chat itself, so it needs the person's number; then Camera or Gallery. */
    private fun attachFrom(recipient: Recipient, body: String, previous: String?): List<Effect> {
        if (recipient.digits == null) return done(Step.Done(recipient, null, note = Note.NO_NUMBER))
        go(Step.AttachFrom(recipient, body, previous))
        // Start fetching the glasses' shots now, so the gallery is fresh if the wearer picks it.
        return listOf(Effect.PullGlassesMedia)
    }

    /** Camera or gallery, back returns to where the wearer chose between them. */
    private fun backToSource(recipient: Recipient, body: String, previous: String?, from: Int): List<Effect> {
        go(Step.AttachFrom(recipient, body, previous))
        selected = from
        return emptyList()
    }

    /** The glasses' and the phone's photos and videos, glasses first, newest first. */
    private fun choosePhoto(recipient: Recipient, body: String, previous: String?): List<Effect> {
        val photos = gallery()
        if (photos.isEmpty()) return listOf(Effect.PullGlassesMedia) + done(Step.Done(recipient, null, note = Note.NO_PHOTOS))
        go(Step.ChoosePhoto(recipient, photos, index = 0, body = body, previousPhoto = previous))
        return listOf(Effect.PullGlassesMedia)
    }

    private fun snap(recipient: Recipient, body: String, previous: String?): List<Effect> {
        go(Step.Snapping(recipient, body, previous))
        return listOf(Effect.StopSpeaking, Effect.TakeSnapshot)
    }

    private fun send(recipient: Recipient, body: String, photo: String? = null): List<Effect> {
        go(Step.Sending(recipient, body, photo))
        return listOf(Effect.StopSpeaking, Effect.Send(recipient, body, photo))
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
        const val THREAD_SEND_PHOTO = 2
        const val THREAD_PHOTO = 3
        private const val THREAD_CHOICES = 3
        const val ATTACH_CAMERA = 0
        const val ATTACH_GALLERY = 1
        private const val ATTACH_CHOICES = 2
        const val REVIEW_SEND = 0
        const val REVIEW_LISTEN = 1
        const val REVIEW_REDO = 2
        const val REVIEW_PHOTO = 3
        private const val REVIEW_CHOICES = 4
    }
}
