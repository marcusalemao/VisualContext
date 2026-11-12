package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneAndLinkTest {
    @Test
    fun `brazilian formats`() {
        assertEquals("5511987654321", PhoneNumbers.toWhatsAppDigits("(11) 98765-4321"))
        assertEquals("5511987654321", PhoneNumbers.toWhatsAppDigits("+55 11 98765-4321"))
        assertEquals("5511987654321", PhoneNumbers.toWhatsAppDigits("011 98765-4321"))
        assertEquals("5511987654321", PhoneNumbers.toWhatsAppDigits("0 15 11 98765-4321"))
        assertEquals("551132654321", PhoneNumbers.toWhatsAppDigits("(11) 3265-4321"))
        assertEquals("5511987654321", PhoneNumbers.toWhatsAppDigits("5511987654321"))
    }

    @Test
    fun `a number without area code is refused, not guessed`() {
        assertNull(PhoneNumbers.toWhatsAppDigits("98765-4321"))
        assertNull(PhoneNumbers.toWhatsAppDigits(""))
    }

    @Test
    fun `international`() {
        assertEquals("14155550100", PhoneNumbers.toWhatsAppDigits("+1 415 555 0100"))
        assertEquals("4915112345678", PhoneNumbers.toWhatsAppDigits("0049 151 12345678"))
        assertEquals("4915112345678", PhoneNumbers.toWhatsAppDigits("0151 12345678", defaultCountryCode = "49"))
    }

    @Test
    fun `whatsapp jid`() {
        assertEquals("5511987654321", PhoneNumbers.fromWhatsAppJid("5511987654321@s.whatsapp.net"))
        assertNull(PhoneNumbers.fromWhatsAppJid("120363000000@g.us"))
        assertNull(PhoneNumbers.fromWhatsAppJid("abc@s.whatsapp.net"))
    }

    @Test
    fun `contact prefers the whatsapp id over the address book number`() {
        val contact = ZapContact("1", "Bruno", waIds = listOf("5511900000000"), phones = listOf("(11) 98765-4321"))
        assertEquals("5511900000000", contact.target())
        assertEquals("5511987654321", contact.copy(waIds = emptyList()).target())
    }

    @Test
    fun `links encode spaces as percent-20, never plus`() {
        assertEquals(
            "whatsapp://send?phone=5511987654321&text=T%C3%B4%20chegando%20%26%20j%C3%A1",
            WhatsAppLink.appUri("5511987654321", "Tô chegando & já"),
        )
        assertEquals("https://wa.me/5511987654321", WhatsAppLink.webUri("5511987654321", null))
    }
}
