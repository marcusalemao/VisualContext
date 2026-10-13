package io.alema.zap

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusCardLine
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusRowTone
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSpeechCallbacks
import com.anezium.rokidbus.client.plugin.NexusSpeechError
import com.anezium.rokidbus.client.plugin.NexusSpeechSession
import com.anezium.rokidbus.client.plugin.NexusSpeechState
import com.anezium.rokidbus.client.plugin.NexusSpeechStopReason
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.client.plugin.NexusTtsCallbacks
import com.anezium.rokidbus.client.plugin.NexusTtsDoneReason
import com.anezium.rokidbus.client.plugin.NexusTtsSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import io.alema.zap.core.ComposeFlow
import io.alema.zap.core.Effect
import io.alema.zap.core.Note
import io.alema.zap.core.SendOutcome
import io.alema.zap.core.Step
import io.alema.zap.core.Recipient
import io.alema.zap.core.ZapConversation
import io.alema.zap.core.ZapMessage

/**
 * Start a WhatsApp message from the glasses, to anyone.
 *
 * Open → "Speak" (say "mensagem pro Bruno: tô chegando") or pick a recent
 * person → review → Send · Listen · Say it again. The logic lives in
 * [ComposeFlow]; this class only draws its steps and runs its effects.
 *
 * HUD rules from the VisualContext design: green on black only, no gesture
 * hints (the wearer knows the native gestures), short lines.
 */
class ZapPluginService : NexusPluginService() {
    private val main = Handler(Looper.getMainLooper())
    private val text = ZapText()
    private lateinit var contacts: ZapContacts
    private lateinit var flow: ComposeFlow

    private var surface: NexusSurfaceSession? = null
    private var speech: NexusSpeechSession? = null
    private var speechGeneration = 0
    private var tts: NexusTtsSession? = null
    private var ttsCounter = 0

    /** Set when the Nexus voice could not speak; the review card then shows where to fix it. */
    private var ttsProblem = false

    /** What the flow asked for after a final transcript, run once the hub closes the session. */
    private var pendingEffects: List<Effect> = emptyList()

    /** Why the last dictation ended, for test builds only (never shown in release). */
    private var speechDetail: String? = null

    private var unobserveInbox: (() -> Unit)? = null

    override fun onNexusOpen() {
        ZapNotifier.inboxOpen = true
        ZapNotifier.release()
        contacts = ZapContacts(this)
        flow = ComposeFlow(
            directory = { contacts.directory() },
            recentPeople = { contacts.shortlist(contacts.directory(), SHORTLIST) },
        )
        flow.open(ZapInbox.list(this))
        unobserveInbox = ZapInbox.observe {
            if (!::flow.isInitialized || surface == null) return@observe
            flow.refresh(ZapInbox.list(this))
            render()
        }
        surface = nexusSurfaceSession(SURFACE_ID)
        render(show = true)
    }

