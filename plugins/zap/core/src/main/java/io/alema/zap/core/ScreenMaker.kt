package io.alema.zap.core

/**
 * Turns the current [Step] of [ComposeFlow] into the [Screen] every host draws.
 *
 * HUD rules from the VisualContext design: green on black only, no gesture
 * hints (the wearer knows the native gestures), short lines. Widths are the
 * ones Relay measured on the Rokid optics.
 */
class ScreenMaker(private val text: ZapText = ZapText()) {

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
        is Step.Thread -> thread(step.conversation, selected, status)
        is Step.Photo -> photo(step)
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
            rows = listOf(Row(text = fit(step.body), tone = Tone.BODY)) +
                listOf(text.send, text.listen, text.redo).mapIndexed { index, label -> Row(text = label, selected = index == selected) } +
                listOfNotNull(Row(text = text.voiceHelp, tone = Tone.DIM).takeIf { status.voiceTrouble }),
        )
        is Step.Sending -> Screen.Card(
            title = fitTitle(text.to(step.recipient.name)),
            rows = listOf(Row(text = fit(step.body), tone = Tone.BODY)),
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
        return Screen.Card(title = text.inboxTitle, rows = rows, handlesBack = false)
    }

    /** One chat: its latest messages, then Reply · Listen again (· See photos). Reply is always there. */
    private fun thread(chat: ZapConversation, selected: Int, status: Status): Screen.Card {
        val rows = buildList {
            chat.messages.takeLast(THREAD_MESSAGES).forEach { message ->
                add(Row(text = fit(message.text), badge = badgeFor(chat, message), tone = Tone.BODY))
            }
            val choices = listOf(text.reply, text.listenAgain) +
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

    fun senderLabel(message: ZapMessage): String = if (message.sender == ZapMessage.ME) text.me else message.sender

    /** In a one-to-one chat only the wearer's own lines need a label; in a group every line does. */
    private fun badgeFor(chat: ZapConversation, message: ZapMessage): String? = when {
        message.sender == ZapMessage.ME -> text.me
        chat.isGroup -> message.sender.take(MAX_BADGE)
        else -> null
    }

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
        const val MAX_BADGE = 24
        const val MAX_CAPTION = 240
        /** Same widths Relay measured on the optics for list rows and their sub line. */
        const val LIST_CHARS = 26
        const val PREVIEW_CHARS = 40
        const val THREAD_MESSAGES = 6
    }
}
