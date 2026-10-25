package io.alema.zap.core

/**
 * Quick reactions, sent as a message of their own (one emoji shows big in
 * WhatsApp). The wearer picks up to [MAX] on the phone, and a skin tone; on
 * the lens everything is green, but the person receiving sees the real color.
 * Each emoji carries a word too: the lens shows both, and voice mode says the word.
 */
data class Reaction(val emoji: String, val word: String)

enum class SkinTone(val modifier: String) {
    DEFAULT(""), LIGHT("🏻"), MEDIUM_LIGHT("🏼"), MEDIUM("🏽"), MEDIUM_DARK("🏾"), DARK("🏿"),
}

object Reactions {
    const val MAX = 7

    /** The palette offered on the phone, WhatsApp's own six first. */
    fun palette(pt: Boolean): List<Reaction> = listOf(
        Reaction("👍", if (pt) "Curtir" else "Like"),
        Reaction("❤️", if (pt) "Amei" else "Love"),
        Reaction("😂", if (pt) "Haha" else "Haha"),
        Reaction("😮", if (pt) "Uau" else "Wow"),
        Reaction("😢", if (pt) "Triste" else "Sad"),
        Reaction("🙏", if (pt) "Obrigado" else "Thanks"),
        Reaction("👏", if (pt) "Palmas" else "Clap"),
        Reaction("🔥", if (pt) "Top" else "Fire"),
        Reaction("💪", if (pt) "Força" else "Strong"),
        Reaction("👌", if (pt) "Beleza" else "OK"),
        Reaction("🙌", if (pt) "Boa!" else "Yay"),
        Reaction("🤝", if (pt) "Fechado" else "Deal"),
        Reaction("😘", if (pt) "Beijo" else "Kiss"),
        Reaction("🎉", if (pt) "Parabéns" else "Congrats"),
        Reaction("🤔", if (pt) "Hum…" else "Hmm"),
        Reaction("✅", if (pt) "Feito" else "Done"),
    )

    /** WhatsApp's own reaction bar: the default set. */
    fun defaults(pt: Boolean): List<Reaction> = palette(pt).take(6)

    /** Hands and gestures take a skin tone; faces, hearts and symbols don't. */
    private val TONED = setOf("👍", "🙏", "👏", "💪", "👌", "🙌", "🤝")

    fun withTone(reaction: Reaction, tone: SkinTone): Reaction =
        if (tone == SkinTone.DEFAULT || reaction.emoji !in TONED) reaction else reaction.copy(emoji = reaction.emoji + tone.modifier)

    /** The chosen emojis (stored as plain emoji, without tone) back into the palette's words, in the wearer's order. */
    fun chosen(stored: List<String>, tone: SkinTone, pt: Boolean): List<Reaction> {
        val byEmoji = palette(pt).associateBy { it.emoji }
        val picked = stored.mapNotNull { byEmoji[it] }.distinct().take(MAX).ifEmpty { defaults(pt) }
        return picked.map { withTone(it, tone) }
    }
}
