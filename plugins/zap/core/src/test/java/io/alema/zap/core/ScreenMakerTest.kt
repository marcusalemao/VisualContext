package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMakerTest {
    private val maker = ScreenMaker(ZapText(pt = true), java.time.ZoneOffset.UTC, now = { 10_000L })

    private val leoChat = ZapConversation(
        key = "5511987650003", title = "Leo", waDigits = "5511987650003", isGroup = false,
        messages = listOf(
            ZapMessage("Leo", "Bora almoçar?", 1_000L),
            ZapMessage(ZapMessage.ME, "Bora", 2_000L),
            ZapMessage("Leo", ZapMessage.PHOTO, 3_000L, image = "a1"),
            ZapMessage("Leo", "o lugar", 4_000L, image = "b2"),
        ),
    )

    private fun card(screen: Screen) = screen as Screen.Card

    @Test
    fun `home lists chats with the last message under each, back leaves Zap`() {
        val home = card(maker.make(Step.Inbox(listOf(leoChat)), selected = 1))
        assertEquals("Conversas", home.title)
        assertFalse(home.handlesBack)
        assertEquals(Row("+ Nova mensagem", tone = Tone.ALERT), home.rows[0])
        // Three messages from Leo not seen yet, and today's time, like WhatsApp's list.
        assertEquals(Row("Leo", sub = "o lugar", trail = listOf("● 3", "00:00"), selected = true), home.rows[1])
    }

    @Test
    fun `a chat already seen has no count and steps back, unless it is the chosen row`() {
        val seen = leoChat.seen()
        assertEquals(0, seen.unread)
        val home = card(maker.make(Step.Inbox(listOf(seen)), selected = 0))
        assertEquals(listOf("00:00"), home.rows[1].trail)
        assertEquals(Tone.DIM, home.rows[1].tone)
        val chosen = card(maker.make(Step.Inbox(listOf(seen)), selected = 1))
        assertEquals(Tone.NORMAL, chosen.rows[1].tone)
        // A newer message from Leo counts again; one of my own does not.
        val more = InboxRules.merge(seen, seen.copy(messages = listOf(ZapMessage("Leo", "e aí?", 9_000L))))
        assertEquals(1, more.unread)
        val mine = InboxRules.merge(seen, seen.copy(messages = listOf(ZapMessage(ZapMessage.ME, "já vou", 9_000L))))
        assertEquals(0, mine.unread)
    }

    @Test
    fun `list times read like WhatsApp - hour today, Ontem, then only the date`() {
        val day = 24L * 60 * 60 * 1000
        val now = 10 * day + 15 * 60 * 60 * 1000 // day 10, 15:00 UTC
        val m = ScreenMaker(ZapText(pt = true), java.time.ZoneOffset.UTC, now = { now })
        val today = java.time.LocalDate.ofEpochDay(10)
        assertEquals("09:05", m.shortTime(10 * day + (9 * 60 + 5) * 60_000L, today))
        assertEquals("Ontem", m.shortTime(9 * day + 23 * 60 * 60 * 1000, today))
        assertEquals("08/01", m.shortTime(7 * day, today))
    }

    @Test
    fun `an empty home says why when notification access is off`() {
        val noAccess = card(maker.make(Step.Inbox(emptyList()), 0, ScreenMaker.Status(notificationAccess = false)))
        assertEquals(Tone.ALERT, noAccess.rows.last().tone)
        val quiet = card(maker.make(Step.Inbox(emptyList()), 0))
        assertEquals(Tone.DIM, quiet.rows.last().tone)
    }

    @Test
    fun `a chat is a list of messages, newest first, under Reply, the marked one is what a tap acts on`() {
        val long = "Olha, ".repeat(40) + "fim."
        val chat = leoChat.copy(messages = leoChat.messages + ZapMessage("Leo", long, 5_000L))
        val list = card(maker.make(Step.Thread(chat), selected = 1))
        assertEquals("✎ Responder", list.rows[0].text)
        assertFalse(list.rows[0].selected)
        assertTrue(list.rows[1].selected) // the newest message
        assertEquals(MessageOption.SHOWN_CHARS, list.rows[1].text.length) // cut at about three lines
        assertTrue(list.rows[1].text.endsWith("…"))
        assertEquals("00:00", list.rows[1].sub) // a one-to-one chat: no name on every row
        assertEquals("📷 o lugar", list.rows[2].text)
        assertEquals("Toque numa mensagem para ver o que fazer", list.subtitle) // up top
        assertNull(list.footer)
        assertTrue(fits(list))
    }

    @Test
    fun `a huge chat stays above the reach and slides with the mark`() {
        val many = (1..300).map { ZapMessage(if (it % 2 == 0) "Leo" else ZapMessage.ME, "mensagem $it", it * 60_000L * 31) }
        val chat = leoChat.copy(messages = many)
        val top = card(maker.make(Step.Thread(chat), selected = 1))
        assertEquals("mensagem 300", top.rows[1].text)
        assertEquals("▾", top.rows.last().text.take(1))
        assertTrue(fits(top))
        val deep = card(maker.make(Step.Thread(chat), selected = 150))
        assertEquals("▴", deep.rows.first().text.take(1))
        assertTrue(deep.rows.any { it.selected && it.text == "mensagem 151" })
        assertTrue(deep.rows.any { it.sub?.startsWith("Você · ") == true }) // your own rows say so
        assertTrue(fits(deep))
    }

    @Test
    fun `a tap on a message offers what fits it, the message on top`() {
        val options = card(maker.make(Step.Thread(leoChat, reading = false, focus = 0), selected = 0))
        assertEquals(
            listOf("📷 o lugar", "Ver a foto", "Responder", "🙂 Reagir", "🔊 Ler em voz alta", "Enviar foto ou vídeo"),
            options.rows.map { it.text },
        )
        assertFalse(options.rows[0].selected)
        assertTrue(options.rows[1].selected)
        assertEquals("Leo · 00:00", options.subtitle)
        val voice = leoChat.copy(messages = listOf(ZapMessage("Leo", "🎤 Mensagem de voz (0:12)", 1_000L)))
        val audio = card(maker.make(Step.Thread(voice, reading = false, focus = 0), selected = 0))
        assertEquals(listOf("🎤 Áudio 0:12", "▶ Ouvir este áudio", "Responder", "🙂 Reagir", "Enviar foto ou vídeo"), audio.rows.map { it.text })
    }

    @Test
    fun `a photo keeps its caption, never the placeholder, and counts its place`() {
        val first = maker.make(Step.Photo(leoChat, 0), 0) as Screen.Picture
        assertEquals("a1", first.photoKey)
        assertNull(first.caption)
        assertEquals("Leo · 1/2", first.title)
        assertNull(first.footer)
        val second = maker.make(Step.Photo(leoChat, 1), 0) as Screen.Picture
        assertEquals("o lugar", second.caption)
    }

    @Test
    fun `done offers try again only when asking again can help, debug line only with a note`() {
        val missed = card(maker.make(Step.Done(null, null, note = Note.NO_SPEECH), 0, ScreenMaker.Status(debugLine = "NO_SPEECH")))
        assertEquals("Tentar de novo", missed.rows.last().text)
        assertTrue(missed.rows.any { it.text == "test: NO_SPEECH" })
        val sent = card(maker.make(Step.Done(Recipient("Leo", "5511987650003"), "Bora", outcome = SendOutcome.SENT), 0, ScreenMaker.Status(debugLine = "x")))
        assertEquals("Voltar às conversas", sent.rows.last().text)
        assertEquals(Tone.NORMAL, sent.rows[1].tone)
        assertFalse(sent.rows.any { it.text.startsWith("test:") })
    }

    @Test
    fun `long names are cut to the width the lens shows`() {
        val long = leoChat.copy(title = "Leonardo de Alcântara Figueiredo Neto")
        val row = card(maker.make(Step.Inbox(listOf(long)), 0)).rows[1]
        assertEquals(ScreenMaker.LIST_CHARS, row.text.length)
        assertTrue(row.text.endsWith("…"))
    }

    @Test
    fun `read aloud says a photo in words, not the emoji`() {
        val text = ZapText(pt = true)
        assertEquals("Leo: mandou uma foto", text.heard("Leo", ZapMessage.PHOTO))
        assertEquals("Leo: oi", text.heard("Leo", "oi"))
    }

    @Test
    fun `no screen shows the version - the lens needs the room`() {
        assertNull(card(maker.make(Step.Inbox(listOf(leoChat)), 0)).subtitle)
        assertNull(card(maker.make(Step.Review(leoChat.recipient(), "oi"), 0)).subtitle)
    }

    @Test
    fun `choosing a shot says glasses or phone, and marks videos`() {
        val pick = maker.make(Step.ChoosePhoto(leoChat.recipient(), listOf("g:o:v:9", "g:c:i:1"), index = 0), 0) as Screen.Picture
        assertEquals("Para Leo · Óculos 1/2 · ▶ Vídeo", pick.title)
        assertNull(pick.footer)
        assertEquals("▶ Vídeo", pick.caption)
        val review = card(maker.make(Step.Review(leoChat.recipient(), "", "g:o:v:9"), 0))
        assertTrue(review.rows.any { it.text == "🎬 Vídeo anexado" })
    }

    @Test
    fun `a long list keeps to the top of the lens and slides with the choice`() {
        val chats = (1..12).map { leoChat.copy(key = "k$it", title = "Pessoa $it") }
        val top = card(maker.make(Step.Inbox(chats), 0))
        assertTrue(top.rows.first().selected)
        assertEquals("▾", top.rows.last().text.take(1))
        assertTrue(fits(top))
        // Far down the list: the chosen one is shown, the rows above are counted.
        val down = card(maker.make(Step.Inbox(chats), 10))
        assertTrue(down.rows.any { it.selected && it.text == "Pessoa 10" })
        assertEquals("▴", down.rows.first().text.take(1))
        assertTrue(fits(down))
        // The whole lens, when the wearer sees all of it.
        val whole = ScreenMaker(ZapText(pt = true), java.time.ZoneOffset.UTC, now = { 10_000L }, reach = null)
        assertTrue(card(whole.make(Step.Inbox(chats), 0)).rows.size > top.rows.size)
    }

    private fun fits(card: Screen.Card): Boolean {
        val used = card.rows.sumOf { row ->
            ScreenMaker.ROW_PX + (if (row.sub != null) ScreenMaker.SUB_PX else 0)
        }
        return (if (card.subtitle != null) ScreenMaker.TOP_WITH_SUBTITLE_PX else ScreenMaker.TOP_PX) + used <= ScreenMaker.REACH_PX
    }
}
