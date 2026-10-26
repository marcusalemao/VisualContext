package io.alema.zap.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The day in a few lines: what came in today across all chats, the most
 * important first, one line per chat that matters. Asked for from home
 * ("✦ Resumo do dia"), never on its own. Made by the AI the wearer chose.
 */
data class Digest(
    /** Two or three spoken-friendly sentences about the whole day. */
    val summary: String,
    /** The chats worth a look, most important first; a tap opens the chat. */
    val items: List<DigestItem> = emptyList(),
    /** How many chats and messages from others went in. */
    val chats: Int = 0,
    val messages: Int = 0,
)

data class DigestItem(val chatKey: String, val title: String, val line: String)

sealed interface DigestState {
    object Loading : DigestState
    /** Nothing came in today: no AI call at all. */
    object Empty : DigestState
    data class Ready(val digest: Digest) : DigestState
    data class Failed(val error: BriefError) : DigestState
}

/** Which messages go into the day's summary. Pure, so it is unit-tested. */
object DigestRules {
    const val MAX_CHATS = 12
    const val PER_CHAT = 8
    const val MAX_MESSAGES = 60
    const val MAX_ITEMS = 6

    /** Today's messages from others (since midnight, in the phone's zone), newest chats first. */
    fun chatsFor(conversations: List<ZapConversation>, nowMs: Long, zone: ZoneId): List<Pair<ZapConversation, List<ZapMessage>>> {
        val midnight = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).atStartOfDay(zone).toInstant().toEpochMilli()
        var total = 0
        val out = mutableListOf<Pair<ZapConversation, List<ZapMessage>>>()
        for (chat in conversations.sortedByDescending { it.lastTimeMs }) {
            if (out.size >= MAX_CHATS || total >= MAX_MESSAGES) break
            val today = chat.messages
                .filter { it.sender != ZapMessage.ME && it.timeMs >= midnight }
                .sortedBy { it.timeMs }
                .takeLast(PER_CHAT)
                .take(MAX_MESSAGES - total)
            if (today.isEmpty()) continue
            total += today.size
            out += chat to today
        }
        return out
    }
}

/** The words sent to the AI for the day's summary, and the reading of its answer. */
object DigestPrompt {
    const val MAX_SUMMARY = 300
    const val MAX_LINE = 70

    fun system(portuguese: Boolean): String = """
You summarize a person's WhatsApp day for smart glasses with a tiny display. They glance at it while walking.
Write every text field in ${if (portuguese) "Brazilian Portuguese, casual and natural" else "English, casual and natural"}.
Return ONLY a JSON object: {"summary": string, "items": [{"chat": string, "line": string}]}

summary: what matters in today's messages across all chats, in two or three short sentences, at most 280 characters. Lead with what needs an answer or a decision, then plans with times and places, then the rest in one phrase. Concrete facts (times, places, amounts, names). No emoji, no greetings. It will be read aloud.

items: at most 6 chats worth a look, most important first. "chat" is the chat's id exactly as given (C1, C2…). "line" is at most 60 characters: what that chat wants, starting with the ask ("Quer saber se você topa o almoço às 12h"). Skip chats with nothing that matters (stickers, "ok", forwards).
Never invent facts that are not in the messages.
""".trim()

    fun user(chats: List<Pair<ZapConversation, List<ZapMessage>>>, nowMs: Long, zone: ZoneId): String = buildString {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        append("NOW: ").append(now.format(STAMP)).append(" (")
            .append(now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)).append(")\n")
        chats.forEachIndexed { index, (chat, messages) ->
            append("\nC").append(index + 1).append(": ").append(chat.title)
                .append(if (chat.isGroup) " (group)" else " (one person)").append('\n')
            messages.forEach { m ->
                val at = Instant.ofEpochMilli(m.timeMs).atZone(zone).format(HOUR)
                val who = m.sender.ifBlank { chat.title }
                val body = when {
                    m.voiceSeconds != null -> "[voice message ${m.voiceSeconds}s]"
                    m.image != null && (m.text.isBlank() || m.text == ZapMessage.PHOTO) -> "[photo]"
                    m.image != null -> "[photo] ${m.text}"
                    else -> m.text
                }
                append("  ").append(at).append(' ').append(who).append(": ").append(body.replace('\n', ' ')).append('\n')
            }
        }
    }

    /** Reads the answer; "C3" becomes that chat. Unknown ids and empty lines are dropped. */
    fun parse(answer: String, chats: List<ZapConversation>): Digest? = runCatching {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val m = Wire.Json.parse(answer.substring(start, end + 1)) as? Map<*, *> ?: return null
        val summary = (m["summary"] as? String)?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() } ?: return null
        val items = (m["items"] as? List<*>).orEmpty().mapNotNull { item ->
            val i = item as? Map<*, *> ?: return@mapNotNull null
            val id = (i["chat"] as? String)?.trim()?.uppercase()?.removePrefix("C")?.toIntOrNull() ?: return@mapNotNull null
            val chat = chats.getOrNull(id - 1) ?: return@mapNotNull null
            val line = (i["line"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DigestItem(chat.key, chat.title, cut(line, MAX_LINE))
        }.distinctBy { it.chatKey }.take(DigestRules.MAX_ITEMS)
        Digest(summary = cut(summary, MAX_SUMMARY), items = items)
    }.getOrNull()

    private fun cut(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
}
