package io.alema.zap.core

import io.alema.zap.core.HeadsetFlow.Answer
import io.alema.zap.core.HeadsetFlow.HeadsetEffect
import io.alema.zap.core.HeadsetFlow.Line
import io.alema.zap.core.HeadsetFlow.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetFlowTest {
    private val beto = ZapConversation("5511900000003", "Beto", "5511900000003", false, listOf(ZapMessage("Beto", "Bom dia", 1L)))
    private val group = ZapConversation("g@g.us", "Família", null, true, listOf(ZapMessage("Mãe", "Almoço?", 2L)))
    private val ana = ZapConversation("5511900000004", "Ana", "5511900000004", false, listOf(ZapMessage("Ana", "Oi", 3L)))

    @Test
    fun `read, reply by voice, send only after yes`() {
        val f = HeadsetFlow()
        val msg = beto.messages.single()
        assertEquals(listOf(HeadsetEffect.Say(Line.Announce(beto, msg, askReply = true))), f.onMessage(beto, msg))
        assertEquals(listOf(HeadsetEffect.Listen(long = false)), f.onSpoken())
        assertEquals(listOf(HeadsetEffect.Say(Line.SayMessage)), f.onHeard("sim"))
        assertEquals(listOf(HeadsetEffect.Listen(long = true)), f.onSpoken())
        assertEquals(listOf(HeadsetEffect.Say(Line.ReadBack("Beto", "Chego em dez minutos"))), f.onHeard("chego em dez minutos"))
        assertEquals(listOf(HeadsetEffect.Listen(long = false)), f.onSpoken())
        val send = f.onHeard("enviar")
        assertEquals(listOf(HeadsetEffect.Send(beto.recipient(), "Chego em dez minutos")), send)
        assertEquals(listOf(HeadsetEffect.Say(Line.Outcome(SendOutcome.SENT))), f.onSendOutcome(SendOutcome.SENT))
        assertEquals(listOf(HeadsetEffect.End), f.onSpoken())
        assertEquals(State.Idle, f.state)
    }

    @Test
    fun `silence or anything unclear never sends`() {
        val f = HeadsetFlow()
        f.onMessage(beto, beto.messages.single()); f.onSpoken(); f.onHeard("responder"); f.onSpoken()
        f.onHeard("tô indo"); f.onSpoken()
        assertEquals(listOf(HeadsetEffect.Say(Line.Cancelled)), f.onHeard(""))
        val g = HeadsetFlow()
        g.onMessage(beto, beto.messages.single()); g.onSpoken(); g.onHeard("sim"); g.onSpoken()
        g.onHeard("tô indo"); g.onSpoken()
        assertEquals(listOf(HeadsetEffect.Say(Line.Cancelled)), g.onHeard("não, manda"))
    }

    @Test
    fun `no answer to reply lets the message go`() {
        val f = HeadsetFlow()
        f.onMessage(beto, beto.messages.single()); f.onSpoken()
        assertEquals(listOf(HeadsetEffect.End), f.onHeard(""))
    }

    @Test
    fun `a group without a live reply is only read`() {
        val f = HeadsetFlow()
        val effects = f.onMessage(group, group.messages.single())
        assertEquals(false, ((effects.single() as HeadsetEffect.Say).line as Line.Announce).askReply)
        assertEquals(listOf(HeadsetEffect.End), f.onSpoken())
    }

    @Test
    fun `messages arriving mid-exchange wait their turn`() {
        val f = HeadsetFlow()
        f.onMessage(beto, beto.messages.single()); f.onSpoken()
        assertTrue(f.onMessage(ana, ana.messages.single()).isEmpty())
        val next = f.onHeard("depois")
        assertEquals(Line.Announce(ana, ana.messages.single(), true), (next.single() as HeadsetEffect.Say).line)
    }

    @Test
    fun `change dictates again, repeat reads back again`() {
        val f = HeadsetFlow()
        f.onMessage(beto, beto.messages.single()); f.onSpoken(); f.onHeard("sim"); f.onSpoken()
        f.onHeard("tô indo"); f.onSpoken()
        assertEquals(listOf(HeadsetEffect.Say(Line.ReadBack("Beto", "Tô indo"))), f.onHeard("repete"))
        f.onSpoken()
        assertEquals(listOf(HeadsetEffect.Say(Line.SayMessage)), f.onHeard("ditar de novo"))
    }

    @Test
    fun `classify answers`() {
        assertEquals(Answer.YES, HeadsetFlow.classify("Sim, responder"))
        assertEquals(Answer.NO, HeadsetFlow.classify("agora não"))
        assertEquals(Answer.NO, HeadsetFlow.classify("Não"))
        assertEquals(Answer.NONE, HeadsetFlow.classify("hmm"))
        assertEquals(Answer.YES, HeadsetFlow.classify("send"))
    }
}
