package io.alema.vcsolo

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/** One WebSocket to the Gemini Live API, straight from the glasses. */
class LiveSession(private val listener: Listener) {
    interface Listener {
        fun onEvents(events: List<LiveProtocol.Event>)
        fun onClosed(reason: String, byUs: Boolean)
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    @Volatile private var closingByUs = false
    @Volatile var open = false
        private set

    fun connect(apiKey: String, voice: String, resumeHandle: String?, model: String = LiveProtocol.MODEL) {
        closingByUs = false
        val req = Request.Builder().url("${LiveProtocol.URL}?key=$apiKey").build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            // After a resume, the old socket may still report late; only the current one counts.
            private fun current(s: WebSocket) = s === ws
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!current(webSocket)) return
                open = true
                webSocket.send(LiveProtocol.setup(voice, resumeHandle, model))
            }
            override fun onMessage(webSocket: WebSocket, text: String) { if (current(webSocket)) listener.onEvents(LiveProtocol.parse(text)) }
            // The Live API sends its JSON in binary frames.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { if (current(webSocket)) listener.onEvents(LiveProtocol.parse(bytes.utf8())) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!current(webSocket)) return
                open = false
                listener.onClosed(reason.ifBlank { "conexão encerrada ($code)" }, closingByUs)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!current(webSocket)) return
                open = false
                val why = when (response?.code) {
                    400, 403 -> "chave do Gemini recusada"
                    429 -> "limite de uso do Gemini"
                    null -> if (closingByUs) "fechado" else "sem conexão com o Gemini"
                    else -> "erro ${response.code} do Gemini"
                }
                listener.onClosed(why, closingByUs)
            }
        })
    }

    fun send(json: String): Boolean = open && ws?.send(json) == true

    fun close() {
        closingByUs = true
        val old = ws
        ws = null
        open = false
        old?.close(1000, "fim")
        if (old != null) listener.onClosed("fechado", true)
    }
}