    override fun onNexusClose() {
        ZapNotifier.inboxOpen = false
        unobserveInbox?.invoke()
        unobserveInbox = null
        stopListening()
        tts?.close()
        tts = null
        surface = null
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || !::flow.isInitialized) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                flow.move(1)
                render()
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                flow.move(-1)
                render()
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> run(flow.tap())
            KeyEvent.KEYCODE_BACK -> run(flow.back())
        }
    }

    private fun run(effects: List<Effect>) {
        effects.forEach { effect ->
            when (effect) {
                Effect.StartListening -> startListening()
                Effect.StopListening -> stopListening()
                is Effect.SpeakDraft -> speak(text.spoken(effect.recipient.name, effect.body))
                is Effect.SpeakMessage -> speak(text.heard(senderLabel(effect.message), effect.message.text))
                Effect.StopSpeaking -> stopSpeaking()
                is Effect.Send -> send(effect.recipient, effect.body)
                Effect.Close -> {
                    surface?.hide()
                    return
                }
            }
        }
        render()
    }

    // --- speech in -------------------------------------------------------

    private fun startListening(retriedBusy: Boolean = false) {
        stopListening()
        stopSpeaking() // the microphone must not take the glasses' own voice
        speechDetail = null
        var finalReceived = false
        val generation = ++speechGeneration
        val session = nexusSpeechSession(object : NexusSpeechCallbacks {
            override fun onSpeechStarted(realtime: Boolean) = Unit
            override fun onSpeechState(state: NexusSpeechState) = Unit

            override fun onSpeechPartial(text: String) = onMain(generation) {
                flow.onSpeechPartial(text)
                render()
            }

            override fun onSpeechFinal(text: String) = onMain(generation) {
                finalReceived = true
                // The hub keeps this session open until its "ended" event; a
                // follow-up dictation started now would be refused as busy.
                // So the next step waits for onSpeechStopped (see below).
                pendingEffects = flow.onSpeechFinal(text)
                render()
            }

            override fun onSpeechStopped(reason: NexusSpeechStopReason, error: NexusSpeechError?) =
                onMain(generation) {
                    speech = null
                    speechDetail = listOfNotNull(reason.name, error?.kind, error?.provider, error?.detail).joinToString(" · ")
                    Log.i(TAG, "speech stopped: $speechDetail final=$finalReceived")
                    val queued = pendingEffects
                    pendingEffects = emptyList()
                    when {
                        queued.isNotEmpty() -> run(queued)
                        finalReceived && reason == NexusSpeechStopReason.COMPLETED -> render()
                        reason == NexusSpeechStopReason.CANCELLED -> Unit
                        reason == NexusSpeechStopReason.DENIED_BUSY && !retriedBusy ->
                            // The previous session may still be closing: try once more.
                            main.postDelayed({ if (generation == speechGeneration) startListening(retriedBusy = true) }, BUSY_RETRY_MS)
                        else -> run(flow.onSpeechFailed(noteFor(reason, error, finalReceived)))
                    }
                }
        })
        if (session == null) {
            speechDetail = "no session"
            run(flow.onSpeechFailed(Note.SPEECH_UNAVAILABLE))
            return
        }
        speech = session
        val result = session.start()
        if (result != NexusSdkResult.SENT) {
            speech = null
            speechDetail = "start: ${result.name}"
            Log.w(TAG, "speech start refused: $result")
            run(flow.onSpeechFailed(Note.SPEECH_UNAVAILABLE))
        }
    }

    /** "Heard nothing" is not "voice broken": only the second points the wearer at Nexus settings. */
    private fun noteFor(reason: NexusSpeechStopReason, error: NexusSpeechError?, finalReceived: Boolean): Note {
        val kind = error?.kind.orEmpty().uppercase()
        return when {
            reason == NexusSpeechStopReason.NO_SPEECH -> Note.NO_SPEECH
            reason == NexusSpeechStopReason.COMPLETED && !finalReceived -> Note.NO_SPEECH
            "NO_SPEECH" in kind || "NO_MATCH" in kind || "TIMEOUT" in kind -> Note.NO_SPEECH
            else -> Note.SPEECH_UNAVAILABLE
        }
    }

    private fun stopListening() {
        pendingEffects = emptyList()
        speechGeneration++
        speech?.stop()
        speech = null
    }

    private fun onMain(generation: Int, block: () -> Unit) {
        main.post { if (generation == speechGeneration && ::flow.isInitialized) block() }
    }

    // --- speech out ------------------------------------------------------

    private fun speak(sentence: String) {
        val session = tts ?: nexusTtsSession(ttsCallbacks)?.also { tts = it }
        ttsProblem = session == null || session.speak(sentence, "zap-${++ttsCounter}") != NexusSdkResult.SENT
    }

    private fun stopSpeaking() {
        tts?.stop()
    }

    private val ttsCallbacks = object : NexusTtsCallbacks {
        override fun onTtsStarted(utteranceId: String) = Unit
        override fun onTtsDone(utteranceId: String, reason: NexusTtsDoneReason) {
            if (reason != NexusTtsDoneReason.UNAVAILABLE) return
            main.post {
                ttsProblem = true
                if (::flow.isInitialized) render()
            }
        }
    }

    // --- sending ---------------------------------------------------------

    private fun send(recipient: Recipient, body: String) {
        recipient.contactId?.let { ZapRecents(this).touch(it) }
        recipient.digits?.let { ZapRecents(this).touch(ZapPeople.idFor(it)) }
        ZapSender.deliver(this, recipient, body) { outcome ->
            if (!::flow.isInitialized) return@deliver
            flow.onSendOutcome(outcome)
            render()
        }
    }

    // --- drawing ---------------------------------------------------------

    private fun render(show: Boolean = false) {
        val card = when (val step = flow.step) {
            is Step.Inbox -> inboxCard(step)
            is Step.Thread -> threadCard(step.conversation)
            is Step.NewMessage -> newMessageCard(step)
            is Step.Listening -> NexusCard(
                title = fitTitle(step.recipient?.let { text.to(it.name) } ?: text.whoTitle),
                lines = emptyList(),
                richLines = listOf(
                    NexusCardLine(
                        text = fit(step.partial.ifBlank { if (step.recipient == null) text.sayWhoAndWhat else text.sayMessage }),
                        tone = if (step.partial.isBlank()) NexusRowTone.DIM else NexusRowTone.BODY,
                    ),
                ),
                subtitle = text.listening,
                handlesBack = true,
            )
            is Step.Pick -> NexusCard(
                title = fitTitle(text.pickTitle(step.spoken)),
                lines = emptyList(),
                richLines = step.choices.take(MAX_ROWS).mapIndexed { index, contact ->
                    NexusCardLine(
                        text = fit(contact.name),
                        sub = text.numberHint(contact.target()),
                        selected = index == flow.selected,
                    )
                },
                handlesBack = true,
            )
            is Step.Review -> NexusCard(
                title = fitTitle(text.to(step.recipient.name)),
                lines = emptyList(),
                richLines = listOf(NexusCardLine(text = fit(step.body), tone = NexusRowTone.BODY)) +
                    listOf(text.send, text.listen, text.redo).mapIndexed { index, label ->
                        NexusCardLine(text = label, selected = index == flow.selected)
                    } +
                    listOfNotNull(
                        NexusCardLine(text = text.voiceHelp, tone = NexusRowTone.DIM).takeIf { ttsProblem },
                    ),
                handlesBack = true,
            )
            is Step.Sending -> NexusCard(
                title = fitTitle(text.to(step.recipient.name)),
                lines = emptyList(),
                richLines = listOf(NexusCardLine(text = fit(step.body), tone = NexusRowTone.BODY)),
                subtitle = text.sending,
                handlesBack = true,
            )
            is Step.Done -> doneCard(step)
        }
        val current = surface ?: return
        if (show) current.showCard(card) else current.updateCard(card)
    }

    /** Home: "New message", then one row per chat — name on top, last message under it. */
    private fun inboxCard(step: Step.Inbox): NexusCard {
        val rows = buildList {
            add(NexusCardLine(text = text.newMessage, selected = flow.selected == 0))
            step.conversations.take(MAX_ROWS - 1).forEachIndexed { index, chat ->
                add(
                    NexusCardLine(
                        text = fit(chat.title, LIST_CHARS),
                        sub = chat.last?.let { fit(preview(chat, it), PREVIEW_CHARS) },
                        selected = flow.selected == index + 1,
                    ),
                )
            }
            if (step.conversations.isEmpty()) {
                // The commonest empty inbox is a missing permission, not a quiet phone: say which.
                val message = if (ZapSendService.isNotificationAccessGranted(this@ZapPluginService)) text.emptyInbox else text.noAccess
                add(NexusCardLine(text = message, tone = if (message == text.noAccess) NexusRowTone.ALERT else NexusRowTone.DIM))
            }
        }
        return NexusCard(title = text.inboxTitle, lines = emptyList(), richLines = rows)
    }

    /** One chat: its latest messages, then Reply · Listen again. Reply is always there. */
    private fun threadCard(chat: ZapConversation): NexusCard {
        val rows = buildList {
            chat.messages.takeLast(THREAD_MESSAGES).forEach { message ->
                add(
                    NexusCardLine(
                        text = fit(message.text),
                        badge = badgeFor(chat, message),
                        tone = NexusRowTone.BODY,
                    ),
                )
            }
            listOf(text.reply, text.listenAgain).forEachIndexed { index, label ->
                add(NexusCardLine(text = label, selected = flow.selected == index))
            }
            if (ttsProblem) add(NexusCardLine(text = text.voiceHelp, tone = NexusRowTone.DIM))
        }
        return NexusCard(title = fitTitle(chat.title), lines = emptyList(), richLines = rows, handlesBack = true)
    }

    /** New message: "Say the name" first, then recent people for one tap. */
    private fun newMessageCard(step: Step.NewMessage): NexusCard {
        val rows = buildList {
            add(NexusCardLine(text = text.sayName, selected = flow.selected == 0))
            step.recent.take(MAX_ROWS - 2).forEachIndexed { index, contact ->
                add(NexusCardLine(text = fit(contact.name, LIST_CHARS), selected = flow.selected == index + 1))
            }
            if (step.recent.isEmpty()) add(NexusCardLine(text = text.noRecent, tone = NexusRowTone.DIM))
        }
        return NexusCard(title = text.newMessage, lines = emptyList(), richLines = rows, handlesBack = true)
    }

    private fun senderLabel(message: ZapMessage): String =
        if (message.sender == ZapInbox.ME) text.me else message.sender

    /** In a one-to-one chat only the wearer's own lines need a label; in a group every line does. */
    private fun badgeFor(chat: ZapConversation, message: ZapMessage): String? = when {
        message.sender == ZapInbox.ME -> text.me
        chat.isGroup -> message.sender.take(MAX_BADGE)
        else -> null
    }

    private fun preview(chat: ZapConversation, message: ZapMessage): String = when {
        message.sender == ZapInbox.ME -> "${text.me}: ${message.text}"
        chat.isGroup -> "${message.sender}: ${message.text}"
        else -> message.text
    }

    private fun doneCard(step: Step.Done): NexusCard {
        val status = when {
            step.outcome == SendOutcome.SENT -> text.sent
            step.outcome == SendOutcome.OPENED_FOR_THUMB -> text.openedForThumb
            step.outcome == SendOutcome.WAITING_FOR_UNLOCK -> text.waitingUnlock
            step.outcome == SendOutcome.NOT_INSTALLED -> text.notInstalled
            step.outcome == SendOutcome.FAILED -> text.failed
            step.note == Note.NO_CONTACT -> text.noContact(step.spoken.orEmpty())
            step.note == Note.NO_NUMBER -> text.noNumber
            step.note == Note.REPLY_ON_PHONE -> text.replyOnPhone
            step.note == Note.NO_SPEECH -> text.noSpeech
            else -> text.speechUnavailable
        }
        val rows = buildList {
            step.body?.takeIf { it.isNotBlank() }?.let { add(NexusCardLine(text = fit(it), tone = NexusRowTone.BODY)) }
            val problem = step.outcome != SendOutcome.SENT
            add(NexusCardLine(text = fit(status), tone = if (problem) NexusRowTone.ALERT else NexusRowTone.NORMAL))
            if (step.note == Note.SPEECH_UNAVAILABLE) add(NexusCardLine(text = text.voiceHelp, tone = NexusRowTone.DIM))
            if (BuildConfig.DEBUG && step.note != null) {
                speechDetail?.let { add(NexusCardLine(text = fit("test: $it"), tone = NexusRowTone.DIM)) }
            }
            add(NexusCardLine(text = if (problem) text.tryAgain else text.newMessage, selected = true))
        }
        return NexusCard(
            title = fitTitle(step.recipient?.let { text.to(it.name) } ?: text.appTitle),
            lines = emptyList(),
            richLines = rows,
            handlesBack = true,
        )
    }

    private fun fit(value: String, max: Int = MAX_LINE) = if (value.length <= max) value else value.take(max - 1) + "…"
    private fun fitTitle(value: String) = if (value.length <= MAX_TITLE) value else value.take(MAX_TITLE - 1) + "…"

    private companion object {
        const val TAG = "Zap"
        const val SURFACE_ID = "zap"
        const val BUSY_RETRY_MS = 700L
        const val SHORTLIST = 8
        const val MAX_ROWS = 64
        const val MAX_LINE = 240
        const val MAX_TITLE = 48
        const val MAX_BADGE = 24
        /** Same widths Relay measured on the optics for list rows and their sub line. */
        const val LIST_CHARS = 26
        const val PREVIEW_CHARS = 40
        const val THREAD_MESSAGES = 6
    }
}
