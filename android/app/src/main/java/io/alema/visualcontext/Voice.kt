package io.alema.visualcontext

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Spec 1i: audio-only mode. Every card has a spoken version; confirmations become sounds.
 * Low volume by default (open-ear speakers are heard up close): names never louder than a whisper.
 */
class Voice(ctx: Context) {
    private var ready = false
    private val tts = TextToSpeech(ctx.applicationContext) { ok -> ready = ok == TextToSpeech.SUCCESS }.also { t ->
        t.language = Locale("pt", "BR"); t.setSpeechRate(1.05f)
    }
    private val tones = try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35) } catch (_: Exception) { null }

    fun say(text: String, interrupt: Boolean = false) {
        if (!ready || text.isBlank()) return
        val p = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.45f) }
        tts.speak(text, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, p, text.hashCode().toString())
    }

    /** A soft sound before a person's card. */
    fun soft() { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
    /** Recording starts / ends: one short tone. */
    fun recStart() { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
    fun recEnd() { tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120) }
    /** Saved: one tick. */
    fun tick() { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 60) }

    fun close() { try { tts.stop(); tts.shutdown() } catch (_: Exception) {}; tones?.release() }

    companion object {
        /** "tela" | "tela_voz" | "voz" */
        fun output(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).getString("output", "tela") ?: "tela"
        fun setOutput(ctx: Context, v: String) { ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE).edit().putString("output", v).apply() }
    }
}
