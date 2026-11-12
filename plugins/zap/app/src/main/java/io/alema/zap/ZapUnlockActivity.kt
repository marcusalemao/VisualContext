package io.alema.zap

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * A phone that trusts the glasses (Smart Lock, "trusted device") has no PIN to
 * ask while they are connected — but its lock screen still sits over everything,
 * and WhatsApp can't open behind it. This invisible screen lights the display,
 * steps past that lock screen (Android only allows it when no PIN is needed) and
 * opens WhatsApp, so a photo goes out with the phone still in the pocket.
 *
 * With a PIN/fingerprint actually required, Android shows its own unlock and
 * nothing is bypassed: the send then waits for the wearer, as before.
 */
class ZapUnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        val next = intent.getParcelableExtra<Intent>(EXTRA_NEXT)
        if (next == null) {
            finish()
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            go(next)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = go(next)
            override fun onDismissError() {
                Log.w(TAG, "lock screen did not step aside")
                finish()
            }
            override fun onDismissCancelled() = finish()
        })
    }

    private fun go(next: Intent) {
        runCatching { startActivity(next) }.onFailure { Log.w(TAG, "WhatsApp did not open", it) }
        finish()
    }

    companion object {
        private const val TAG = "ZapUnlock"
        private const val EXTRA_NEXT = "next"

        /** Locked, but no PIN needed right now (Smart Lock trusts the glasses, or a swipe-only lock). */
        fun trusted(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
            return keyguard.isKeyguardLocked && !keyguard.isDeviceLocked
        }

        fun open(context: Context, next: Intent) {
            context.startActivity(
                Intent(context, ZapUnlockActivity::class.java)
                    .putExtra(EXTRA_NEXT, next)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            )
        }
    }
}
