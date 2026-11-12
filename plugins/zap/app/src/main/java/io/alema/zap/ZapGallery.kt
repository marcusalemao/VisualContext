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
import android.util.Size
import io.alema.zap.core.GalleryKey
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photos and videos the wearer can attach to a message.
 *
 * What the glasses shot comes first: Rokid Nexus' photo sync (and Hi Rokid's
 * own imports) put those in the phone's "Download/Hi Rokid" folder. Then the
 * phone's newest photos and videos. Keys are [GalleryKey]s ("g:o:v:123"):
 * nothing is copied until the wearer actually sends one.
 */
internal object ZapGallery {
    private const val TAG = "ZapGallery"
    private const val LIMIT = 30
    /** WhatsApp takes long videos, but copying one for it must stay quick and cheap. */
    private const val MAX_SHARE_BYTES = 300L * 1024 * 1024

    fun isKey(key: String) = GalleryKey.parse(key) != null || key.startsWith(SNAP_PREFIX)

    // --- photos taken just now with the glasses' camera ("snap:<time>") ---------

    private const val SNAP_PREFIX = "snap:"
    private const val SNAP_DIR = "snaps"
    private const val SNAP_KEEP_MS = 24 * 60 * 60 * 1000L

    /** Keeps a photo the glasses just took, in Zap's private files, for a day. Its key works like any gallery key. */
    fun saveSnapshot(context: Context, jpeg: ByteArray): String? = runCatching {
        init(context)
        val dir = File(context.filesDir, SNAP_DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > SNAP_KEEP_MS }?.forEach { it.delete() }
        File(dir, "$now.jpg").writeBytes(jpeg)
        "$SNAP_PREFIX$now"
    }.onFailure { Log.w(TAG, "could not keep the glasses' photo", it) }.getOrNull()

    private var filesDir: File? = null

    private fun snapFile(key: String): File? {
        val time = key.removePrefix(SNAP_PREFIX).takeIf { key.startsWith(SNAP_PREFIX) }?.toLongOrNull() ?: return null
        return filesDir?.let { File(File(it, SNAP_DIR), "$time.jpg") }?.takeIf { it.exists() }
    }

    /** Remembers where snapshots live; called once when the plugin starts. */
    fun init(context: Context) {
        if (filesDir == null) filesDir = context.applicationContext.filesDir
    }

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Photos and videos both allowed (or Android 14's "selected only"): what the settings switch shows. */
    fun hasPhotosAndVideos(context: Context): Boolean {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> true
            Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    enum class Access { NONE, PARTIAL, FULL }

    /**
     * Android 14 lets people allow only some photos. Then Zap sees only those —
     * not new shots from the glasses either — so settings say so and offer "all".
     */
    fun access(context: Context): Access {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
            else -> Access.NONE
        }
    }

    /** Any access at all — Android 14's "selected photos only" counts. */
    fun hasPermission(context: Context): Boolean =
        permissions().any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** The newest photo that came over from the glasses (not videos), with when it was added, epoch seconds. */
    /** The newest glasses photo, when it reached the phone (seconds) and when it was taken (ms, 0 if unknown). */
    fun newestGlassesPhoto(context: Context): Pair<GalleryKey, Long>? = newestGlassesPhotoTimes(context)?.let { it.first to it.second }

    fun newestGlassesPhotoTimes(context: Context): Triple<GalleryKey, Long, Long>? {
        if (access(context) != Access.FULL) return null
        return runCatching {
            val path = MediaStore.MediaColumns.RELATIVE_PATH
            val bucket = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN),
                "(COALESCE($path, '') LIKE ? OR COALESCE($bucket, '') LIKE ?)",
                arrayOf("%rokid%", "%rokid%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) Triple(GalleryKey(fromGlasses = true, video = false, id = cursor.getLong(0)), cursor.getLong(1), cursor.getLong(2)) else null
            }
        }.onFailure { Log.w(TAG, "newest glasses photo query failed", it) }.getOrNull()
    }

    /** Newest first: the glasses' shots, then the phone's. */
    fun recent(context: Context): List<String> {
        if (!hasPermission(context)) return emptyList()
        val glasses = query(context, glasses = true)
        val phone = query(context, glasses = false)
        return (glasses + phone).map(GalleryKey::toString)
    }

