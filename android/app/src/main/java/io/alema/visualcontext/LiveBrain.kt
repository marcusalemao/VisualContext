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
import org.json.JSONArray
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
        /** A paper or note was kept ("guardado: Receita do Dr. Paulo"). */
        fun onSaved(title: String) {}
        /** Spec 1o: the prep brief for a person, as a lens card. */
        fun onBrief(name: String, lines: List<String>) {}
        /** The user said "sim" / "copia": true when the lens had a question waiting (a QR code, for now). */
        fun onYes(): Boolean = false
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
    /** Context to hand to Live (a menu, for now): sent now if open, or right after it connects. */
    private var pendingContext: String? = null
    fun context(text: String) { if (open) live.send(LiveProtocol.text(text)) else pendingContext = text }
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
                pendingContext?.let { pendingContext = null; live.send(LiveProtocol.text(it)) }
            }
            is LiveProtocol.Event.UserText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (lastActivity - heardAt > 8000) heard.setLength(0)
                heardAt = lastActivity
                heard.append(e.text); userTurn.append(e.text)
                host.onTranscript(transcript.lines(), heard.toString().trim().takeLast(40))
                spotName(); spotPaper(); spotYes()
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
            Describe.memoryVersion(ctx, text)?.let { (sum, conv) -> if (sum.isNotBlank()) body.put("summary", sum); body.put("transcript", conv) }
            Moments.send(ctx, body)
        }.start()
    }

    private var yesAt = 0L
    private fun spotYes() {
        if (!YES.containsMatchIn(heard.toString().takeLast(30))) return
        val now = SystemClock.elapsedRealtime(); if (now - yesAt < 3000) return
        if (host.onYes()) { yesAt = now; if (open) live.send(LiveProtocol.text("(o app já fez o que o usuário aceitou na lente. Diga só \"pronto\".)")) }
    }

    /** Spec 1n: "lembra disso" / "guarda isso" with a paper in view: read it, keep it, offer it to the notes app. */
    @Volatile private var paperAt = 0L
    @Volatile private var lastPaper = ""
    private fun spotPaper() {
        if (!REMEMBER.containsMatchIn(heard)) return
        val now = SystemClock.elapsedRealtime()
        if (now - paperAt < 30_000) return
        paperAt = now
        io.execute { paperNow(fromSpeech = true) }
    }

    private fun paperNow(fromSpeech: Boolean = false): JSONObject {
        paperAt = SystemClock.elapsedRealtime()
        val f = host.frame()?.let { Frames.shrink(it, 1600) } ?: return JSONObject().put("erro", "a câmera não respondeu")
        val p = Papers.read(ctx, f)
        if (p == null) { if (fromSpeech) paperAt = 0; return JSONObject().put("papel", false).put("dica", "não vi papel com texto; se for outra coisa, use guardar_nota") }
        lastPaper = p.title
        val place = Where.location(ctx)?.let { Where.address(ctx, it) }
        Papers.save(ctx, p, place)
        main.post { host.onSaved(p.title) }
        if (fromSpeech && open) live.send(LiveProtocol.text("(o app já guardou na memória: ${p.title}. Confirme em poucas palavras, sem chamar ferramenta.)"))
        return JSONObject().put("guardado", true).put("titulo", p.title).put("tipo", p.kind)
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
                    // "lembra disso" with a paper in view is kept by spotPaper(); don't add a second, poorer note.
                    if (SystemClock.elapsedRealtime() - paperAt < 30_000) JSONObject().put("guardado", true).put("papel", lastPaper)
                    else {
                        val texto = call.args.optString("texto")
                        val dir = File(ctx.filesDir, "notas").apply { mkdirs() }
                        File(dir, "notas.jsonl").appendText(JSONObject().put("quando", System.currentTimeMillis()).put("texto", texto).toString() + "\n")
                        Moments.send(ctx, JSONObject().put("action", "note").put("text", texto).put("source", "voice"))
                        main.post { host.onSaved(texto.take(40)) }
                        JSONObject().put("guardado", true)
                    }
                }
                LiveProtocol.TOOL_PAPER -> paperNow()
                LiveProtocol.TOOL_PREP -> {
                    val b = Prep.forPerson(ctx, call.args.optString("nome"))
                    if (b == null) JSONObject().put("erro", "não consegui preparar agora")
                    else { main.post { host.onBrief(b.name, b.lines) }; JSONObject().put("pessoa", b.name).put("na_lente", JSONArray(b.lines)).put("diga", b.spoken) }
                }
                LiveProtocol.TOOL_WHERE -> Where.asJson(ctx)
                else -> JSONObject().put("erro", "ferramenta desconhecida")
            }
            live.send(LiveProtocol.toolResponse(call.id, call.name, r))
        }
    }

    fun destroy() { stop(); io.shutdownNow() }

    companion object {
        private val YES = Regex("""\b(sim|pode|copia|copiar|isso|ok|beleza|quero)\b""", RegexOption.IGNORE_CASE)
        private val REMEMBER = Regex("""\b(lembra (disso|isso|desse|dessa|deste|desta)|guarda (isso|esse papel|essa receita|esse cart[ãa]o)|anota (isso|esse|essa))\b""", RegexOption.IGNORE_CASE)
        private val FORGET = Regex("""(n[ãa]o (guarda|grava|salva|anota)|esquece (isso|o que eu (disse|falei))|apaga (isso|o que eu (disse|falei)))""", RegexOption.IGNORE_CASE)
    }
}
