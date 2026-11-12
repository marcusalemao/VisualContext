package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ZapSkillsTest {
    private var clock = 10L * 60 * 60_000 // 10:00 on day one
    private val leo = ZapConversation(
        key = "5511987650003", title = "Leo", waDigits = "5511987650003", isGroup = false,
        messages = listOf(
            ZapMessage("Leo", "Bora almoçar?", clock - 70 * 60_000),
            ZapMessage(ZapMessage.ME, "Bora", clock - 50 * 60_000),
            ZapMessage("Leo", ZapMessage.PHOTO, clock - 5 * 60_000, image = "a1"),
        ),
    )
    private val leonardo = ZapConversation(
        key = "5511912340000", title = "Leonardo Costa", waDigits = "5511912340000", isGroup = false,
        messages = listOf(ZapMessage("Leonardo Costa", "Chego às 3", clock - 30 * 60_000)),
    )
    private val family = ZapConversation(
        key = "Família", title = "Família", waDigits = null, isGroup = true,
        messages = listOf(ZapMessage("Mãe", "Jantar domingo?", clock - 120 * 60_000)),
    )
    private var chats = listOf(leo, leonardo, family)
    private val skills = ZapSkills(
        inbox = { chats },
        directory = { listOf(ZapContact(id = "c9", name = "Bruna Alves", waIds = listOf("5511999990000"))) },
        secret = "test-secret".toByteArray(),
        text = ZapText(pt = true),
        zone = ZoneOffset.UTC,
        now = { clock },
    )

    @Suppress("UNCHECKED_CAST")
    private fun done(answer: ZapSkills.Answer) = (answer as ZapSkills.Answer.Done).data
    @Suppress("UNCHECKED_CAST")
    private fun list(value: Any?) = value as List<Map<String, Any?>>

    @Test
    fun `chats come newest first, with the last message and a photo in words`() {
        val result = done(skills.run(ZapSkills.LIST_CHATS, emptyMap()))
        val rows = list(result["chats"])
        assertEquals(listOf("Leo", "Leonardo Costa", "Família"), rows.map { it["name"] })
        assertEquals("[mandou uma foto]", rows[0]["last_text"])
        assertEquals(5, rows[0]["minutes_ago"])
        assertEquals("09:55", rows[0]["last_time"])
        assertEquals(3, result["total"])
        val filtered = list(done(skills.run(ZapSkills.LIST_CHATS, mapOf("filter" to "fam")))["chats"])
        assertEquals(listOf("Família"), filtered.map { it["name"] })
    }

    @Test
    fun `reading a chat gives its messages oldest first, with who said what`() {
        val result = done(skills.run(ZapSkills.READ_CHAT, mapOf("chat" to leo.key)))
        val messages = list(result["messages"])
        assertEquals(listOf("Leo", "Você", "Leo"), messages.map { it["from"] })
        assertEquals("Bora almoçar?", messages[0]["text"])
        assertEquals(true, messages[2]["photo"])
        assertEquals(false, result["older_left_out"])
        val last = done(skills.run(ZapSkills.READ_CHAT, mapOf("chat" to leo.key, "count" to 1)))
        assertEquals(1, list(last["messages"]).size)
        assertEquals(true, last["older_left_out"])
    }

    @Test
    fun `a name that fits two chats asks which one instead of guessing`() {
        // "leo" is clearly Leo, not Leonardo...
        assertEquals(leo.key, done(skills.run(ZapSkills.READ_CHAT, mapOf("name" to "leo")))["chat"])
        // ...but with two Leos, Zap asks.
        chats = chats + ZapConversation("5511900001111", "Leo Martins", "5511900001111", false, listOf(ZapMessage("Leo Martins", "oi", clock)))
        val answer = skills.run(ZapSkills.READ_CHAT, mapOf("name" to "leo"))
        assertTrue(answer is ZapSkills.Answer.Choose)
        val choose = answer as ZapSkills.Answer.Choose
        assertTrue(choose.choices.map { it.label }.containsAll(listOf("Leo", "Leo Martins")))
        assertTrue(choose.choices.all { it.refType == ZapSkills.CHAT_REF })
        assertEquals(ZapSkills.Answer.Failed(ZapSkills.NO_CHAT), skills.run(ZapSkills.READ_CHAT, mapOf("name" to "zuleica")))
        assertEquals(ZapSkills.Answer.Failed(ZapSkills.INVALID_ARGUMENTS), skills.run(ZapSkills.READ_CHAT, emptyMap()))
    }

    @Test
    fun `recent messages are what others sent, newest first, within the hours asked`() {
        val result = done(skills.run(ZapSkills.RECENT, mapOf("hours" to 1)))
        val messages = list(result["messages"])
        assertEquals(listOf("Leo", "Leonardo Costa"), messages.map { it["from"] })
        assertFalse(messages.any { it["from"] == "Você" })
        assertEquals(4, list(done(skills.run(ZapSkills.RECENT, mapOf("hours" to 3)))["messages"]).size)
    }

    @Test
    fun `sending never happens on the first call, only after the wearer picks Send`() {
        val first = skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "chego em 10"))
        val choose = first as ZapSkills.Answer.Choose
        assertEquals(ZapSkills.CONFIRM, choose.reason)
        assertEquals("Para Leo: “chego em 10”", choose.prompt)
        val send = choose.choices.single()
        assertEquals("Enviar", send.label)
        assertEquals(ZapSkills.CONFIRM_REF, send.refType)

        val second = skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "chego em 10", "confirm" to send.ref))
        assertEquals(ZapSkills.Answer.Send(leo.recipient(), "chego em 10"), second)
    }

    @Test
    fun `a Send choice is only good for that text, that chat, and a few minutes`() {
        val ref = (skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "oi")) as ZapSkills.Answer.Choose).choices.single().ref
        val stale = ZapSkills.Answer.Failed(ZapSkills.STALE_REFERENCE)
        assertEquals(stale, skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "oi, te amo", "confirm" to ref)))
        assertEquals(stale, skills.run(ZapSkills.SEND, mapOf("chat" to leonardo.key, "text" to "oi", "confirm" to ref)))
        assertEquals(stale, skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "oi", "confirm" to "123.abc")))
        clock += ZapSkills.CONFIRM_TTL_MS + 1
        assertEquals(stale, skills.run(ZapSkills.SEND, mapOf("chat" to leo.key, "text" to "oi", "confirm" to ref)))
    }

    @Test
    fun `send by name reaches people from the address book too, and a group only while its reply is live`() {
        val toBruna = skills.run(ZapSkills.SEND, mapOf("name" to "bruna", "text" to "oi")) as ZapSkills.Answer.Choose
        assertEquals("Para Bruna Alves: “oi”", toBruna.prompt)
        assertEquals(
            ZapSkills.Answer.Failed(ZapSkills.REPLY_ON_PHONE),
            skills.run(ZapSkills.SEND, mapOf("chat" to family.key, "text" to "oi")),
        )
        chats = listOf(leo, leonardo, family.copy(canReplyLive = true))
        assertTrue(skills.run(ZapSkills.SEND, mapOf("chat" to family.key, "text" to "oi")) is ZapSkills.Answer.Choose)
    }

    @Test
    fun `an unknown operation is refused`() {
        assertEquals(ZapSkills.Answer.Failed(ZapSkills.UNSUPPORTED), skills.run("delete_everything", emptyMap()))
    }
}
