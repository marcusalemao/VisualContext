package io.alema.zap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.anezium.rokidbus.client.PluginRegistrationResult
import com.anezium.rokidbus.client.plugin.NexusNotice
import com.anezium.rokidbus.client.plugin.NexusNoticeAction
import com.anezium.rokidbus.client.plugin.NexusNoticeCloseReason
import com.anezium.rokidbus.client.plugin.NexusNoticeImage
import com.anezium.rokidbus.client.plugin.NexusNoticeUpdate
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSpeechCallbacks
import com.anezium.rokidbus.client.plugin.NexusSpeechError
import com.anezium.rokidbus.client.plugin.NexusSpeechSession
import com.anezium.rokidbus.client.plugin.NexusSpeechState
import com.anezium.rokidbus.client.plugin.NexusSpeechStopReason
import com.anezium.rokidbus.client.plugin.speechSession
import io.alema.zap.core.ComposeParser
import io.alema.zap.core.ContactMatcher
import io.alema.zap.core.RecentPeople
import io.alema.zap.core.ZapContact
import io.alema.zap.core.Recipient
import io.alema.zap.core.SendOutcome
import com.anezium.rokidbus.client.plugin.NexusPluginCallbacks
import com.anezium.rokidbus.client.plugin.NexusPluginClient
import com.anezium.rokidbus.client.plugin.NexusTtsCallbacks
import com.anezium.rokidbus.client.plugin.NexusTtsDoneReason
import com.anezium.rokidbus.client.plugin.NexusTtsSession
import com.anezium.rokidbus.client.plugin.ttsSession
import io.alema.zap.core.ZapText
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import com.anezium.rokidbus.shared.plugin.PluginCapability

/**
 * What the glasses do when a message arrives while Zap is closed, as the
 * wearer chose in settings:
 * - notice: who wrote and the start of the message, a few seconds, no buttons;
 * - notice + voice: the whole message over a dark backdrop, read aloud at
 *   once (Nexus speaks only on the glasses or earbuds, never the phone speaker).
 * Either way the full chat waits in the inbox.
 *
 * When the chat can be answered, the band carries one chip, Responder: the
 * wearer dictates right there, sees the text, and chooses Enviar or Ditar de
 * novo — the same rules as inside Zap (nothing goes without Enviar).
 *
 * A photo taken with the glasses' button is offered once ([offerPhoto]): the
 * shot on the band with the two latest people as chips, plus "someone else"
 * by voice; then Send, or a spoken caption first.
 *
 * Plugins are dormant unless opened, so this borrows Relay's pattern: the
 * notification listener keeps the process alive, a bus client connects to say
 * one thing and is closed again after a short idle. Only one client per plugin
 * id may be on the bus, so nothing is announced while Zap itself is open on
 * the glasses (its inbox refreshes live instead).
 */
internal object ZapNotifier : NexusPluginCallbacks {
    private const val TAG = "ZapNotifier"
    private const val PLUGIN_ID = "zap"
    private const val NOTICE_TTL_MS = 4_000L
    private const val IDLE_CLOSE_MS = 30_000L
    private const val TITLE_CHARS = 32
    private const val BODY_CHARS = 90
    private const val FULL_BODY_CHARS = 1_024
    /** Long enough to be read aloud at an ordinary voice speed; Nexus caps notices at 45 s. */
    private const val READ_MS_PER_CHAR = 80L
    private const val READ_BASE_MS = 3_000L
    private const val READ_MAX_MS = 45_000L

    private val main = Handler(Looper.getMainLooper())
    private var client: NexusPluginClient? = null
    private var pending: Arrival? = null
    private var tts: NexusTtsSession? = null
    private var spoken = 0
    private val text = ZapText()

    private data class Arrival(val sender: String, val text: String, val readAloud: Boolean, val recipient: Recipient?)

    /** Answering from the band: idle (just shown), listening, reviewing the draft, sending. */
    private enum class Reply { NONE, LISTENING, REVIEW, SENDING, NAME }
    private var reply = Reply.NONE
    private var replyTo: Recipient? = null
    private var draft = ""
    private var speech: NexusSpeechSession? = null
    private var speechGeneration = 0
    private var appContext: Context? = null

    /** A glasses photo waiting to be offered, and the one being sent from the band. */
    private data class PhotoOffer(val key: String, val photo: ZapPhotos.Photo, val recents: List<Recipient>)
    private var pendingPhoto: PhotoOffer? = null
    private var photoKey: String? = null
    private var choices: List<Recipient> = emptyList()

