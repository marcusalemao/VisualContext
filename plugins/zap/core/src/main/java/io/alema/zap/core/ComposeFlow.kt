package io.alema.zap.core

/** Where a send ended up. Shared by the phone sender and the flow. */
enum class SendOutcome { SENT, OPENED_FOR_THUMB, WAITING_FOR_UNLOCK, NOT_INSTALLED, FAILED }

/** Why the flow stopped somewhere other than a sent message. */
enum class Note { NO_SPEECH, SPEECH_UNAVAILABLE, NO_CONTACT, NO_NUMBER }

sealed interface Step {
    /** "Speak" first, then the shortlist of people. */
    data class Home(val shortlist: List<ZapContact>) : Step

    /** Listening for the whole command, or only the message once the person is known. */
    data class Listening(val contact: ZapContact?, val partial: String = "") : Step

    /** The name fits more than one person. */
    data class Pick(val spoken: String, val choices: List<ZapContact>, val body: String?) : Step

    /** Send · Listen · Say it again. */
    data class Review(val contact: ZapContact, val body: String) : Step

    data class Sending(val contact: ZapContact, val body: String) : Step

    data class Done(
        val contact: ZapContact?,
        val body: String?,
        val outcome: SendOutcome? = null,
        val note: Note? = null,
        val spoken: String? = null,
    ) : Step
}

sealed interface Effect {
    object StartListening : Effect
    object StopListening : Effect
    data class Speak(val contact: ZapContact, val body: String) : Effect
    object StopSpeaking : Effect
    data class Send(val contact: ZapContact, val digits: String, val body: String) : Effect
    object Close : Effect
}

/**
 * The whole "new message" conversation on the glasses, as a pure state
 * machine: the plugin feeds it gestures and speech, renders [step] and runs
 * the returned [Effect]s. Kept free of Android so every path is unit-tested.
 *
 * Rule carried over from Relay: nothing is sent without the wearer choosing
 * Send on the review card.
 */
class ComposeFlow(
    private val directory: () -> List<ZapContact>,
    private val defaultCountryCode: String = PhoneNumbers.BRAZIL,
) {
    var step: Step = Step.Home(emptyList())
        private set
    var selected: Int = 0
        private set

    fun open(shortlist: List<ZapContact>): List<Effect> {
        go(Step.Home(shortlist))
        return emptyList()
    }

    /** Number of rows the wearer can land on in the current step. */
    fun choiceCount(): Int = when (val s = step) {
        is Step.Home -> 1 + s.shortlist.size
        is Step.Pick -> s.choices.size
        is Step.Review -> REVIEW_CHOICES
        is Step.Done -> 1
        is Step.Listening, is Step.Sending -> 0
    }

    fun move(delta: Int) {
        val count = choiceCount()
        if (count > 0) selected = Math.floorMod(selected + delta, count)
    }

    fun tap(): List<Effect> = when (val s = step) {
        is Step.Home -> if (selected == 0) {
            listen(null)
        } else {
            choose(s.shortlist[selected - 1], body = null)
        }
        is Step.Pick -> choose(s.choices[selected], s.body)
        is Step.Review -> when (selected) {
            REVIEW_SEND -> send(s.contact, s.body)
            REVIEW_LISTEN -> listOf(Effect.Speak(s.contact, s.body))
            else -> listOf(Effect.StopSpeaking) + listen(s.contact)
        }
        is Step.Done -> {
            go(Step.Home(home()))
            emptyList()
        }
        is Step.Listening, is Step.Sending -> emptyList()
    }

    fun back(): List<Effect> = when (step) {
        is Step.Home -> listOf(Effect.Close)
        is Step.Listening -> {
            go(Step.Home(home()))
            listOf(Effect.StopListening)
        }
        is Step.Sending -> emptyList()
        is Step.Review -> {
            go(Step.Home(home()))
            listOf(Effect.StopSpeaking)
        }
        is Step.Pick, is Step.Done -> {
            go(Step.Home(home()))
            emptyList()
        }
    }

    fun onSpeechPartial(text: String) {
        val s = step as? Step.Listening ?: return
        step = s.copy(partial = text)
    }

    fun onSpeechFinal(text: String): List<Effect> {
        val s = step as? Step.Listening ?: return emptyList()
        if (text.isBlank()) return done(Step.Done(s.contact, null, note = Note.NO_SPEECH))
        val contact = s.contact
        if (contact != null) return review(contact, ComposeParser.tidyBody(text))

        val people = directory()
        val request = ComposeParser(ContactMatcher(people)).parse(text)
            ?: return done(Step.Done(null, null, note = Note.NO_SPEECH))
        val matcher = ContactMatcher(people)
        return when {
            request.matches.isEmpty() ->
                done(Step.Done(null, request.body, note = Note.NO_CONTACT, spoken = request.recipient))
            request.matches.size == 1 || matcher.isConfident(request.matches) ->
                choose(request.matches.first().contact, request.body)
            else -> {
                go(Step.Pick(request.recipient, request.matches.map { it.contact }, request.body))
                emptyList()
            }
        }
    }

    /** No speech, link lost, mic busy: the wearer sees why and can try again. */
    fun onSpeechFailed(note: Note): List<Effect> {
        val s = step as? Step.Listening ?: return emptyList()
        return done(Step.Done(s.contact, null, note = note))
    }

    fun onSendOutcome(outcome: SendOutcome) {
        when (val s = step) {
            is Step.Sending -> go(Step.Done(s.contact, s.body, outcome = outcome))
            // A locked phone reports "waiting" first and "sent" once unlocked.
            is Step.Done -> if (s.outcome == SendOutcome.WAITING_FOR_UNLOCK) step = s.copy(outcome = outcome)
            else -> Unit
        }
    }

    private fun choose(contact: ZapContact, body: String?): List<Effect> {
        if (contact.target(defaultCountryCode) == null) {
            return done(Step.Done(contact, body, note = Note.NO_NUMBER))
        }
        return if (body.isNullOrBlank()) listen(contact) else review(contact, body)
    }

    private fun listen(contact: ZapContact?): List<Effect> {
        go(Step.Listening(contact))
        return listOf(Effect.StartListening)
    }

    private fun review(contact: ZapContact, body: String): List<Effect> {
        go(Step.Review(contact, body))
        return emptyList()
    }

    private fun send(contact: ZapContact, body: String): List<Effect> {
        val digits = contact.target(defaultCountryCode) ?: return done(Step.Done(contact, body, note = Note.NO_NUMBER))
        go(Step.Sending(contact, body))
        return listOf(Effect.StopSpeaking, Effect.Send(contact, digits, body))
    }

    private fun done(next: Step.Done): List<Effect> {
        go(next)
        return emptyList()
    }

    private fun home(): List<ZapContact> = (step as? Step.Home)?.shortlist ?: lastShortlist

    private var lastShortlist: List<ZapContact> = emptyList()

    private fun go(next: Step) {
        if (next is Step.Home) lastShortlist = next.shortlist
        step = next
        selected = 0
    }

    companion object {
        const val REVIEW_SEND = 0
        const val REVIEW_LISTEN = 1
        const val REVIEW_REDO = 2
        private const val REVIEW_CHOICES = 3
    }
}
