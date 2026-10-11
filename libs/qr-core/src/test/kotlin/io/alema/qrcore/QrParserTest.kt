package io.alema.qrcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrParserTest {

    // --- Wi-Fi ---

    @Test fun wifiWpa() {
        val r = assertIs<QrResult.Wifi>(QrParser.parse("WIFI:T:WPA;S:Cafe da Paim;P:cafezinho123;;"))
        assertEquals("Cafe da Paim", r.ssid)
        assertEquals("cafezinho123", r.password)
        assertEquals(QrResult.Wifi.Security.WPA, r.security)
        assertEquals(false, r.hidden)
    }

    @Test fun wifiEscapesAndOrder() {
        val r = assertIs<QrResult.Wifi>(QrParser.parse("WIFI:S:Bar\\;Restô;H:true;P:a\\:b\\\\c;T:WPA2;;"))
        assertEquals("Bar;Restô", r.ssid)
        assertEquals("a:b\\c", r.password)
        assertTrue(r.hidden)
    }

    @Test fun wifiOpenAndSae() {
        assertEquals(QrResult.Wifi.Security.OPEN, assertIs<QrResult.Wifi>(QrParser.parse("WIFI:T:nopass;S:Livre;;")).security)
        assertNull(assertIs<QrResult.Wifi>(QrParser.parse("WIFI:T:nopass;S:Livre;P:ignored;;")).password)
        assertEquals(QrResult.Wifi.Security.SAE, assertIs<QrResult.Wifi>(QrParser.parse("WIFI:T:SAE;S:Novo;P:x12345678;;")).security)
    }

    @Test fun wifiInvalid() {
        assertIs<QrResult.Invalid>(QrParser.parse("WIFI:T:WPA;P:x;;"))
        assertIs<QrResult.Invalid>(QrParser.parse("WIFI:T:WPA;S:SemSenha;;"))
    }

    // --- Pix ---

    private val staticPix =
        "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D"

    @Test fun pixReferenceCrc() {
        // Example from the Banco Central Pix manual; its published CRC is 1D3D.
        assertEquals("1D3D", QrParser.crc16(staticPix.dropLast(4)))
    }

    @Test fun pixStatic() {
        val r = assertIs<QrResult.Pix>(QrParser.parse(staticPix))
        assertEquals("123e4567-e12b-12d1-a456-426655440000", r.key)
        assertEquals("Fulano de Tal", r.merchantName)
        assertEquals("BRASILIA", r.merchantCity)
        assertNull(r.amount)
        assertNull(r.txid)
        assertEquals(false, r.isDynamic)
    }

    @Test fun pixWithAmountAndDynamic() {
        val body = "000201010212" +
            "26" + tlvLen("00" + tlvLen("br.gov.bcb.pix") + "25" + tlvLen("pix.example.com/qr/v2/9d36b84fc70b478f")) +
            "52040000" + "5303986" + "54" + tlvLen("2.50") + "5802BR" +
            "59" + tlvLen("Padaria Boa P") + "60" + tlvLen("SAO PAULO") +
            "62" + tlvLen("05" + tlvLen("TX12345")) + "6304"
        val code = body + QrParser.crc16(body)
        val r = assertIs<QrResult.Pix>(QrParser.parse(code))
        assertEquals("2.50", r.amount)
        assertEquals("https://pix.example.com/qr/v2/9d36b84fc70b478f", r.dynamicUrl)
        assertTrue(r.isDynamic)
        assertEquals("TX12345", r.txid)
        assertEquals("Padaria Boa P", r.merchantName)
    }

    @Test fun pixBadCrcIsRejected() {
        val tampered = staticPix.replace("Fulano de Tal", "Golpista  Tal")
        val r = assertIs<QrResult.Invalid>(QrParser.parse(tampered))
        assertEquals("pix", r.kind)
    }

    private fun tlvLen(v: String) = "%02d".format(v.length) + v

    // --- Contacts ---

    @Test fun vcard() {
        val card = """
            BEGIN:VCARD
            VERSION:3.0
            N:Souza;Rafael;;;
            FN:Rafael Souza
            ORG:Torra Fina;Vendas
            TITLE:Fornecedor de café
            item1.TEL;TYPE=CELL:+55 11 98888-7777
            EMAIL;TYPE=WORK:rafael@torrafina.example
            URL:https://torrafina.example
            END:VCARD
        """.trimIndent()
        val r = assertIs<QrResult.Contact>(QrParser.parse(card))
        assertEquals("Rafael Souza", r.name)
        assertEquals(listOf("+55 11 98888-7777"), r.phones)
        assertEquals("Torra Fina", r.org)
        assertEquals("Fornecedor de café", r.title)
        assertEquals(QrResult.Contact.Source.VCARD, r.source)
    }

    @Test fun vcardFoldedAndNOnly() {
        val r = assertIs<QrResult.Contact>(QrParser.parse("BEGIN:VCARD\r\nN:Lima;Carla\r\nEMAIL:carla@exa\r\n mple.com\r\nEND:VCARD"))
        assertEquals("Carla Lima", r.name)
        assertEquals(listOf("carla@example.com"), r.emails)
    }

    @Test fun mecard() {
        val r = assertIs<QrResult.Contact>(QrParser.parse("MECARD:N:Lima,Carla;TEL:+5511977776666;EMAIL:carla@example.com;;"))
        assertEquals("Carla Lima", r.name)
        assertEquals(listOf("+5511977776666"), r.phones)
    }

    @Test fun linkedinAndWhatsapp() {
        assertEquals(QrResult.Contact.Source.LINKEDIN,
            assertIs<QrResult.Contact>(QrParser.parse("https://www.linkedin.com/in/rafael-souza")).source)
        val wa = assertIs<QrResult.Contact>(QrParser.parse("https://wa.me/5511988887777"))
        assertEquals(listOf("+5511988887777"), wa.phones)
    }

    // --- Links and text ---

    @Test fun link() {
        val r = assertIs<QrResult.Link>(QrParser.parse("https://www.cafedapaim.example/cardapio"))
        assertEquals("cafedapaim.example", r.host)
    }

    @Test fun nonWebSchemesAreText() {
        assertIs<QrResult.Text>(QrParser.parse("javascript:alert(1)"))
        assertIs<QrResult.Text>(QrParser.parse("Mesa 12"))
    }
}