    private const val ACTION_REPLY = "reply"
    private const val ACTION_SEND = "send"
    private const val ACTION_AGAIN = "again"
    private const val ACTION_TO = "to:"
    private const val ACTION_NAME = "name"
    private const val ACTION_CAPTION = "caption"
    private const val OFFER_TTL_MS = 20_000L
    private const val ANSWER_TTL_MS = 45_000L
    private const val RESULT_TTL_MS = 3_000L

    @Volatile
    var inboxOpen = false

    /** [recipient] is set when the chat can be answered (live reply or a number). */
    fun announce(context: Context, sender: String, text: String, recipient: Recipient?) = main.post {
        val mode = ZapSettings(context).arrivalMode()
        if (inboxOpen || mode == ZapSettings.ARRIVAL_OFF) return@post
        // Never pull the band out from under someone answering: the new message waits in the inbox.
        if (reply != Reply.NONE) return@post
        appContext = context.applicationContext
        pending = Arrival(sender, text, readAloud = mode == ZapSettings.ARRIVAL_READ, recipient = recipient)
        if (client == null) {
            client = NexusPluginClient.create(context.applicationContext, PLUGIN_ID, this).also(NexusPluginClient::connect)
        }
        tryShow()
        main.removeCallbacks(closeIdle)
        main.postDelayed(closeIdle, IDLE_CLOSE_MS)
    }

    /**
     * A photo just came over from the glasses: offer it once. Skipped while Zap is
     * open (attach it there) or while the band is busy answering a message.
     */
    /** Set while Zap is open on the glasses: it shows the offer itself, inside the app. */
    @Volatile var inAppOffer: ((String) -> Unit)? = null

    fun offerPhoto(context: Context, key: String) = main.post {
        if (inboxOpen) {
            inAppOffer?.invoke(key)
            return@post
        }
        if (reply != Reply.NONE) return@post
        val photo = ZapPhotos.loadAny(context, key) ?: return@post
        appContext = context.applicationContext
        // A photo goes through the chat itself, so only people with a number.
        val recents = ZapInbox.list(context)
            .filter { !it.isGroup && it.waDigits != null }
            .sortedByDescending { it.lastTimeMs }
            .take(2)
            .map { it.recipient() }
        pendingPhoto = PhotoOffer(key, photo, recents)
        pending = null
        if (client == null) {
            client = NexusPluginClient.create(context.applicationContext, PLUGIN_ID, this).also(NexusPluginClient::connect)
        }
        tryShow()
        keepAlive()
    }

    /** Zap opened on the glasses: step aside so its own session is the only client. */
    fun release() = main.post {
        pending = null
        pendingPhoto = null
        photoKey = null
        stopListening()
        reply = Reply.NONE
        main.removeCallbacks(closeIdle)
        closeIdle.run()
    }

    private val closeIdle = Runnable {
        stopListening()
        reply = Reply.NONE
        photoKey = null
        pendingPhoto = null
        tts?.close()
        tts = null
        client?.close()
        client = null
    }

    private fun tryShow() {
        val current = client ?: return
        if (!current.isApproved || !current.hasCapability(PluginCapability.SURFACES) || !current.supportsNoticeSurface) return
        pendingPhoto?.let { offer ->
            pendingPhoto = null
            showPhotoOffer(current, offer)
            appContext?.getSharedPreferences("zap_photo_offer", Context.MODE_PRIVATE)?.edit()
                ?.putLong(ZapPhotoWatcher.KEY_SHOWN, System.currentTimeMillis())?.apply()
            return
        }
        val arrival = pending ?: return
        photoKey = null
        val body = arrival.text.ifBlank { " " }
        // One chip only: with a single answer the band can still page through a long message.
        val actions = if (arrival.recipient != null && current.hasCapability(PluginCapability.STT)) {
            listOf(NexusNoticeAction(ACTION_REPLY, "reply", text.reply))
        } else {
            emptyList()
        }
        replyTo = arrival.recipient
        reply = Reply.NONE
        val notice = if (arrival.readAloud) {
            val shown = cut(body, FULL_BODY_CHARS)
            NexusNotice(
                title = arrival.sender.take(TITLE_CHARS),
                body = shown,
                footer = "Zap",
                ttlMs = (READ_BASE_MS + READ_MS_PER_CHAR * shown.length).coerceAtMost(READ_MAX_MS),
                wakeDisplay = true,
                backdrop = true, // over whatever is on the lens: this one may be urgent
                actions = actions,
            )
        } else {
            NexusNotice(
                title = arrival.sender.take(TITLE_CHARS),
                body = cut(body, BODY_CHARS),
                footer = "Zap",
                backdrop = appContext?.let { ZapSettings(it).extraContrast() } == true,
                // A reply chip needs time to be reached.
                ttlMs = if (actions.isEmpty()) NOTICE_TTL_MS else NOTICE_TTL_MS * 3,
                wakeDisplay = true,
                actions = actions,
            )
        }
        val result = current.showNotice(notice)
        Log.i(TAG, "arrival notice result=$result read=${arrival.readAloud}")
        pending = null
        if (arrival.readAloud) speak(current, arrival)
    }

