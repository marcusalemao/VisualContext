package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {
    private val maker = ScreenMaker(ZapText(pt = true), java.time.ZoneOffset.UTC, now = { 10_000L })
    private val chat = ZapConversation(
        key = "5511987650003", title = "Leo \"o Leão\"", waDigits = "5511987650003", isGroup = false,
        messages = listOf(
            ZapMessage("Leo", "linha 1\nlinha 2 \\ {chaves} 😊", 1_000L),
            ZapMessage(ZapMessage.ME, "Bora", 2_000L),
            ZapMessage("Leo", "o lugar", 4_000L, image = "b2"),
        ),
    )

    private fun roundTrip(screen: Screen) {
        val line = Wire.encode(screen)
        assertFalse("one message per line", line.contains('\n'))
        assertEquals(screen, Wire.decodeScreen(line))
    }

    @Test
    fun `every screen survives the trip, quotes, newlines, braces and emoji included`() {
        roundTrip(maker.make(Step.Inbox(listOf(chat)), 1))
        roundTrip(maker.make(Step.Thread(chat), 0))
        roundTrip(maker.make(Step.Thread(chat, reading = false), 2))
        roundTrip(maker.make(Step.Photo(chat, 0), 0))
        roundTrip(maker.make(Step.Listening(null, "oi"), 0))
        roundTrip(Screen.Card("t", listOf(Row("{\"x\":1}"))))
    }

    @Test
    fun `inputs survive the trip and swipes are one step at a time`() {
        val inputs = listOf(Wire.Input.Tap, Wire.Input.Back, Wire.Input.Move(1), Wire.Input.Move(-1),
            Wire.Input.Said("diga \"oi\"", final = true), Wire.Input.NeedPhoto("g:12"))
        inputs.forEach { assertEquals(it, Wire.decodeInput(Wire.encode(it))) }
        assertEquals(Wire.Input.Move(1), Wire.decodeInput("""{"v":1,"in":"move","d":40}"""))
    }

    @Test
    fun `other versions and garbage are ignored, never thrown`() {
        assertNull(Wire.decodeScreen("""{"v":2,"t":"card","title":"x"}"""))
        assertNull(Wire.decodeScreen("not json"))
        assertNull(Wire.decodeInput("""{"v":1,"in":"explode"}"""))
        assertNull(Wire.decodeInput("{"))
        assertTrue(Wire.encode(Wire.Input.Tap).startsWith("{\"v\":1"))
    }
}
