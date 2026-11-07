package io.alema.visualcontext

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import io.alema.livecore.LiveProtocol
import io.alema.livecore.LiveSession
import io.alema.livecore.Transcript
import io.alema.facecore.NameSpotter
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Gemini Live with the VisualContext tools, for any surface (phone emulator now, glasses later).
 * The host gives it a camera frame, the face brain and the location; it gives back what to show.
 */
class LiveBrain(private val ctx: Context, private val host: Host) : LiveSession.Listener {
    interface Host {
        fun frame(): ByteArray?                          // latest JPEG from the camera, or null
        fun faces(): FaceBrain
        fun onState(state: String)                       // "idle" | "connecting" | "listening" | "speaking" | "error: …"
        fun onTranscript(lines: List<String>, userLine: String?)
        fun onFaces(seen: List<FaceBrain.Seen>, bw: Int, bh: Int)
        fun onNamed(name: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val live = LiveSession(this).apply { faceTools = true; locationTools = true }
    private val mic = MicIn { pcm -> if (open) live.send(LiveProtocol.audio(Base64.encodeToString(if (speaker.speaking) ByteArray(pcm.size) else pcm, Base64.NO_WRAP))) }
    private val speaker = SpeakerOut()
    private val transcript = Transcript(3, 34)
    private var turnDone = true
    /** What the user has said in this turn, to catch a name ("essa é a Ana", "meu nome é Pedro"). */
    private val heard = StringBuilder()
    private var heardAt = 0L
    private var lastNamed = ""
    private var lastNamedAt = 0L
    private var resumeHandle: String? = null
    /** The conversation so far, in text (never audio): it becomes one moment in the memory when Live closes. */
    private val turns = mutableListOf<Pair<String, String>>()
    private val userTurn = StringBuilder()
    private val modelTurn = StringBuilder()
    private var talkStartedAt = 0L
    @Volatile var open = false; private set
    var lastActivity = 0L; private set

    fun start() {
        val key = Backend.geminiKey(ctx)
        if (key.length < 20) { host.onState("error: falta a chave do Gemini (Ajustes)"); return }
        host.onState("connecting")
        speaker.start()
        io.execute {
            val model = Backend.liveModel(ctx) ?: (live.listModels(key)?.let { LiveProtocol.pickLiveModel(it) } ?: LiveProtocol.MODEL).also { Backend.saveLiveModel(ctx, it) }
            main.post { live.connect(key, Backend.voiceName(ctx), resumeHandle, model) }
        }
    }

    fun stop() {
        saveConversation()
        open = false
        mic.stop(); speaker.stop(); live.close()
        host.onState("idle")
    }

    fun interrupt() { speaker.flush() }
    val speaking get() = speaker.speaking

    override fun onEvents(events: List<LiveProtocol.Event>) {
        for (e in events) {
            if (e is LiveProtocol.Event.Audio) { speaker.play(Base64.decode(e.base64Pcm24k, Base64.DEFAULT)); continue }
            main.post { handle(e) }
        }
    }

    private fun handle(e: LiveProtocol.Event) {
        when (e) {
            is LiveProtocol.Event.SetupComplete -> {
                open = true; lastActivity = SystemClock.elapsedRealtime()
                if (turns.isEmpty()) talkStartedAt = System.currentTimeMillis()
                if (!mic.start()) { host.onState("error: microfone não liberado"); return }
                host.onState("listening")
            }
            is LiveProtocol.Event.UserText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (lastActivity - heardAt > 8000) heard.setLength(0)
                heardAt = lastActivity
                heard.append(e.text); userTurn.append(e.text)
                host.onTranscript(transcript.lines(), heard.toString().trim().takeLast(40))
                spotName()
            }
            is LiveProtocol.Event.ModelText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (turnDone) { transcript.clear(); turnDone = false }
                modelTurn.append(e.text)
                transcript.add(e.text); host.onTranscript(transcript.lines(), null); host.onState("speaking")
            }
            is LiveProtocol.Event.Interrupted -> speaker.flush()
            is LiveProtocol.Event.TurnComplete -> { endTurn(); heard.setLength(0); turnDone = true; lastActivity = SystemClock.elapsedRealtime(); host.onState("listening") }
            is LiveProtocol.Event.ToolCall -> tool(e)
            is LiveProtocol.Event.Resumption -> resumeHandle = e.handle
            is LiveProtocol.Event.GoAway -> Unit
            is LiveProtocol.Event.Error -> host.onState("error: " + e.message.take(60))
            is LiveProtocol.Event.Audio -> Unit
        }
    }

    /** "não guarda isso" / "esquece o que eu disse": that exchange (and the one before) leaves the memory. */
    private fun endTurn() {
        val u = userTurn.toString().trim(); val m = modelTurn.toString().trim()
        userTurn.setLength(0); modelTurn.setLength(0)
        if (FORGET.containsMatchIn(u)) { if (turns.isNotEmpty()) turns.removeAt(turns.lastIndex); return }
        if (u.isNotEmpty() || m.isNotEmpty()) turns += u to m
    }

