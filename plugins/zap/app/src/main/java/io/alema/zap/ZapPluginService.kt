package io.alema.zap

import android.os.Handler
import android.os.Looper
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
import io.alema.zap.core.ZapContact

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

    override fun onNexusOpen() {
        contacts = ZapContacts(this)
        flow = ComposeFlow({ contacts.directory() })
        flow.open(contacts.shortlist(contacts.directory(), SHORTLIST))
        surface = nexusSurfaceSession(SURFACE_ID)
        render(show = true)
    }

    override fun onNexusClose() {
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
                is Effect.Speak -> speak(text.spoken(effect.contact.name, effect.body))
                Effect.StopSpeaking -> stopSpeaking()
                is Effect.Send -> send(effect.contact, effect.digits, effect.body)
                Effect.Close -> {
                    surface?.hide()
                    return
                }
            }
        }
        render()
    }

    // --- speech in -------------------------------------------------------

    private fun startListening() {
        stopListening()
        stopSpeaking() // the microphone must not take the glasses' own voice
        val generation = ++speechGeneration
        val session = nexusSpeechSession(object : NexusSpeechCallbacks {
            override fun onSpeechStarted(realtime: Boolean) = Unit
            override fun onSpeechState(state: NexusSpeechState) = Unit

            override fun onSpeechPartial(text: String) = onMain(generation) {
                flow.onSpeechPartial(text)
                render()
            }

            override fun onSpeechFinal(text: String) = onMain(generation) {
                speech = null
                run(flow.onSpeechFinal(text))
            }

            override fun onSpeechStopped(reason: NexusSpeechStopReason, error: NexusSpeechError?) =
                onMain(generation) {
                    speech = null
                    if (reason == NexusSpeechStopReason.CANCELLED || reason == NexusSpeechStopReason.COMPLETED) return@onMain
                    val note = if (reason == NexusSpeechStopReason.NO_SPEECH) Note.NO_SPEECH else Note.SPEECH_UNAVAILABLE
                    run(flow.onSpeechFailed(note))
                }
        })
        if (session == null) {
            run(flow.onSpeechFailed(Note.SPEECH_UNAVAILABLE))
            return
        }
        speech = session
        if (session.start() != NexusSdkResult.SENT) {
            speech = null
            run(flow.onSpeechFailed(Note.SPEECH_UNAVAILABLE))
        }
    }

    private fun stopListening() {
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

    private fun send(contact: ZapContact, digits: String, body: String) {
        ZapRecents(this).touch(contact.id)
        ZapSender.send(this, digits, body) { outcome ->
            if (!::flow.isInitialized) return@send
            flow.onSendOutcome(outcome)
            render()
        }
    }

    // --- drawing ---------------------------------------------------------

    private fun render(show: Boolean = false) {
        val card = when (val step = flow.step) {
            is Step.Home -> homeCard(step)
            is Step.Listening -> NexusCard(
                title = step.contact?.let { text.to(it.name) } ?: text.whoTitle,
                lines = emptyList(),
                richLines = listOf(
                    NexusCardLine(
                        text = fit(step.partial.ifBlank { if (step.contact == null) text.speakExample else text.sayMessage }),
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
                title = fitTitle(text.to(step.contact.name)),
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
                title = fitTitle(text.to(step.contact.name)),
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

    private fun homeCard(step: Step.Home): NexusCard {
        val rows = buildList {
            add(NexusCardLine(text = text.speak, sub = text.speakExample, selected = flow.selected == 0))
            step.shortlist.take(MAX_ROWS - 2).forEachIndexed { index, contact ->
                add(NexusCardLine(text = fit(contact.name), selected = flow.selected == index + 1))
            }
            if (step.shortlist.isEmpty()) add(NexusCardLine(text = text.emptyPeople, tone = NexusRowTone.DIM))
        }
        return NexusCard(title = text.appTitle, lines = emptyList(), richLines = rows, subtitle = text.newMessage)
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
            step.note == Note.NO_SPEECH -> text.noSpeech
            else -> text.speechUnavailable
        }
        val rows = buildList {
            step.body?.takeIf { it.isNotBlank() }?.let { add(NexusCardLine(text = fit(it), tone = NexusRowTone.BODY)) }
            val problem = step.outcome != SendOutcome.SENT
            add(NexusCardLine(text = fit(status), tone = if (problem) NexusRowTone.ALERT else NexusRowTone.NORMAL))
            if (step.note == Note.SPEECH_UNAVAILABLE) add(NexusCardLine(text = text.voiceHelp, tone = NexusRowTone.DIM))
            add(NexusCardLine(text = if (problem) text.tryAgain else text.newMessage, selected = true))
        }
        return NexusCard(
            title = fitTitle(step.contact?.let { text.to(it.name) } ?: text.appTitle),
            lines = emptyList(),
            richLines = rows,
            handlesBack = true,
        )
    }

    private fun fit(value: String) = if (value.length <= MAX_LINE) value else value.take(MAX_LINE - 1) + "…"
    private fun fitTitle(value: String) = if (value.length <= MAX_TITLE) value else value.take(MAX_TITLE - 1) + "…"

    private companion object {
        const val SURFACE_ID = "zap"
        const val SHORTLIST = 8
        const val MAX_ROWS = 64
        const val MAX_LINE = 240
        const val MAX_TITLE = 48
    }
}
