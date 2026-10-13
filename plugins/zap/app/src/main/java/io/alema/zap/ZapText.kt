package io.alema.zap

import android.content.Context
import java.util.Locale

/** Wearer-facing words. Portuguese when the phone is in Portuguese, English otherwise. */
internal class ZapText(private val pt: Boolean = Locale.getDefault().language == "pt") {
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
    fun seePhotos(count: Int) = when {
        count <= 1 -> if (pt) "Ver foto" else "See photo"
        pt -> "Ver fotos ($count)"
        else -> "See photos ($count)"
    }
    val photoGone get() = if (pt) "Esta foto não está mais guardada no celular" else "This photo is no longer saved on the phone"
    val replyOnPhone get() = if (pt) "Responda este grupo pelo celular" else "Answer this group on the phone"
    fun heard(sender: String, text: String) = "$sender: $text"
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
    fun spoken(name: String, body: String) = if (pt) "Para $name: $body" else "To $name: $body"

    /** "•• 4321": enough to tell two Brunos apart without putting a number on the lens. */
    fun numberHint(digits: String?) = digits?.takeLast(4)?.let { "•• $it" }

    private fun capitalized(value: String) = value.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    companion object {
        fun of(@Suppress("UNUSED_PARAMETER") context: Context) = ZapText()
    }
}

/**
 * Two timestamps that tell apart "Android never hands Zap a WhatsApp
 * notification" (access off, or restricted) from "it does, but Zap could not
 * read it" — the first question when the inbox stays empty.
 */
internal class ZapHealth(context: Context) {
    private val prefs = context.getSharedPreferences("zap_health", Context.MODE_PRIVATE)

    fun sawWhatsApp() = prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
    fun captured() = prefs.edit().putLong("captured", System.currentTimeMillis()).apply()
    fun lastSeen(): Long = prefs.getLong("seen", 0L)
    fun lastCaptured(): Long = prefs.getLong("captured", 0L)
    fun failed(reason: String) = prefs.edit().putString("error", reason.take(300)).apply()
    fun lastError(): String? = prefs.getString("error", null)

    /** How WhatsApp identified the last chat ("phone", "lid", "title"…): tells why a number is missing. */
    fun sawIdKind(kind: String) = prefs.edit().putString("id_kind", kind).apply()
    fun lastIdKind(): String? = prefs.getString("id_kind", null)
}

internal class ZapSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zap_settings", Context.MODE_PRIVATE)

    /** How long the inbox keeps messages on this phone. */
    fun keepDays(): Int = prefs.getInt(KEY_KEEP_DAYS, io.alema.zap.core.InboxRules.DEFAULT_KEEP_DAYS)

    fun setKeepDays(days: Int) {
        prefs.edit().putInt(KEY_KEEP_DAYS, days).apply()
    }

    /** How many conversations the inbox lists. */
    fun maxConversations(): Int = prefs.getInt(KEY_MAX_CHATS, io.alema.zap.core.InboxRules.DEFAULT_MAX_CONVERSATIONS)

    fun setMaxConversations(count: Int) {
        prefs.edit().putInt(KEY_MAX_CHATS, count).apply()
    }

    /** A short notice on the glasses when a WhatsApp message arrives. On by default. */
    fun announceArrivals(): Boolean = prefs.getBoolean(KEY_ANNOUNCE, true)

    fun setAnnounceArrivals(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCE, enabled).apply()
    }

    /** Off by default: the people list starts from who messaged you, no contacts permission. */
    fun importAddressBook(): Boolean = prefs.getBoolean(KEY_IMPORT, false)

    fun setImportAddressBook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT, enabled).apply()
    }

    private companion object {
        const val KEY_IMPORT = "import_address_book"
        const val KEY_ANNOUNCE = "announce_arrivals"
        const val KEY_KEEP_DAYS = "keep_days"
        const val KEY_MAX_CHATS = "max_conversations"
    }
}
