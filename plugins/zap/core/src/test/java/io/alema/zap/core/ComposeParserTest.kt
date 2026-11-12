package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeParserTest {
    private val contacts = listOf(
        ZapContact("1", "Bruno Costa", waIds = listOf("5511987650001")),
        ZapContact("2", "Leo", waIds = listOf("5511987650002")),
        ZapContact("3", "Mariana Alves", waIds = listOf("5511987650003")),
        ZapContact("4", "Carolina (irmã)", waIds = listOf("5511987650004")),
        ZapContact("5", "Sarah Miller", phones = listOf("+1 415 555 0100")),
        ZapContact("6", "Ana", waIds = listOf("5511987650006")),
        ZapContact("7", "Bruna Lima", waIds = listOf("5511987650007")),
    )
    private val parser = ComposeParser(ContactMatcher(contacts))

    private fun parse(text: String) = parser.parse(text)!!

    @Test
    fun `portuguese with a colon`() {
        val r = parse("mensagem pro Bruno: tô chegando")
        assertEquals("Bruno Costa", r.matches.first().contact.name)
        assertEquals("Tô chegando", r.body)
    }

    @Test
    fun `portuguese with que`() {
        val r = parse("manda pro leo que já tô indo")
        assertEquals("Leo", r.matches.first().contact.name)
        assertEquals("Já tô indo", r.body)
    }

    @Test
    fun `speech to text without punctuation splits on the address book`() {
        val r = parse("mensagem pra mariana alves chego em dez minutos")
        assertEquals("Mariana Alves", r.matches.first().contact.name)
        assertEquals("Chego em dez minutos", r.body)
    }

    @Test
    fun `bare name then message`() {
        val r = parse("Leo tô chegando")
        assertEquals("Leo", r.matches.first().contact.name)
        assertEquals("Tô chegando", r.body)
    }

    @Test
    fun `only the name means the message comes next`() {
        val r = parse("mensagem pro Bruno")
        assertEquals("Bruno Costa", r.matches.first().contact.name)
        assertNull(r.body)
    }

    @Test
    fun `accents and nicknames in parentheses do not get in the way`() {
        val r = parse("avisa a carolina que o almoço atrasou")
        assertEquals("Carolina (irmã)", r.matches.first().contact.name)
        assertEquals("O almoço atrasou", r.body)
    }

    @Test
    fun `english`() {
        val r = parse("send a message to Sarah saying I'm running late")
        assertEquals("Sarah Miller", r.matches.first().contact.name)
        assertEquals("I'm running late", r.body)
        val t = parse("tell Leo that the meeting moved")
        assertEquals("Leo", t.matches.first().contact.name)
        assertEquals("The meeting moved", t.body)
    }

    @Test
    fun `spanish`() {
        val r = parse("mensaje para Ana que llego tarde")
        assertEquals("Ana", r.matches.first().contact.name)
        assertEquals("Llego tarde", r.body)
    }

    @Test
    fun `bruno and bruna are both offered, bruno first`() {
        val r = parse("mensagem pro bruno")
        val names = r.matches.map { it.contact.name }
        assertEquals("Bruno Costa", names.first())
        assertTrue("Bruna Lima" in names)
    }

    @Test
    fun `unknown name comes back with no matches instead of a guess`() {
        val r = parse("mensagem pro zeca tô chegando")
        assertTrue(r.matches.isEmpty())
        assertEquals("zeca", r.recipient)
    }

    @Test
    fun `empty input`() {
        assertNull(parser.parse("   "))
    }
}
