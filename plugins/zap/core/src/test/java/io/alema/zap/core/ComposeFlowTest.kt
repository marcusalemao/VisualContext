package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeFlowTest {
    private val bruno = ZapContact("1", "Bruno Costa", waIds = listOf("5511987650001"))
    private val bruna = ZapContact("2", "Bruna Lima", waIds = listOf("5511987650002"))
    private val leo = ZapContact("3", "Leo", waIds = listOf("5511987650003"))
    private val noNumber = ZapContact("4", "Zeca", phones = listOf("98765-4321"))
    private val people = listOf(bruno, bruna, leo, noNumber)

    private val leoChat = ZapConversation(
        key = "5511987650003", title = "Leo", waDigits = "5511987650003", isGroup = false,
        messages = listOf(ZapMessage("Leo", "Bora almoçar?", 1_000L), ZapMessage("Leo", "Tô no centro", 2_000L)),
    )
    private val family = ZapConversation(
        key = "Família", title = "Família", waDigits = null, isGroup = true,
        messages = listOf(ZapMessage("Mãe", "Domingo tem almoço", 3_000L)),
    )

    private fun flow() = ComposeFlow({ people }, { listOf(leo) }).also { it.open(listOf(family, leoChat)) }

    @Test
    fun `the inbox is home, with new message first`() {
        val f = flow()
        assertEquals(Step.Inbox(listOf(family, leoChat)), f.step)
        assertEquals(3, f.choiceCount())
        f.tap()
        assertEquals(Step.NewMessage(listOf(leo)), f.step)
    }

    @Test
    fun `open a conversation, listen again, reply`() {
        val f = flow()
        f.move(2) // Leo's chat
        f.tap()
        assertEquals(Step.Thread(leoChat), f.step)

        f.move(1) // Listen again
        assertEquals(listOf(Effect.SpeakMessage(ZapMessage("Leo", "Tô no centro", 2_000L))), f.tap())

        f.move(-1) // Reply
        assertEquals(Effect.StartListening, f.tap().last())
        f.onSpeechFinal("chego em dez minutos")
        val review = f.step as Step.Review
        assertEquals("5511987650003", review.recipient.digits)
        assertEquals("5511987650003", review.recipient.conversationKey)
        assertEquals("Chego em dez minutos", review.body)

        val effects = f.tap() // Send
        assertEquals(Effect.Send(review.recipient, "Chego em dez minutos"), effects.last())
        f.onSendOutcome(SendOutcome.SENT)
        assertEquals(SendOutcome.SENT, (f.step as Step.Done).outcome)
    }

    @Test
    fun `a group without a live reply says to answer on the phone`() {
        val f = flow()
        f.move(1)
        f.tap()
        f.tap() // Reply
        assertEquals(Note.REPLY_ON_PHONE, (f.step as Step.Done).note)
    }

    @Test
    fun `a group with a live reply can be answered`() {
        val f = ComposeFlow({ people }).also { it.open(listOf(family.copy(canReplyLive = true))) }
        f.move(1)
        f.tap()
        assertEquals(Effect.StartListening, f.tap().last())
    }

    @Test
    fun `name first, then the message, nothing sent before Send`() {
        val f = flow()
        f.tap() // New message
        f.tap() // Say the name
        assertEquals(Step.Listening(null), f.step)
        val effects = f.onSpeechFinal("leo")
        assertEquals(Step.Listening(Recipient.of(leo)), f.step) // now: say the message
        assertTrue(effects.none { it is Effect.Send })
        f.onSpeechFinal("tô chegando")
        assertEquals(Step.Review(Recipient.of(leo), "Tô chegando"), f.step)
    }

    @Test
    fun `a greeting before the name does not get in the way`() {
        val f = flow()
        f.tap(); f.tap()
        f.onSpeechFinal("oi leo tudo bem")
        assertEquals(Step.Listening(Recipient.of(leo)), f.step)
    }

    @Test
    fun `new message to someone from the recent list asks only for the text`() {
        val f = flow()
        f.tap()
        f.move(1)
        f.tap()
        assertEquals(Step.Listening(Recipient.of(leo)), f.step)
    }

    @Test
    fun `Marcos finds Marcus`() {
        val marcus = ZapContact("9", "Marcus Alemão", waIds = listOf("5511900000009"))
        val f = ComposeFlow({ listOf(marcus, bruno) }).also { it.open(emptyList()) }
        f.tap()
        f.tap()
        f.onSpeechFinal("marcos")
        assertEquals(Step.Listening(Recipient.of(marcus)), f.step)
    }

    @Test
    fun `two people with a similar name are shown to compare, with say it again last`() {
        val f = flow()
        f.tap()
        f.tap()
        f.onSpeechFinal("brun")
        val pick = f.step as Step.Pick
        assertTrue(bruno in pick.choices && bruna in pick.choices)
        assertEquals(pick.choices.size + 1, f.choiceCount())
        f.move(pick.choices.indexOf(bruna))
        f.tap()
        assertEquals(Step.Listening(Recipient.of(bruna)), f.step)

        val g = flow()
        g.tap(); g.tap()
        g.onSpeechFinal("brun")
        g.move((g.step as Step.Pick).choices.size) // last row: say it again
        assertEquals(listOf(Effect.StartListening), g.tap())
        assertEquals(Step.Listening(null), g.step)
    }

    @Test
    fun `unknown name and missing number end with a reason, not a guess`() {
        val f = flow()
        f.tap(); f.tap()
        f.onSpeechFinal("joaquim")
        val done = f.step as Step.Done
        assertEquals(Note.NO_CONTACT, done.note)
        assertTrue(done.canRetry)
        assertEquals(listOf(Effect.StartListening), f.tap()) // "Try again" listens for the name again
        assertEquals(Step.Listening(null), f.step)

        val g = flow()
        g.tap(); g.tap()
        g.onSpeechFinal("zeca")
        assertEquals(Note.NO_NUMBER, (g.step as Step.Done).note)
    }

    @Test
    fun `someone known only from a live chat can be written to without a number`() {
        val beto = ZapConversation(
            key = "123456789@lid", title = "Beto", waDigits = null, isGroup = false,
            messages = listOf(ZapMessage("Beto", "Bom dia", 5_000L)), canReplyLive = true,
        )
        val recent = RecentPeople.build(listOf(beto), emptyList(), limit = 8)
        val f = ComposeFlow({ people }, { recent }).also { it.open(listOf(beto)) }
        f.tap(); f.tap()
        f.onSpeechFinal("beto")
        val listening = f.step as Step.Listening
        assertEquals("123456789@lid", listening.recipient?.conversationKey)
    }

    @Test
    fun `back returns to where the wearer came from`() {
        val f = flow()
        f.move(2); f.tap() // thread
        f.tap() // reply → listening
        assertEquals(listOf(Effect.StopListening), f.back())
        assertEquals(Step.Thread(leoChat), f.step)
        f.back()
        assertTrue(f.step is Step.Inbox)
        assertEquals(listOf(Effect.Close), f.back())
    }

    @Test
    fun `a new message refreshes the open thread`() {
        val f = flow()
        f.move(2); f.tap()
        val updated = leoChat.copy(messages = leoChat.messages + ZapMessage("Leo", "Cadê você?", 4_000L))
        f.refresh(listOf(updated, family))
        assertEquals(Step.Thread(updated), f.step)
    }

    @Test
    fun `a locked phone reports waiting, then sent`() {
        val f = flow()
        f.move(2); f.tap(); f.tap()
        f.onSpeechFinal("oi")
        f.tap()
        f.onSendOutcome(SendOutcome.WAITING_FOR_UNLOCK)
        f.onSendOutcome(SendOutcome.SENT)
        assertEquals(SendOutcome.SENT, (f.step as Step.Done).outcome)
    }

    @Test
    fun `silence is reported`() {
        val f = flow()
        f.tap(); f.tap()
        f.onSpeechFinal("  ")
        assertEquals(Note.NO_SPEECH, (f.step as Step.Done).note)
    }

    @Test
    fun `photos open from the conversation, swipe between them, back returns`() {
        val chat = leoChat.copy(messages = leoChat.messages + listOf(
            ZapMessage("Leo", "📷 Foto", 5_000L, image = "a"),
            ZapMessage("Leo", "Olha isso", 6_000L, image = "b"),
        ))
        val f = ComposeFlow({ people }).also { it.open(listOf(chat)) }
        f.move(1); f.tap() // open the chat
        assertEquals(3, f.choiceCount()) // Reply · Listen again · See photo
        f.move(ComposeFlow.THREAD_PHOTO)
        f.tap()
        assertEquals("b", (f.step as Step.Photo).message.image) // newest first
        f.move(-1)
        assertEquals("a", (f.step as Step.Photo).message.image)
        f.move(-1) // no wrap past the first photo
        assertEquals("a", (f.step as Step.Photo).message.image)
        f.back()
        assertEquals(Step.Thread(chat), f.step)
        assertEquals(ComposeFlow.THREAD_PHOTO, f.selected)
    }

    @Test
    fun `two photos in the same minute are both kept`() {
        val merged = InboxRules.merge(null, leoChat.copy(messages = listOf(
            ZapMessage("Leo", "📷 Foto", 60_000L, image = "a"),
            ZapMessage("Leo", "📷 Foto", 61_000L, image = "b"),
        )))
        assertEquals(2, merged.photos.size)
    }
}
