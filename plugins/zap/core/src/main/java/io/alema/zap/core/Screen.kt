package io.alema.zap.core

/**
 * What the glasses show, in no particular glasses' terms.
 *
 * Zap's logic decides *what* is on the lens; each host ("plug") only draws it:
 * the Rokid Nexus plugin turns a [Screen] into a Nexus card, the standalone
 * glasses app draws it itself, and other glasses can do the same later. So a
 * host never needs to know about steps, contacts or WhatsApp.
 */
sealed interface Screen {
    /** A titled list: text rows, some of them choices (one [Row.selected]). */
    data class Card(
        val title: String,
        val rows: List<Row>,
        /** A status under the title ("Listening…", "Sending…"). */
        val subtitle: String? = null,
        /** False only at home: there, back leaves Zap. Elsewhere back stays inside Zap. */
        val handlesBack: Boolean = true,
    ) : Screen

    /**
     * One photo, full screen. [photoKey] names the encrypted copy on the phone;
     * the host loads the bytes. When they are gone the host shows [missing].
     */
    data class Picture(
        val photoKey: String,
        val title: String,
        val caption: String?,
        /** "2/5" when the chat has several photos. */
        val footer: String?,
        val missing: Card,
    ) : Screen
}

data class Row(
    val text: String,
    /** A second, smaller line: the last message under a chat, the end of a number under a name. */
    val sub: String? = null,
    /** A short label before the text: "You", or the sender in a group. */
    val badge: String? = null,
    val tone: Tone = Tone.NORMAL,
    val selected: Boolean = false,
)

/** How a row reads: plain choice, message text, quiet hint, or a problem. */
enum class Tone { NORMAL, BODY, DIM, ALERT }
