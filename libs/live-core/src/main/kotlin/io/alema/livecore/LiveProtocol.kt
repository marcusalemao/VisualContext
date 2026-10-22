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

    val SYSTEM = """
        Você é o VisualContext, um assistente nos óculos do usuário. Fale sempre em português do Brasil, com sotaque brasileiro.
        Fique quieto até ser chamado. Respostas curtas: uma ou duas frases, porque elas também aparecem num visor pequeno.
        Nunca pergunte "quer mais alguma coisa?" nem tente encerrar a conversa; quando o usuário parar de falar, apenas espere.
        Se a pergunta depender de ver algo ("o que é isso?", "lê isso pra mim"), chame a ferramenta olhar antes de responder.
        Se a foto vier cortada, torta, escura ou longe, diga em poucas palavras como mirar ("vire um pouco para a direita", "chegue mais perto", "incline para baixo") e chame olhar de novo.
        Se a pergunta for sobre uma pessoa que o usuário conhece, chame buscar_pessoa com o nome.
        Se o usuário pedir para guardar, anotar ou lembrar algo, chame guardar_nota com um resumo curto.
        Para horários, preços e fatos atuais, use a busca do Google. Não invente.
    """.trimIndent()

    fun setup(voice: String, resumeHandle: String?, model: String = MODEL): String {
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            // No languageCode: native-audio models pick the language from the conversation; the system text asks for pt-BR.
            .put("speechConfig", JSONObject()
                .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
        val functions = JSONArray()
            .put(fn(TOOL_LOOK, "Tira uma foto do que o usuário está olhando agora, com a câmera dos óculos.", null))
            .put(fn(TOOL_PERSON, "Procura na memória do usuário uma pessoa pelo nome ou apelido: de onde se conhecem, notas, último encontro.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("nome", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("nome"))))
            .put(fn(TOOL_SAVE, "Guarda uma nota curta na memória do usuário, quando ele pedir.",
                JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("texto", JSONObject().put("type", "STRING"))).put("required", JSONArray().put("texto"))))
        val setup = JSONObject()
            .put("model", model)
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functions)).put(JSONObject().put("googleSearch", JSONObject())))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })
        return JSONObject().put("setup", setup).toString()
    }

    private fun fn(name: String, description: String, params: JSONObject?) =
        JSONObject().put("name", name).put("description", description).apply { params?.let { put("parameters", it) } }

    fun audio(base64Pcm16k: String): String = JSONObject().put("realtimeInput", JSONObject()
        .put("audio", JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", base64Pcm16k))).toString()

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
        return out
    }
}