    private fun query(context: Context, glasses: Boolean): List<GalleryKey> = runCatching {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val path = MediaStore.MediaColumns.RELATIVE_PATH
        val bucket = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val kinds = "($type = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR $type = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        // COALESCE: a row without a folder name would make NOT(...) unknown and hide it from the phone list.
        val fromGlasses = "(COALESCE($path, '') LIKE ? OR COALESCE($bucket, '') LIKE ?)"
        val selection = if (glasses) "$kinds AND $fromGlasses" else "$kinds AND NOT $fromGlasses"
        context.contentResolver.query(
            files,
            arrayOf(MediaStore.Files.FileColumns._ID, type),
            selection,
            arrayOf("%rokid%", "%rokid%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < LIMIT) {
                    add(GalleryKey(glasses, cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, cursor.getLong(0)))
                }
            }
        }.orEmpty()
    }.onFailure { Log.w(TAG, "gallery query failed", it) }.getOrDefault(emptyList())

    fun uri(key: String): Uri? = snapFile(key)?.let(Uri::fromFile) ?: GalleryKey.parse(key)?.let {
        val base = if (it.video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ContentUris.withAppendedId(base, it.id)
    }

    private val thumbs = object : LinkedHashMap<String, ZapPhotos.Photo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ZapPhotos.Photo>?) = size > 8
    }

    /** A small JPEG for the glasses (≤512 px, ≤64 KB) — a video shows its first frame. Memory only. */
    fun thumbnail(context: Context, key: String): ZapPhotos.Photo? {
        synchronized(thumbs) { thumbs[key] }?.let { return it }
        init(context)
        val uri = uri(key) ?: return null
        val bitmap = runCatching {
            if (GalleryKey.isVideo(key)) {
                context.contentResolver.loadThumbnail(uri, Size(ZapPhotos.MAX_EDGE * 2, ZapPhotos.MAX_EDGE * 2), null)
            } else {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    val target = ZapPhotos.MAX_EDGE * 2
                    if (longest > target) {
                        val scale = target.toFloat() / longest
                        decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                    }
                }
            }
        }.onFailure { Log.w(TAG, "could not read photo or video", it) }.getOrNull() ?: return null
        return ZapPhotos.thumbnail(bitmap)?.also { photo -> synchronized(thumbs) { thumbs[key] = photo } }
    }

    fun mimeType(key: String) = if (GalleryKey.isVideo(key)) "video/mp4" else "image/jpeg"

    /**
     * A copy WhatsApp may read, through Zap's own [ZapShareProvider]: WhatsApp then
     * needs no access to the wearer's gallery. Only the latest copy is kept.
     */
    fun shareable(context: Context, key: String): Uri? {
        init(context)
        val source = uri(key) ?: return null
        val video = GalleryKey.isVideo(key)
        return runCatching {
            val size = context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
            if (size > MAX_SHARE_BYTES) {
                Log.w(TAG, "too big to hand over: $size bytes")
                return null
            }
            val dir = File(context.cacheDir, ZapShareProvider.DIR).apply { deleteRecursively(); mkdirs() }
            val file = File(dir, ZapShareProvider.fileName(video))
            context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return null
            ZapShareProvider.uri(context, video)
        }.onFailure { Log.w(TAG, "could not prepare it for WhatsApp", it) }.getOrNull()
    }
}

/**
 * Hands WhatsApp the one photo or video the wearer just chose to send —
 * read-only, and only to the app Zap granted it to. Not exported.
 */
class ZapShareProvider : android.content.ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File? {
        val name = uri.lastPathSegment?.takeIf { it == fileName(false) || it == fileName(true) } ?: return null
        return context?.let { File(File(it.cacheDir, DIR), name) }?.takeIf { it.exists() }
    }

    override fun getType(uri: Uri) = if (uri.lastPathSegment == fileName(true)) "video/mp4" else "image/jpeg"

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val file = file(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): android.database.Cursor {
        val file = file(uri)
        val columns = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
        return android.database.MatrixCursor(columns).apply {
            if (file != null) addRow(arrayOf<Any>(file.name, file.length()))
        }
    }

    override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
    override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val DIR = "share"
        fun fileName(video: Boolean) = if (video) "zap-video.mp4" else "zap-photo.jpg"
        fun uri(context: Context, video: Boolean): Uri = Uri.parse("content://${context.packageName}.share/${fileName(video)}")
    }
}
