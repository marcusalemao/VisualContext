package io.alema.zap

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import io.alema.zap.core.ZapMessage
import kotlin.math.abs

/**
 * WhatsApp voice messages, played on the glasses' audio.
 *
 * The notification only says "🎤 Mensagem de voz (0:12)"; the audio itself is
 * the file WhatsApp downloads into its "Voice Notes" folder. The wearer grants
 * Zap that one folder (the system folder picker, nothing else on the phone),
 * and Zap finds the file by when it arrived and how long it is.
 *
 * Privacy as with Nexus' voice: it plays only when the glasses (or earbuds)
 * are the phone's audio, never on the phone's speaker.
 */
internal object ZapVoiceNotes {
    private const val TAG = "ZapVoiceNotes"
    private const val PREFS = "zap_voice"
    private const val KEY_TREES = "trees"
    private const val STORAGE = "com.android.externalstorage.documents"

    /** Where WhatsApp (and WhatsApp Business) keep voice messages. */
    private val FOLDERS = listOf(
        "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
        "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes",
    )

    /** The folder picker, opened right at WhatsApp's voice notes. */
    fun pickIntent(business: Boolean = false): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(STORAGE, FOLDERS[if (business) 1 else 0]))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    fun remember(context: Context, tree: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { Log.w(TAG, "could not keep folder access", it); return }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trees = prefs.getStringSet(KEY_TREES, emptySet()).orEmpty() + tree.toString()
        prefs.edit().putStringSet(KEY_TREES, trees).apply()
    }

    /** Folders the wearer granted and Android still honours. */
    fun trees(context: Context): List<Uri> {
        val held = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_TREES, emptySet()).orEmpty()
            .filter { it in held }
            .map(Uri::parse)
    }

    fun hasAccess(context: Context) = trees(context).isNotEmpty()

    /** True when sound would come out of the glasses, earbuds or a headset — not the phone itself. */
    fun privateOutput(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val private = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in private }
    }

    private data class Note(val uri: Uri, val modifiedMs: Long)

    /**
     * The file for a voice message: downloaded around when the notification
     * came (WhatsApp fetches voice messages at once), and as long as it said.
     */
    fun find(context: Context, message: ZapMessage): Uri? {
        val candidates = trees(context).flatMap { list(context, it) }
            .filter { it.modifiedMs in (message.timeMs - EARLY_MS)..(message.timeMs + LATE_MS) }
            .sortedBy { abs(it.modifiedMs - message.timeMs) }
            .take(6)
        if (candidates.isEmpty()) return null
        val wanted = message.voiceSeconds?.takeIf { it > 0 } ?: return candidates.first().uri
        return candidates.firstOrNull { abs(seconds(context, it.uri) - wanted) <= 1 }?.uri ?: candidates.first().uri
    }

    /** The voice notes folder and its weekly subfolders ("202642"). */
    private fun list(context: Context, tree: Uri): List<Note> = runCatching {
        val out = ArrayList<Note>()
        fun walk(documentId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val mime = c.getString(1).orEmpty()
                    val name = c.getString(3).orEmpty()
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR && depth < 2 -> walk(id, depth + 1)
                        name.endsWith(".opus") || mime.startsWith("audio/") ->
                            out += Note(DocumentsContract.buildDocumentUriUsingTree(tree, id), c.getLong(2))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        out
    }.onFailure { Log.w(TAG, "could not list voice notes", it) }.getOrDefault(emptyList())

    private fun seconds(context: Context, uri: Uri): Int = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt()
        }
    }.getOrDefault(-99)

    // --- playing -----------------------------------------------------------------

    private var player: MediaPlayer? = null

    fun play(context: Context, uri: Uri, onDone: () -> Unit, onError: () -> Unit) {
        stop()
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            mp.setDataSource(context, uri)
            mp.setOnCompletionListener { if (player === mp) { stop(); onDone() } }
            mp.setOnErrorListener { _, _, _ -> if (player === mp) { stop(); onError() }; true }
            mp.setOnPreparedListener { if (player === mp) it.start() }
            mp.prepareAsync()
        }.onFailure {
            Log.w(TAG, "could not play", it)
            stop()
            onError()
        }
    }

    fun stop() {
        val mp = player ?: return
        player = null
        runCatching { mp.stop() }
        mp.release()
    }

    private const val EARLY_MS = 2 * 60_000L
    private const val LATE_MS = 30 * 60_000L
}
