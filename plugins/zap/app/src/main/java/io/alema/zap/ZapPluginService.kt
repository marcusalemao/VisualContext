package io.alema.zap

import android.database.ContentObserver
import android.os.Handler
import android.provider.MediaStore
import com.anezium.rokidbus.shared.plugin.PluginCapability
import org.json.JSONObject
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
import com.anezium.rokidbus.client.plugin.NexusSkillInvocation
import com.anezium.rokidbus.client.plugin.NexusSnapshotCallbacks
import com.anezium.rokidbus.client.plugin.NexusSnapshotError
import com.anezium.rokidbus.client.plugin.NexusSnapshotSession
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
import io.alema.zap.core.BriefDone
import io.alema.zap.core.BriefState
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
    /** Rebuilt on every open: the wearer may have changed the visible area in settings. */
    private var screens = ScreenMaker(text)
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
            gallery = { ZapGallery.recent(this) },
            // On without a key still opens the summary card: it says what is missing and where to fix it.
            briefs = { ZapAi.enabled(this) },
            today = { chats -> io.alema.zap.core.DigestRules.chatsFor(chats, System.currentTimeMillis(), java.time.ZoneId.systemDefault()) },
            reactions = { ZapSettings(this).reactions(java.util.Locale.getDefault().language == "pt") },
        )
        flow.open(ZapInbox.list(this))
        unobserveInbox = ZapInbox.observe {
            if (!::flow.isInitialized || surface == null) return@observe
            flow.refresh(ZapInbox.list(this))
            render()
        }
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, galleryChanged)
        screens = ScreenMaker(text, reach = ZapSettings(this).reachPx())
        // A photo taken with the glasses button while Zap is open is offered here, not by the notice.
        ZapNotifier.inAppOffer = { key -> main.post { offerPhotoHere(key) } }
        preferInstantSync()
        surface = nexusSurfaceSession(SURFACE_ID)
        shown = Shown.NOTHING
        render(show = true)
    }

    // --- photos and videos from the glasses --------------------------------

    /**
     * Ask Nexus' photo sync to bring over what the glasses shot since last time.
     * Each photo takes a few seconds over Bluetooth; as they land in the gallery
     * the list being browsed grows by itself (see [galleryChanged]).
     */
    private fun pullGlassesMedia() {
        val client = nexusClient ?: return
        if (!client.hasCapability(PluginCapability.MEDIA_SYNC)) return
        val sent = client.send(MEDIA_SYNC_NOW, java.util.UUID.randomUUID().toString(), JSONObject().put("version", MEDIA_SYNC_VERSION))
        Log.i(TAG, "glasses media pull sent=$sent")
    }

    /**
     * A photo with the glasses' camera, right now. Nexus asks the wearer the
     * first time; the JPEG comes back over Bluetooth in a second or two.
     */
    private fun takeSnapshot() {
        snapshot?.cancel()
        val session = runCatching {
            nexusSnapshotSession(object : NexusSnapshotCallbacks {
                override fun onSnapshotCaptured(jpeg: ByteArray) {
                    val key = ZapGallery.saveSnapshot(this@ZapPluginService, jpeg)
                    main.post {
                        if (!::flow.isInitialized) return@post
                        if (key != null) flow.onSnapshot(key) else flow.onSnapshotFailed()
                        render()
                    }
                }

                override fun onSnapshotError(error: NexusSnapshotError) {
                    Log.w(TAG, "snapshot failed: $error")
                    main.post {
                        if (!::flow.isInitialized || error == NexusSnapshotError.CANCELLED) return@post
                        flow.onSnapshotFailed()
                        render()
                    }
                }
            })
        }.onFailure { Log.w(TAG, "no snapshot session", it) }.getOrNull()
        snapshot = session
        val result = session?.capture()
        Log.i(TAG, "snapshot capture=$result")
        if (result != NexusSdkResult.SENT) {
            // No camera permission yet, or the link is down: say so instead of waiting forever.
            main.post {
                flow.onSnapshotFailed()
                render()
            }
        }
    }

    private var snapshot: NexusSnapshotSession? = null

    // --- WhatsApp voice messages -------------------------------------------

    /** Find the file (off the main thread: it lists a folder), then play it on the glasses' audio. */
    private fun playAudio(effect: Effect.PlayAudio) {
        stopSpeaking()
        when {
            !ZapVoiceNotes.hasAccess(this) -> return audioFailed(Note.NO_AUDIO_ACCESS)
            !ZapVoiceNotes.privateOutput(this) -> return audioFailed(Note.NO_HEADPHONES)
        }
        Thread {
            val uri = ZapVoiceNotes.find(this, effect.message)
            main.post {
                if (!::flow.isInitialized || (flow.step as? Step.Audio)?.message != effect.message) return@post
                if (uri == null) return@post audioFailed(Note.AUDIO_NOT_FOUND)
                ZapVoiceNotes.play(
                    this,
                    uri,
                    onStarted = { main.post { flow.onAudioStarted(); render() } },
                    onDone = { main.post { flow.onAudioDone(); render() } },
                    onError = { main.post { audioFailed(Note.AUDIO_PLAY_FAILED) } },
                )
            }
        }.start()
    }

    private fun audioFailed(note: Note) {
        if (!::flow.isInitialized) return
        flow.onAudioFailed(note)
        render()
    }

    /**
     * Offering a just-taken photo only works if it reaches the phone right away;
     * Nexus' default syncs only while charging. Set "always" once, when the wearer
     * left the offer on; if they change it back in Nexus, Zap leaves it alone.
     */
    private fun preferInstantSync() {
        val client = nexusClient ?: return
        if (!ZapSettings(this).offerNewPhotos() || !client.hasCapability(PluginCapability.MEDIA_SYNC)) return
        val prefs = getSharedPreferences("zap_photo_offer", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SYNC_ALWAYS_SET, false)) return
        val sent = client.send(
            MEDIA_SYNC_SETTINGS,
            java.util.UUID.randomUUID().toString(),
            JSONObject().put("version", MEDIA_SYNC_VERSION).put("syncMode", "always"),
        )
        Log.i(TAG, "photo sync set to always sent=$sent")
        if (sent) prefs.edit().putBoolean(KEY_SYNC_ALWAYS_SET, true).apply()
    }

    private val galleryChanged = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (!::flow.isInitialized || flow.step !is Step.ChoosePhoto) return
            main.removeCallbacks(refreshGallery)
            main.postDelayed(refreshGallery, GALLERY_SETTLE_MS)
        }
    }

    private val refreshGallery = Runnable {
        if (!::flow.isInitialized || flow.step !is Step.ChoosePhoto) return@Runnable
        val before = flow.step
        flow.refreshPhotos(ZapGallery.recent(this))
        if (flow.step != before) render()
    }

    /**
     * The wearer's assistant (Anezium's Assistant, with their own AI) asks Zap
     * something: answered from the inbox on the phone, no AI of Zap's own.
     */
    override fun onNexusSkillInvoked(invocation: NexusSkillInvocation) {
        ZapSkillsHost.answer(this, invocation)
    }

    override fun onNexusClose() {
        ZapVoiceNotes.stop()
        snapshot?.cancel()
        snapshot = null
        runCatching { contentResolver.unregisterContentObserver(galleryChanged) }
        main.removeCallbacks(refreshGallery)
        ZapNotifier.inboxOpen = false
        ZapNotifier.inAppOffer = null
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
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> run(flow.move(1))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> run(flow.move(-1))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> run(flow.tap())
            KeyEvent.KEYCODE_BACK -> run(flow.back())
        }
    }

    private fun offerPhotoHere(key: String) {
        if (!::flow.isInitialized || surface == null) return
        val recents = ZapInbox.list(this)
            .filter { !it.isGroup && it.waDigits != null }
            .sortedByDescending { it.lastTimeMs }
            .take(2)
            .map { it.recipient() }
        if (flow.offerPhoto(key, recents)) {
            getSharedPreferences("zap_photo_offer", MODE_PRIVATE).edit()
                .putLong(ZapPhotoWatcher.KEY_SHOWN, System.currentTimeMillis()).apply()
            render(show = true)
        }
    }

    private fun run(effects: List<Effect>) {
        effects.forEach { effect ->
            when (effect) {
                Effect.StartListening -> startListening()
                Effect.StopListening -> stopListening()
                is Effect.SpeakDraft -> speak(text.spoken(effect.recipient.name, effect.body))
                is Effect.SpeakMessage -> speak(text.heard(screens.senderLabel(effect.message), effect.message.text))
                is Effect.SpeakText -> speak(effect.text)
                is Effect.Summarize -> summarize(effect)
                is Effect.AddToCalendar -> {
                    val ok = ZapCalendar.add(this, effect.event, effect.chatTitle)
                    flow.onBriefAction(BriefDone.CALENDAR, ok)
                }
                is Effect.RemindToReply -> {
                    val summary = ((flow.step as? Step.Brief)?.state as? BriefState.Ready)?.brief?.summary
                    val ok = ZapReminders.schedule(this, effect.conversation, effect.minutes, summary)
                    flow.onBriefAction(BriefDone.REMIND, ok)
                }
                is Effect.ScheduleTask -> {
                    val ok = ZapReminders.scheduleTask(this, effect.task, effect.conversation, effect.laterMinutes)
                    flow.onBriefAction(BriefDone.TASK, ok)
                }
                is Effect.OpenMap -> {
                    if (!ZapPlaces.open(this, effect.place, effect.chatTitle)) flow.onPlaceSaved("", ok = false)
                }
                is Effect.SavePlace -> flow.onPlaceSaved(effect.place.address, ZapPlaces.save(this, effect.place, effect.chatTitle))
                is Effect.SummarizeDay -> ZapAi.digest(this, effect.chats) { state ->
                    main.post {
                        if (!::flow.isInitialized || surface == null) return@post
                        flow.onDigest(state)
                        render()
                    }
                }
                Effect.StopSpeaking -> stopSpeaking()
                is Effect.Send -> send(effect.recipient, effect.body, effect.photo)
                Effect.PullGlassesMedia -> pullGlassesMedia()
                Effect.TakeSnapshot -> takeSnapshot()
                is Effect.PlayAudio -> playAudio(effect)
                Effect.StopAudio -> ZapVoiceNotes.stop()
                Effect.Close -> {
                    surface?.hide()
                    return
                }
            }
        }
        render()
    }

    // --- summaries -------------------------------------------------------

    /** Ask the wearer's AI; the card says "Summarizing…" meanwhile and fills in when the answer lands. */
    private fun summarize(effect: Effect.Summarize) {
        ZapAi.summarize(this, effect.conversation, effect.messages) { state ->
            main.post {
                if (!::flow.isInitialized || surface == null) return@post
                flow.onBrief(effect.conversation.key, state)
                render()
            }
        }
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

    private fun send(recipient: Recipient, body: String, photo: String?) {
        recipient.contactId?.let { ZapRecents(this).touch(it) }
        recipient.digits?.let { ZapRecents(this).touch(ZapPeople.idFor(it)) }
        ZapSender.deliver(this, recipient, body, photo) { outcome ->
            if (!::flow.isInitialized) return@deliver
            flow.onSendOutcome(outcome)
            render()
        }
    }

    // --- drawing ---------------------------------------------------------

    /** What the glasses show now: switching between card, reader and photo needs show, not update. */
    private enum class Shown { NOTHING, CARD, READER, PHOTO }
    private var shown = Shown.NOTHING
    private var lastSubtitle: String? = null
    private var lastFooter: String? = null

    /** What to show comes from the core; this plug only turns it into Nexus cards and images. */
    private fun render(show: Boolean = false) {
        val status = ScreenMaker.Status(
            notificationAccess = ZapSendService.isNotificationAccessGranted(this),
            voiceTrouble = ttsProblem,
            aiHint = !ZapAi.decided(this),
            debugLine = speechDetail.takeIf { BuildConfig.DEBUG },
        )
        // A chat open on the lens has been seen: it leaves the unread count, like on the phone.
        when (val step = flow.step) {
            is Step.Thread -> ZapInbox.markRead(this, step.conversation.key)
            is Step.Read -> ZapInbox.markRead(this, step.conversation.key)
            // The summary counts as seeing the messages: they leave the unread count.
            is Step.Brief -> ZapInbox.markRead(this, step.conversation.key)
            else -> Unit
        }
        when (val screen = screens.make(flow.step, flow.selected, status)) {
            is Screen.Card -> showCard(screen, show)
            is Screen.Reader -> showReader(screen, show)
            is Screen.Picture -> showPicture(screen)
        }
    }

    private fun showCard(card: Screen.Card, show: Boolean) {
        val current = surface ?: return
        val nexus = card.toNexus()
        // Nexus merges an update into the card on screen: a subtitle or footer left
        // out stays from before ("Listening…" stuck on the inbox). Dropping one needs a fresh card.
        val drops = (lastSubtitle != null && card.subtitle == null) || (lastFooter != null && card.footer == null)
        if (show || shown != Shown.CARD || drops) current.showCard(nexus) else current.updateCard(nexus)
        shown = Shown.CARD
        lastSubtitle = card.subtitle
        lastFooter = card.footer
    }

    /** A whole chat, every message in full: the glasses wrap and scroll it. */
    private fun showReader(reader: Screen.Reader, show: Boolean) {
        val current = surface ?: return
        val nexus = NexusReader(
            title = reader.title,
            subtitle = reader.subtitle,
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
            anchor = NexusReaderAnchor.TOP, // newest first: a chat opens on its newest message, at the top
        )
        val result = if (show || shown != Shown.READER) current.showReader(nexus) else current.updateReader(nexus)
        if (result != NexusSdkResult.SENT) Log.w(TAG, "reader refused: $result")
        shown = Shown.READER
    }

    /** One photo, full screen; swipe for the others in this chat. */
    private fun showPicture(picture: Screen.Picture) {
        val current = surface ?: return
        val photo = ZapPhotos.loadAny(this, picture.photoKey)
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
            NexusCardLine(text = row.text, sub = row.sub, badge = row.badge, trail = row.trail, tone = row.tone.toNexus(), selected = row.selected)
        },
        subtitle = subtitle,
        footer = footer,
        handlesBack = handlesBack,
        // The glasses hub merges a card into the previous one with the same content key and
        // fills an absent subtitle/footer from it ("Sending…" stuck on the inbox). A key that
        // says which shell lines this card has makes a card without them a fresh card.
        contentKey = "zap-card-" + (if (subtitle != null) "s" else "") + (if (footer != null) "f" else ""),
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
        const val GALLERY_SETTLE_MS = 600L
        const val MEDIA_SYNC_NOW = "/mediasync/now"
        const val MEDIA_SYNC_SETTINGS = "/mediasync/settings"
        private const val KEY_SYNC_ALWAYS_SET = "sync_always_set"
        const val MEDIA_SYNC_VERSION = 2
    }
}
