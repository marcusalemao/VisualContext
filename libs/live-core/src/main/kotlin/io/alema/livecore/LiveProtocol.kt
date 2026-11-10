package io.alema.livecore

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini Live API messages (BidiGenerateContent over WebSocket). Plain Kotlin + org.json, unit-tested.
 * In: 16 kHz PCM16 mono audio, JPEG frames (≤1 per second). Out: 24 kHz PCM16 audio + transcripts + tool calls.
 */
object LiveProtocol {
    const val MODEL = "models/gemini-3.8-live"
    const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    const val TOOL_LOOK = "olhar"
    const val TOOL_PERSON = "buscar_pessoa"
    const val TOOL_SAVE = "guardar_nota"
    const val TOOL_WHO = "quem_e"
    const val TOOL_ENROLL = "guardar_pessoa"
    const val TOOL_WHERE = "onde_estou"
    const val TOOL_PAPER = "guardar_papel"
    const val TOOL_PREP = "preparar_conversa"
    const val TOOL_GO = "ir_para"

    /** Extra instructions when the app knows where the user is (phone GPS). */
    val PLACES = """
        Se o usuário perguntar onde está, em que rua está, o que tem em volta, ou sobre um lugar ou prédio que está vendo, chame onde_estou (e olhar, se for algo que ele está vendo) antes de responder. Pergunta de lugar nunca é sobre pessoas: não chame quem_e.
        Se onde_estou não trouxer lugares, use a busca do Google com o endereço para achar o que é conhecido ali perto (restaurantes famosos, museus, prédios). Nunca diga "não identifiquei lugares" nem "não há lugares por perto": se não souber, diga só o endereço.
        Se o usuário aceitar ir até um lugar, chame ir_para com o nome e o endereço do lugar: o celular abre a navegação a pé.
        Depois de responder onde algo está (a moto, um lugar), faça uma única pergunta curta, já com o modo sugerido pela distância: perto, "Quer ir até lá? A pé são uns X minutos."; longe, "Quer ir até lá? São X km: transporte público, moto ou carro?". Se o usuário não responder ou disser não, não insista e não pergunte de novo.
        Quando o usuário vir uma pessoa sem nome conhecido, nunca chame de "pessoa 1" ou "desconhecido": descreva em poucas palavras neutras (ex.: "homem de barba, jaqueta azul") e a ocasião.
    """.trimIndent()

    /** Extra instructions when the app can recognise faces (the Solo app on the glasses). */
    val FACES = """
        Só chame quem_e quando o usuário perguntar sobre uma pessoa ("quem é?", "como ele se chama?"). Nunca fale de pessoas (nem "não há ninguém na sua frente") quando a pergunta for sobre outra coisa. Diga só o nome e de onde se conhecem, baixinho, em uma frase.
        Se quem_e responder que a pessoa é desconhecida, diga isso em poucas palavras. Só guarde um rosto se o usuário pedir ou disser o nome da pessoa ("essa é a Ana", "guarda o rosto do Rafael"): aí chame guardar_pessoa com o nome.
        Nunca descreva o corpo, a aparência ou a roupa de alguém sem o usuário pedir.
    """.trimIndent()

    val SYSTEM = """
        Você é o VisualContext, um assistente nos óculos do usuário. Fale sempre em português do Brasil, com sotaque brasileiro.
        Fique quieto até ser chamado. Respostas curtas: uma ou duas frases, porque elas também aparecem num visor pequeno.
        Nunca pergunte "quer mais alguma coisa?" nem tente encerrar a conversa; quando o usuário parar de falar, apenas espere.
        Se a pergunta depender de ver algo ("o que é isso?", "lê isso pra mim"), chame a ferramenta olhar antes de responder.
        Se a foto vier cortada, torta, escura ou longe, diga em poucas palavras como mirar ("vire um pouco para a direita", "chegue mais perto", "incline para baixo") e chame olhar de novo.
        Se a pergunta for sobre uma pessoa que o usuário conhece, chame buscar_pessoa com o nome.
        Se o usuário pedir para guardar, anotar ou lembrar algo, chame guardar_nota com um resumo curto.
        Se ele pedir para lembrar ou guardar um papel, cartão de visita, crachá, receita, formulário ou documento que está olhando, chame guardar_papel (ele lê o papel pela câmera).
        Ler algo para o usuário (cardápio, placa, bula) é ajuda: não guarde a leitura, a menos que ele peça "lembra disso".
        Para horários, preços e fatos atuais, use a busca do Google. Não invente.
        Converse sobre qualquer assunto que o usuário trouxer; se ele quiser explicar mais, responda com a profundidade que ele pedir.
    """.trimIndent()

