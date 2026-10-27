package io.alema.vclive

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import io.alema.livecore.LiveProtocol
import io.alema.livecore.LiveSession

/** "Ouvir": a short Gemini Live session that says one sentence in the chosen voice, on the phone. */
class VoicePreview(private val ctx: Context, private val status: (String) -> Unit) : LiveSession.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val speaker = Speaker()
    private val live = LiveSession(this)
    private var voice = ""

    fun play(key: String, voiceName: String) {
        stop()
        voice = voiceName
        if (key.length < 20) { status("cole a chave do Gemini primeiro"); return }
        status("conectando…")
        speaker.start()
        Thread {
            val model = LiveConfig.model(ctx) ?: (live.listModels(key)?.let { LiveProtocol.pickLiveModel(it) } ?: LiveProtocol.MODEL)
                .also { LiveConfig.saveModel(ctx, it) }
            main.post { live.connect(key, voiceName, null, model) }
        }.start()
    }

    fun stop() { live.close(); speaker.stop() }

    override fun onEvents(events: List<LiveProtocol.Event>) {
        for (e in events) when (e) {
            is LiveProtocol.Event.SetupComplete -> {
                live.send(LiveProtocol.text("Diga exatamente esta frase, em português do Brasil, com naturalidade: Oi! Eu sou a voz $voice. É assim que eu vou falar com você nos óculos."))
                main.post { status("tocando $voice…") }
            }
            is LiveProtocol.Event.Audio -> speaker.play(Base64.decode(e.base64Pcm24k, Base64.DEFAULT))
            is LiveProtocol.Event.TurnComplete -> main.postDelayed({ live.close(); main.postDelayed({ speaker.stop(); status("") }, 4000) }, 300)
            is LiveProtocol.Event.Error -> main.post { status(e.message.take(80)) }
            else -> Unit
        }
    }

    override fun onClosed(reason: String, byUs: Boolean) { if (!byUs) main.post { status(reason) } }
}
