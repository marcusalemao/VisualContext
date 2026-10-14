package io.alema.zap.core

import java.util.Locale

/** Wearer-facing words. Portuguese when the phone is in Portuguese, English otherwise. */
class ZapText(private val pt: Boolean = Locale.getDefault().language == "pt") {
    val appTitle = "Zap"
    val newMessage get() = if (pt) "Nova mensagem" else "New message"
    val speak get() = if (pt) "Ditar mensagem" else "Dictate message"
    /** Listening hints: instructions, never a sample message that could pass for a real one. */
    val sayOnlyName get() = if (pt) "Diga só o nome da pessoa" else "Say just the person's name"
    val emptyPeople get() = if (pt) "Sua lista se forma com quem te manda mensagem" else "Your list fills with people who message you"
    val whoTitle get() = if (pt) "Para quem?" else "To whom?"
    val listening get() = if (pt) "Ouvindo…" else "Listening…"
    val inboxTitle get() = if (pt) "Conversas" else "Chats"
    val emptyInbox get() = if (pt) "As conversas aparecem aqui quando chegarem mensagens" else "Chats appear here as messages arrive"
    val noAccess get() = if (pt) "Zap sem acesso às notificações: permita nos ajustes do Zap, no celular" else "Zap can't see notifications: allow it in Zap's settings on the phone"
    val sayName get() = if (pt) "Dizer o nome" else "Say the name"
    val noRecent get() = if (pt) "Ainda sem contatos recentes" else "No recent contacts yet"
    val reply get() = if (pt) "Responder" else "Reply"
    val listenAgain get() = if (pt) "Ouvir de novo" else "Listen again"
    val me get() = if (pt) "Você" else "You"
    /** Under a chat being read: what a tap brings up. */
    fun readerFooter(photos: Boolean) = when {
        photos && pt -> "Responder · Ouvir de novo · Fotos"
        photos -> "Reply · Listen again · Photos"
        pt -> "Responder · Ouvir de novo"
        else -> "Reply · Listen again"
    }
    val emptyChat get() = if (pt) "Nenhuma mensagem guardada" else "No saved messages"
    val earlier get() = if (pt) "mensagens anteriores não cabem aqui" else "earlier messages don't fit here"
    fun seePhotos(count: Int) = when {
        count <= 1 -> if (pt) "Ver foto" else "See photo"
        pt -> "Ver fotos ($count)"
        else -> "See photos ($count)"
    }
    val attachFrom get() = if (pt) "Anexar de onde?" else "Attach from"
    val camera get() = if (pt) "📷 Câmera" else "📷 Camera"
    val cameraHint get() = if (pt) "foto agora, com os óculos" else "a photo now, with the glasses"
    val gallery get() = if (pt) "🖼 Galeria" else "🖼 Gallery"
    val galleryHint get() = if (pt) "dos óculos e do celular" else "from the glasses and the phone"
    val takingPhoto get() = if (pt) "Tirando foto…" else "Taking a photo…"
    val cameraFailed get() = if (pt) "Não consegui tirar a foto. Confira se o Zap tem acesso à câmera nos ajustes do Nexus" else "Couldn't take the photo. Check that Zap has camera access in Nexus settings"
    val sendPhoto get() = if (pt) "Enviar foto ou vídeo" else "Send photo or video"
    val attachPhoto get() = if (pt) "Anexar foto ou vídeo" else "Attach photo or video"
    val changePhoto get() = if (pt) "Trocar anexo" else "Change attachment"
    val photoAttached get() = if (pt) "📷 Foto anexada" else "📷 Photo attached"
    val videoAttached get() = if (pt) "🎬 Vídeo anexado" else "🎬 Video attached"
    val video get() = if (pt) "▶ Vídeo" else "▶ Video"
    val fromGlasses get() = if (pt) "Óculos" else "Glasses"
    val fromPhone get() = if (pt) "Celular" else "Phone"
    val sayCaption get() = if (pt) "Ditar legenda" else "Say a caption"
    val noPhotos get() = if (pt) "Nenhuma foto ou vídeo para anexar. Permita o acesso às fotos nos ajustes do Zap, no celular" else "No photos or videos to attach. Allow photo access in Zap's settings on the phone"
    val photoGone get() = if (pt) "Esta foto não está mais guardada no celular" else "This photo is no longer saved on the phone"
    val replyOnPhone get() = if (pt) "Responda este grupo pelo celular" else "Answer this group on the phone"
    fun heard(sender: String, text: String) = "$sender: ${if (text == ZapMessage.PHOTO) photoSaid else text}"
    /** Said instead of the photo placeholder, which a voice would read as an emoji. */
    val photoSaid get() = if (pt) "mandou uma foto" else "sent a photo"
    fun pickTitle(name: String) = if (pt) "Qual ${capitalized(name)}?" else "Which ${capitalized(name)}?"
    val sayNameAgain get() = if (pt) "Dizer o nome de novo" else "Say the name again"
    /** Under a recent person Zap knows only through the chat, not by number. */
    val fromChat get() = if (pt) "da conversa recente" else "from a recent chat"
    val backToChats get() = if (pt) "Voltar às conversas" else "Back to chats"
    fun to(name: String) = if (pt) "Para $name" else "To $name"
    val sayMessage get() = if (pt) "Agora diga a mensagem" else "Now say the message"
    val send get() = if (pt) "Enviar" else "Send"
    val listen get() = if (pt) "Ouvir antes" else "Listen first"
    val redo get() = if (pt) "Ditar de novo" else "Say it again"
    val sending get() = if (pt) "Enviando…" else "Sending…"
    val sent get() = if (pt) "Enviado" else "Sent"
    val openedForThumb get() = if (pt) "Aberto no celular: toque Enviar lá" else "Open on the phone: tap Send there"
    val waitingUnlock get() = if (pt) "Desbloqueie o celular para enviar" else "Unlock the phone to send"
    val notInstalled get() = if (pt) "WhatsApp não está instalado" else "WhatsApp is not installed"
    val failed get() = if (pt) "Não consegui abrir o WhatsApp" else "Could not open WhatsApp"
    fun noContact(name: String) = if (pt) "Não achei \"$name\" nas conversas nem nos contatos" else "Couldn't find \"$name\" in chats or contacts"
    val noNumber get() = if (pt) "Sem o número dessa pessoa. Responda quando ela escrever de novo, ou importe os contatos nos ajustes do Zap" else "No number for this person. Reply when they write again, or import contacts in Zap's settings"
    val noSpeech get() = if (pt) "Não ouvi nada" else "Didn't hear anything"
    val speechUnavailable get() = if (pt) "Voz indisponível" else "Voice unavailable"

    /**
     * Zap keeps no speech keys: dictation and the read-back voice are whatever
     * the wearer configured in Rokid Nexus. When either fails, point there —
     * without recommending a provider; that choice is the wearer's.
     */
    val voiceHelp get() = if (pt) "Confira Speech e Voice nos ajustes do Nexus" else "Check Speech and Voice in Nexus settings"
    val tryAgain get() = if (pt) "Tentar de novo" else "Try again"

    /** What the glasses say for "Listen": the recipient, then the message. */
    fun spoken(name: String, body: String) = if (pt) "Para $name: ${body.ifBlank { "uma foto" }}" else "To $name: ${body.ifBlank { "a photo" }}"

    /** "•• 4321": enough to tell two Brunos apart without putting a number on the lens. */
    fun numberHint(digits: String?) = digits?.takeLast(4)?.let { "•• $it" }

    private fun capitalized(value: String) = value.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
