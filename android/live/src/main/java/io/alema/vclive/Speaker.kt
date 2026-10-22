package io.alema.vclive

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Gemini's voice (24 kHz PCM16 mono) on the phone's audio output — the glasses, if they are the Bluetooth device. */
class Speaker {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    @Volatile private var playingUntil = 0L
    private var track: AudioTrack? = null

    val speaking: Boolean get() = queue.isNotEmpty() || SystemClock.elapsedRealtime() < playingUntil + 400

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
                playingUntil = SystemClock.elapsedRealtime() + chunk.size / 48L
            }
            t.stop(); t.release()
        }, "gemini-voice").start()
    }

    fun play(pcm: ByteArray) { if (running) queue.offer(pcm) }

    fun flush() {
        queue.clear()
        track?.let { try { it.pause(); it.flush(); it.play() } catch (_: Exception) {} }
        playingUntil = 0
    }

    fun stop() { running = false; queue.clear() }
}
