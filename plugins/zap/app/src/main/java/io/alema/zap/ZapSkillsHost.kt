package io.alema.zap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.anezium.rokidbus.client.plugin.NexusSkillInvocation
import com.anezium.rokidbus.shared.skills.SkillDispatch
import com.anezium.rokidbus.shared.skills.SkillErrorCodes
import com.anezium.rokidbus.shared.skills.SkillProviderChoice
import com.anezium.rokidbus.shared.skills.SkillProviderReference
import io.alema.zap.core.SendOutcome
import io.alema.zap.core.ZapSkills
import io.alema.zap.core.ZapText
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Answers Nexus Skills calls (from Anezium's Assistant, with the wearer's own
 * AI) using [ZapSkills]. The rules live in core; this only reads the inbox,
 * converts JSON, and sends once the wearer picked Send.
 */
internal object ZapSkillsHost {
    private const val TAG = "ZapSkills"
    private const val PREFS = "zap_skills"
    private const val KEY_SECRET = "secret"
    private val main = Handler(Looper.getMainLooper())

    fun answer(context: Context, invocation: NexusSkillInvocation) {
        if (!ZapSendService.isNotificationAccessGranted(context)) {
            // Without it Zap knows no chats; the hub tells the wearer setup is needed.
            invocation.fail(SkillErrorCodes.SETUP_REQUIRED, SkillDispatch.NONE)
            return
        }
        val skills = ZapSkills(
            inbox = { ZapInbox.list(context) },
            directory = { runCatching { ZapContacts(context).directory() }.getOrDefault(emptyList()) },
            secret = secret(context),
            text = ZapText(),
        )
        val answer = runCatching { skills.run(invocation.operationId, invocation.arguments.toMap()) }
            .onFailure { Log.w(TAG, "skill ${invocation.operationId} failed", it) }
            .getOrElse { ZapSkills.Answer.Failed(SkillErrorCodes.UNAVAILABLE) }
        Log.i(TAG, "skill ${invocation.operationId} → ${answer::class.simpleName}")
        when (answer) {
            is ZapSkills.Answer.Done -> invocation.complete(JSONObject(answer.data))
            is ZapSkills.Answer.Choose -> invocation.needsInput(
                answer.reason,
                answer.prompt,
                answer.choices.map { SkillProviderChoice(it.label, it.detail, SkillProviderReference(it.refType, it.ref)) },
            )
            is ZapSkills.Answer.Failed -> invocation.fail(answer.code, SkillDispatch.NONE)
            is ZapSkills.Answer.Send -> send(context, invocation, answer)
        }
    }

    /** The wearer picked Send: deliver like Reply in Zap, and answer once, inside the hub's deadline. */
    private fun send(context: Context, invocation: NexusSkillInvocation, answer: ZapSkills.Answer.Send) {
        val answered = AtomicBoolean(false)
        fun once(block: () -> Unit) {
            if (answered.compareAndSet(false, true)) block()
        }
        // If WhatsApp never reports back in time, say honestly that Zap can't tell.
        val giveUp = Runnable { once { invocation.unknown() } }
        main.postDelayed(giveUp, (invocation.remainingMs - 1_000).coerceAtLeast(1_000))
        main.post {
            ZapSender.deliver(context, answer.recipient, answer.body) { outcome ->
                main.removeCallbacks(giveUp)
                val to = answer.recipient.name.take(80)
                once {
                    when (outcome) {
                        SendOutcome.SENT -> invocation.complete(JSONObject().put("outcome", "sent").put("to", to))
                        SendOutcome.OPENED_FOR_THUMB, SendOutcome.HELPER_OFF ->
                            invocation.accepted(JSONObject().put("outcome", "opened_for_thumb").put("to", to))
                        SendOutcome.WAITING_FOR_UNLOCK ->
                            invocation.accepted(JSONObject().put("outcome", "waiting_for_unlock").put("to", to))
                        SendOutcome.NOT_INSTALLED -> invocation.fail("whatsapp_not_installed", SkillDispatch.NONE)
                        SendOutcome.FAILED -> invocation.fail("send_failed", SkillDispatch.UNKNOWN)
                    }
                }
            }
        }
    }

    /** A per-install key that signs Send choices; never leaves the phone. */
    private fun secret(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_SECRET, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_SECRET, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
        when (val value = opt(key)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> (0 until value.length()).map { value.opt(it) }
            else -> value
        }
    }
}
