package io.alema.vcsolo

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Glasses microphone → 16 kHz PCM16 mono chunks of 100 ms. */
class MicIn(private val onChunk: (ByteArray) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_COMMUNICATION turns on the platform echo canceller, so Gemini does not hear itself.
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
        } catch (_: Exception) { return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
        running = true
        thread = Thread({
            rec.startRecording()
            val buf = ByteArray(3200) // 100 ms
            while (running) {
                var n = 0
                while (n < buf.size && running) { val r = rec.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                if (n == buf.size) onChunk(buf.copyOf())
            }
            rec.stop(); rec.release()
        }, "mic").apply { start() }
        return true
    }

    fun stop() { running = false; thread = null }
}

/** Gemini's voice: 24 kHz PCM16 mono, played as it arrives. */
class SpeakerOut {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    @Volatile private var lastAudioAt = 0L
    private var track: AudioTrack? = null

    /** True while the assistant is talking (or finished less than 400 ms ago). */
    val speaking: Boolean get() = queue.isNotEmpty() || SystemClock.elapsedRealtime() - lastAudioAt < 400

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 9600))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        Thread({
            t.play()
            while (running) {
                val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                t.write(chunk, 0, chunk.size)
                lastAudioAt = SystemClock.elapsedRealtime() + chunk.size / 48L // ms of audio just queued
            }
            t.stop(); t.release()
        }, "speaker").start()
    }

    fun play(pcm: ByteArray) { if (running) queue.offer(pcm) }

    /** User interrupted (or Gemini said "interrupted"): drop what was not played yet. */
    fun flush() {
        queue.clear()
        track?.let { try { it.pause(); it.flush(); it.play() } catch (_: Exception) {} }
        lastAudioAt = 0
    }

    fun stop() { running = false; queue.clear() }
}
