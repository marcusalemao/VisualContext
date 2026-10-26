package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BriefTest {
    private val zone = ZoneOffset.UTC
    private val min = 60_000L

    /** Bruno, five new messages and two voice notes, after one old exchange already seen. */
    private val bruno = ZapConversation(
        key = "5511999990001", title = "Bruno", waDigits = "5511999990001", isGroup = false,
        messages = listOf(
            ZapMessage("Bruno", "fala", 1 * min),
            ZapMessage(ZapMessage.ME, "e aí", 2 * min),
            ZapMessage("Bruno", "bora almoçar?", 10 * min),
            ZapMessage("Bruno", "no Lume", 11 * min),
            ZapMessage("Bruno", "🎤 Mensagem de voz (0:15)", 12 * min),
            ZapMessage("Bruno", "🎤 Mensagem de voz (0:40)", 13 * min),
            ZapMessage("Bruno", "depois academia", 14 * min),
        ),
        readUpToMs = 2 * min,
    )

    @Test
    fun `a pile of new messages, or two voice notes in a row, is worth a summary`() {
        assertTrue(BriefRules.worthBriefing(bruno))
        val two = bruno.copy(messages = bruno.messages.take(4))
        assertFalse(BriefRules.worthBriefing(two)) // two new texts: just read them
        val voices = bruno.copy(messages = bruno.messages.take(2) + bruno.messages.filter { it.voiceSeconds != null })
        assertTrue(BriefRules.worthBriefing(voices))
        assertFalse(BriefRules.worthBriefing(bruno.seen()))
    }

    @Test
    fun `the AI gets a little context and every new message, oldest first, new ones marked`() {
        val sent = BriefRules.messagesFor(bruno)
        assertEquals(bruno.messages, sent)
        val prompt = BriefPrompt.user(bruno, sent, nowMs = 20 * min, zone = zone, audioFor = setOf(4))
        assertTrue(prompt.contains("NOW: 1970-01-01 00:20 (Thursday)"))
        assertTrue(prompt.contains("    1970-01-01 00:02 Eu: e aí")) // context, not marked
        assertTrue(prompt.contains("new 1970-01-01 00:10 Bruno: bora almoçar?"))
        assertTrue(prompt.contains("[voice message 15s — audio #1 attached]"))
        assertTrue(prompt.contains("[voice message 40s — not available]"))
    }

    @Test
    fun `the answer is read even with code fences, replies are cut short and the event lands in local time`() {
        val answer = """
            ```json
            {"summary": "Bruno chama para almoçar no Lume às 12h e depois academia.",
             "replies": ["Topo! Te encontro lá", "Hoje não dá, amanhã?", "terceira"],
             "event": {"title": "Almoço com Bruno no Lume", "start": "1970-01-02T12:00", "end": null, "place": "Lume"}}
            ```
        """.trimIndent()
        val brief = BriefPrompt.parse(answer, zone)
        assertNotNull(brief)
        brief!!
        assertEquals(2, brief.replies.size)
        assertEquals("Lume", brief.event?.place)
        assertEquals((24 * 60 + 12 * 60) * min, brief.event?.startMs)
        assertNull(BriefPrompt.parse("sorry, I can't", zone))
        assertNull(BriefPrompt.parse("""{"summary": "", "replies": []}""", zone))
    }

    @Test
    fun `opening a busy chat shows the summary card and asks the AI once, reopening reuses it`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and "✦ The day's summary"
        val effects = flow.tap()
        val step = flow.step as Step.Brief
        assertEquals(BriefState.Loading, step.state)
        assertTrue(effects.single() is Effect.Summarize)

        val brief = Brief("Bruno chama para almoçar no Lume e depois academia.", replies = listOf("Topo!"),
            event = SuggestedEvent("Almoço", 24 * 60 * min))
        flow.onBrief(bruno.key, BriefState.Ready(brief))
        val ready = flow.step as Step.Brief
        assertEquals(
            listOf(BriefChoice.SendReply("Topo!"), BriefChoice.Dictate, BriefChoice.Calendar(brief.event!!),
                BriefChoice.Remind(BriefRules.REMIND_MINUTES), BriefChoice.Listen, BriefChoice.SeeMessages),
            ready.choices(),
        )
        assertEquals(0, flow.selected)

        // A ready reply goes to the review card: nothing is sent without Send.
        flow.tap()
        val review = flow.step as Step.Review
        assertEquals("Topo!", review.body)

        flow.back() // review → the summary (its base)
        assertTrue(flow.step is Step.Brief)
        flow.back() // → home
        flow.move(2) // past "New message" and "✦ The day's summary"
        assertTrue(flow.tap().isEmpty()) // cached: no second call
        assertEquals(BriefState.Ready(brief), (flow.step as Step.Brief).state)
    }

    @Test
    fun `calendar and reminder rows turn into a check once done, and are not repeated`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and "✦ The day's summary"
        flow.tap()
        val event = SuggestedEvent("Almoço", 0L)
        flow.onBrief(bruno.key, BriefState.Ready(Brief("Almoço?", event = event)))
        val calendarAt = (flow.step as Step.Brief).choices().indexOf(BriefChoice.Calendar(event))
        flow.move(calendarAt)
        assertEquals(listOf(Effect.AddToCalendar(event, "Bruno")), flow.tap())
        flow.onBriefAction(BriefDone.CALENDAR, ok = true)
        assertTrue(flow.tap().isEmpty())
        val card = ScreenMaker(ZapText(pt = true), zone, now = { 0L }).make(flow.step, flow.selected) as Screen.Card
        assertTrue(card.rows.any { it.text.startsWith("✓ Na agenda: Almoço") })
    }

    @Test
    fun `see the messages leaves the summary for the usual chat list, and a failure still offers it`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and "✦ The day's summary"
        flow.tap()
        flow.onBrief(bruno.key, BriefState.Failed(BriefError.NO_KEY))
        val failed = flow.step as Step.Brief
        assertEquals(listOf(BriefChoice.Dictate, BriefChoice.SeeMessages), failed.choices())
        val card = ScreenMaker(ZapText(pt = true), zone, now = { 0L }).make(failed, 0) as Screen.Card
        assertEquals(Tone.ALERT, card.rows.first().tone)
        flow.move(1)
        flow.tap()
        assertTrue((flow.step as Step.Thread).reading)
    }

    @Test
    fun `the card keeps counting what was new when it opened, even after the chat is marked read`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and "✦ The day's summary"
        flow.tap()
        flow.refresh(listOf(bruno.seen()))
        val step = flow.step as Step.Brief
        assertEquals(5, BriefRules.unreadOf(step.conversation).size)
        val card = ScreenMaker(ZapText(pt = true), zone, now = { 0L }).make(step, 0) as Screen.Card
        assertEquals("✦ Resumindo 5 mensagens e 2 áudios…", card.subtitle)
    }

    @Test
    fun `an answer that lands after the wearer left is kept for reopening`() {
        val flow = ComposeFlow(directory = { emptyList() }, briefs = { true })
        flow.open(listOf(bruno))
        flow.move(2) // past "New message" and "✦ The day's summary"
        flow.tap()
        flow.back()
        flow.onBrief(bruno.key, BriefState.Ready(Brief("Almoço?")))
        flow.move(2) // past "New message" and "✦ The day's summary"
        assertTrue(flow.tap().isEmpty())
        assertTrue((flow.step as Step.Brief).state is BriefState.Ready)
    }

    @Test
    fun `summaries off, or a quiet chat, open the chat as before`() {
        val off = ComposeFlow(directory = { emptyList() }, briefs = { false })
        off.open(listOf(bruno))
        off.move(1)
        off.tap()
        assertTrue(off.step is Step.Thread)
    }

    @Test
    fun `a reaction goes as a message of its own, in the chosen skin tone`() {
        val reactions = Reactions.chosen(listOf("👍", "❤️"), SkinTone.MEDIUM, pt = true)
        assertEquals("👍🏽", reactions[0].emoji)
        assertEquals("❤️", reactions[1].emoji) // hearts have no skin
        val flow = ComposeFlow(directory = { emptyList() }, reactions = { reactions })
        flow.open(listOf(bruno.seen()))
        flow.move(1)
        flow.tap() // chat list, newest message marked
        flow.tap() // its options
        val options = MessageOption.of((flow.step as Step.Thread).message!!)
        flow.move(options.indexOf(MessageOption.REACT))
        flow.tap()
        assertTrue(flow.step is Step.React)
        val effects = flow.tap()
        assertEquals(Effect.Send(bruno.recipient(), "👍🏽"), effects.last())
    }

    @Test
    fun `my own messages offer no reaction, and an empty choice falls back to WhatsApp's six`() {
        assertFalse(MessageOption.REACT in MessageOption.of(ZapMessage(ZapMessage.ME, "oi", 1L)))
        assertEquals(6, Reactions.chosen(emptyList(), SkinTone.DEFAULT, pt = true).size)
        assertEquals(Reactions.MAX, Reactions.chosen(Reactions.palette(true).map { it.emoji }, SkinTone.DEFAULT, pt = true).size)
    }
}
