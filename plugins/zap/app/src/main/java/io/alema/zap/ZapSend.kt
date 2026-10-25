package io.alema.zap

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.alema.zap.core.Recipient
import io.alema.zap.core.SendOutcome
import io.alema.zap.core.WhatsAppLink

/**
 * Opens a WhatsApp chat with the message typed in and, when the wearer has
 * enabled [ZapSendService], has it press Send.
 *
 * Nothing here runs unless the wearer confirmed this exact message on the
 * glasses: [send] is only called from the review card's "Send" row.
 */
internal object ZapSender {
    private val main = Handler(Looper.getMainLooper())

    fun whatsAppPackage(context: Context): String? =
        listOf(WhatsAppLink.PACKAGE, WhatsAppLink.BUSINESS_PACKAGE).firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }

    fun canStartFromBackground(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * One Reply, two routes the wearer never sees: WhatsApp's own reply action
     * while the notification is alive, otherwise the chat reopened with the
     * text typed (and pressed Send by the helper, when enabled).
     */
    fun deliver(context: Context, recipient: Recipient, text: String, photo: String? = null, onDone: (SendOutcome) -> Unit) {
        val key = recipient.conversationKey
        // What the wearer sent shows in the chat as soon as it is on its way (WhatsApp never
        // notifies your own messages, so this is the only place Zap learns them). Once per send:
        // "waiting for unlock" and then "sent" must not add it twice.
        var recorded = false
        fun record(outcome: SendOutcome, body: String) {
            if (recorded || outcome !in ON_ITS_WAY) return
            recorded = true
            ZapInbox.recordSent(context, key ?: recipient.digits, body, title = recipient.name, digits = recipient.digits)
        }
        if (photo != null) {
            val digits = recipient.digits ?: return onDone(SendOutcome.FAILED)
            return sendPhoto(context, digits, photo, text, { outcome ->
                record(outcome, text.ifBlank { ZapInbox.PHOTO }.let { if (text.isBlank()) it else "📷 $it" })
                onDone(outcome)
            }, name = recipient.name)
        }
        if (key != null && ZapInbox.replyLive(context, key, text)) return onDone(SendOutcome.SENT)
        val digits = recipient.digits ?: return onDone(SendOutcome.FAILED)
        send(context, digits, text) { outcome ->
            record(outcome, text)
            onDone(outcome)
        }
    }

    /** Sent, or handed to WhatsApp and only waiting for the phone to unlock or a thumb on Send. */
    private val ON_ITS_WAY = setOf(SendOutcome.SENT, SendOutcome.WAITING_FOR_UNLOCK, SendOutcome.OPENED_FOR_THUMB, SendOutcome.HELPER_OFF)

    fun send(context: Context, digits: String, text: String, onDone: (SendOutcome) -> Unit) {
        val pkg = whatsAppPackage(context) ?: return onDone(SendOutcome.NOT_INSTALLED)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLink.appUri(digits, text)))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        open(context, pkg, intent, text, photo = false, onDone)
    }

    /**
     * WhatsApp's own share screen, aimed at one chat ("jid"): the photo with
     * the caption under it and WhatsApp's Send button. If WhatsApp ignores the
     * chat and shows its contact picker, the helper finds no caption box and
     * presses nothing — the wearer's thumb finishes it.
     */
    fun sendPhoto(context: Context, digits: String, photo: String, caption: String, onDone: (SendOutcome) -> Unit, name: String? = null) {
        val pkg = whatsAppPackage(context) ?: return onDone(SendOutcome.NOT_INSTALLED)
        val uri = ZapGallery.shareable(context, photo) ?: return onDone(SendOutcome.FAILED)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(ZapGallery.mimeType(photo))
            .setPackage(pkg)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra("jid", "$digits@s.whatsapp.net")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (caption.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, caption)
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        open(context, pkg, intent, caption, photo = true, onDone, name)
    }

    private fun open(context: Context, pkg: String, intent: Intent, text: String, photo: Boolean, onDone: (SendOutcome) -> Unit, name: String? = null) {
        // Smart Lock trusting the glasses (no PIN needed now): step past the lock screen and send at once.
        val trusted = ZapUnlockActivity.trusted(context)
        val locked = !trusted && context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val auto = ZapSendService.isEnabled(context)
        ZapSendTrace.start(context, photo, if (trusted) ZapSendTrace.Lock.TRUSTED else if (locked) ZapSendTrace.Lock.PIN else ZapSendTrace.Lock.OPEN, auto)
        val report = onDone
        @Suppress("NAME_SHADOWING")
        val onDone: (SendOutcome) -> Unit = { outcome -> ZapSendTrace.end(context, outcome); report(outcome) }
        var armed: PendingSend? = null
        val windowMs = if (locked) UNLOCK_WINDOW_MS else SEND_WINDOW_MS
        if (auto) {
            armed = PendingSend(pkg, text, System.currentTimeMillis() + windowMs, photo, name)
            ZapSendService.arm(armed, onDone)
        }
        try {
            if (trusted) ZapUnlockActivity.open(context, intent) else context.startActivity(intent)
        } catch (_: RuntimeException) {
            ZapSendService.disarm()
            return onDone(SendOutcome.FAILED)
        }
        if (armed == null) return onDone(SendOutcome.HELPER_OFF)
        // A locked phone reports now and again when the send happens after unlock.
        if (locked) onDone(SendOutcome.WAITING_FOR_UNLOCK)
        main.postDelayed({ ZapSendService.expire(armed) }, windowMs + 500)
    }

    /** Room for the screen to light and the lock screen to step aside first. */
    private const val SEND_WINDOW_MS = 20_000L
    private const val UNLOCK_WINDOW_MS = 120_000L
}