    fun setup(voice: String, resumeHandle: String?, model: String = MODEL, faces: Boolean = false, location: Boolean = false,
              extraTools: JSONArray? = null, extraSystem: String = "", languages: List<String> = emptyList()): String {
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            // No languageCode: native-audio models pick the language from the conversation; the system text asks for pt-BR.
            .put("speechConfig", JSONObject()
                .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
        val functions = JSONArray()
            .put(fn(TOOL_LOOK, "Tira uma foto do que o usuário está olhando agora, com a câmera dos óculos.", null))
            .put(fn(TOOL_PERSON, "Procura na memória do usuário uma pessoa pelo nome ou apelido: de onde se conhecem, notas, último encontro.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("nome", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("nome"))))
            .put(fn(TOOL_PREP, "Prepara o usuário para uma conversa com uma pessoa: quem é para ele, último encontro, pendências, o que não errar e o que há de público na web. Use quando ele pedir para se preparar ou perguntar 'o que eu preciso saber sobre fulano'.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("nome", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("nome"))))
            .put(fn(TOOL_PAPER, "Lê pela câmera o papel, cartão de visita, crachá, receita ou formulário que o usuário está olhando e guarda na memória dele como nota. Só quando ele pedir para lembrar ou guardar.", null))
            .put(fn(TOOL_SAVE, "Guarda uma nota curta na memória do usuário, quando ele pedir.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("texto", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("texto"))))
        if (location) functions.put(fn(TOOL_GO, "Abre no celular a navegação a pé (Google Maps) até um lugar, quando o usuário aceitar ir até lá.",
            JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("destino", JSONObject().put("type", "STRING").put("description", "nome e endereço do lugar"))).put("required", JSONArray().put("destino"))))
        if (location) functions.put(fn(TOOL_WHERE, "Diz onde o usuário está agora (endereço aproximado pelo GPS do celular) e os lugares importantes por perto, com distância.", null))
        if (faces) {
            functions.put(fn(TOOL_WHO, "Reconhece o rosto da pessoa na frente do usuário, com a câmera dos óculos e a memória de rostos dele.", null))
            functions.put(fn(TOOL_ENROLL, "Guarda o rosto da pessoa na frente do usuário com o nome que ele disse. Só quando o usuário pedir.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("nome", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("nome"))))
        }
        extraTools?.let { for (i in 0 until it.length()) functions.put(it.get(i)) }
        val setup = JSONObject()
            .put("model", model)
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", now() + "\n" + SYSTEM + (if (faces) "\n" + FACES else "") + (if (location) "\n" + PLACES else "") + (if (extraSystem.isNotEmpty()) "\n" + extraSystem else "")))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functions)).put(JSONObject().put("googleSearch", JSONObject())))
            // Marcus, 08/11: his Portuguese was transcribed as other languages. The codes he speaks bias recognition.
            .put("inputAudioTranscription", JSONObject().apply { if (languages.isNotEmpty()) put("languageCodes", JSONArray(languages)) })
            .put("outputAudioTranscription", JSONObject())
            // Faster turns (Marcus, 08/11: "bem lento para me entender e para responder"): the turn ends after half a
            // second of silence instead of the default (longer), and speech starts are caught early.
            .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject()
                .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH").put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
                .put("prefixPaddingMs", 100).put("silenceDurationMs", 500))
                .put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS"))
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })
        return JSONObject().put("setup", setup).toString()
    }

    /** The user's local time and zone (Marcus, 07/11: it said "4:48, horário UTC" at 01:48 in São Paulo). */
    private fun now(): String {
        val z = java.time.ZonedDateTime.now()
        return "Agora são " + z.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'de' EEEE, d 'de' MMMM 'de' yyyy", java.util.Locale("pt", "BR"))) +
            ", no fuso do usuário (" + z.zone.id + "). Diga sempre a hora local, nunca UTC."
    }

    private fun fn(name: String, description: String, params: JSONObject?) =
        JSONObject().put("name", name).put("description", description).apply { params?.let { put("parameters", it) } }

    /** PCM16 mono; the rate is whatever the mic really delivers (Gemini resamples). */
    fun audio(base64Pcm: String, rate: Int = 16_000): String = JSONObject().put("realtimeInput", JSONObject()
        .put("audio", JSONObject().put("mimeType", "audio/pcm;rate=$rate").put("data", base64Pcm))).toString()

    /** A typed turn (used by the voice preview). */
    fun text(t: String): String = JSONObject().put("clientContent", JSONObject()
        .put("turns", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", t)))))
        .put("turnComplete", true)).toString()

    fun image(base64Jpeg: String): String = JSONObject().put("realtimeInput", JSONObject()
        .put("video", JSONObject().put("mimeType", "image/jpeg").put("data", base64Jpeg))).toString()

    fun toolResponse(id: String, name: String, response: JSONObject): String = JSONObject().put("toolResponse", JSONObject()
        .put("functionResponses", JSONArray().put(JSONObject().put("id", id).put("name", name).put("response", response)))).toString()

    const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200"

    /**
     * Picks the Live model this key can actually use, from the ListModels answer, instead of trusting a hard-coded name
     * (Google renames Live models often). Only models that speak `bidiGenerateContent` qualify;
     * native-audio ones win, then the newest-looking name.
     */
    fun pickLiveModel(listModelsJson: String): String? {
        val models = try { JSONObject(listModelsJson).optJSONArray("models") } catch (_: Exception) { null } ?: return null
        val live = (0 until models.length()).mapNotNull { i ->
            val m = models.optJSONObject(i) ?: return@mapNotNull null
            val methods = m.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
            if ((0 until methods.length()).none { methods.optString(it) == "bidiGenerateContent" }) null else m.optString("name")
        }.filter { it.startsWith("models/") }
        fun score(n: String) = (if ("native-audio" in n) 4 else 0) + (if ("live" in n) 2 else 0) + (if ("preview" in n || "exp" in n) 0 else 1)
        return live.sortedWith(compareByDescending<String> { score(it) }.thenByDescending { it }).firstOrNull()
    }

    sealed class Event {
        object SetupComplete : Event()
        data class Audio(val base64Pcm24k: String) : Event()
        data class ModelText(val text: String) : Event()
        data class UserText(val text: String) : Event()
        object Interrupted : Event()
        object TurnComplete : Event()
        data class ToolCall(val id: String, val name: String, val args: JSONObject) : Event()
        data class Resumption(val handle: String) : Event()
        data class GoAway(val timeLeft: String) : Event()
        data class Error(val message: String) : Event()
        /** Tokens Google counted for one turn, by modality (TEXT, AUDIO, IMAGE, VIDEO): what the session really costs. */
        data class Usage(val input: Map<String, Int>, val output: Map<String, Int>) : Event()
    }

    fun parse(text: String): List<Event> {
        val j = try { JSONObject(text) } catch (_: Exception) { return listOf(Event.Error("resposta ilegível")) }
        val out = mutableListOf<Event>()
        if (j.has("setupComplete")) out += Event.SetupComplete
        j.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    parts.optJSONObject(i)?.optJSONObject("inlineData")?.let { d ->
                        if (d.optString("mimeType").startsWith("audio/")) out += Event.Audio(d.optString("data"))
                    }
                }
            }
            sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { out += Event.ModelText(it) }
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { out += Event.UserText(it) }
            if (sc.optBoolean("interrupted")) out += Event.Interrupted
            if (sc.optBoolean("turnComplete")) out += Event.TurnComplete
        }
        j.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val c = calls.optJSONObject(i) ?: continue
                out += Event.ToolCall(c.optString("id"), c.optString("name"), c.optJSONObject("args") ?: JSONObject())
            }
        }
        j.optJSONObject("sessionResumptionUpdate")?.let { u ->
            if (u.optBoolean("resumable", true)) u.optString("newHandle").takeIf { it.isNotEmpty() }?.let { out += Event.Resumption(it) }
        }
        j.optJSONObject("goAway")?.let { out += Event.GoAway(it.optString("timeLeft")) }
        j.optJSONObject("error")?.let { out += Event.Error(it.optString("message", "erro")) }
        j.optJSONObject("usageMetadata")?.let { u ->
            fun mods(key: String, total: String): Map<String, Int> {
                val a = u.optJSONArray(key)
                val m = HashMap<String, Int>()
                if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { d -> m[d.optString("modality", "TEXT")] = (m[d.optString("modality", "TEXT")] ?: 0) + d.optInt("tokenCount") }
                if (m.isEmpty() && u.optInt(total) > 0) m["TEXT"] = u.optInt(total)
                return m
            }
            val thoughts = u.optInt("thoughtsTokenCount")
            val outM = mods("responseTokensDetails", "responseTokenCount").toMutableMap()
            if (thoughts > 0) outM["TEXT"] = (outM["TEXT"] ?: 0) + thoughts // thinking is billed as text output
            out += Event.Usage(mods("promptTokensDetails", "promptTokenCount"), outM)
        }
        return out
    }
}
