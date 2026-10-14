package io.alema.zap

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusCardLine
import com.anezium.rokidbus.client.plugin.NexusImage
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusReader
import com.anezium.rokidbus.client.plugin.NexusReaderAnchor
import com.anezium.rokidbus.client.plugin.NexusReaderSegment
import com.anezium.rokidbus.client.plugin.NexusReaderSegmentKind
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
import io.alema.zap.core.Step
import io.alema.zap.core.Recipient
import io.alema.zap.core.RecentPeople
import io.alema.zap.core.Screen
import io.alema.zap.core.ScreenMaker
import io.alema.zap.core.Segment
import io.alema.zap.core.Tone
import io.alema.zap.core.ZapText

/**
 * Zap's Rokid Nexus plug.
 *
 * The logic lives in the pure core: [ComposeFlow] decides the steps and
 * [ScreenMaker] what each one shows. This class only carries Nexus input,
 * speech and voice to the flow, and draws its screens as Nexus cards and
 * images. The standalone glasses app is another plug over the same core.
 */
class ZapPluginService : NexusPluginService() {
    private val main = Handler(Looper.getMainLooper())
    private val text = ZapText()
    private val screens = ScreenMaker(text)
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
            // Strictly who you talked to lately, newest first — no favourites, no frequency mix.
            recentPeople = { RecentPeople.build(ZapInbox.list(this), contacts.directory(), SHORTLIST) },
        )
        flow.open(ZapInbox.list(this))
        unobserveInbox = ZapInbox.observe {
            if (!::flow.isInitialized || surface == null) return@observe
            flow.refresh(ZapInbox.list(this))
            render()
        }
        surface = nexusSurfaceSession(SURFACE_ID)
        shown = Shown.NOTHING
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
                is Effect.SpeakMessage -> speak(text.heard(screens.senderLabel(effect.message), effect.message.text))
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

    /** What the glasses show now: switching between card, reader and photo needs show, not update. */
    private enum class Shown { NOTHING, CARD, READER, PHOTO }
    private var shown = Shown.NOTHING

    /** What to show comes from the core; this plug only turns it into Nexus cards and images. */
    private fun render(show: Boolean = false) {
        val status = ScreenMaker.Status(
            notificationAccess = ZapSendService.isNotificationAccessGranted(this),
            voiceTrouble = ttsProblem,
            debugLine = speechDetail.takeIf { BuildConfig.DEBUG },
        )
        when (val screen = screens.make(flow.step, flow.selected, status)) {
            is Screen.Card -> showCard(screen, show)
            is Screen.Reader -> showReader(screen, show)
            is Screen.Picture -> showPicture(screen)
        }
    }

    private fun showCard(card: Screen.Card, show: Boolean) {
        val current = surface ?: return
        val nexus = card.toNexus()
        if (show || shown != Shown.CARD) current.showCard(nexus) else current.updateCard(nexus)
        shown = Shown.CARD
    }

    /** A whole chat, every message in full: the glasses wrap and scroll it. */
    private fun showReader(reader: Screen.Reader, show: Boolean) {
        val current = surface ?: return
        val nexus = NexusReader(
            title = reader.title,
            footer = reader.footer,
            contentKey = reader.contentKey,
            segments = reader.segments.map { segment ->
                NexusReaderSegment(
                    kind = when (segment.kind) {
                        Segment.Kind.HEADER -> NexusReaderSegmentKind.HEADER
                        Segment.Kind.PROSE -> NexusReaderSegmentKind.PROSE
                        Segment.Kind.ASIDE -> NexusReaderSegmentKind.ASIDE
                    },
                    text = segment.text,
                    emphasis = segment.emphasis,
                )
            },
            anchor = NexusReaderAnchor.BOTTOM, // a chat opens on its newest message
        )
        val result = if (show || shown != Shown.READER) current.showReader(nexus) else current.updateReader(nexus)
        if (result != NexusSdkResult.SENT) Log.w(TAG, "reader refused: $result")
        shown = Shown.READER
    }

    /** One photo, full screen; swipe for the others in this chat. */
    private fun showPicture(picture: Screen.Picture) {
        val current = surface ?: return
        val photo = ZapPhotos.load(this, picture.photoKey)
        if (photo == null) {
            current.showCard(picture.missing.toNexus())
            shown = Shown.CARD
            return
        }
        val image = NexusImage(
            contentKey = "zap-${picture.photoKey}",
            mimeType = "image/jpeg",
            pixelWidth = photo.width,
            pixelHeight = photo.height,
            title = picture.title.take(120),
            caption = picture.caption?.take(240),
            footer = picture.footer,
            handlesBack = true,
        )
        val result = if (shown == Shown.PHOTO) current.updateImage(image, photo.bytes) else current.showImage(image, photo.bytes)
        shown = Shown.PHOTO
        // Swiping fast can outrun the glasses' image rate limit: show the photo the wearer stopped on.
        if (result == NexusSdkResult.IMAGE_RATE_LIMITED) {
            main.removeCallbacks(retryPhoto)
            main.postDelayed(retryPhoto, PHOTO_RETRY_MS)
        }
    }

    private val retryPhoto = Runnable { if (::flow.isInitialized && flow.step is Step.Photo) render() }

    private fun Screen.Card.toNexus() = NexusCard(
        title = title,
        lines = emptyList(),
        richLines = rows.map { row ->
            NexusCardLine(text = row.text, sub = row.sub, badge = row.badge, tone = row.tone.toNexus(), selected = row.selected)
        },
        subtitle = subtitle,
        handlesBack = handlesBack,
    )

    private fun Tone.toNexus() = when (this) {
        Tone.NORMAL -> NexusRowTone.NORMAL
        Tone.BODY -> NexusRowTone.BODY
        Tone.DIM -> NexusRowTone.DIM
        Tone.ALERT -> NexusRowTone.ALERT
    }

    private companion object {
        const val TAG = "Zap"
        const val SURFACE_ID = "zap"
        const val BUSY_RETRY_MS = 700L
        const val SHORTLIST = 8
        const val PHOTO_RETRY_MS = 450L
    }
}
