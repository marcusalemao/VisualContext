package io.alema.zap

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photos on the phone the wearer can attach to a message.
 *
 * Glasses photos first: when the phone holds pictures in a folder named after
 * Rokid (where the companion app saves what the glasses shoot), only those are
 * offered; otherwise the phone's newest photos. Keys are "g:<MediaStore id>";
 * nothing is copied until the wearer actually sends one.
 */
internal object ZapGallery {
    private const val TAG = "ZapGallery"
    private const val PREFIX = "g:"
    private const val LIMIT = 24

    fun isKey(key: String) = key.startsWith(PREFIX)

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Any access at all — Android 14's "selected photos only" counts. */
    fun hasPermission(context: Context): Boolean =
        permissions().any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Newest first. Glasses photos when there are any, else every recent photo. */
    fun recent(context: Context): List<String> {
        if (!hasPermission(context)) return emptyList()
        val glasses = query(context, glassesOnly = true)
        return (glasses.ifEmpty { query(context, glassesOnly = false) }).map { "$PREFIX$it" }
    }

    private fun query(context: Context, glassesOnly: Boolean): List<Long> = runCatching {
        val selection = if (glassesOnly) {
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            null
        }
        val args = if (glassesOnly) arrayOf("%rokid%", "%rokid%") else null
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < LIMIT) add(cursor.getLong(0))
            }
        }.orEmpty()
    }.onFailure { Log.w(TAG, "gallery query failed", it) }.getOrDefault(emptyList())

    fun uri(key: String): Uri? = key.removePrefix(PREFIX).toLongOrNull()
        ?.let { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }

    private val thumbs = object : LinkedHashMap<String, ZapPhotos.Photo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ZapPhotos.Photo>?) = size > 8
    }

    /** A small JPEG of the photo for the glasses (≤512 px, ≤64 KB), kept in memory only. */
    fun thumbnail(context: Context, key: String): ZapPhotos.Photo? {
        synchronized(thumbs) { thumbs[key] }?.let { return it }
        val uri = uri(key) ?: return null
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                val target = ZapPhotos.MAX_EDGE * 2
                if (longest > target) {
                    val scale = target.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                }
            }
        }.onFailure { Log.w(TAG, "could not read photo", it) }.getOrNull() ?: return null
        return ZapPhotos.thumbnail(bitmap)?.also { photo -> synchronized(thumbs) { thumbs[key] = photo } }
    }

    /**
     * A copy WhatsApp may read, through Zap's own [ZapShareProvider]: WhatsApp then
     * needs no access to the wearer's gallery. Only the latest copy is kept.
     */
    fun shareable(context: Context, key: String): Uri? {
        val source = uri(key) ?: return null
        return runCatching {
            val dir = File(context.cacheDir, ZapShareProvider.DIR).apply { deleteRecursively(); mkdirs() }
            val file = File(dir, ZapShareProvider.FILE)
            context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return null
            ZapShareProvider.uri(context)
        }.onFailure { Log.w(TAG, "could not prepare photo for WhatsApp", it) }.getOrNull()
    }
}

/**
 * Hands WhatsApp the one photo the wearer just chose to send — read-only, and
 * only to the app Zap granted it to. Not exported; nothing else is served.
 */
class ZapShareProvider : android.content.ContentProvider() {
    override fun onCreate() = true

    private fun file(): File? = context?.let { File(File(it.cacheDir, DIR), FILE) }?.takeIf { it.exists() }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val file = file() ?: throw java.io.FileNotFoundException(uri.toString())
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): android.database.Cursor {
        val file = file()
        val columns = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
        return android.database.MatrixCursor(columns).apply {
            if (file != null) addRow(arrayOf<Any>(FILE, file.length()))
        }
    }

    override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
    override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val DIR = "share"
        const val FILE = "zap-photo.jpg"
        fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.share/$FILE")
    }
}
