package io.alema.visualcontext

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.alema.facecore.NameSpotter
import org.json.JSONObject

/**
 * Spec 1h: every conversation is transcribed, without the assistant. While someone is in front of you and
 * the assistant (Falar) is off, the phone listens with its own on-device recognizer (free, offline when the
 * phone supports it), catches names ("prazer, Carla") and keeps the conversation for the encounter.
 * It never speaks. Ends 60 s after the last face; under 20 s, or cancelled, nothing is kept. Main thread.
 */
class Ears(private val ctx: Context, private val onName: (String) -> Unit, private val onState: (Boolean) -> Unit, private val onQuestion: (String) -> Unit = {}) {
    /**
     * Standby (Marcus, 08/11): between encounters the phone keeps listening, offline, for a question to the
     * memory ("o que eu fiz hoje?") or a call to the assistant ("assistente, …"). Nothing else is kept.
     */
    var standby = false
        set(v) { field = v; if (v && startedAt == 0L && !paused) start() else if (!v && startedAt == 0L) stopRec() }
    private val inEncounter get() = startedAt > 0L
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var on = false
    private var paused = false
    private val text = StringBuilder()
    private var startedAt = 0L
    private var lastFaceAt = 0L
    var who: String? = null; private set
    private var lastName = ""
    private var lastNameAt = 0L

    /** Called on every face scan with the person in front (name if known, else null). */
    fun faceSeen(name: String?) {
        val now = System.currentTimeMillis()
        lastFaceAt = now
        if (name != null) who = name
        if (startedAt == 0L) startedAt = now
        if (!on && !paused) start()
        main.removeCallbacks(endCheck); main.postDelayed(endCheck, 60_000)
    }

    /** When someone last spoke near the glasses (a conversation, not a poster or a doll). */
    @Volatile var lastSpeechAt = 0L

    /** The screen goes away: stop listening and give back any muted sound. */
    fun close() { standby = false; stopRec() }

    /** The assistant takes the microphone. */
    fun pause() { paused = true; stopRec() }
    fun resume() { paused = false; if ((startedAt > 0 && System.currentTimeMillis() - lastFaceAt < 60_000) || standby) start() }

    /** ✕ during the countdown: the whole buffer goes. */
    fun discard() { text.setLength(0); startedAt = 0; who = null; if (!standby) stopRec() }

    fun finish() { main.removeCallbacks(endCheck); save(); if (!standby) stopRec() }

    private val endCheck = Runnable { if (System.currentTimeMillis() - lastFaceAt >= 59_000) { save(); if (!standby) stopRec() } }

    private fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        on = true; onState(true)
        listenOnce()
    }

    private fun stopRec() { on = false; onState(false); try { rec?.cancel(); rec?.destroy() } catch (_: Exception) {}; rec = null; quiet(false); musicQuiet(false) }

    /**
     * Marcus, 08/11: with standby on it "fica o tempo todo fazendo bips". Android's recognizer plays an earcon at
     * every start and stop, and listening restarts after each phrase or silence. While Ears listens, the system stream
     * is muted (notifications keep their sound); the music stream only for the instant the recognizer starts, and only
     * when nothing is playing (so the assistant's voice and music are never cut). Everything is restored on stop.
     */
    private val am by lazy { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var muted = false
    private var musicMuted = false
    private fun quiet(on: Boolean) {
        if (on == muted) return; muted = on
        for (st in intArrayOf(AudioManager.STREAM_SYSTEM)) // notifications keep their sound (WhatsApp, calls)
            try { am.adjustStreamVolume(st, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
    }
    private fun musicQuiet(on: Boolean) {
        if (on == musicMuted) return
        if (on && am.isMusicActive) return
        musicMuted = on
        try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
    }
    private val unmuteMusic = Runnable { musicQuiet(false) }

    private fun listenOnce() {
        if (!on || paused) return
        val r = rec ?: (if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            else SpeechRecognizer.createSpeechRecognizer(ctx)).also { rec = it; it.setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        quiet(true); musicQuiet(true); main.removeCallbacks(unmuteMusic); main.postDelayed(unmuteMusic, 700)
        try { r.startListening(i) } catch (_: Exception) { main.postDelayed(::listenOnce, 1500) }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { heard(it, final = true) }
            main.postDelayed(::listenOnce, 150)
        }
        override fun onPartialResults(b: Bundle?) { lastSpeechAt = SystemClock.elapsedRealtime(); b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard(it, final = false) } }
        override fun onError(error: Int) { main.postDelayed(::listenOnce, when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1200
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 600 // silence: restart a bit slower
            else -> 300 }) }
        override fun onReadyForSpeech(p: Bundle?) { main.removeCallbacks(unmuteMusic); main.postDelayed(unmuteMusic, 250) }
        override fun onBeginningOfSpeech() { lastSpeechAt = SystemClock.elapsedRealtime() }
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private fun heard(s: String, final: Boolean) {
        NameSpotter.find(s, Backend.meName(ctx))?.let { n ->
            val now = System.currentTimeMillis()
            if (!(n.equals(lastName, true) && now - lastNameAt < 20_000)) { lastName = n; lastNameAt = now; onName(n) }
        }
        if (!inEncounter) { if (final && standby && QUESTION.containsMatchIn(s.lowercase())) onQuestion(s.trim()); return } // standby keeps nothing
        if (final) text.append(s.trim()).append('\n')
    }

    companion object {
        /** What counts as talking to the glasses while nobody is in front. */
        val QUESTION = Regex("""^(ei|ô|oi|olha|escuta)?[,\s]*(assistente|visual\s?context|visualcontext|óculos)\b|\b(reviver (o )?(meu )?dia|reviver ontem|como foi (o )?meu dia|me conta (o )?meu dia|o que (eu )?(fiz|anotei|combinei|guardei)|quem (eu )?(encontrei|vi)|quando (foi que )?(eu )?(vi|encontrei|falei)|onde (eu )?deixei|cad[eê] (meu|minha|o|a|as|os)\b|última vez que|que horas são|que dia é hoje|que música)""")
    }

    private fun save() {
        val t = text.toString().trim(); val at = startedAt; val person = who
        text.setLength(0); startedAt = 0; who = null
        if (t.isEmpty() || lastFaceAt - at < 20_000) return
        Thread {
            val body = JSONObject().put("action", "moment").put("transcript", t).put("capture_mode", "encounter")
                .put("at", java.time.Instant.ofEpochMilli(at).toString()).put("context", "encontro")
            if (person != null) body.put("person_name", person)
            Where.location(ctx)?.let { loc -> body.put("lat", loc.latitude).put("lng", loc.longitude); Where.placeLabel(ctx, loc)?.let { body.put("place", it) } }
            Describe.memoryVersion(ctx, t)?.let { (sum, _) -> if (sum.isNotBlank()) body.put("summary", sum) }
            Moments.send(ctx, body)
        }.start()
    }
}
