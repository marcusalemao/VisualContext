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

    private fun flow() = ComposeFlow({ people }).also { it.open(listOf(leo)) }

    @Test
    fun `one sentence goes straight to review, and nothing is sent before Send`() {
        val f = flow()
        assertEquals(listOf(Effect.StartListening), f.tap()) // "Speak" row
        val effects = f.onSpeechFinal("mensagem pro leo tô chegando")
        assertTrue(effects.none { it is Effect.Send })
        assertEquals(Step.Review(leo, "Tô chegando"), f.step)

        val send = f.tap() // Send is the first choice
        assertEquals(Effect.Send(leo, "5511987650003", "Tô chegando"), send.last())
        assertTrue(f.step is Step.Sending)
        f.onSendOutcome(SendOutcome.SENT)
        assertEquals(SendOutcome.SENT, (f.step as Step.Done).outcome)
    }

    @Test
    fun `tapping a person from the list asks only for the message`() {
        val f = flow()
        f.move(1) // first person in the shortlist
        f.tap()
        assertEquals(Step.Listening(leo), f.step)
        f.onSpeechFinal("já vou")
        assertEquals(Step.Review(leo, "Já vou"), f.step)
    }

    @Test
    fun `listen speaks the draft and keeps it`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("leo tô chegando")
        f.move(1)
        assertEquals(listOf(Effect.Speak(leo, "Tô chegando")), f.tap())
        assertTrue(f.step is Step.Review)
    }

    @Test
    fun `say it again re-listens for the same person`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("leo tô chegando")
        f.move(2)
        val effects = f.tap()
        assertEquals(listOf(Effect.StopSpeaking, Effect.StartListening), effects)
        assertEquals(Step.Listening(leo), f.step)
    }

    @Test
    fun `an ambiguous name asks which one and keeps the message`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("mensagem pra brun: oi")
        val pick = f.step as Step.Pick
        assertTrue(bruno in pick.choices && bruna in pick.choices)
        f.move(pick.choices.indexOf(bruna))
        f.tap()
        assertEquals(Step.Review(bruna, "Oi"), f.step)
    }

    @Test
    fun `unknown name and missing number end with a reason, not a guess`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("mensagem pro joaquim oi")
        assertEquals(Note.NO_CONTACT, (f.step as Step.Done).note)

        val g = flow()
        g.tap()
        g.onSpeechFinal("mensagem pro zeca: oi")
        assertEquals(Note.NO_NUMBER, (g.step as Step.Done).note)
    }

    @Test
    fun `back stops listening and returns home, back at home closes`() {
        val f = flow()
        f.tap()
        assertEquals(listOf(Effect.StopListening), f.back())
        assertEquals(Step.Home(listOf(leo)), f.step)
        assertEquals(listOf(Effect.Close), f.back())
    }

    @Test
    fun `a locked phone reports waiting, then sent`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("leo: oi")
        f.tap()
        f.onSendOutcome(SendOutcome.WAITING_FOR_UNLOCK)
        f.onSendOutcome(SendOutcome.SENT)
        assertEquals(SendOutcome.SENT, (f.step as Step.Done).outcome)
    }

    @Test
    fun `silence is reported`() {
        val f = flow()
        f.tap()
        f.onSpeechFinal("  ")
        assertEquals(Note.NO_SPEECH, (f.step as Step.Done).note)
    }
}
