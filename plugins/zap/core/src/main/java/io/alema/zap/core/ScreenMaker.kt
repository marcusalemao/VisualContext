package io.alema.zap.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns the current [Step] of [ComposeFlow] into the [Screen] every host draws.
 *
 * HUD rules from the VisualContext design: green on black only, no gesture
 * hints (the wearer knows the native gestures), short lines. Widths are the
 * ones Relay measured on the Rokid optics.
 */
class ScreenMaker(
    private val text: ZapText = ZapText(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    /** Shown small at the bottom of home, so the wearer can tell which Zap is installed. */
    private val version: String? = null,
) {

    /** Phone-side facts a screen may need to mention. */
    data class Status(
        /** Off means an empty inbox is a missing permission, not a quiet phone. */
        val notificationAccess: Boolean = true,
        /** The read-back voice failed: show where to fix it. */
        val voiceTrouble: Boolean = false,
        /** Why the last dictation ended — test builds only, never in a release. */
        val debugLine: String? = null,
    )

    fun make(step: Step, selected: Int, status: Status = Status()): Screen = when (step) {
        is Step.Inbox -> inbox(step, selected, status)
        is Step.Thread -> if (step.reading) reader(step.conversation) else choices(step.conversation, selected, status)
        is Step.Photo -> photo(step)
        is Step.ChoosePhoto -> Screen.Picture(
            photoKey = step.photo,
            title = fitTitle(text.to(step.recipient.name)),
            caption = step.body.ifBlank { null }?.take(MAX_CAPTION),
            footer = if (step.photos.size > 1) "${step.index + 1}/${step.photos.size}" else null,
            missing = Screen.Card(title = fitTitle(text.to(step.recipient.name)), rows = listOf(Row(text = text.photoGone, tone = Tone.DIM))),
        )
        is Step.NewMessage -> newMessage(step, selected)
        is Step.Listening -> Screen.Card(
            title = fitTitle(step.recipient?.let { text.to(it.name) } ?: text.whoTitle),
            rows = listOf(
                Row(
                    text = fit(step.partial.ifBlank { if (step.recipient == null) text.sayOnlyName else text.sayMessage }),
                    tone = if (step.partial.isBlank()) Tone.DIM else Tone.BODY,
                ),
            ),
            subtitle = text.listening,
        )
        is Step.Pick -> Screen.Card(
            title = fitTitle(text.pickTitle(step.spoken)),
            rows = step.choices.take(MAX_ROWS - 1).mapIndexed { index, contact ->
                Row(
                    text = fit(contact.name, LIST_CHARS),
                    // The end of the number tells two people with the same name apart.
                    sub = text.numberHint(contact.target()) ?: text.fromChat.takeIf { contact.conversationKey != null },
                    selected = index == selected,
                )
            } + Row(text = text.sayNameAgain, selected = selected == step.choices.size),
        )
        is Step.Review -> Screen.Card(
            title = fitTitle(text.to(step.recipient.name)),
            rows = draft(step.body, step.photo) +
                listOf(
                    text.send,
                    text.listen,
                    if (step.photo != null && step.body.isBlank()) text.sayCaption else text.redo,
                    if (step.photo == null) text.attachPhoto else text.changePhoto,
                ).mapIndexed { index, label -> Row(text = label, selected = index == selected) } +
                listOfNotNull(Row(text = text.voiceHelp, tone = Tone.DIM).takeIf { status.voiceTrouble }),
        )
        is Step.Sending -> Screen.Card(
            title = fitTitle(text.to(step.recipient.name)),
            rows = draft(step.body, step.photo),
            subtitle = text.sending,
        )
        is Step.Done -> done(step, status)
    }

    /** Home: "New message", then one row per chat — name on top, last message under it. */
    private fun inbox(step: Step.Inbox, selected: Int, status: Status): Screen.Card {
        val rows = buildList {
            add(Row(text = text.newMessage, selected = selected == 0))
            step.conversations.take(MAX_ROWS - 1).forEachIndexed { index, chat ->
                add(
                    Row(
                        text = fit(chat.title, LIST_CHARS),
                        sub = chat.last?.let { fit(preview(chat, it), PREVIEW_CHARS) },
                        selected = selected == index + 1,
                    ),
                )
            }
            if (step.conversations.isEmpty()) {
                add(
                    if (status.notificationAccess) Row(text = text.emptyInbox, tone = Tone.DIM)
                    else Row(text = text.noAccess, tone = Tone.ALERT),
                )
            }
        }
        return Screen.Card(title = text.inboxTitle, rows = rows, handlesBack = false, footer = version?.let { "Zap $it" })
    }

    /**
     * One chat, read like a document: every kept message in full, the glasses
     * scroll it. A new header whenever the sender changes or time passes.
     */
    private fun reader(chat: ZapConversation): Screen.Reader {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now()), zone)
        val built = ArrayList<Segment>()
        var lastSender: String? = null
        var lastTime = Long.MIN_VALUE
        for (message in chat.messages.sortedBy { it.timeMs }) {
            if (message.sender != lastSender || message.timeMs - lastTime > NEW_HEADER_AFTER_MS) {
                if (built.isNotEmpty()) built += Segment(Segment.Kind.PROSE, "") // paragraph break
                built += Segment(Segment.Kind.HEADER, "${senderLabel(message)} · ${clock(message.timeMs, today)}", emphasis = message.sender == ZapMessage.ME)
            }
            if (message.image != null) built += Segment(Segment.Kind.ASIDE, ZapMessage.PHOTO)
            val body = message.text.takeIf { it.isNotBlank() && !(message.image != null && it == ZapMessage.PHOTO) }
            body?.let { built += Segment(Segment.Kind.PROSE, it.take(MAX_SEGMENT_CHARS)) }
            lastSender = message.sender
            lastTime = message.timeMs
        }
        return Screen.Reader(
            title = fitTitle(chat.title),
            segments = newestThatFit(built),
            footer = text.readerFooter(chat.photos.isNotEmpty()),
            contentKey = "zap-chat-${chat.key}".take(128),
        )
    }

    /** The glasses take 240 segments and 40,000 characters: keep the newest, say when older ones are left out. */
    private fun newestThatFit(all: List<Segment>): List<Segment> {
        if (all.isEmpty()) return listOf(Segment(Segment.Kind.ASIDE, text.emptyChat))
        var chars = 0
        var from = all.size
        while (from > 0) {
            val next = all[from - 1]
            if (all.size - from + 1 > MAX_SEGMENTS - 1 || chars + next.text.length > MAX_READER_CHARS - 200) break
            chars += next.text.length
            from--
        }
        // Start on a header, never mid-run.
        while (from < all.size && all[from].kind != Segment.Kind.HEADER) from++
        val kept = all.subList(from, all.size)
        return if (from == 0) kept else listOf(Segment(Segment.Kind.ASIDE, "⋯ ${text.earlier}")) + kept
    }

    private fun clock(timeMs: Long, today: LocalDate): String {
        val at = Instant.ofEpochMilli(timeMs).atZone(zone)
        return if (at.toLocalDate() == today) at.format(HOUR) else at.format(DAY_HOUR)
    }

    /** One chat's choices: Reply · Listen again (· See photos). Reply is always there. */
    private fun choices(chat: ZapConversation, selected: Int, status: Status): Screen.Card {
        val rows = buildList {
            val choices = listOf(text.reply, text.listenAgain, text.sendPhoto) +
                listOfNotNull(text.seePhotos(chat.photos.size).takeIf { chat.photos.isNotEmpty() })
            choices.forEachIndexed { index, label -> add(Row(text = label, selected = selected == index)) }
            if (status.voiceTrouble) add(Row(text = text.voiceHelp, tone = Tone.DIM))
        }
        return Screen.Card(title = fitTitle(chat.title), rows = rows)
    }

    /** New message: "Say the name" first, then recent people for one tap. */
    private fun newMessage(step: Step.NewMessage, selected: Int): Screen.Card {
        val rows = buildList {
            add(Row(text = text.sayName, selected = selected == 0))
            step.recent.take(MAX_ROWS - 2).forEachIndexed { index, contact ->
                add(Row(text = fit(contact.name, LIST_CHARS), selected = selected == index + 1))
            }
            if (step.recent.isEmpty()) add(Row(text = text.noRecent, tone = Tone.DIM))
        }
        return Screen.Card(title = text.newMessage, rows = rows)
    }

    /** One photo, full screen; swipe for the others in this chat. */
    private fun photo(step: Step.Photo): Screen {
        val message = step.message
        val chat = step.conversation
        val missing = Screen.Card(title = fitTitle(chat.title), rows = listOf(Row(text = text.photoGone, tone = Tone.DIM)))
        val key = message.image ?: return missing
        return Screen.Picture(
            photoKey = key,
            title = fitTitle(if (chat.isGroup) "${chat.title} · ${senderLabel(message)}" else senderLabel(message)),
            caption = message.text.takeIf { it.isNotBlank() && it != ZapMessage.PHOTO }?.take(MAX_CAPTION),
            footer = if (chat.photos.size > 1) "${step.index + 1}/${chat.photos.size}" else null,
            missing = missing,
        )
    }

    private fun done(step: Step.Done, status: Status): Screen.Card {
        val message = when {
            step.outcome == SendOutcome.SENT -> text.sent
            step.outcome == SendOutcome.OPENED_FOR_THUMB -> text.openedForThumb
            step.outcome == SendOutcome.WAITING_FOR_UNLOCK -> text.waitingUnlock
            step.outcome == SendOutcome.NOT_INSTALLED -> text.notInstalled
            step.outcome == SendOutcome.FAILED -> text.failed
            step.note == Note.NO_CONTACT -> text.noContact(step.spoken.orEmpty())
            step.note == Note.NO_NUMBER -> text.noNumber
            step.note == Note.REPLY_ON_PHONE -> text.replyOnPhone
            step.note == Note.NO_PHOTOS -> text.noPhotos
            step.note == Note.NO_SPEECH -> text.noSpeech
            else -> text.speechUnavailable
        }
        val rows = buildList {
            step.body?.takeIf { it.isNotBlank() }?.let { add(Row(text = fit(it), tone = Tone.BODY)) }
            add(Row(text = fit(message), tone = if (step.outcome != SendOutcome.SENT) Tone.ALERT else Tone.NORMAL))
            if (step.note == Note.SPEECH_UNAVAILABLE) add(Row(text = text.voiceHelp, tone = Tone.DIM))
            if (step.note != null) status.debugLine?.let { add(Row(text = fit("test: $it"), tone = Tone.DIM)) }
            add(Row(text = if (step.canRetry) text.tryAgain else text.backToChats, selected = true))
        }
        return Screen.Card(title = fitTitle(step.recipient?.let { text.to(it.name) } ?: text.appTitle), rows = rows)
    }

    /** What is about to go: the text, and a line for the photo when one is attached. */
    private fun draft(body: String, photo: String?): List<Row> = listOfNotNull(
        Row(text = fit(body), tone = Tone.BODY).takeIf { body.isNotBlank() },
        Row(text = text.photoAttached, tone = Tone.BODY).takeIf { photo != null },
    )

    fun senderLabel(message: ZapMessage): String = if (message.sender == ZapMessage.ME) text.me else message.sender

    private fun preview(chat: ZapConversation, message: ZapMessage): String = when {
        message.sender == ZapMessage.ME -> "${text.me}: ${message.text}"
        chat.isGroup -> "${message.sender}: ${message.text}"
        else -> message.text
    }

    private fun fit(value: String, max: Int = MAX_LINE) = if (value.length <= max) value else value.take(max - 1) + "…"
    private fun fitTitle(value: String) = fit(value, MAX_TITLE)

    companion object {
        const val MAX_ROWS = 64
        const val MAX_LINE = 240
        const val MAX_TITLE = 48
        const val MAX_CAPTION = 240
        /** Same widths Relay measured on the optics for list rows and their sub line. */
        const val LIST_CHARS = 26
        const val PREVIEW_CHARS = 40
        const val MAX_SEGMENTS = 240
        const val MAX_SEGMENT_CHARS = 4_096
        const val MAX_READER_CHARS = 40_000
        /** A pause this long starts a new header even from the same sender. */
        const val NEW_HEADER_AFTER_MS = 30 * 60_000L
        private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_HOUR = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    }
}
