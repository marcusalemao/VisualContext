package io.alema.qrreader

import io.alema.qrcore.QrParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultCardTest {

    @Test fun wifiShowsNetworkAndPassword() {
        val c = ResultCard.of(QrParser.parse("WIFI:T:WPA;S:Cafe da Paim;P:cafezinho123;;"))
        assertEquals("Wi-Fi", c.title)
        assertEquals(listOf("Cafe da Paim", "senha: cafezinho123"), c.lines)
        assertTrue(c.action is PhoneAction.JoinWifi)
    }

    @Test fun pixSaysWhatTheCodeClaimsAndOnlyCopies() {
        val c = ResultCard.of(QrParser.parse(
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D"))
        assertEquals("Pix", c.title)
        assertEquals("o código diz: Fulano de Tal", c.lines[0])
        assertEquals("valor: você digita · BRASILIA", c.lines[1])
        assertTrue(c.action is PhoneAction.CopyText)
    }

    @Test fun tamperedPixIsAWarningWithNoAction() {
        val c = ResultCard.of(QrParser.parse(
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Golpista  Tal6008BRASILIA62070503***63041D3D"))
        assertTrue(c.warning)
        assertNull(c.action)
        assertEquals("não pague por este código", c.lines[1])
    }

    @Test fun money() {
        assertEquals("R$ 2,50", ResultCard.money("2.50"))
        assertEquals("R$ 1.234,00", ResultCard.money("1234"))
        assertEquals("R$ 0,99", ResultCard.money("0.99"))
    }

    @Test fun bareLinkedInOpensTheProfile() {
        val c = ResultCard.of(QrParser.parse("https://www.linkedin.com/in/rafael-souza"))
        assertEquals("LinkedIn", c.title)
        assertTrue(c.action is PhoneAction.OpenLink)
    }

    @Test fun linkShowsHost() {
        val c = ResultCard.of(QrParser.parse("https://www.cafedapaim.example/cardapio"))
        assertEquals("cafedapaim.example", c.lines[0])
    }

    @Test fun cardsFitThreeLines() {
        listOf(
            "WIFI:T:WPA;S:Rede;P:x12345678;H:true;;",
            "BEGIN:VCARD\nFN:Rafael Souza\nORG:Torra Fina\nTITLE:Fornecedor\nTEL:+55 11 98888-7777\nEND:VCARD",
            "um texto bem comprido que não cabe numa linha só e precisa quebrar em várias linhas no visor dos óculos",
        ).forEach { assertTrue(ResultCard.of(QrParser.parse(it)).lines.size <= 3) }
    }
}
