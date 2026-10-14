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
        if (photo != null) {
            val digits = recipient.digits ?: return onDone(SendOutcome.FAILED)
            return sendPhoto(context, digits, photo, text) { outcome ->
                if (outcome == SendOutcome.SENT) ZapInbox.recordSent(context, key, text.ifBlank { ZapInbox.PHOTO })
                onDone(outcome)
            }
        }
        if (key != null && ZapInbox.replyLive(context, key, text)) return onDone(SendOutcome.SENT)
        val digits = recipient.digits ?: return onDone(SendOutcome.FAILED)
        send(context, digits, text) { outcome ->
            if (outcome == SendOutcome.SENT) ZapInbox.recordSent(context, key, text)
            onDone(outcome)
        }
    }

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
    fun sendPhoto(context: Context, digits: String, photo: String, caption: String, onDone: (SendOutcome) -> Unit) {
        val pkg = whatsAppPackage(context) ?: return onDone(SendOutcome.NOT_INSTALLED)
        val uri = ZapGallery.shareable(context, photo) ?: return onDone(SendOutcome.FAILED)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .setPackage(pkg)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra("jid", "$digits@s.whatsapp.net")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (caption.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, caption)
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        open(context, pkg, intent, caption, photo = true, onDone)
    }

    private fun open(context: Context, pkg: String, intent: Intent, text: String, photo: Boolean, onDone: (SendOutcome) -> Unit) {
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val auto = ZapSendService.isEnabled(context)
        var armed: PendingSend? = null
        val windowMs = if (locked) UNLOCK_WINDOW_MS else SEND_WINDOW_MS
        if (auto) {
            armed = PendingSend(pkg, text, System.currentTimeMillis() + windowMs, photo)
            ZapSendService.arm(armed, onDone)
        }
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            ZapSendService.disarm()
            return onDone(SendOutcome.FAILED)
        }
        if (armed == null) return onDone(SendOutcome.OPENED_FOR_THUMB)
        // A locked phone reports now and again when the send happens after unlock.
        if (locked) onDone(SendOutcome.WAITING_FOR_UNLOCK)
        main.postDelayed({ ZapSendService.expire(armed) }, windowMs + 500)
    }

    private const val SEND_WINDOW_MS = 12_000L
    private const val UNLOCK_WINDOW_MS = 120_000L
}

internal data class PendingSend(val pkg: String, val text: String, val expiresAtMs: Long, val photo: Boolean = false)

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
            if (!isPhotoPreview(root, armed)) return
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
     */
    private fun isPhotoPreview(root: AccessibilityNodeInfo, armed: PendingSend): Boolean {
        if (root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/entry").isNotEmpty()) return false
        val caption = CAPTION_IDS.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId("${armed.pkg}:id/$id").firstOrNull()
        } ?: return false
        val shown = caption.text?.toString()?.trim().orEmpty()
        // An empty caption box shows its hint ("Add a caption…") as text on some versions.
        return if (armed.text.isBlank()) shown.isEmpty() || caption.isShowingHintText else shown == armed.text.trim()
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isEnabled }?.let { return it }
        SEND_LABELS.forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.isClickable && it.contentDescription?.toString().equals(label, ignoreCase = true) }
                ?.let { return it }
        }
        return null
    }

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private val SEND_LABELS = listOf("Enviar", "Send", "Senden")
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
