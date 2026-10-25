package io.alema.zap.core

import io.alema.zap.core.ContactFile.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactFileTest {
    @Test
    fun `vcard from a phone export`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Silva;Bruno;;;\r\nFN:Bruno Silva\r\nTEL;TYPE=CELL:+55 11 98765-0001\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Lima;Ana;;;\r\nTEL;TYPE=CELL:(11) 98765-0002\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Sem Número\r\nEND:VCARD\r\n"
        assertEquals(
            listOf(Entry("Bruno Silva", "5511987650001"), Entry("Ana Lima", "5511987650002")),
            ContactFile.parse(vcf),
        )
    }

    @Test
    fun `google contacts csv`() {
        val csv = "First Name,Last Name,Phone 1 - Label,Phone 1 - Value\n" +
            "Leo,Souza,Mobile,+55 21 99999-0003 ::: +55 21 3333-0000\n" +
            "\"Costa, Bia\",,Mobile,11 98765-0004\n"
        assertEquals(
            listOf(Entry("Leo Souza", "5521999990003"), Entry("Costa, Bia", "5511987650004")),
            ContactFile.parse(csv),
        )
    }

    @Test
    fun `excel in portuguese with semicolons, and no header at all`() {
        assertEquals(
            listOf(Entry("Beto", "5511987650005")),
            ContactFile.parse("Nome;Celular\nBeto;(11) 98765-0005\n"),
        )
        assertEquals(
            listOf(Entry("Zeca", "5511987650006")),
            ContactFile.parse("Zeca,11987650006\n"),
        )
    }

    @Test
    fun `numbers without area code are skipped, not guessed`() {
        assertEquals(emptyList<Entry>(), ContactFile.parse("Nome,Telefone\nFulano,98765-4321\n"))
    }
}