/**
 * The last send, in plain words for the settings screen and the log: photo or
 * text, how the phone was locked, whether the helper was on, and how it ended.
 * So "it asked me to unlock again" can be answered without a cable.
 */
internal object ZapSendTrace {
    enum class Lock { OPEN, TRUSTED, PIN }
    private const val PREFS = "zap_send_trace"

    fun start(context: Context, photo: Boolean, lock: Lock, auto: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis()).putBoolean("photo", photo)
            .putString("lock", lock.name).putBoolean("auto", auto).remove("outcome").apply()
        android.util.Log.i("Zap", "send start photo=$photo lock=$lock auto=$auto")
    }

    fun end(context: Context, outcome: SendOutcome) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("outcome", outcome.name).apply()
        android.util.Log.i("Zap", "send outcome=$outcome")
    }

    /** "13:40 · foto · celular bloqueado com PIN · envio automático ligado → esperou o desbloqueio". */
    fun describe(context: Context, pt: Boolean): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L).takeIf { it > 0 } ?: return null
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
        val what = if (p.getBoolean("photo", false)) (if (pt) "foto" else "photo") else (if (pt) "texto" else "text")
        val lock = when (runCatching { Lock.valueOf(p.getString("lock", "") ?: "") }.getOrNull()) {
            Lock.OPEN -> if (pt) "celular desbloqueado" else "phone unlocked"
            Lock.TRUSTED -> if (pt) "bloqueado sem pedir PIN (dispositivo de confiança)" else "locked, no PIN needed (trusted device)"
            Lock.PIN -> if (pt) "bloqueado pedindo PIN" else "locked, PIN required"
            null -> "?"
        }
        val auto = if (p.getBoolean("auto", false)) (if (pt) "envio automático ligado" else "auto-send on") else (if (pt) "envio automático DESLIGADO" else "auto-send OFF")
        val end = when (runCatching { SendOutcome.valueOf(p.getString("outcome", "") ?: "") }.getOrNull()) {
            SendOutcome.SENT -> if (pt) "enviado" else "sent"
            SendOutcome.WAITING_FOR_UNLOCK -> if (pt) "esperando desbloqueio" else "waiting for unlock"
            SendOutcome.OPENED_FOR_THUMB -> if (pt) "não achou o botão Enviar; ficou aberto para o dedo" else "Send button not found; left open for the thumb"
            SendOutcome.HELPER_OFF -> if (pt) "ajudante desligado; ficou aberto para o dedo" else "helper off; left open for the thumb"
            SendOutcome.NOT_INSTALLED -> if (pt) "WhatsApp não encontrado" else "WhatsApp not found"
            SendOutcome.FAILED -> if (pt) "falhou" else "failed"
            null -> if (pt) "em andamento" else "in progress"
        }
        return "$time · $what · $lock · $auto → $end"
    }
}

internal data class PendingSend(val pkg: String, val text: String, val expiresAtMs: Long, val photo: Boolean = false, val name: String? = null) {
    /** Where the helper is on WhatsApp's contact picker, when a photo share lands there instead of the chat. */
    enum class Picker { NONE, SEARCHING, TYPED, CHOSEN }
    @Volatile var picker: Picker = Picker.NONE
}

