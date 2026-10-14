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
        assertEquals(Row("Nova mensagem"), home.rows[0])
        assertEquals(Row("Leo", sub = "o lugar", selected = true), home.rows[1])
    }

    @Test
    fun `an empty home says why when notification access is off`() {
        val noAccess = card(maker.make(Step.Inbox(emptyList()), 0, ScreenMaker.Status(notificationAccess = false)))
        assertEquals(Tone.ALERT, noAccess.rows.last().tone)
        val quiet = card(maker.make(Step.Inbox(emptyList()), 0))
        assertEquals(Tone.DIM, quiet.rows.last().tone)
    }

    @Test
    fun `a chat reads as a document, every message in full`() {
        val long = "Olha, ".repeat(200) + "fim."
        val chat = leoChat.copy(messages = leoChat.messages + ZapMessage("Leo", long, 5_000L))
        val reader = maker.make(Step.Thread(chat), 0) as Screen.Reader
        val segments = reader.segments
        assertEquals(Segment(Segment.Kind.HEADER, "Leo · 00:00"), segments[0])
        assertEquals(Segment(Segment.Kind.PROSE, "Bora almoçar?"), segments[1])
        // Your own turn: its own header, bright.
        assertEquals(Segment(Segment.Kind.HEADER, "Você · 00:00", emphasis = true), segments[3])
        // A photo without caption is one quiet line; with a caption, the caption follows.
        assertTrue(segments.contains(Segment(Segment.Kind.ASIDE, ZapMessage.PHOTO)))
        assertTrue(segments.contains(Segment(Segment.Kind.PROSE, "o lugar")))
        assertFalse(segments.contains(Segment(Segment.Kind.PROSE, ZapMessage.PHOTO)))
        // Nothing cut: the whole long message is there.
        assertEquals(long, segments.last().text)
        assertEquals("Responder · Ouvir de novo · Fotos", reader.footer)
        assertEquals("zap-chat-5511987650003", reader.contentKey)
    }

    @Test
    fun `a huge chat keeps the newest messages and says older ones are left out`() {
        val many = (1..300).map { ZapMessage(if (it % 2 == 0) "Leo" else ZapMessage.ME, "mensagem $it", it * 60_000L * 31) }
        val reader = maker.make(Step.Thread(leoChat.copy(messages = many)), 0) as Screen.Reader
        assertTrue(reader.segments.size <= ScreenMaker.MAX_SEGMENTS)
        assertEquals(Segment.Kind.ASIDE, reader.segments[0].kind)
        assertEquals(Segment.Kind.HEADER, reader.segments[1].kind)
        assertEquals("mensagem 300", reader.segments.last().text)
    }

    @Test
    fun `a tap on the chat brings its choices`() {
        val choices = card(maker.make(Step.Thread(leoChat, reading = false), selected = 0))
        assertEquals(listOf("Responder", "Ouvir de novo", "Enviar foto ou vídeo", "Ver fotos (2)"), choices.rows.map { it.text })
        assertTrue(choices.rows[0].selected)
    }

    @Test
    fun `a photo keeps its caption, never the placeholder, and counts its place`() {
        val first = maker.make(Step.Photo(leoChat, 0), 0) as Screen.Picture
        assertEquals("a1", first.photoKey)
        assertNull(first.caption)
        assertEquals("1/2", first.footer)
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
    fun `home shows the installed version at the bottom, other screens don't`() {
        val withVersion = ScreenMaker(ZapText(pt = true), version = "0.5.2")
        assertEquals("Zap 0.5.2", card(withVersion.make(Step.Inbox(emptyList()), 0)).footer)
        assertNull(card(withVersion.make(Step.Thread(leoChat, reading = false), 0)).footer)
    }

    @Test
    fun `choosing a shot says glasses or phone, and marks videos`() {
        val pick = maker.make(Step.ChoosePhoto(leoChat.recipient(), listOf("g:o:v:9", "g:c:i:1"), index = 0), 0) as Screen.Picture
        assertEquals("Óculos · 1/2", pick.footer)
        assertEquals("▶ Vídeo", pick.caption)
        val review = card(maker.make(Step.Review(leoChat.recipient(), "", "g:o:v:9"), 0))
        assertTrue(review.rows.any { it.text == "🎬 Vídeo anexado" })
    }
}
