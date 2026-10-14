package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMakerTest {
    private val maker = ScreenMaker(ZapText(pt = true))

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
    fun `a chat labels only your own lines, then offers reply, listen and photos`() {
        val thread = card(maker.make(Step.Thread(leoChat), selected = 0))
        assertEquals(listOf(null, "Você", null, null), thread.rows.take(4).map { it.badge })
        assertEquals(listOf("Responder", "Ouvir de novo", "Ver fotos (2)"), thread.rows.drop(4).map { it.text })
        assertTrue(thread.rows[4].selected)
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
}
