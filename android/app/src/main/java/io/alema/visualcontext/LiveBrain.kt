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
        /** Where we are, in words, for memories made by tools (blocking, may be null). */
        fun placeName(): String? = null
        /** The lyrics card on the lens: cover, title, previous / current / next line. */
        fun onLyric(title: String, artist: String, prev: String?, cur: String?, next: String?, cover: android.graphics.Bitmap?) { onBrief("♪ $title", listOfNotNull(cur)) }
        fun onLyricEnd() {}
        /** A commitment went in the calendar (uri) or is waiting as a suggestion in the Manager (null). */
        fun onPlan(p: Plans.Plan, uri: android.net.Uri?) {}
        /** "Reviver o dia": chapters walked with the temple. */
        fun onStory(chapters: List<Story.Chapter>) {}
        /** A text in view translated: original and translation side by side on the lens. */
        fun onTranslation(title: String, original: String, translation: String, lang: String) { onBrief("⇄ $title", listOf(translation.take(300))) }
        /** Nothing said for a while after an answer: the assistant should close (the host stops it). */
        fun onIdle() {}
        /** Where an object was last seen, with its photo when there is one. */
        fun onThing(name: String, where: String, whenTxt: String, photo: android.graphics.Bitmap?) { onBrief("⌖ $name", listOf(where, whenTxt)) }
        /** Another app (the music player) took the screen: bring the glasses view back. */
        fun comeBack() {}
        /** A paper or label was read: the lens offers what to do with it (pills). */
        fun onPaper(p: Papers.Paper, saved: Boolean) {}
        /** The other assistant (Grok, Claude…) answered: shown and read with the phone's voice. */
        fun onAltAnswer(who: String, text: String) { onBrief("◆ $who", listOf(text.take(200))) }
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val live = LiveSession(this).apply { faceTools = true; locationTools = true }
    private val mic = MicIn { pcm -> Music.feed(pcm); if (open) onMic(pcm) }

    /**
     * Marcus, 08/11: "às vezes eu já perguntei duas, três coisas e ainda está respondendo a primeira". While the
     * assistant talked, the mic sent silence (so it wouldn't hear itself) and he couldn't cut in. Now a clearly
     * louder voice than the echo, held for 300 ms, stops the playback and goes to Gemini, which drops its answer.
     */
    private val held = ArrayDeque<ByteArray>()
    private var loud = 0
    /** When his voice was last heard by the mic (for the idle close), independent of the transcription. */
    @Volatile private var lastVoice = 0L
    private fun onMic(pcm: ByteArray) {
        if (rms(pcm) > VOICE_RMS) lastVoice = SystemClock.elapsedRealtime()
        if (!speaker.speaking) { loud = 0; held.clear(); live.send(LiveProtocol.audio(Base64.encodeToString(pcm, Base64.NO_WRAP))); return }
        if (rms(pcm) > BARGE_RMS) loud++ else loud = 0
        held.addLast(pcm); while (held.size > 3) held.removeFirst()
        if (loud >= 3) {
            speaker.flush(); loud = 0
            while (held.isNotEmpty()) live.send(LiveProtocol.audio(Base64.encodeToString(held.removeFirst(), Base64.NO_WRAP)))
        } else live.send(LiveProtocol.audio(Base64.encodeToString(ByteArray(pcm.size), Base64.NO_WRAP)))
    }
    private fun rms(pcm: ByteArray): Double {
        var sum = 0.0; var i = 0
        while (i + 1 < pcm.size) { val v = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toDouble(); sum += v * v; i += 2 }
        return kotlin.math.sqrt(sum / (pcm.size / 2).coerceAtLeast(1))
    }
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
        Player.duck(ctx, true) // music plays low while the assistant is open (Marcus, 08/11)
        speaker.start()
        io.execute {
            val model = Backend.liveModel(ctx) ?: (live.listModels(key)?.let { LiveProtocol.pickLiveModel(it) } ?: LiveProtocol.MODEL).also { Backend.saveLiveModel(ctx, it) }
            // Spec 1r: the learned skills become tools of this session.
            Skills.refresh(ctx)
            MyPlaces.refresh(ctx)
            val placeHint = Where.location(ctx)?.let { loc -> MyPlaces.askHint(ctx, loc) } ?: ""
            live.extraTools = Skills.tools(ctx).put(Music.tool()).put(Recall.tool()).put(MyPlaces.tool()).put(Agenda.tool()).also { a -> Things.tools().forEach { a.put(it) }; Player.tools().forEach { a.put(it) }; a.put(Zap.tool()); a.put(Story.tool()); a.put(Plans.tool()); Tasks.tools().forEach { a.put(it) }; if (Claude.provider(Claude.key(ctx)).isNotEmpty()) a.put(Claude.tool(ctx)); Things.names(ctx).takeIf { it.isNotEmpty() }?.let { n -> a.put(Things.seenTool(n)) } }
            live.extraSystem = Skills.system(ctx) + " ENCERRAMENTO: depois de responder, fique em silêncio; nunca pergunte \"mais alguma coisa?\" nem se despeça. Se ele não falar mais, o app fecha sozinho; se falar, continue." + " MÚSICA: se ele perguntar que música está tocando, chame identificar_musica e espere; não adivinhe. \"Toca…\" / \"quero ouvir…\" → tocar_musica; pausar, pular, voltar → controlar_musica; \"mostra a letra\" → mostrar_letra." + Things.SYSTEM +
                (if (Claude.provider(Claude.key(ctx)).isNotEmpty()) " OUTRO ASSISTENTE: ele configurou o ${Claude.friendlyName(ctx)}. \"Quero falar com o ${Claude.friendlyName(ctx)}\" (ou com o nome parecido que a transcrição escrever, ex. Groc) → chame outro_assistente com cada fala dele, sem criar habilidade e sem julgar o tom (o estilo é escolha dele e quem responde é o outro), até ele dizer \"volta\"." else "") +
                " AGENDA (escrever): quando ele combinar um compromisso na conversa ou pedir \"marca na agenda\", chame marcar_agenda com título, início, local, com quem e TODAS as outras informações ditas em notas; não pergunte antes, o app grava e mostra Desfazer." +
                " TAREFAS: \"me lembra de…\", \"preciso mandar…\", \"depois eu escrevo\", algo que alguém pediu e ele aceitou → anotar_tarefa (com a ação: whatsapp, email, ligar, agenda, comprar). \"O que eu tenho para fazer?\" → minhas_tarefas. \"Já fiz…\" → concluir_tarefa. Ao ler uma tarefa, ofereça a ação (\"quer que eu abra a conversa com o Bruno?\" → conversar_com)." +
                " CONVERSAR: \"manda mensagem pro Bruno\", \"abre a conversa com a Ana\", \"fala com o Pedro\" → conversar_com (o app abre o Zap ou o WhatsApp na pessoa; você não escreve a mensagem)." +
                " TRADUZIR: \"traduz isso\" (placa, cardápio, texto) → diga só \"deixa eu ver\" e espere: o app lê, traduz e manda a tradução; não traduza pela imagem pequena." +
                " LER: quando ele pedir para ler algo (\"o que está escrito\", etiqueta, bula, placa), diga só \"deixa eu ler\" e espere: o app lê em alta resolução e manda o texto; nunca diga que não consegue ler antes disso." +
                " MEMÓRIA: perguntas sobre o passado dele (o que fiz hoje, quem encontrei, quando vi fulano, o que combinei) vão para consultar_memoria; leia a resposta em voz alta como veio." +
                " AGENDA: compromissos, \"o que eu tenho para hoje/amanhã\" → agenda (nunca diga que não tem acesso). PESSOAS: \"o que eu preciso lembrar sobre a Ana\", \"do que falamos nos últimos dias\" → consultar_memoria com dias (10 se ele não disser) e, se for alguém prestes a encontrar, também preparar_conversa." +
                " LUGARES: \"aqui é minha casa\", \"estou no trabalho\", \"isso é o Pop Vegan\" → marcar_lugar." +
                " IDIOMAS: ele fala " + Backend.languageNames(ctx).joinToString(", ") + "; entenda a fala dele sempre numa dessas línguas. INTÉRPRETE: se outra pessoa falar com ele numa língua que ele não fala, " +
                "ou ele pedir \"traduz\", diga em português, curto, o que ela disse (\"Ela disse: …\"); se ele pedir para responder, diga a frase na língua dela para ele repetir ou para ela ouvir. " + placeHint
            live.languages = Backend.languages(ctx)
            main.post { live.connect(key, Backend.voiceName(ctx), resumeHandle, model) }
        }
    }

    /**
     * Marcus, 09/11: like the glasses' own assistant — answers, and if nothing more is said it goes away by itself.
     * After the model finishes a turn, 12 s with no voice and no tool running closes the session; the next long
     * press resumes it with the same context (session resumption + the last turns sent as a reminder).
     */
    private val idleCheck = object : Runnable { override fun run() {
        if (!open) return
        val now = SystemClock.elapsedRealtime()
        val quiet = turnDone && !speaker.speaking && now - lastActivity > IDLE_MS && now - lastVoice > IDLE_MS
        if (quiet) { host.onIdle(); return }
        main.postDelayed(this, 1000)
    } }
    private var closedAt = 0L
    private var lastTurns = ""

    fun stop() {
        main.removeCallbacks(idleCheck)
        Player.duck(ctx, false); Claude.reset()
        lastTurns = (turns.takeLast(3)).joinToString(" | ") { (u, m) -> listOfNotNull(u.takeIf { it.isNotEmpty() }?.let { "Você: ${it.take(160)}" }, m.takeIf { it.isNotEmpty() }?.let { "Assistente: ${it.take(160)}" }).joinToString(" / ") }
        closedAt = SystemClock.elapsedRealtime()
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
                // Picking up where it left (Marcus, 09/11: "que ele lembre do que você falou por último"): within two hours
                // the last turns go back as a reminder, in case the server did not resume the session.
                if (lastTurns.isNotEmpty() && closedAt > 0 && SystemClock.elapsedRealtime() - closedAt < 2 * 3600_000L) {
                    live.send(LiveProtocol.text("(retomando a conversa de há ${(SystemClock.elapsedRealtime() - closedAt) / 60_000} min; o fim dela foi: $lastTurns. Continue de onde parou, sem recapitular.)"))
                }
                pendingContext?.let { pendingContext = null; live.send(LiveProtocol.text(it)) }
                lastVoice = SystemClock.elapsedRealtime(); main.removeCallbacks(idleCheck); main.postDelayed(idleCheck, 1000)
            }
            is LiveProtocol.Event.UserText -> {
                lastActivity = SystemClock.elapsedRealtime()
                if (lastActivity - heardAt > 8000) heard.setLength(0)
                heardAt = lastActivity
                heard.append(e.text); userTurn.append(e.text)
                host.onTranscript(transcript.lines(), heard.toString().trim().takeLast(40))
                spotName(); spotPaper(); spotRead(); spotYes()
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
            val me = Backend.meName(ctx)
            if (who.size == 1 && !who[0].equals(me, true) && who[0] != "você") body.put("person_name", who[0])
            if (loc != null) body.put("lat", loc.latitude).put("lng", loc.longitude).put("place", Where.placeLabel(ctx, loc) ?: Where.address(ctx, loc))
            val mv = Describe.memoryVersion(ctx, text)
            if (mv != null) { if (mv.first.isNotBlank()) body.put("summary", mv.first); body.put("transcript", mv.second) }
            else firstQuestion(text)?.let { body.put("summary", it) } // never a conversation without what it was about
            Moments.send(ctx, body)
        }.start()
    }

    /** The first real thing he asked (garbled recognizer lines in other alphabets are skipped). */
    private fun firstQuestion(t: String): String? = t.lines().filter { it.startsWith("Você:") }.map { it.removePrefix("Você:").trim() }
        .firstOrNull { l -> l.split(Regex("\\s+")).size >= 3 && l.all { c -> c.code < 0x250 || !c.isLetter() } }?.take(140)

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

    /**
     * Marcus, 08/11: "o que está escrito aqui?" on a tiny medicine label — the Live said it couldn't read while the
     * phone's reader had read it. Small print is read here, at full resolution, and the text goes to the Live, which
     * answers from it. Nothing is saved: the lens shows the options (Ler, Guardar, Keep, Agenda).
     */
    @Volatile private var readAt = 0L
    private fun spotRead() {
        val translate = TRANSLATE.find(heard)
        if (translate == null && !READ.containsMatchIn(heard)) return
        val now = SystemClock.elapsedRealtime(); if (now - readAt < 15_000 || now - paperAt < 15_000) return
        readAt = now
        main.post { host.onBrief(if (translate != null) "⇄ Traduzindo…" else "▤ Lendo…", listOf("segure parado um instante")) }
        io.execute {
            val f = host.frame()?.let { Frames.shrink(it, 1600) }
            val p = f?.let { Papers.read(ctx, it) }
            if (p != null && translate != null) {
                // "Traduz isso (para o inglês)": the text is read, translated here, shown with the original and kept as a note
                // the Manager can translate again into any language (Marcus, 09/11).
                val target = translate.groupValues.getOrNull(2)?.trim()?.ifBlank { null } ?: Backend.languageNames(ctx).firstOrNull() ?: "português"
                val src = listOf(p.summary, p.text).filter { it.isNotBlank() }.joinToString("\n")
                val tr = Describe.translate(ctx, src, target)
                if (tr == null) { if (open) live.send(LiveProtocol.text("(o app não conseguiu traduzir agora; diga isso a ele.)")); return@execute }
                val fields = JSONObject().put("original", src.take(6000)).put("traducao", tr.second.take(6000)).put("idioma", target).put("idioma_original", tr.first)
                Moments.send(ctx, JSONObject().put("action", "note").put("kind", "traducao").put("title", "Tradução: " + p.title).put("text", tr.second.take(6000)).put("fields", fields).put("source", "paper").put("place", host.placeName()))
                if (open) live.send(LiveProtocol.text("(o app leu e traduziu para $target; a tradução já está na lente e guardada. Leia em voz alta só a tradução, sem comentar: ${tr.second.take(2500)})"))
                main.post { host.onTranslation(p.title, src, tr.second, target) }
                return@execute
            }
            if (p == null) { if (open) live.send(LiveProtocol.text("(o app tentou ler em alta resolução e não achou texto legível; peça para ele aproximar e segurar parado.)")); main.post { host.onBrief("▤", listOf("não consegui ler; aproxime e segure parado")) }; return@execute }
            if (open) live.send(LiveProtocol.text("(o app leu pela câmera em alta resolução — ${p.title}: ${(p.summary + "\n" + p.text).take(3000)}\nResponda à pergunta dele a partir deste texto, curto. Não diga que não consegue ler e não chame ferramenta. Não guarde nada: a lente já mostra as opções.)"))
            main.post { host.onPaper(p, saved = false) }
        }
    }

    private fun paperNow(fromSpeech: Boolean = false): JSONObject {
        paperAt = SystemClock.elapsedRealtime()
        val f = host.frame()?.let { Frames.shrink(it, 1600) } ?: return JSONObject().put("erro", "a câmera não respondeu")
        val p = Papers.read(ctx, f)
        if (p == null) { if (fromSpeech) paperAt = 0; return JSONObject().put("papel", false).put("dica", "não vi papel com texto; se for outra coisa, use guardar_nota") }
        lastPaper = p.title
        val place = Where.location(ctx)?.let { Where.placeLabel(ctx, it) }
        Papers.save(ctx, p, place)
        main.post { host.onSaved(p.title); host.onPaper(p, saved = true) }
        if (fromSpeech && open) live.send(LiveProtocol.text("(o app já guardou na memória: ${p.title}. Confirme em poucas palavras, sem chamar ferramenta.)"))
        return JSONObject().put("guardado", true).put("titulo", p.title).put("tipo", p.kind)
    }

    /** Names are caught here, not left to Gemini: saying the name is enough, no button, no tool call needed. */
    private fun spotName() {
        val n = NameSpotter.find(heard.toString(), Backend.meName(ctx)) ?: return
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
        main.post { saveConversation(); open = false; mic.stop(); Player.duck(ctx, false); if (!byUs) { Backend.saveLiveModel(ctx, null); host.onState("error: $reason") } }
    }

    private fun tool(call: LiveProtocol.Event.ToolCall) {
        lastActivity = SystemClock.elapsedRealtime()
        io.execute {
            val r: JSONObject = when (call.name) {
                LiveProtocol.TOOL_LOOK -> {
                    val f = host.frame()?.let { Frames.shrink(it, 1280) } // small print needs the pixels
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
                        // Keep has no API: the phone shows one notification and a tap puts the note there (spec 1n).
                        NotesOut.offer(ctx, texto.take(40), texto, sensitive = false)
                        JSONObject().put("guardado", true).put("diga_ao_usuario", "Guardei na sua memória. Se ele pediu o Keep: deixei um aviso no celular; um toque nele põe a nota no Keep (o Keep não deixa nenhum app gravar sozinho).")
                    }
                }
                LiveProtocol.TOOL_PAPER -> paperNow()
                LiveProtocol.TOOL_PREP -> {
                    val b = Prep.forPerson(ctx, call.args.optString("nome"))
                    if (b == null) JSONObject().put("erro", "não consegui preparar agora")
                    else { main.post { host.onBrief(b.name, b.lines) }; JSONObject().put("pessoa", b.name).put("na_lente", JSONArray(b.lines)).put("diga", b.spoken) }
                }
                LiveProtocol.TOOL_WHERE -> Where.asJson(ctx)
                Agenda.TOOL -> {
                    val (res, lines) = Agenda.call(ctx, call.args)
                    main.post { host.onBrief("▦ Agenda", lines) }
                    res
                }
                MyPlaces.TOOL -> {
                    val nome = call.args.optString("nome").trim().ifEmpty { "Casa" }; val tipo = call.args.optString("tipo").ifEmpty { "outro" }
                    val loc = Where.location(ctx)
                    if (loc == null) JSONObject().put("erro", "sem localização agora")
                    else {
                        val ok = MyPlaces.save(ctx, nome, tipo, loc, Where.address(ctx, loc))
                        main.post { host.onBrief(when (tipo) { "casa" -> "⌂ $nome"; "trabalho" -> "▣ $nome"; else -> "★ $nome" }, listOf(if (ok) "lugar guardado" else "sem internet; tente de novo")) }
                        JSONObject().put("ok", ok).put("diga", if (ok) "Anotado: aqui é $nome." else "Não consegui guardar agora.")
                    }
                }
                Recall.TOOL -> {
                    val q = call.args.optString("pergunta"); val days = call.args.optInt("dias", 30).let { if (it <= 0) 30 else it }
                    main.post { host.onBrief("✦ Lembrando…", listOf(q.take(38))) }
                    val a = Recall.ask(ctx, q, days)
                    if (a == null) JSONObject().put("erro", "não consegui consultar a memória agora")
                    else { main.post { host.onBrief(a.title.removePrefix("✦ "), a.lines) }; JSONObject().put("resposta", a.say).put("diga", a.say) }
                }
                Things.TOOL_KEEP -> {
                    val name = call.args.optString("nome").ifEmpty { "objeto" }; val said = call.args.optString("onde").ifEmpty { null }
                    val (ok, where) = Things.keep(ctx, name, said, host.frame(), host.placeName())
                    Things.forgetNames()
                    main.post { host.onSaved("$name guardado"); host.onBrief("⌖ $name", listOf(where ?: "", if (ok) "guardado na memória" else "guardado; envia quando tiver rede")) }
                    JSONObject().put("ok", true).put("diga", "Guardei: $name" + (where?.let { " em $it" } ?: "") + ".")
                }
                Things.TOOL_SEEN -> { // the camera saw one of his objects somewhere: written silently
                    val name = call.args.optString("nome").lowercase(); val where = call.args.optString("onde").trim('.', ' ').lowercase()
                    if (name.isBlank() || where.isBlank() || where.contains("na mão")) JSONObject().put("ok", false)
                    else if (!Things.worthSaving(name, where)) JSONObject().put("ok", true).put("ja_anotado", true)
                    else {
                        Things.keep(ctx, name, where, host.frame(), host.placeName())
                        main.post { host.onBrief("⌖ $name", listOf(where, "visto agora")) }
                        JSONObject().put("ok", true).put("diga", "nada: não comente")
                    }
                }
                Things.TOOL_FIND -> {
                    val q = call.args.optString("objeto")
                    val f = Things.find(ctx, q)
                    if (f.isEmpty()) JSONObject().put("resultado", "nenhum registro de \"$q\"").put("diga", "Não tenho registro de onde você deixou $q. Posso olhar agora, se quiser.")
                    else {
                        val t = f[0]; val whenTxt = runCatching { java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale("pt", "BR")).format(java.time.OffsetDateTime.parse(t.at).toInstant().toEpochMilli()) }.getOrDefault("")
                        // The photo of when it was last seen goes to the lens (Marcus, 09/11: "ele devia ter mostrado a foto").
                        val img = t.snapshot?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }?.let { b -> runCatching { Base64.decode(b, Base64.DEFAULT) }.getOrNull()?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
                        main.post { host.onThing(t.name, t.where ?: "lugar não anotado", "visto por último $whenTxt", img) }
                        JSONObject().put("objeto", t.name).put("onde", t.where ?: "").put("quando", t.at).put("diga", "Você deixou ${t.name}" + (t.where?.let { " em $it" } ?: "") + (if (whenTxt.isNotEmpty()) ", $whenTxt" else "") + ".")
                    }
                }
                Music.TOOL -> {
                    main.post { host.onBrief("♪ Ouvindo…", listOf("uns segundos de silêncio, por favor")) }
                    try {
                        val song = Music.identify(ctx)
                        if (song == null) { main.post { host.onBrief("♪", listOf("não reconheci essa música")) }; JSONObject().put("resultado", "não reconhecida").put("diga", "Não reconheci essa música. Quer que eu tente de novo?") }
                        else {
                            val line = "${song.title} — ${song.artist}" + (song.album?.let { " · $it" } ?: "")
                            // The 8 s capture ended now: AudD's timecode is where the fragment started.
                            Player.heard(song.title, song.artist, song.timecode); Player.lastPosMs.takeIf { it >= 0 }?.let { Player.lastPosMs = it + Music.SECONDS_MS }
                            main.post { host.onBrief("♪ " + song.title, listOf(song.artist, song.album ?: "")) }
                            Moments.send(ctx, JSONObject().put("action", "note").put("kind", "musica").put("title", line).put("text", "Ouviu $line").put("source", "glasses").put("place", host.placeName()))
                            JSONObject().put("musica", song.title).put("artista", song.artist).put("album", song.album ?: "").put("diga", "É ${song.title}, de ${song.artist}.").put("letra", "se ele quiser ver a letra na lente, chame mostrar_letra")
                        }
                    } catch (e: IllegalStateException) {
                        if (e.message == "sem chave") {
                            val ok = Music.openGoogle(ctx)
                            JSONObject().put("resultado", if (ok) "abri a pesquisa por som do Google no celular; o resultado aparece lá" else "sem serviço de identificação de música; a chave do AudD pode ser configurada nos Ajustes")
                                .put("diga", if (ok) "Abri a pesquisa por som do Google no seu celular." else "Ainda não tenho como identificar músicas; dá para ligar isso nos Ajustes.")
                        } else JSONObject().put("erro", e.message)
                    }
                }
                Tasks.TOOL_ADD -> {
                    Tasks.add(ctx, call.args.optString("texto"), call.args.optString("acao"), call.args.optString("pessoa").ifBlank { null }, call.args.optString("quando").ifBlank { null }, call.args.optString("detalhes").ifBlank { null }, null)
                    main.post { host.onBrief("✓ Tarefa anotada", listOf(call.args.optString("texto"))) }
                    JSONObject().put("ok", true).put("diga", "Anotado.")
                }
                Tasks.TOOL_LIST -> {
                    val ts = Tasks.open(ctx)
                    if (ts.isEmpty()) JSONObject().put("vazio", true).put("diga", "Nada pendente.")
                    else { main.post { host.onStory(Tasks.chapters(ts)) }
                        JSONObject().put("tarefas", JSONArray(ts.take(8).map { JSONObject().put("tarefa", it.text).put("prazo", it.due?.toString() ?: "").put("pessoa", it.person ?: "").put("acao_sugerida", it.suggestion()) }))
                            .put("diga", "Leia curto: quantas são e as três primeiras com a ação sugerida; a lista está na lente.") }
                }
                Tasks.TOOL_DONE -> {
                    val t = Tasks.match(Tasks.open(ctx), call.args.optString("texto"))
                    if (t == null) JSONObject().put("erro", "não achei essa tarefa") else { Tasks.done(ctx, t); main.post { host.onBrief("✓ Feito", listOf(t.text)) }; JSONObject().put("ok", true).put("diga", "Tirei: ${t.text}.") }
                }
                Plans.TOOL -> {
                    val p = Plans.fromArgs(call.args)
                    if (p == null) JSONObject().put("erro", "data/hora não entendida; peça o dia e a hora")
                    else {
                        // He asked, or it was agreed in front of the assistant: it goes in now (undo on the lens).
                        val uri = Plans.save(ctx, p)
                        Plans.remember(ctx, p, if (uri != null) "na_agenda" else "sugerido", p.with)
                        main.post { host.onPlan(p, uri) }
                        JSONObject().put("ok", true).put("diga", if (uri != null) "Pronto, ${p.title} ${p.line()} está na agenda." else "Abri a agenda preenchida para você salvar.")
                    }
                }
                Story.TOOL -> {
                    val days = call.args.optInt("dias_atras", 0).coerceIn(0, 30)
                    val ch = Story.build(ctx, days)
                    if (ch.isEmpty()) JSONObject().put("vazio", true).put("diga", "Não tenho nada registrado nesse dia.")
                    else { main.post { host.onStory(ch) }; JSONObject().put("capitulos", ch.size).put("diga", "Está na lente: ${ch.size} partes. Haste para frente e para trás; dois toques saem. Diga só isso, curto.") }
                }
                Zap.TOOL -> {
                    val who = call.args.optString("nome")
                    val r = Zap.open(ctx, who)
                    main.post { host.comeBack() }
                    when (r) { "zap" -> JSONObject().put("ok", true).put("diga", "Abri a conversa com $who no Zap."); "whatsapp" -> JSONObject().put("ok", true).put("diga", "Abri a conversa com $who no WhatsApp.")
                        "sem_numero" -> JSONObject().put("erro", "não tenho o telefone de $who na memória").put("diga", "Não tenho o número de $who. Dá para pôr no Manager.")
                        "sem_pessoa" -> JSONObject().put("erro", "não achei $who na memória")
                        else -> JSONObject().put("erro", "nem o Zap nem o WhatsApp estão no celular").put("diga", "Para conversar pelos óculos é preciso o Zap. Deixei o link para instalar no celular.") }
                }
                Claude.TOOL -> {
                    val who = Claude.friendlyName(ctx)
                    main.post { host.onBrief("◆ $who", listOf("pensando…")) }
                    val (a, why) = Claude.chat(ctx, call.args.optString("fala"))
                    if (a == null) JSONObject().put("erro", why).put("diga", "O $who não respondeu: $why")
                    else {
                        // Read word for word with the phone's other voice, so it is the other assistant speaking, not Gemini retelling it.
                        main.post { host.onAltAnswer(who, a) }
                        JSONObject().put("lido_pelo_app", true).put("diga", "Não diga nada: o app já está lendo a resposta do $who em voz alta. Espere a próxima fala dele.")
                    }
                }
                Player.TOOL -> {
                    val app = Player.play(ctx, call.args.optString("pedido"), call.args.optString("artista").ifBlank { null }, call.args.optString("musica").ifBlank { null })
                    if (app == null) JSONObject().put("erro", "não achei um app de música no celular (Spotify, YouTube Music…)")
                    else { main.postDelayed({ host.comeBack() }, 2500); main.post { host.onBrief("♪ Tocando", listOf(call.args.optString("pedido"))) }; JSONObject().put("tocando", call.args.optString("pedido")).put("app", app).put("diga", "Tocando.") }
                }
                Player.TOOL_CTRL -> JSONObject().put("ok", Player.control(ctx, call.args.optString("acao")))
                Player.TOOL_LYRICS -> {
                    if (call.args.optBoolean("parar")) { Player.stopLyrics(); main.post { host.onLyricEnd() }; JSONObject().put("ok", true) }
                    else {
                        val last = Player.lastSong
                        val t = call.args.optString("musica").ifBlank { last?.first ?: "" }; val a = call.args.optString("artista").ifBlank { last?.second ?: "" }
                        if (t.isBlank()) JSONObject().put("erro", "não sei qual música; chame identificar_musica antes ou pergunte o nome")
                        else {
                            val l = Player.lyrics(t, a)
                            if (l == null) JSONObject().put("erro", "não achei a letra dessa música")
                            else {
                                val same = last != null && last.first.equals(t, true)
                                val pos = if (same) Player.lastPosMs else -1L
                                val cover = Player.cover(l.title, l.artist)
                                main.post { Player.showLyrics(l, pos, Player.lastAt) { pv, cu, nx -> host.onLyric(l.title, l.artist, pv, cu, nx, cover) } }
                                JSONObject().put("na_lente", if (l.synced.isNotEmpty() && pos >= 0) "letra sincronizada" else "começo da letra").put("diga", "Pronto, a letra está na lente.")
                            }
                        }
                    }
                }
                Skills.TOOL_LEARN -> {
                    val sk = Skills.learn(ctx, call.args.optString("nome"), call.args.optString("o_que_faz"), call.args.optString("detalhes"))
                    if (sk == null) JSONObject().put("erro", "não consegui criar a habilidade agora")
                    else { main.post { host.onSaved("habilidade " + sk.name) }; JSONObject().put("criada", sk.name).put("como_pedir", JSONArray(sk.triggers)).put("dica", "funciona a partir da próxima vez que o assistente abrir; agora você pode responder o pedido normalmente") }
                }
                LiveProtocol.TOOL_GO -> {
                    val dest = call.args.optString("destino")
                    try {
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=" + android.net.Uri.encode(dest) + "&mode=w"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (i.resolveActivity(ctx.packageManager) == null) i.data = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=" + android.net.Uri.encode(dest))
                        main.post { ctx.startActivity(i) }
                        JSONObject().put("ok", true).put("dica", "a navegação a pé abriu no celular")
                    } catch (e: Exception) { JSONObject().put("erro", "não consegui abrir a navegação") }
                }
                else -> Skills.all(ctx).firstOrNull { it.tool == call.name }?.let { sk ->
                    val r = Skills.run(ctx, sk, call.args.optString("pedido"))
                    main.post { host.onBrief(r.title, r.lines) }
                    JSONObject().put("na_lente", JSONArray(r.lines)).put("diga", r.spoken)
                } ?: JSONObject().put("erro", "ferramenta desconhecida")
            }
            live.send(LiveProtocol.toolResponse(call.id, call.name, r))
        }
    }

    fun destroy() { stop(); io.shutdownNow() }

    companion object {
        private val YES = Regex("""\b(sim|pode|copia|copiar|isso|ok|beleza|quero)\b""", RegexOption.IGNORE_CASE)
        private const val IDLE_MS = 12_000L
        private const val VOICE_RMS = 900.0 // someone speaking near the glasses
        private const val BARGE_RMS = 2200.0 // a voice at the glasses' mic; the echo after the canceller stays well below
        private val TRANSLATE = Regex("""\b(traduz|traduza|tradução|o que significa isso|o que quer dizer isso)\b(?:.*?\bpara (?:o |a )?(inglês|alemão|português|espanhol|francês|italiano|chinês|mandarim|japonês|coreano|tailandês|árabe|russo))?""", RegexOption.IGNORE_CASE)
        private val READ = Regex("""(o que (est[áa]|t[áa]) escrito|o que (diz|fala) (a|o|aqui|essa|esse|isso|nessa|nesse)|l[êe] (isso|pra mim|aqui|essa|esse|o que)|leia|consegue ler|qual [ée] o (nome|texto) (do|da|desse|dessa))""", RegexOption.IGNORE_CASE)
        private val REMEMBER = Regex("""\b(lembra (disso|isso|desse|dessa|deste|desta)|guarda (isso|esse papel|essa receita|esse cart[ãa]o)|anota (isso|esse|essa))\b""", RegexOption.IGNORE_CASE)
        private val FORGET = Regex("""(n[ãa]o (guarda|grava|salva|anota)|esquece (isso|o que eu (disse|falei))|apaga (isso|o que eu (disse|falei)))""", RegexOption.IGNORE_CASE)
    }
}
