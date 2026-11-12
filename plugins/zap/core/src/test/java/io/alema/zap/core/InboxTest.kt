package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InboxTest {
    private val day = 24L * 60 * 60 * 1000

    private fun chat(key: String, vararg times: Long) = ZapConversation(
        key = key, title = key, waDigits = null, isGroup = false,
        messages = times.map { ZapMessage(key, "m$it", it) },
    )

    @Test
    fun `merging keeps history and drops reposts`() {
        val stored = chat("Leo", 60_000, 120_000)
        val incoming = stored.copy(messages = listOf(ZapMessage("Leo", "m120000", 120_000), ZapMessage("Leo", "new", 180_000)))
        val merged = InboxRules.merge(stored, incoming)
        assertEquals(listOf("m60000", "m120000", "new"), merged.messages.map { it.text })
    }

    @Test
    fun `merging keeps a known number when the new notification lacks it`() {
        val stored = chat("Leo", 1).copy(waDigits = "5511900000000")
        val merged = InboxRules.merge(stored, chat("Leo", 2))
        assertEquals("5511900000000", merged.waDigits)
    }

    @Test
    fun `prune forgets old messages, sorts newest first, caps the list`() {
        val now = 30 * day
        val old = chat("old", now - 8 * day)
        val mixed = chat("mixed", now - 9 * day, now - day)
        val fresh = chat("fresh", now - 1000)
        val result = InboxRules.prune(listOf(old, mixed, fresh), now, keepDays = 7, maxConversations = 20)
        assertEquals(listOf("fresh", "mixed"), result.map { it.key })
        assertEquals(1, result[1].messages.size)
        assertEquals(1, InboxRules.prune(listOf(old, mixed, fresh), now, maxConversations = 1).size)
    }

    @Test
    fun `sound keys`() {
        assertEquals(Text.soundKey("Marcus"), Text.soundKey("Marcos"))
        assertEquals(Text.soundKey("Phillipe"), Text.soundKey("Filipe"))
        assertEquals(Text.soundKey("Kaio"), Text.soundKey("Caio"))
        assertEquals(Text.soundKey("Kelly"), Text.soundKey("Keli"))
        assertNotEquals(Text.soundKey("Bruno"), Text.soundKey("Bruna"))
    }
}
