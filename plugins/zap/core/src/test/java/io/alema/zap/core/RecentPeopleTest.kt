package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPeopleTest {
    private fun chat(key: String, title: String, digits: String?, at: Long, group: Boolean = false) =
        ZapConversation(key, title, digits, group, listOf(ZapMessage(title, "oi", at)))

    @Test
    fun `newest first, no starred contacts the wearer has not talked to`() {
        val mom = ZapContact("m", "Mãe", waIds = listOf("5511900000001"), starred = true, lastUsedMs = 0L)
        val bruno = ZapContact("b", "Bruno", waIds = listOf("5511900000002"), lastUsedMs = 3_000L)
        val inbox = listOf(chat("5511900000003", "Beto", "5511900000003", 5_000L), chat("g", "Família", null, 9_000L, group = true))

        val recent = RecentPeople.build(inbox, listOf(mom, bruno), limit = 8)

        assertEquals(listOf("Beto", "Bruno"), recent.map { it.name })
    }

    @Test
    fun `the same person from the inbox and the contacts appears once, with the newest time`() {
        val book = ZapContact("42", "Beto Silva", waIds = listOf("5511900000003"), lastUsedMs = 1_000L)
        val inbox = listOf(chat("5511900000003", "Beto", "5511900000003", 5_000L))

        val recent = RecentPeople.build(inbox, listOf(book), limit = 8)

        assertEquals(1, recent.size)
        assertEquals(5_000L, recent.single().lastUsedMs)
        assertEquals("5511900000003", recent.single().conversationKey)
    }

    @Test
    fun `a chat without a number still makes the list, keyed by its conversation`() {
        val recent = RecentPeople.build(listOf(chat("abc@lid", "Beto", null, 5_000L)), emptyList(), limit = 8)
        assertEquals("abc@lid", recent.single().conversationKey)
        assertEquals("abc@lid", Recipient.of(recent.single()).conversationKey)
    }
}
