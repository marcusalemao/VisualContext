package io.alema.zap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(Step.Thread(leoChat), f.step) // the messages, newest first, under Reply
        assertEquals(1 + leoChat.messages.size, f.choiceCount())
        assertEquals(1, f.selected) // the newest message is marked
        f.tap() // what to do with it
        assertEquals(Step.Thread(leoChat, reading = false, focus = 0), f.step)

        f.move(2) // Read it aloud (after Reply and React)
        assertEquals(listOf(Effect.SpeakMessage(ZapMessage("Leo", "Tô no centro", 2_000L))), f.tap())

        f.move(-2) // Reply
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
        f.tap(); f.tap() // the chat, its choices
        f.tap() // Reply
        assertEquals(Note.REPLY_ON_PHONE, (f.step as Step.Done).note)
    }

    @Test
    fun `a group with a live reply can be answered`() {
        val f = ComposeFlow({ people }).also { it.open(listOf(family.copy(canReplyLive = true))) }
        f.move(1)
        f.tap(); f.tap()
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
        f.tap() // the newest message's options
        f.tap() // reply → listening
        assertEquals(listOf(Effect.StopListening), f.back())
        assertEquals(Step.Thread(leoChat), f.step) // the chat's list
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
        f.move(2); f.tap(); f.tap(); f.tap()
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
        f.move(1); f.tap(); f.tap() // open the chat, then the newest message (a photo with a caption)
        assertEquals(5, f.choiceCount()) // See the photo · Reply · React · Read it aloud · Send a photo
        f.tap()
        assertEquals("b", (f.step as Step.Photo).message.image) // newest first
        f.move(-1)
        assertEquals("a", (f.step as Step.Photo).message.image)
        f.move(-1) // no wrap past the first photo
        assertEquals("a", (f.step as Step.Photo).message.image)
        f.back()
        assertEquals(Step.Thread(chat, focus = 1), f.step) // the list, on photo "a"
        assertEquals(2, f.selected)
    }

    @Test
    fun `two photos in the same minute are both kept`() {
        val merged = InboxRules.merge(null, leoChat.copy(messages = listOf(
            ZapMessage("Leo", "📷 Foto", 60_000L, image = "a"),
            ZapMessage("Leo", "📷 Foto", 61_000L, image = "b"),
        )))
        assertEquals(2, merged.photos.size)
    }

    @Test
    fun `send a photo from a chat, alone or with a caption`() {
        val f = ComposeFlow({ people }, gallery = { listOf("g:3", "g:2", "g:1") }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap() // Leo's chat, its choices
        f.move(TEXT_SEND_PHOTO)
        f.tap()
        assertTrue(f.step is Step.AttachFrom) // Camera / Gallery
        f.move(ComposeFlow.ATTACH_GALLERY); f.tap()
        assertEquals("g:3", (f.step as Step.ChoosePhoto).photo) // newest first
        f.move(1)
        f.tap() // attach g:2
        assertEquals(Step.Review(leoChat.recipient(), "", "g:2"), f.step)
        f.move(ComposeFlow.REVIEW_REDO) // "Say a caption"
        assertEquals(Effect.StartListening, f.tap().last())
        f.onSpeechFinal("olha que lindo")
        assertEquals(Step.Review(leoChat.recipient(), "Olha que lindo", "g:2"), f.step)
        val effects = f.tap() // Send
        assertEquals(Effect.Send(leoChat.recipient(), "Olha que lindo", "g:2"), effects.last())
    }

    @Test
    fun `attach a photo on the review card, back keeps the draft as it was`() {
        val f = ComposeFlow({ people }, gallery = { listOf("g:9") }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap(); f.tap() // reply
        f.onSpeechFinal("chegando")
        f.move(ComposeFlow.REVIEW_PHOTO)
        f.tap()
        f.move(ComposeFlow.ATTACH_GALLERY); f.tap()
        assertTrue(f.step is Step.ChoosePhoto)
        f.back() // back to Camera / Gallery, on Gallery
        assertTrue(f.step is Step.AttachFrom)
        assertEquals(ComposeFlow.ATTACH_GALLERY, f.selected)
        f.back()
        assertEquals(Step.Review(leoChat.recipient(), "Chegando"), f.step)
    }

    @Test
    fun `no number or no photos says so instead of opening the gallery`() {
        val lid = leoChat.copy(key = "1@lid", waDigits = null, canReplyLive = true)
        val noNumber = ComposeFlow({ people }, gallery = { listOf("g:1") }).also { it.open(listOf(lid)) }
        noNumber.move(1); noNumber.tap(); noNumber.tap()
        noNumber.move(TEXT_SEND_PHOTO); noNumber.tap()
        assertEquals(Note.NO_NUMBER, (noNumber.step as Step.Done).note)

        val empty = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        empty.move(1); empty.tap(); empty.tap()
        empty.move(TEXT_SEND_PHOTO); empty.tap()
        empty.move(ComposeFlow.ATTACH_GALLERY); empty.tap()
        assertEquals(Note.NO_PHOTOS, (empty.step as Step.Done).note)
    }

    @Test
    fun `choosing asks the glasses for new shots, and fresh ones keep the wearer on the same photo`() {
        var gallery = listOf("g:c:i:2", "g:c:i:1")
        val f = ComposeFlow({ people }, gallery = { gallery }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap()
        f.move(TEXT_SEND_PHOTO)
        assertTrue(Effect.PullGlassesMedia in f.tap()) // already on Camera / Gallery
        f.move(ComposeFlow.ATTACH_GALLERY); f.tap()
        f.move(1) // on g:c:i:1
        gallery = listOf("g:o:v:9", "g:o:i:8") + gallery // the glasses' shots arrive, newest first
        f.refreshPhotos(gallery)
        assertEquals("g:c:i:1", (f.step as Step.ChoosePhoto).photo)
        assertEquals(3, (f.step as Step.ChoosePhoto).index)
    }

    @Test
    fun `gallery keys say where a shot came from and whether it is a video`() {
        assertEquals(GalleryKey(fromGlasses = true, video = true, id = 9), GalleryKey.parse("g:o:v:9"))
        assertEquals("g:c:i:12", GalleryKey(false, false, 12).toString())
        assertEquals(null, GalleryKey.parse("photo-key"))
    }

    @Test
    fun `take a photo with the glasses, check it, attach it`() {
        val f = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap()
        f.move(TEXT_SEND_PHOTO); f.tap()
        assertEquals(ComposeFlow.ATTACH_CAMERA, f.selected) // Camera comes first
        assertTrue(Effect.TakeSnapshot in f.tap())
        assertTrue(f.step is Step.Snapping)
        f.onSnapshot("snap:1")
        assertEquals("snap:1", (f.step as Step.ChoosePhoto).photo)
        f.tap()
        assertEquals(Step.Review(leoChat.recipient(), "", "snap:1"), f.step)
    }

    @Test
    fun `a failed or abandoned snapshot never attaches anything`() {
        val f = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap(); f.move(TEXT_SEND_PHOTO); f.tap(); f.tap()
        f.onSnapshotFailed()
        assertEquals(Note.CAMERA_FAILED, (f.step as Step.Done).note)
        assertFalse((f.step as Step.Done).canRetry)
        val g = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        g.move(1); g.tap(); g.tap(); g.move(TEXT_SEND_PHOTO); g.tap(); g.tap()
        g.back()
        assertTrue(g.step is Step.AttachFrom)
        g.onSnapshot("snap:late")
        assertFalse(g.step is Step.ChoosePhoto)
    }

    @Test
    fun `a camera photo taken from the review card keeps the words already said`() {
        val f = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        f.move(1); f.tap(); f.tap(); f.tap() // reply
        f.onSpeechFinal("olha isso")
        f.move(ComposeFlow.REVIEW_PHOTO); f.tap()
        f.tap() // Camera
        f.onSnapshot("snap:2")
        f.tap() // attach
        assertEquals(Step.Review(leoChat.recipient(), "Olha isso", "snap:2"), f.step)
    }

    @Test
    fun `a voice message plays straight from the chat, stops, swipes, and says why when it cannot`() {
        val voice = leoChat.copy(
            messages = leoChat.messages + listOf(
                ZapMessage("Leo", "🎤 Mensagem de voz (0:12)", 9_000_000L),
                ZapMessage("Leo", "🎤 Mensagem de voz (1:05)", 9_500_000L),
            ),
        )
        assertEquals(65, voice.voiceNotes.last().voiceSeconds)
        assertEquals(null, ZapMessage.voiceSeconds("oi"))
        val f = ComposeFlow({ people }).also { it.open(listOf(voice)) }
        f.move(1); f.tap(); f.tap() // the newest message: a voice note
        val play = f.tap() // ▶ Play this voice message
        assertEquals(Effect.PlayAudio(voice.voiceNotes.last(), voice.key), play.last())
        assertTrue((f.step as Step.Audio).playing)
        assertFalse((f.step as Step.Audio).started) // "Loading…" until the sound starts
        f.onAudioStarted()
        assertTrue((f.step as Step.Audio).started)
        assertEquals(listOf(Effect.StopAudio), f.tap()) // stop
        f.tap() // play again
        assertEquals(listOf(Effect.StopAudio), f.move(-1)) // the older one, stopping this
        assertEquals("🎤 Mensagem de voz (0:12)", (f.step as Step.Audio).message.text)
        f.tap()
        f.onAudioFailed(Note.NO_HEADPHONES)
        assertEquals(Note.NO_HEADPHONES, (f.step as Step.Audio).problem)
        assertEquals(listOf(Effect.StopAudio), f.back())
        assertEquals(2, f.selected) // the list, on the voice note just heard
    }

    @Test
    fun `a long chat is a list with no wrap, a tap acts on the marked message, back keeps the mark`() {
        val long = "palavra ".repeat(20).trim()
        val chat = leoChat.copy(messages = leoChat.messages + ZapMessage("Leo", long, 9_000L))
        val f = ComposeFlow({ people }).also { it.open(listOf(chat)) }
        f.move(1); f.tap()
        f.move(-5) // up stops at Reply
        assertEquals(0, f.selected)
        f.move(50) // down stops at the oldest
        assertEquals(chat.messages.size, f.selected)
        f.move(-(chat.messages.size - 1)) // back to the newest (the long one)
        f.tap()
        assertEquals(MessageOption.of(chat.newestFirst()[0]), listOf(MessageOption.REPLY, MessageOption.REACT, MessageOption.READ_ALOUD, MessageOption.READ_FULL, MessageOption.SEND_PHOTO))
        f.move(3); f.tap() // Read the whole message
        assertTrue(f.step is Step.Read)
        f.back()
        assertEquals(3, f.selected) // on "Read the whole message"
        f.back()
        assertEquals(Step.Thread(chat), f.step)
        assertEquals(1, f.selected)
    }

    @Test
    fun `a photo taken while Zap is open is offered right there, the open chat first, back drops it`() {
        val f = ComposeFlow({ people }, recentPeople = { listOf(leo) }).also { it.open(listOf(family, leoChat)) }
        f.move(2); f.tap() // Leo's chat
        val before = f.step
        val ana = Recipient("Ana", "5511999990000")
        assertTrue(f.offerPhoto("g:o:i:7", listOf(ana, leoChat.recipient())))
        val offer = f.step as Step.PhotoOffer
        assertEquals(listOf("Leo", "Ana"), offer.choices.map { it.name }) // this chat first, no duplicates
        f.move(5) // stops at "someone else"
        assertEquals(2, (f.step as Step.PhotoOffer).index)
        f.back()
        assertEquals(before, f.step)
        assertEquals(1, f.selected)

        f.offerPhoto("g:o:i:7", listOf(ana))
        f.tap() // Leo
        assertEquals(Step.Review(leoChat.recipient(), "", "g:o:i:7"), f.step)
    }

    @Test
    fun `someone else carries the photo to the person picked, and writing is never interrupted`() {
        val f = ComposeFlow({ people }, recentPeople = { listOf(leo) }).also { it.open(listOf(leoChat)) }
        assertTrue(f.offerPhoto("g:o:i:8", emptyList()))
        f.tap() // only "someone else"
        assertTrue(f.step is Step.NewMessage)
        f.move(1); f.tap() // Leo from the recent people
        assertEquals("g:o:i:8", (f.step as Step.Review).photo)

        val g = ComposeFlow({ people }).also { it.open(listOf(leoChat)) }
        g.tap(); g.tap() // new message → listening for a name
        assertFalse(g.offerPhoto("g:o:i:9", emptyList()))
        assertTrue(g.step is Step.Listening)
    }

    private companion object {
        /** A text message's options: Reply · Read it aloud · Send a photo. */
        const val TEXT_SEND_PHOTO = 3 // Reply · React · Read it aloud · Send a photo
    }
}
