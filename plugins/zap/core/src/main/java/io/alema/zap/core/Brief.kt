package io.alema.zap.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The short version of a pile of messages: "Bruno asks you to lunch at X at 12
 * and the gym after. Wants to know if you're in." Plus what the wearer is
 * likely to do next, so the glasses can offer it as one tap: two short
 * replies in the wearer's own voice, an event for the calendar, a reminder.
 *
 * Made by an AI the wearer chose (their own key, or the Easy plan). Nothing
 * here acts on its own: every suggestion is a row the wearer taps.
 */
data class Brief(
    /** One or two spoken-friendly sentences, no emoji, the wearer's language. */
    val summary: String,
    /** At most two very short replies the wearer might send ("Topo! Que horas?"). */
    val replies: List<String> = emptyList(),
    /** Something with a day and time in the messages, ready for the calendar. */
    val event: SuggestedEvent? = null,
    /** How many messages went into it, and how many voice messages were actually heard. */
    val messages: Int = 0,
    val voiceHeard: Int = 0,
    /** Voice messages that could not go in (no folder access, or an AI that can't hear audio). */
    val voiceMissing: Int = 0,
)

data class SuggestedEvent(
    val title: String,
    val startMs: Long,
    val endMs: Long? = null,
    val place: String? = null,
)

/** Why there is no summary: each one has its own short line on the lens and its own fix. */
enum class BriefError { NO_KEY, BAD_KEY, OFFLINE, LIMIT, FAILED }

sealed interface BriefState {
    object Loading : BriefState
    data class Ready(val brief: Brief) : BriefState
    data class Failed(val error: BriefError) : BriefState
}

/** What a tap on the summary card can do. */
sealed interface BriefChoice {
    data class SendReply(val text: String) : BriefChoice
    object Dictate : BriefChoice
    data class Calendar(val event: SuggestedEvent) : BriefChoice
    data class Remind(val minutes: Int) : BriefChoice
    object Listen : BriefChoice
    object SeeMessages : BriefChoice
}

/** What the wearer already did on this card, so the row turns into "✓ …" instead of offering it again. */
enum class BriefDone { CALENDAR, REMIND }

/** When to summarize, and which messages go in. Pure, so it is unit-tested. */
object BriefRules {
    /** Three or more new messages, or two voice messages in a row: that's when reading gets slow. */
    const val MIN_UNREAD = 3
    const val MIN_VOICE_RUN = 2
    /** Earlier messages sent along so "yes, that one" makes sense; the summary is about the new ones. */
    const val CONTEXT = 6
    const val MAX_MESSAGES = 40
    /** The reminder offered on the card: answer in half an hour. */
    const val REMIND_MINUTES = 30

    fun worthBriefing(chat: ZapConversation): Boolean {
        val unread = unreadOf(chat)
        if (unread.size >= MIN_UNREAD) return true
        var run = 0
        var best = 0
        for (m in unread) {
            run = if (m.voiceSeconds != null) run + 1 else 0
            best = maxOf(best, run)
        }
        return best >= MIN_VOICE_RUN
    }

    /** Oldest first: a few already-seen messages for context, then everything new. */
    fun messagesFor(chat: ZapConversation): List<ZapMessage> {
        val sorted = chat.messages.sortedBy { it.timeMs }
        val firstNew = sorted.indexOfFirst { it.sender != ZapMessage.ME && it.timeMs > chat.readUpToMs }
        if (firstNew < 0) return sorted.takeLast(MAX_MESSAGES)
        return sorted.subList(maxOf(0, firstNew - CONTEXT), sorted.size).takeLast(MAX_MESSAGES)
    }

    fun unreadOf(chat: ZapConversation): List<ZapMessage> =
        chat.messages.filter { it.sender != ZapMessage.ME && it.timeMs > chat.readUpToMs }.sortedBy { it.timeMs }

    /** The choices on the card, in the order a busy wearer wants them. */
    fun choices(state: BriefState, canReply: Boolean): List<BriefChoice> = when (state) {
        is BriefState.Ready -> buildList {
            if (canReply) state.brief.replies.forEach { add(BriefChoice.SendReply(it)) }
            if (canReply) add(BriefChoice.Dictate)
            state.brief.event?.let { add(BriefChoice.Calendar(it)) }
            add(BriefChoice.Remind(REMIND_MINUTES))
            add(BriefChoice.Listen)
            add(BriefChoice.SeeMessages)
        }
        BriefState.Loading -> listOf(BriefChoice.SeeMessages)
        is BriefState.Failed -> listOfNotNull(BriefChoice.Dictate.takeIf { canReply }, BriefChoice.SeeMessages)
    }
}

