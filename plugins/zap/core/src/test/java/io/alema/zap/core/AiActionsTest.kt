package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** 0.15: places and tasks in a chat's summary, and the day's summary from home. */
class AiActionsTest {
    private val zone = ZoneOffset.UTC
    private val min = 60_000L
    private val day = 24 * 60 * min

    private fun chat(key: String, title: String, vararg messages: ZapMessage, read: Long = 0L) =
        ZapConversation(key = key, title = title, waDigits = key, isGroup = false, messages = messages.toList(), readUpToMs = read)

    private val bruno = chat(
        "5511999990001", "Bruno",
        ZapMessage("Bruno", "bora almoçar?", day + 10 * min),
        ZapMessage("Bruno", "no Lume, Rua Augusta 1500", day + 11 * min),
        ZapMessage("Bruno", "e leva o contrato", day + 12 * min),
    )
    private val ana = chat("5511999990002", "Ana", ZapMessage("Ana", "chegou a encomenda?", day + 30 * min))
    private val old = chat("5511999990003", "Leo", ZapMessage("Leo", "ontem", 5 * min))

    @Test
    fun `places and a task are read from the answer, vague or empty ones dropped`() {
        val answer = """
            {"summary": "Bruno chama para almoçar no Lume e pede o contrato.",
             "replies": [],
             "event": null,
             "places": [{"name": "Lume", "address": "Rua Augusta 1500, São Paulo"},
                        {"name": "", "address": "Rua Augusta 1500, são paulo"},
                        {"name": "lá", "address": ""}],
             "task": {"text": "Levar o contrato.", "at": "1970-01-02T11:30"}}
        """.trimIndent()
        val brief = BriefPrompt.parse(answer, zone)
        assertNotNull(brief)
        assertEquals(listOf(SuggestedPlace("Lume", "Rua Augusta 1500, São Paulo")), brief!!.places)
        assertEquals("Levar o contrato", brief.task?.text)
        assertEquals(day + 11 * 60 * min + 30 * min, brief.task?.atMs)
    }

    @Test
    fun `a task without a time still comes through, and no task is null`() {
        val withTask = BriefPrompt.parse("""{"summary": "x", "task": {"text": "Pagar o boleto", "at": null}}""", zone)
        assertEquals(SuggestedTask("Pagar o boleto", null), withTask?.task)
        val none = BriefPrompt.parse("""{"summary": "x", "task": null, "places": []}""", zone)
        assertNull(none?.task)
        assertTrue(none!!.places.isEmpty())
    }

    @Test
    fun `the card offers the task, then maps, then saving, before the reply reminder`() {
        val place = SuggestedPlace("Lume", "Rua Augusta 1500")
        val task = SuggestedTask("Levar o contrato")
        val brief = Brief(summary = "x", replies = listOf("Topo"), places = listOf(place), task = task)
        val choices = BriefRules.choices(BriefState.Ready(brief), canReply = true)
        assertEquals(
            listOf(
                BriefChoice.SendReply("Topo"), BriefChoice.Dictate, BriefChoice.Task(task),
                BriefChoice.Map(place), BriefChoice.SavePlace(place),
                BriefChoice.Remind(BriefRules.REMIND_MINUTES), BriefChoice.Listen, BriefChoice.SeeMessages,
            ),
            choices,
        )
    }

    @Test
    fun `the day's summary takes only today's messages from others, newest chats first`() {
        val me = chat("5511999990004", "Eu mesmo", ZapMessage(ZapMessage.ME, "oi", day + 40 * min))
        val chats = DigestRules.chatsFor(listOf(old, bruno, ana, me), nowMs = day + 60 * min, zone = zone)
        assertEquals(listOf("Ana", "Bruno"), chats.map { it.first.title })
        assertEquals(3, chats[1].second.size)
    }

    @Test
    fun `the day's answer maps C1, C2 back to the chats and ignores unknown ids`() {
        val chats = listOf(ana, bruno)
        val prompt = DigestPrompt.user(listOf(ana to ana.messages, bruno to bruno.messages), nowMs = day + 60 * min, zone = zone)
        assertTrue(prompt.contains("C1: Ana (one person)"))
        assertTrue(prompt.contains("C2: Bruno (one person)"))
        val digest = DigestPrompt.parse(
            """{"summary": "Bruno quer almoçar no Lume; Ana pergunta da encomenda.",
                "items": [{"chat": "C2", "line": "Almoço no Lume e levar o contrato"},
                          {"chat": "c1", "line": "Pergunta se a encomenda chegou"},
                          {"chat": "C9", "line": "não existe"}]}""",
            chats,
        )
        assertNotNull(digest)
        assertEquals(listOf("Bruno", "Ana"), digest!!.items.map { it.title })
        assertEquals(bruno.key, digest.items.first().chatKey)
    }

    @Test
    fun `home offers the day's summary only with summaries on, and chats keep their place`() {
        val off = ComposeFlow(directory = { emptyList() })
        off.open(listOf(bruno, ana))
        assertEquals(3, off.choiceCount())

        var asked: List<Pair<ZapConversation, List<ZapMessage>>>? = null
        val on = ComposeFlow(directory = { emptyList() }, briefs = { true }, today = { list -> list.map { it to it.messages }.also { asked = it } })
        on.open(listOf(bruno, ana))
        assertEquals(4, on.choiceCount())
        on.move(1)
        val effects = on.tap()
        assertTrue(on.step is Step.Digest)
        assertTrue(effects.single() is Effect.SummarizeDay)
        assertEquals(2, asked?.size)

        // The answer arrives; a tap on its first item opens that chat.
        on.onDigest(DigestState.Ready(Digest("dia", listOf(DigestItem(ana.key, "Ana", "encomenda")), chats = 2, messages = 4)))
        on.tap()
        assertTrue(on.step is Step.Thread)
        assertEquals("Ana", (on.step as Step.Thread).conversation.title)

        // Back from the summary lands on its row at home.
        on.back()
        on.move(1)
        on.tap()
        on.back()
        assertTrue(on.step is Step.Inbox)
        assertEquals(1, on.selected)
    }

    @Test
    fun `nothing today means no AI call`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true }, today = { emptyList() })
        flow.open(listOf(old))
        flow.move(1)
        assertTrue(flow.tap().isEmpty())
        assertEquals(DigestState.Empty, (flow.step as Step.Digest).state)
    }

    @Test
    fun `saving a place turns its row into done and a second tap does nothing`() {
        val place = SuggestedPlace("Lume", "Rua Augusta 1500")
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and the day's summary, onto Bruno
        val opened = flow.tap()
        assertTrue(opened.single() is Effect.Summarize)
        flow.onBrief(bruno.key, BriefState.Ready(Brief(summary = "x", places = listOf(place))))
        val save = (flow.step as Step.Brief).choices().indexOf(BriefChoice.SavePlace(place))
        repeat(save) { flow.move(1) }
        assertTrue(flow.tap().single() is Effect.SavePlace)
        flow.onPlaceSaved(place.address, ok = true)
        assertTrue(place.address in (flow.step as Step.Brief).savedPlaces)
        assertTrue(flow.tap().isEmpty())
    }
}
