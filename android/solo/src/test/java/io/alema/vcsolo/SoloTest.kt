package io.alema.vcsolo

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoloTest {
    private val key = "AIzaSyA-fake-key-for-tests-0123456789"

    @Test fun setupCodeRoundTrip() {
        val c = SoloConfig(key, SaveMode.PHOTO, "Puck", "vc_0123456789abcdef")
        assertEquals(c, SetupCode.parse(SetupCode.encode(c)))
    }

    @Test fun modelOverride() {
        assertEquals("models/gemini-live-x", SetupCode.parse("VCSOLO1:{\"gemini\":\"$key\",\"model\":\"gemini-live-x\"}")!!.model)
        assertEquals(LiveProtocol.MODEL, SetupCode.parse("VCSOLO1:{\"gemini\":\"$key\",\"model\":\"bad model!\"}")!!.model)
    }

    @Test fun picksALiveModelFromTheKeysOwnList() {
        val list = """{"models":[
            {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-live","supportedGenerationMethods":["bidiGenerateContent"]},
            {"name":"models/gemini-2.5-flash-native-audio-preview","supportedGenerationMethods":["bidiGenerateContent","countTokens"]}]}"""
        assertEquals("models/gemini-2.5-flash-native-audio-preview", LiveProtocol.pickLiveModel(list))
        assertNull(LiveProtocol.pickLiveModel("""{"models":[{"name":"models/x","supportedGenerationMethods":["generateContent"]}]}"""))
    }

    @Test fun foreignQrIsIgnored() {
        assertNull(SetupCode.parse("WIFI:T:WPA;S:x;P:y;;"))
        assertNull(SetupCode.parse("VCSOLO1:{\"gemini\":\"short\"}"))
        assertNull(SetupCode.parse("VCSOLO1:not json"))
    }

    @Test fun defaultsWhenOptionalFieldsMissing() {
        val c = SetupCode.parse("VCSOLO1:{\"gemini\":\"$key\"}")!!
        assertEquals(SaveMode.NONE, c.saveMode)
        assertEquals(SoloConfig.DEFAULT_VOICE, c.voice)
        assertNull(c.vcToken)
    }

    @Test fun setupAsksForAudioBrazilianPortugueseAndTools() {
        val s = JSONObject(LiveProtocol.setup("Kore", null)).getJSONObject("setup")
        assertEquals(LiveProtocol.MODEL, s.getString("model"))
        val g = s.getJSONObject("generationConfig")
        assertEquals("AUDIO", g.getJSONArray("responseModalities").getString(0))
        assertTrue(s.getJSONObject("systemInstruction").toString().contains("português do Brasil"))
        val names = s.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
        assertEquals(listOf("olhar", "buscar_pessoa", "guardar_nota"), names)
        assertTrue(s.getJSONObject("sessionResumption").length() == 0)
    }

    @Test fun setupCarriesResumeHandle() {
        val s = JSONObject(LiveProtocol.setup("Kore", "h-123")).getJSONObject("setup")
        assertEquals("h-123", s.getJSONObject("sessionResumption").getString("handle"))
    }

    @Test fun parsesServerContent() {
        val msg = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAA="}}]},
            "outputTranscription":{"text":"É um "},"turnComplete":true}}"""
        val ev = LiveProtocol.parse(msg)
        assertTrue(ev.contains(LiveProtocol.Event.Audio("AAA=")))
        assertTrue(ev.contains(LiveProtocol.Event.ModelText("É um ")))
        assertTrue(ev.contains(LiveProtocol.Event.TurnComplete))
    }

    @Test fun parsesToolCallAndResumption() {
        val ev = LiveProtocol.parse("""{"toolCall":{"functionCalls":[{"id":"c1","name":"buscar_pessoa","args":{"nome":"Bruno"}}]}}""")
        val call = ev.single() as LiveProtocol.Event.ToolCall
        assertEquals("Bruno", call.args.getString("nome"))
        assertEquals(listOf(LiveProtocol.Event.Resumption("h9")), LiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h9","resumable":true}}"""))
    }

    @Test fun toolResponseShape() {
        val r = JSONObject(LiveProtocol.toolResponse("c1", "olhar", JSONObject().put("ok", true)))
        val f = r.getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("c1", f.getString("id"))
        assertTrue(f.getJSONObject("response").getBoolean("ok"))
    }

    @Test fun transcriptKeepsLastThreeLines() {
        val t = Transcript()
        t.add("Esse é o Museu de Arte de São Paulo, ")
        t.add("projetado pela Lina Bo Bardi e inaugurado em 1968 na Avenida Paulista.")
        val l = t.lines()
        assertTrue(l.size <= 3)
        assertTrue(l.all { it.length <= 30 })
        assertTrue(l.last().endsWith("Paulista."))
    }
}