/**
 * Presses WhatsApp's Send button once, for one message the wearer confirmed.
 *
 * Scoped to WhatsApp in its XML config, idle unless armed by [ZapSender],
 * and it only clicks when the text box holds exactly the confirmed message —
 * so it can never send something else, or send twice. It reads nothing else
 * on screen and keeps nothing.
 *
 * View ids `com.whatsapp:id/entry` and `:id/send` are what WhatsApp uses
 * today; if a WhatsApp update renames them, the button is also found by its
 * label, and failing that the chat simply stays open for the wearer's thumb.
 */
class ZapSendService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val armed = pending ?: return
        if (System.currentTimeMillis() > armed.expiresAtMs) return expire(armed)
        if (event?.packageName?.toString() != armed.pkg) return
        val root = rootInActiveWindow ?: return
        if (armed.photo) {
            if (!isPhotoPreview(root, armed)) {
                // WhatsApp ignored the chat and opened its contact picker: find the person there.
                if (!armed.name.isNullOrBlank()) drivePicker(root, armed)
                return
            }
        } else {
            val entry = root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/entry").firstOrNull() ?: return
            if (entry.text?.toString()?.trim() != armed.text.trim()) return
        }
        val button = findSendButton(root, armed.pkg) ?: return
        pending = null
        val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        finish(if (clicked) SendOutcome.SENT else SendOutcome.OPENED_FOR_THUMB)
        // Leave the phone as it was: in a pocket, WhatsApp should not stay open.
        if (clicked) handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 700)
    }

    /**
     * WhatsApp's photo preview, not a chat and not the contact picker: it has
     * a caption box (holding exactly the confirmed caption) and no message box.
     * WhatsApp renames its views now and then, so the caption box is also found
     * as "the editable field holding exactly our caption" — still never a click
     * on anything that doesn't hold what the wearer confirmed.
     */
    private fun isPhotoPreview(root: AccessibilityNodeInfo, armed: PendingSend): Boolean {
        if (root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/entry").isNotEmpty()) return false
        val caption = CAPTION_IDS.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/$id").firstOrNull()
        } ?: editables(root).firstOrNull { node ->
            val shown = node.text?.toString()?.trim().orEmpty()
            if (armed.text.isBlank()) shown.isEmpty() || node.isShowingHintText else shown == armed.text.trim()
        } ?: return false
        val shown = caption.text?.toString()?.trim().orEmpty()
        // An empty caption box shows its hint ("Add a caption…") as text on some versions.
        return if (armed.text.isBlank()) shown.isEmpty() || caption.isShowingHintText else shown == armed.text.trim()
    }

    /**
     * WhatsApp's "Send to…" picker (a share that did not land in the chat): search the
     * recipient's name, choose the row that is exactly that name, press the picker's Send.
     * The preview that follows is handled as usual. Each step runs once; a step that
     * finds nothing leaves the screen to the wearer's thumb. Nothing but the name is typed.
     */
    private fun drivePicker(root: AccessibilityNodeInfo, armed: PendingSend) {
        val name = armed.name?.trim().orEmpty()
        val box = editables(root).firstOrNull()
        when (armed.picker) {
            PendingSend.Picker.NONE -> {
                if (box != null) {
                    armed.picker = PendingSend.Picker.SEARCHING
                    return drivePicker(root, armed)
                }
                // No search box yet: open it (the magnifier in the toolbar).
                val search = SEARCH_IDS.firstNotNullOfOrNull { id ->
                    root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/$id").firstOrNull()
                } ?: SEARCH_LABELS.firstNotNullOfOrNull { label ->
                    root.findAccessibilityNodeInfosByText(label)
                        .firstOrNull { it.contentDescription?.toString().equals(label, ignoreCase = true) }
                } ?: return
                clickable(search)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: return
                armed.picker = PendingSend.Picker.SEARCHING
            }
            PendingSend.Picker.SEARCHING -> {
                box ?: return
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
                }
                if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) armed.picker = PendingSend.Picker.TYPED
            }
            PendingSend.Picker.TYPED -> {
                if (box?.text?.toString()?.trim() != name) return
                // The one row that is this person: exact name, not the search box itself.
                val row = walk(root).firstOrNull { node ->
                    !node.isEditable && node.childCount == 0 && sameName(node.text?.toString(), name)
                }?.let(::clickable) ?: return
                if (row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) armed.picker = PendingSend.Picker.CHOSEN
            }
            PendingSend.Picker.CHOSEN -> {
                // Multi-select pickers show a green Send once someone is chosen; a single-tap
                // picker goes straight to the preview, where the next event finds the caption.
                val button = findSendButton(root, armed.pkg) ?: return
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                armed.picker = PendingSend.Picker.NONE
            }
        }
    }

    /** "Beto" is "beto" and "Béto": accents and case don't decide who gets the photo. */
    private fun sameName(a: String?, b: String): Boolean {
        fun fold(s: String) = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase()
        return a != null && fold(a) == fold(b)
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        SEND_IDS.forEach { id ->
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull { it.isEnabled }?.let { return clickable(it) }
        }
        // By its spoken label ("Enviar"), on the button itself or the icon inside it.
        SEND_LABELS.forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.contentDescription?.toString().equals(label, ignoreCase = true) }
                ?.let { node -> clickable(node)?.let { return it } }
        }
        // Last resort: a clickable view whose id says "send" (send_media_btn, send_button…).
        return walk(root).firstOrNull { node ->
            node.isClickable && node.isEnabled &&
                node.viewIdResourceName?.substringAfter(":id/")?.lowercase()?.let { it == "send" || it.startsWith("send_") || it.endsWith("_send") } == true
        }
    }

    /** The node, or the nearest ancestor that takes a click (an icon inside a button). */
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var at: AccessibilityNodeInfo? = node
        var hops = 0
        while (at != null && hops < 4) {
            if (at.isClickable && at.isEnabled) return at
            at = at.parent
            hops++
        }
        return null
    }

    private fun editables(root: AccessibilityNodeInfo) = walk(root).filter { it.isEditable }

    private fun walk(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && out.size < 400) {
            val node = queue.removeFirst()
            out += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return out
    }

    /**
     * When a photo send ends without a click: which views WhatsApp's screen had
     * (ids and labels only, never any text), so a renamed button can be fixed.
     */
    private fun logLayout() = runCatching {
        val root = rootInActiveWindow ?: return@runCatching
        val views = walk(root)
            .filter { it.isClickable || it.isEditable || it.viewIdResourceName != null }
            .take(60)
            .joinToString(" | ") { n ->
                listOfNotNull(
                    n.viewIdResourceName?.substringAfter(":id/"),
                    n.className?.toString()?.substringAfterLast('.'),
                    n.contentDescription?.toString()?.take(24)?.let { "\"$it\"" },
                    "click".takeIf { n.isClickable },
                    "edit".takeIf { n.isEditable },
                ).joinToString(" ")
            }
        android.util.Log.i("Zap", "send helper found no match on ${root.packageName}: $views")
    }

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        /** Turns Zap's Accessibility off right now (bank apps); false if it was not running. */
        fun pause(): Boolean {
            val running = instance ?: return false
            running.disableSelf()
            return true
        }

        private val SEND_LABELS = listOf("Enviar", "Send", "Senden")
        private val SEARCH_LABELS = listOf("Pesquisar", "Buscar", "Search", "Suchen")
        private val SEARCH_IDS = listOf("menuitem_search", "search", "search_button", "action_search")
        /** Send button ids WhatsApp has used, newest guesses after the classic one. */
        private val SEND_IDS = listOf("send", "send_media_btn", "send_button", "sendButton", "media_send_button")
        /** Caption box ids WhatsApp has used on its photo preview. */
        private val CAPTION_IDS = listOf("caption", "media_caption", "caption_text")

        @Volatile
        private var instance: ZapSendService? = null

        @Volatile
        private var pending: PendingSend? = null
        private var callback: ((SendOutcome) -> Unit)? = null
        private val main = Handler(Looper.getMainLooper())

        internal fun arm(send: PendingSend, onDone: (SendOutcome) -> Unit) {
            pending = send
            callback = onDone
        }

        internal fun disarm() {
            pending = null
            callback = null
        }

        /** Window passed without a match: the chat is open, the wearer's thumb finishes it. */
        internal fun expire(which: PendingSend) {
            if (pending !== which) return
            if (which.photo) instance?.logLayout()
            pending = null
            finish(SendOutcome.OPENED_FOR_THUMB)
        }

        private fun finish(outcome: SendOutcome) {
            val done = callback ?: return
            callback = null
            main.post { done(outcome) }
        }

        fun isEnabled(context: Context): Boolean {
            val wanted = ComponentName(context, ZapSendService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals(wanted, ignoreCase = true) }
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            return enabled.contains(context.packageName)
        }

        @Suppress("unused")
        fun hasContactsPermission(context: Context): Boolean =
            context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