    /** Spec: every free conversation with the assistant is a moment, with when, where and who was around. */
    private fun saveConversation() {
        endTurn()
        if (turns.isEmpty()) return
        val text = turns.joinToString("\n") { (u, m) -> listOfNotNull(u.takeIf { it.isNotEmpty() }?.let { "Você: $it" }, m.takeIf { it.isNotEmpty() }?.let { "Assistente: $it" }).joinToString("\n") }
        val at = talkStartedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        turns.clear(); talkStartedAt = 0
        val who = host.faces().recentNames(10 * 60_000)
        Thread {
            val loc = Where.location(ctx)
            val body = JSONObject().put("action", "moment").put("transcript", text)
                .put("at", java.time.Instant.ofEpochMilli(at).toString()).put("capture_mode", "live")
                .put("context", if (who.isEmpty()) "conversa com o assistente" else "conversa com o assistente, com " + who.joinToString(", "))
            if (who.size == 1) body.put("person_name", who[0])
            if (loc != null) body.put("lat", loc.latitude).put("lng", loc.longitude).put("place", Where.address(ctx, loc))
            Describe.summary(ctx, text)?.let { body.put("summary", it) }
            Moments.send(ctx, body)
        }.start()
    }

    /** Names are caught here, not left to Gemini: saying the name is enough, no button, no tool call needed. */
    private fun spotName() {
        val n = NameSpotter.find(heard.toString()) ?: return
        val now = SystemClock.elapsedRealtime()
        if (n.equals(lastNamed, ignoreCase = true) && now - lastNamedAt < 20_000) return
        lastNamed = n; lastNamedAt = now
        io.execute {
            val r = host.faces().name(n)
            if (!r.optBoolean("guardado")) return@execute
            host.frame()?.let { Frames.bitmap(it, 960) }?.let { b -> val seen = host.faces().look(b, describeUnknown = false); main.post { host.onFaces(seen, b.width, b.height) } }
            main.post { host.onNamed(r.optString("nome", n)) }
        }
    }

    override fun onClosed(reason: String, byUs: Boolean) {
        main.post { saveConversation(); open = false; mic.stop(); if (!byUs) { Backend.saveLiveModel(ctx, null); host.onState("error: $reason") } }
    }

    private fun tool(call: LiveProtocol.Event.ToolCall) {
        lastActivity = SystemClock.elapsedRealtime()
        io.execute {
            val r: JSONObject = when (call.name) {
                LiveProtocol.TOOL_LOOK -> {
                    val f = host.frame()?.let { Frames.shrink(it, 768) }
                    if (f != null) live.send(LiveProtocol.image(Base64.encodeToString(f, Base64.NO_WRAP)))
                    JSONObject().put("ok", f != null).apply { if (f == null) put("erro", "a câmera não respondeu") }
                }
                LiveProtocol.TOOL_WHO -> {
                    val f = host.frame()?.let { Frames.bitmap(it, 960) }
                    if (f == null) JSONObject().put("erro", "a câmera não respondeu") else {
                        val seen = host.faces().look(f, describeUnknown = true)
                        main.post { host.onFaces(seen, f.width, f.height) }
                        if (seen.isEmpty()) JSONObject().put("rostos", 0).put("dica", "não vi rosto; peça para olhar de frente ou chegar mais perto") else host.faces().asJson(seen)
                    }
                }
                LiveProtocol.TOOL_ENROLL -> {
                    val r = host.faces().name(call.args.optString("nome"))
                    // The card must change right away: look again at the current frame with the new name.
                    host.frame()?.let { Frames.bitmap(it, 960) }?.let { b -> val seen = host.faces().look(b, describeUnknown = false); main.post { host.onFaces(seen, b.width, b.height) } }
                    r
                }
                LiveProtocol.TOOL_PERSON -> {
                    val q = call.args.optString("nome").lowercase()
                    host.faces().refreshPeople()
                    val hits = host.faces().people.filter { q.isNotEmpty() && (it.name.lowercase().contains(q) || q.contains(it.name.lowercase().substringBefore(' '))) }.take(3)
                    JSONObject().put("pessoas", org.json.JSONArray().apply { hits.forEach { put(JSONObject().put("nome", it.name).put("relacao", it.relation).put("empresa", it.company).put("ultima_vez", it.lastSeenContext).put("data", it.lastSeenDate)) } })
                }
                LiveProtocol.TOOL_SAVE -> {
                    val dir = File(ctx.filesDir, "notas").apply { mkdirs() }
                    File(dir, "notas.jsonl").appendText(JSONObject().put("quando", System.currentTimeMillis()).put("texto", call.args.optString("texto")).toString() + "\n")
                    JSONObject().put("guardado", true)
                }
                LiveProtocol.TOOL_WHERE -> Where.asJson(ctx)
                else -> JSONObject().put("erro", "ferramenta desconhecida")
            }
            live.send(LiveProtocol.toolResponse(call.id, call.name, r))
        }
    }

    fun destroy() { stop(); io.shutdownNow() }

    companion object {
        private val FORGET = Regex("""(n[ãa]o (guarda|grava|salva|anota)|esquece (isso|o que eu (disse|falei))|apaga (isso|o que eu (disse|falei)))""", RegexOption.IGNORE_CASE)
    }
}
