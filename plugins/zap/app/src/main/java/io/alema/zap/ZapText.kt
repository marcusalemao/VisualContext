package io.alema.zap

import android.content.Context
import java.util.Locale

/** Wearer-facing words. Portuguese when the phone is in Portuguese, English otherwise. */
internal class ZapText(private val pt: Boolean = Locale.getDefault().language == "pt") {
    val appTitle = "Zap"
    val newMessage get() = if (pt) "Nova mensagem" else "New message"
    val speak get() = if (pt) "Ditar mensagem" else "Dictate message"
    /** The listening card's hint: an instruction, never a sample message that could pass for a real one. */
    val sayWhoAndWhat get() = if (pt) "Diga o nome e a mensagem" else "Say the name and the message"
    val emptyPeople get() = if (pt) "Sua lista se forma com quem te manda mensagem" else "Your list fills with people who message you"
    val whoTitle get() = if (pt) "Para quem e o quê?" else "Who, and what?"
    val listening get() = if (pt) "Ouvindo…" else "Listening…"
    fun pickTitle(name: String) = if (pt) "Qual $name?" else "Which $name?"
    fun to(name: String) = if (pt) "Para $name" else "To $name"
    val sayMessage get() = if (pt) "Diga a mensagem" else "Say the message"
    val send get() = if (pt) "Enviar" else "Send"
    val listen get() = if (pt) "Ouvir antes" else "Listen first"
    val redo get() = if (pt) "Ditar de novo" else "Say it again"
    val sending get() = if (pt) "Enviando…" else "Sending…"
    val sent get() = if (pt) "Enviado" else "Sent"
    val openedForThumb get() = if (pt) "Aberto no celular: toque Enviar lá" else "Open on the phone: tap Send there"
    val waitingUnlock get() = if (pt) "Desbloqueie o celular para enviar" else "Unlock the phone to send"
    val notInstalled get() = if (pt) "WhatsApp não está instalado" else "WhatsApp is not installed"
    val failed get() = if (pt) "Não consegui abrir o WhatsApp" else "Could not open WhatsApp"
    fun noContact(name: String) = if (pt) "Ninguém chamado \"$name\"" else "No one called \"$name\""
    val noNumber get() = if (pt) "Sem número de WhatsApp" else "No WhatsApp number"
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

    companion object {
        fun of(@Suppress("UNUSED_PARAMETER") context: Context) = ZapText()
    }
}

internal class ZapSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zap_settings", Context.MODE_PRIVATE)

    /** Off by default: the people list starts from who messaged you, no contacts permission. */
    fun importAddressBook(): Boolean = prefs.getBoolean(KEY_IMPORT, false)

    fun setImportAddressBook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT, enabled).apply()
    }

    private companion object {
        const val KEY_IMPORT = "import_address_book"
    }
}