    /** One voice: a newer message cuts off the one being read, like a person would. */
    private fun speak(current: NexusPluginClient, arrival: Arrival) {
        if (!current.hasCapability(PluginCapability.TTS)) return
        val session = tts ?: current.ttsSession(ttsCallbacks).also { tts = it }
        val sentence = cut(text.heard(arrival.sender.replace(" · ", ", "), arrival.text), FULL_BODY_CHARS)
        val result = session.speak(sentence, "zap-arrival-${++spoken}")
        Log.i(TAG, "read aloud result=$result")
        // Keep the bus client until the voice has had time to finish.
        main.removeCallbacks(closeIdle)
        main.postDelayed(closeIdle, IDLE_CLOSE_MS + READ_MAX_MS)
    }

    private val ttsCallbacks = object : NexusTtsCallbacks {
        override fun onTtsStarted(utteranceId: String) = Unit
        override fun onTtsDone(utteranceId: String, reason: NexusTtsDoneReason) {
            if (reason == NexusTtsDoneReason.UNAVAILABLE) Log.w(TAG, "read aloud unavailable: no glasses or earbuds, or voice engine failed")
        }
    }

    private fun cut(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"

    override fun onRegistrationState(result: Int) {
        main.post {
            when (result) {
                // Not approved for good (wearer denied Zap, bad descriptor…): stop holding a client.
                PluginRegistrationResult.DENIED,
                PluginRegistrationResult.INVALID_DESCRIPTOR,
                PluginRegistrationResult.IDENTITY_MISMATCH,
                PluginRegistrationResult.UNSUPPORTED_API -> {
                    Log.w(TAG, "registration terminal result=$result")
                    pending = null
                    closeIdle.run()
                }
                else -> tryShow()
            }
        }
    }

    // --- a photo from the glasses ------------------------------------------------

    private fun showPhotoOffer(current: NexusPluginClient, offer: PhotoOffer) {
        val canListen = current.hasCapability(PluginCapability.STT)
        val actions = offer.recents.mapIndexed { i, person -> NexusNoticeAction("$ACTION_TO$i", "send", person.name.take(TITLE_CHARS)) } +
            listOfNotNull(NexusNoticeAction(ACTION_NAME, "mic", text.otherPerson).takeIf { canListen })
        if (actions.isEmpty()) return
        photoKey = offer.key
        choices = offer.recents
        replyTo = null
        draft = ""
        reply = Reply.NONE
        val notice = NexusNotice(
            title = text.photoOffer,
            footer = "Zap",
            actions = actions,
            ttlMs = OFFER_TTL_MS,
            wakeDisplay = true,
            backdrop = appContext?.let { ZapSettings(it).extraContrast() } == true,
        )
        // The shot itself under the title when the link carries images; the question alone otherwise.
        val result = if (current.supportsImageSurface) {
            current.showNotice(
                notice.copy(image = NexusNoticeImage("zap-offer-${offer.key}".take(96), "image/jpeg", offer.photo.width, offer.photo.height)),
                offer.photo.bytes,
            )
        } else {
            current.showNotice(notice.copy(body = text.photoAttached))
        }
        Log.i(TAG, "photo offer result=$result")
        keepAlive()
    }

    /** The person is chosen: Send now, or say a caption first. */
    private fun photoTo(person: Recipient) {
        replyTo = person
        draft = ""
        reply = Reply.REVIEW
        keepAlive()
        client?.updateNotice(
            NexusNoticeUpdate(
                title = text.to(person.name).take(TITLE_CHARS),
                body = text.photoAttached,
                footer = "Zap",
                actions = listOfNotNull(
                    NexusNoticeAction(ACTION_SEND, "send", text.send),
                    NexusNoticeAction(ACTION_CAPTION, "mic", text.sayCaption).takeIf { client?.hasCapability(PluginCapability.STT) == true },
                ),
                ttlMs = ANSWER_TTL_MS,
            ),
        )
    }

    /** "Someone else": listen for a name only, like Zap's own new-message step. */
    private fun listenName() {
        val current = client ?: return
        stopListening()
        reply = Reply.NAME
        keepAlive()
        current.updateNotice(NexusNoticeUpdate(title = text.whoTitle, body = text.sayOnlyName, footer = text.listening, ttlMs = ANSWER_TTL_MS))
        val generation = ++speechGeneration
        var finalText: String? = null
        val session = current.speechSession(object : NexusSpeechCallbacks {
            override fun onSpeechStarted(realtime: Boolean) = Unit
            override fun onSpeechState(state: NexusSpeechState) = Unit
            override fun onSpeechPartial(text: String) = onMain(generation) {
                if (text.isNotBlank()) client?.updateNotice(NexusNoticeUpdate(body = cut(text, BODY_CHARS)))
            }
            override fun onSpeechFinal(text: String) = onMain(generation) { finalText = text }
            override fun onSpeechStopped(reason: NexusSpeechStopReason, error: NexusSpeechError?) = onMain(generation) {
                speech = null
                val said = finalText.orEmpty()
                if (said.isNotBlank()) pickName(said) else nameFailed(heardNothing = reason == NexusSpeechStopReason.NO_SPEECH || reason == NexusSpeechStopReason.COMPLETED)
            }
        })
        speech = session
        if (session.start() != NexusSdkResult.SENT) {
            speech = null
            nameFailed(heardNothing = false)
        }
    }

    private fun pickName(said: String) {
        val context = appContext ?: return
        val chats = ZapInbox.list(context)
        val people = RecentPeople.withDirectory(
            chats.filter { !it.isGroup }.map { ZapContact(id = "chat:" + it.key, name = it.title, waIds = listOfNotNull(it.waDigits), conversationKey = it.key) },
            runCatching { ZapContacts(context).directory() }.getOrDefault(emptyList()),
        )
        val matcher = ContactMatcher(people)
        val (spoken, found) = matcher.findName(said)
        val reachable = found.filter { it.contact.target() != null }.take(3)
        fun recipientOf(contact: ZapContact) =
            contact.conversationKey?.let { key -> chats.firstOrNull { it.key == key }?.recipient() } ?: Recipient.of(contact)
        when {
            reachable.isEmpty() -> nameFailed(heardNothing = false, spoken = spoken)
            reachable.size == 1 || matcher.isConfident(reachable) -> photoTo(recipientOf(reachable.first().contact))
            else -> {
                choices = reachable.map { recipientOf(it.contact) }
                reply = Reply.NONE
                client?.updateNotice(
                    NexusNoticeUpdate(
                        title = text.pickTitle(spoken).take(TITLE_CHARS),
                        body = " ",
                        footer = "Zap",
                        actions = choices.mapIndexed { i, person -> NexusNoticeAction("$ACTION_TO$i", "send", person.name.take(TITLE_CHARS)) },
                        ttlMs = ANSWER_TTL_MS,
                    ),
                )
            }
        }
    }

    private fun nameFailed(heardNothing: Boolean, spoken: String? = null) {
        reply = Reply.NONE
        client?.updateNotice(
            NexusNoticeUpdate(
                body = when {
                    spoken != null -> text.noContact(spoken)
                    heardNothing -> text.noSpeech
                    else -> text.speechUnavailable
                },
                footer = "Zap",
                actions = listOf(NexusNoticeAction(ACTION_NAME, "mic", text.tryAgain)),
                ttlMs = ANSWER_TTL_MS,
            ),
        )
    }

    // --- answering from the band -------------------------------------------------

    override fun onNoticeAction(id: String) {
        main.post {
            when {
                id == ACTION_REPLY || id == ACTION_AGAIN || id == ACTION_CAPTION -> listen()
                id == ACTION_SEND -> send()
                id == ACTION_NAME -> listenName()
                id.startsWith(ACTION_TO) -> id.removePrefix(ACTION_TO).toIntOrNull()?.let(choices::getOrNull)?.let(::photoTo)
            }
        }
    }

    override fun onNoticeClosed(reason: NexusNoticeCloseReason) {
        main.post {
            // Dismissed with Back, or timed out: stop listening. A send already under way finishes.
            if (reply == Reply.LISTENING || reply == Reply.REVIEW || reply == Reply.NAME) {
                stopListening()
                reply = Reply.NONE
            }
            if (reply != Reply.SENDING) photoKey = null
        }
    }

    private fun listen() {
        val current = client ?: return
        val to = replyTo ?: return
        tts?.stop() // the microphone must not take the glasses' own voice
        stopListening()
        reply = Reply.LISTENING
        draft = ""
        keepAlive()
        val prompt = if (photoKey != null) text.sayCaptionNow else text.sayMessage
        current.updateNotice(NexusNoticeUpdate(title = text.to(to.name).take(TITLE_CHARS), body = prompt, footer = text.listening, ttlMs = ANSWER_TTL_MS))
        val generation = ++speechGeneration
        var finalText: String? = null
        val session = current.speechSession(object : NexusSpeechCallbacks {
            override fun onSpeechStarted(realtime: Boolean) = Unit
            override fun onSpeechState(state: NexusSpeechState) = Unit
            override fun onSpeechPartial(text: String) = onMain(generation) {
                if (text.isNotBlank()) client?.updateNotice(NexusNoticeUpdate(body = cut(text, FULL_BODY_CHARS)))
            }
            override fun onSpeechFinal(text: String) = onMain(generation) { finalText = text }
            override fun onSpeechStopped(reason: NexusSpeechStopReason, error: NexusSpeechError?) = onMain(generation) {
                speech = null
                val said = finalText?.let(ComposeParser::tidyBody).orEmpty()
                when {
                    said.isNotBlank() -> review(said)
                    // A photo can still go without a caption.
                    photoKey != null -> replyTo?.let(::photoTo)
                    else -> failed(reason == NexusSpeechStopReason.NO_SPEECH || reason == NexusSpeechStopReason.COMPLETED)
                }
            }
        })
        speech = session
        if (session.start() != NexusSdkResult.SENT) {
            speech = null
            failed(heardNothing = false)
        }
    }

    private fun review(said: String) {
        val to = replyTo ?: return
        draft = said
        reply = Reply.REVIEW
        keepAlive()
        client?.updateNotice(
            NexusNoticeUpdate(
                title = text.to(to.name).take(TITLE_CHARS),
                body = cut(if (photoKey != null) "📷 $said" else said, FULL_BODY_CHARS),
                footer = "Zap",
                actions = listOf(
                    NexusNoticeAction(ACTION_SEND, "send", text.send),
                    NexusNoticeAction(ACTION_AGAIN, "mic", text.redo),
                ),
                ttlMs = ANSWER_TTL_MS,
            ),
        )
    }

    private fun failed(heardNothing: Boolean) {
        reply = Reply.REVIEW
        client?.updateNotice(
            NexusNoticeUpdate(
                body = if (heardNothing) text.noSpeech else text.speechUnavailable,
                footer = if (heardNothing) "Zap" else text.voiceHelp,
                actions = listOf(NexusNoticeAction(ACTION_AGAIN, "mic", text.tryAgain)),
                ttlMs = ANSWER_TTL_MS,
            ),
        )
    }

    private fun send() {
        val context = appContext ?: return
        val to = replyTo ?: return
        val photo = photoKey
        if (reply != Reply.REVIEW || (draft.isBlank() && photo == null)) return
        reply = Reply.SENDING
        keepAlive()
        client?.updateNotice(NexusNoticeUpdate(body = cut(draft.ifBlank { text.photoAttached }, FULL_BODY_CHARS), footer = text.sending))
        to.contactId?.let { ZapRecents(context).touch(it) }
        to.digits?.let { ZapRecents(context).touch(ZapPeople.idFor(it)) }
        ZapSender.deliver(context, to, draft, photo) { outcome ->
            main.post {
                val status = when (outcome) {
                    SendOutcome.SENT -> text.sent
                    SendOutcome.OPENED_FOR_THUMB -> text.openedForThumb
                    SendOutcome.WAITING_FOR_UNLOCK -> text.waitingUnlock
                    SendOutcome.NOT_INSTALLED -> text.notInstalled
                    SendOutcome.FAILED -> text.failed
                }
                client?.updateNotice(NexusNoticeUpdate(footer = status, ttlMs = if (outcome == SendOutcome.WAITING_FOR_UNLOCK) ANSWER_TTL_MS else RESULT_TTL_MS))
                // "Waiting for unlock" reports again once sent; until then keep the band's state.
                if (outcome != SendOutcome.WAITING_FOR_UNLOCK) {
                    reply = Reply.NONE
                    draft = ""
                    photoKey = null
                }
            }
        }
    }

    private fun stopListening() {
        speechGeneration++
        speech?.stop()
        speech = null
    }

    private fun onMain(generation: Int, block: () -> Unit) {
        main.post { if (generation == speechGeneration) block() }
    }

    /** The bus client must outlive an answer in progress. */
    private fun keepAlive() {
        main.removeCallbacks(closeIdle)
        main.postDelayed(closeIdle, IDLE_CLOSE_MS + ANSWER_TTL_MS)
    }

    override fun onLinkState(state: Int) {
        main.post { tryShow() }
    }

    override fun onOpen() = Unit
    override fun onClose() = Unit
    override fun onInput(event: NexusInputEvent) = Unit
}
