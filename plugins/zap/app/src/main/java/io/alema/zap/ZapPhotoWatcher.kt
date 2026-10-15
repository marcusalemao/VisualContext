package io.alema.zap

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

/**
 * Notices a photo taken with the glasses' own camera button once Nexus' photo
 * sync brings it to the phone, and offers it for sending, once.
 *
 * Accidental shots are the glasses' business: the native camera lets the
 * wearer delete one right away, and Nexus only syncs a capture after it has
 * been stable for a few seconds, so a deleted photo never gets here.
 *
 * Lives in the notification listener, the one part of Zap that stays alive.
 */
internal class ZapPhotoWatcher(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("zap_photo_offer", Context.MODE_PRIVATE)

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(check)
            main.postDelayed(check, SETTLE_MS)
        }
    }

    private val check = Runnable {
        if (!ZapSettings(context).offerNewPhotos()) return@Runnable
        val (key, addedSec) = ZapGallery.newestGlassesPhoto(context) ?: return@Runnable
        val seen = prefs.getLong(KEY_LAST_ID, -1L)
        if (key.id == seen) return@Runnable
        prefs.edit().putLong(KEY_LAST_ID, key.id).apply()
        // Only fresh shots: a batch synced hours later (charging mode) is not "just taken".
        val ageMs = System.currentTimeMillis() - addedSec * 1000
        if (seen == -1L || ageMs > FRESH_MS) {
            Log.i(TAG, "glasses photo ${key.id} not offered (first run or ${ageMs / 1000}s old)")
            return@Runnable
        }
        ZapNotifier.offerPhoto(context, key.toString())
    }

    fun start() {
        runCatching {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            // Remember what is already there, so only shots from now on are offered.
            if (prefs.getLong(KEY_LAST_ID, -1L) == -1L) {
                ZapGallery.newestGlassesPhoto(context)?.let { prefs.edit().putLong(KEY_LAST_ID, it.first.id).apply() }
            }
        }.onFailure { Log.w(TAG, "could not watch the gallery", it) }
    }

    fun stop() {
        main.removeCallbacks(check)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    companion object {
        private const val TAG = "ZapPhotoWatcher"
        private const val KEY_LAST_ID = "last_offered_id"
        private const val SETTLE_MS = 1_500L
        /** Nexus waits ~5 s for a stable file and ~5 s to copy it: a minute is generous. */
        private const val FRESH_MS = 2 * 60_000L
    }
}