/**
 * The words sent to the AI and the reading of its answer. The instructions are
 * in English (models follow them best); the answer comes in the wearer's language.
 */
object BriefPrompt {
    const val MAX_SUMMARY = 220
    const val MAX_REPLY = 40

    fun system(portuguese: Boolean): String = """
You summarize a WhatsApp chat for someone wearing smart glasses with a tiny display. They glance at it while walking.
Write every text field in ${if (portuguese) "Brazilian Portuguese, casual and natural" else "English, casual and natural"}.
Return ONLY a JSON object: {"summary": string, "replies": string[], "event": null | {"title": string, "start": "YYYY-MM-DDTHH:MM", "end": null | "YYYY-MM-DDTHH:MM", "place": null | string}}

summary:
- What the OTHER person wants or said in the NEW messages (marked new), in one or two short sentences, at most 200 characters. Earlier messages are context only.
- Lead with the ask or the decision needed ("Bruno chama para almoçar no Lume às 12h e depois academia. Quer saber se você topa.").
- Include concrete facts: times, places, amounts, names. Merge repeated messages. No emoji, no greetings, no "the user".
- It will also be read aloud: write numbers and times the way people say them.
- Voice messages may be attached as audio: listen to them and include what they say.

replies: at most 2 replies the WEARER could send right now, each at most 40 characters, in the tone of the chat. Different from each other (e.g. yes / not now). Empty list if nothing needs an answer.

event: only if the new messages propose or confirm something with a day and time (meeting, lunch, call, appointment). Resolve "amanhã", "sábado", "às 3" against NOW below. Otherwise null.
Never invent facts that are not in the messages.
""".trim()

    /** The chat itself, numbered, with who wrote and when; "new" marks what the summary is about. */
    fun user(
        chat: ZapConversation,
        messages: List<ZapMessage>,
        nowMs: Long,
        zone: ZoneId,
        /** Voice messages whose audio goes along, by their place in [messages]; attached in that order. */
        audioFor: Set<Int> = emptySet(),
        me: String = "Eu",
    ): String = buildString {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        append("NOW: ").append(now.format(STAMP)).append(" (").append(now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)).append(")\n")
        append("CHAT: ").append(chat.title).append(if (chat.isGroup) " (group)" else " (one person)").append('\n')
        append("MESSAGES (oldest first):\n")
        var audio = 0
        messages.forEachIndexed { index, m ->
            val at = Instant.ofEpochMilli(m.timeMs).atZone(zone).format(STAMP)
            val who = if (m.sender == ZapMessage.ME) me else m.sender.ifBlank { chat.title }
            val fresh = m.sender != ZapMessage.ME && m.timeMs > chat.readUpToMs
            val body = when {
                m.voiceSeconds != null && index in audioFor -> "[voice message ${m.voiceSeconds}s — audio #${++audio} attached]"
                m.voiceSeconds != null -> "[voice message ${m.voiceSeconds}s — not available]"
                m.image != null && (m.text.isBlank() || m.text == ZapMessage.PHOTO) -> "[photo]"
                m.image != null -> "[photo] ${m.text}"
                else -> m.text
            }
            append(if (fresh) "new " else "    ").append(at).append(' ').append(who).append(": ").append(body.replace('\n', ' ')).append('\n')
        }
    }

    /** Reads the AI's answer; tolerant of code fences and extra words around the JSON. */
    fun parse(answer: String, zone: ZoneId): Brief? = runCatching {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val m = Wire.Json.parse(answer.substring(start, end + 1)) as? Map<*, *> ?: return null
        val summary = (m["summary"] as? String)?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() } ?: return null
        val replies = (m["replies"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim()?.trim('"', '“', '”')?.takeIf { r -> r.isNotBlank() } }
            .map { cut(it, MAX_REPLY) }
            .distinct()
            .take(2)
        val event = (m["event"] as? Map<*, *>)?.let { e ->
            val title = (e["title"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: return@let null
            val start = (e["start"] as? String)?.let { time(it, zone) } ?: return@let null
            SuggestedEvent(
                title = cut(title, 60),
                startMs = start,
                endMs = (e["end"] as? String)?.let { time(it, zone) }?.takeIf { it > start },
                place = (e["place"] as? String)?.trim()?.takeIf { it.isNotBlank() }?.let { cut(it, 60) },
            )
        }
        Brief(summary = cut(summary, MAX_SUMMARY), replies = replies, event = event)
    }.getOrNull()

    private fun time(text: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(text.trim().take(16), DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun cut(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
}
