package io.alema.zap

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photos people send on WhatsApp, as small JPEGs the glasses can show.
 *
 * WhatsApp attaches the picture to its notification only for a while (a
 * content link that expires), so Zap copies it at once, shrunk to what the
 * glasses' image surface accepts (≤512 px, ≤64 KB), and keeps it encrypted
 * with the inbox key, next to the inbox. Photos go when their message goes.
 */
internal object ZapPhotos {
    private const val TAG = "ZapPhotos"
    private const val DIR = "photos"
    const val MAX_EDGE = 512
    const val MAX_BYTES = 65_536

    data class Photo(val bytes: ByteArray, val width: Int, val height: Int)

    /** A photo from a MessagingStyle message bundle ("type" + "uri"), stored; returns its key. */
    fun fromMessage(context: Context, message: Bundle): String? {
        val type = message.getString("type")?.lowercase().orEmpty()
        @Suppress("DEPRECATION")
        val uri = message.getParcelable<Uri>("uri") ?: return null
        if (!type.startsWith("image/")) return null
        // WhatsApp reposts its notification with every new message: decode each photo once.
        synchronized(seen) { seen[uri.toString()] }?.let { return it }
        return decode(context, uri)?.let { store(context, it) }?.also { key ->
            synchronized(seen) { seen[uri.toString()] = key }
        }
    }

    private val seen = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    /** The big picture some WhatsApp builds attach instead (BigPictureStyle). */
    fun fromBigPicture(context: Context, extras: Bundle): String? {
        @Suppress("DEPRECATION")
        val bitmap = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
            ?: (extras.getParcelable<Icon>("android.pictureIcon"))?.loadDrawable(context)?.let { drawable ->
                Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            ?: return null
        return store(context, bitmap)
    }

    /** A photo from the inbox (encrypted copy) or from the phone's gallery. */
    fun loadAny(context: Context, key: String): Photo? =
        if (ZapGallery.isKey(key)) ZapGallery.thumbnail(context, key) else load(context, key)

    /** Shrinks a bitmap to what the glasses accept, as a JPEG with its size. */
    fun thumbnail(bitmap: Bitmap): Photo? = encode(bitmap)?.let { bytes ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        Photo(bytes, bounds.outWidth, bounds.outHeight)
    }

    fun load(context: Context, key: String): Photo? = runCatching {
        val file = File(File(context.filesDir, DIR), "$key.bin").takeIf { it.exists() } ?: return null
        val raw = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ZapInbox.secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        val bytes = cipher.doFinal(raw.copyOfRange(12, raw.size))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        Photo(bytes, bounds.outWidth, bounds.outHeight)
    }.onFailure { Log.w(TAG, "photo unreadable", it) }.getOrNull()

    /** Deletes every stored photo no message points to any more. */
    fun keepOnly(context: Context, keys: Set<String>) {
        File(context.filesDir, DIR).listFiles()?.forEach { file ->
            if (file.name.removeSuffix(".bin") !in keys) file.delete()
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE * 2) {
                val scale = (MAX_EDGE * 2).toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
        }
    }.onFailure { Log.w(TAG, "could not read the photo WhatsApp attached", it) }.getOrNull()

    /** Shrinks until it fits the glasses' limits, then saves it encrypted. */
    private fun store(context: Context, bitmap: Bitmap): String? {
        val jpeg = encode(bitmap) ?: return null
        val key = MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it) }.take(32)
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$key.bin")
        if (file.exists()) return key
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, ZapInbox.secretKey())
            file.writeBytes(cipher.iv + cipher.doFinal(jpeg))
            key
        }.onFailure { Log.w(TAG, "could not save photo", it) }.getOrNull()
    }

    private fun encode(source: Bitmap): ByteArray? {
        for (edge in intArrayOf(MAX_EDGE, 448, 384, 320, 256)) {
            val scale = minOf(1f, edge.toFloat() / max(source.width, source.height))
            val w = (source.width * scale).roundToInt().coerceIn(1, MAX_EDGE)
            val h = (source.height * scale).roundToInt().coerceIn(1, MAX_EDGE)
            val scaled = if (w == source.width && h == source.height) source else Bitmap.createScaledBitmap(source, w, h, true)
            for (quality in intArrayOf(72, 60, 48)) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= MAX_BYTES) return out.toByteArray()
            }
        }
        return null
    }
}
