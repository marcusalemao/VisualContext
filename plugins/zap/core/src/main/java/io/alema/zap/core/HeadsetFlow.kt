package io.alema.zap.core

/**
 * Zap without Nexus: the glasses are only a Bluetooth headset, so everything
 * happens by voice. A message is read aloud, Zap asks "Reply?", the wearer
 * dictates, Zap reads the draft back and sends only after a spoken "send".
 *
 * Pure state machine like [ComposeFlow]: the phone runs the [HeadsetEffect]s
 * (speak, listen, send) and reports back. Messages that arrive mid-exchange
 * wait in a queue, one per chat, and are read when the exchange ends.
 */
class HeadsetFlow {

    sealed interface State {
        object Idle : State
        data class Announcing(val chat: ZapConversation, val message: ZapMessage) : State
        data class AskingReply(val chat: ZapConversation, val message: ZapMessage) : State
        data class PromptingDictation(val chat: ZapConversation) : State
        data class Dictating(val chat: ZapConversation) : State
        data class ReadingBack(val chat: ZapConversation, val body: String) : State
        data class Confirming(val chat: ZapConversation, val body: String) : State
        data class Sending(val chat: ZapConversation, val body: String) : State

        /** Saying a last line ("Sent", "Cancelled"); the next queued message follows. */
        object Finishing : State
    }

    /** What to say. The phone turns these into words in the wearer's language. */
    sealed interface Line {
        /** "Beto: on my way." plus "Reply?" when a reply is possible. */
        data class Announce(val chat: ZapConversation, val message: ZapMessage, val askReply: Boolean) : Line
        object SayMessage : Line
        data class ReadBack(val name: String, val body: String) : Line
        data class Outcome(val outcome: SendOutcome) : Line
        object Cancelled : Line
        object NotHeard : Line
    }

    sealed interface HeadsetEffect {
        data class Say(val line: Line) : HeadsetEffect
        /** Short = a yes/no answer; long = a whole message. */
        data class Listen(val long: Boolean) : HeadsetEffect
        data class Send(val recipient: Recipient, val body: String) : HeadsetEffect
        /** Nothing left to do: release the microphone, the audio route and the on-phone indicator. */
        object End : HeadsetEffect
    }

    enum class Answer { YES, NO, REPEAT, CHANGE, NONE }

    var state: State = State.Idle
        private set

    private val queue = linkedMapOf<String, Pair<ZapConversation, ZapMessage>>()

    val isBusy: Boolean get() = state != State.Idle

    fun onMessage(chat: ZapConversation, message: ZapMessage): List<HeadsetEffect> {
        if (state != State.Idle) {
            // Newest message per chat wins; the chat moves to the back of the line.
            queue.remove(chat.key)
            queue[chat.key] = chat to message
            return emptyList()
        }
        return announce(chat, message)
    }

    /** The last line finished playing. */
    fun onSpoken(): List<HeadsetEffect> = when (val s = state) {
        is State.Announcing -> if (s.chat.canReply) {
            state = State.AskingReply(s.chat, s.message)
            listOf(HeadsetEffect.Listen(long = false))
        } else {
            next()
        }
        is State.PromptingDictation -> {
            state = State.Dictating(s.chat)
            listOf(HeadsetEffect.Listen(long = true))
        }
        is State.ReadingBack -> {
            state = State.Confirming(s.chat, s.body)
            listOf(HeadsetEffect.Listen(long = false))
        }
        State.Finishing -> next()
        else -> emptyList()
    }

    /** What the microphone heard; blank means silence or a recognition error. */
    fun onHeard(text: String): List<HeadsetEffect> = when (val s = state) {
        is State.AskingReply -> when (classify(text)) {
            Answer.YES -> promptDictation(s.chat)
            Answer.REPEAT -> announce(s.chat, s.message)
            // Silence or "no": let it go quietly. The message stays in the inbox.
            else -> next()
        }
        is State.Dictating -> {
            val body = ComposeParser.tidyBody(text)
            if (body.isBlank()) {
                finish(Line.NotHeard)
            } else {
                state = State.ReadingBack(s.chat, body)
                listOf(HeadsetEffect.Say(Line.ReadBack(s.chat.title, body)))
            }
        }
        is State.Confirming -> when (classify(text)) {
            Answer.YES -> {
                state = State.Sending(s.chat, s.body)
                listOf(HeadsetEffect.Send(s.chat.recipient(), s.body))
            }
            Answer.CHANGE -> promptDictation(s.chat)
            Answer.REPEAT -> {
                state = State.ReadingBack(s.chat, s.body)
                listOf(HeadsetEffect.Say(Line.ReadBack(s.chat.title, s.body)))
            }
            // Never send on silence or anything unclear.
            else -> finish(Line.Cancelled)
        }
        else -> emptyList()
    }

    fun onSendOutcome(outcome: SendOutcome): List<HeadsetEffect> {
        if (state !is State.Sending) return emptyList()
        return finish(Line.Outcome(outcome))
    }

    /** Audio lost (headset gone, call started): drop the exchange, keep the queue for later. */
    fun abort(): List<HeadsetEffect> {
        if (state == State.Idle) return emptyList()
        state = State.Idle
        return listOf(HeadsetEffect.End)
    }

    private fun announce(chat: ZapConversation, message: ZapMessage): List<HeadsetEffect> {
        state = State.Announcing(chat, message)
        return listOf(HeadsetEffect.Say(Line.Announce(chat, message, askReply = chat.canReply)))
    }

    private fun promptDictation(chat: ZapConversation): List<HeadsetEffect> {
        state = State.PromptingDictation(chat)
        return listOf(HeadsetEffect.Say(Line.SayMessage))
    }

    private fun finish(line: Line): List<HeadsetEffect> {
        state = State.Finishing
        return listOf(HeadsetEffect.Say(line))
    }

    private fun next(): List<HeadsetEffect> {
        val key = queue.keys.firstOrNull() ?: run {
            state = State.Idle
            return listOf(HeadsetEffect.End)
        }
        val (chat, message) = queue.remove(key)!!
        return announce(chat, message)
    }

    companion object {
        private val YES = setOf(
            "sim", "responder", "responde", "resposta", "quero", "pode", "claro", "isso", "manda", "mandar",
            "envia", "enviar", "envie", "ok", "okay", "bora", "yes", "yeah", "reply", "send", "si", "enviar",
        )
        private val NO = setOf(
            "nao", "cancela", "cancelar", "depois", "deixa", "nada", "no", "nope", "cancel", "later", "agora",
        )
        private val REPEAT = setOf("repete", "repetir", "repita", "again", "repeat", "quem", "oi", "que", "como")
        private val CHANGE = setOf("novo", "mudar", "muda", "corrigir", "corrige", "refazer", "outra", "change", "edit", "errado")

        /**
         * The first word that means something decides: "não, manda" is a no.
         * "agora" counts as no only because "agora não" is the common way to say it.
         */
        fun classify(text: String): Answer {
            for (word in Text.tokens(text)) {
                when (word) {
                    in NO -> return Answer.NO
                    in CHANGE -> return Answer.CHANGE
                    in REPEAT -> return Answer.REPEAT
                    in YES -> return Answer.YES
                }
            }
            return Answer.NONE
        }
    }
}
