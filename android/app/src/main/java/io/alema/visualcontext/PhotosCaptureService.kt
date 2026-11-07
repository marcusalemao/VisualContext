package io.alema.visualcontext

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Spec 5b: screen capture while the user scrolls Google Photos' "People and pets".
 * Frames stay on the phone; only faces with names are kept, and only after the user picks them.
 */
class PhotosCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var grid: PhotosGrid? = null
    private val busy = AtomicBoolean(false)
    private var lastSig = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finishCapture(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION") val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTE, note(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        running = true
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
        projection = p
        val t = HandlerThread("vc-capture").also { it.start() }; thread = t
        val h = Handler(t.looper)
        p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { h.post { finishCapture() } } }, h)
        val m = resources.displayMetrics
        val w = m.widthPixels; val hgt = m.heightPixels
        val r = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2); reader = r
        display = p.createVirtualDisplay("vc-photos", w, hgt, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, h)
        h.post { grid = PhotosGrid(this) }
        r.setOnImageAvailableListener({ ir ->
            val img = try { ir.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            if (!busy.compareAndSet(false, true)) { img.close(); return@setOnImageAvailableListener }
            val bmp = try {
                val pl = img.planes[0]; val rowPx = pl.rowStride / pl.pixelStride
                Bitmap.createBitmap(rowPx, img.height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pl.buffer) }
                    .let { full -> if (rowPx != img.width) Bitmap.createBitmap(full, 0, 0, img.width, img.height) else full }
            } catch (_: Exception) { null } finally { img.close() }
            if (bmp == null) { busy.set(false); return@setOnImageAvailableListener }
            h.postDelayed({
                try {
                    val sig = signature(bmp)
                    if (sig != lastSig) {
                        lastSig = sig
                        val g = grid ?: PhotosGrid(this).also { grid = it }
                        if (g.read(bmp) > 0) { count = g.found.size; getSystemService(NotificationManager::class.java).notify(NOTE, note(count)) }
                    }
                } catch (_: Throwable) {
                } finally { bmp.recycle(); busy.set(false) }
            }, 450)
        }, h)
        return START_NOT_STICKY
    }

    /** A cheap fingerprint, so a still screen is not read again and again. */
    private fun signature(b: Bitmap): Long {
        var s = 0L
        for (yy in 1..12) for (xx in 1..8) s = s * 31 + (b.getPixel(b.width * xx / 9, b.height * yy / 13) and 0xF0F0F0)
        return s
    }

    private fun finishCapture() {
        if (!running) return
        running = false
        PhotosGrid.last = grid?.found?.values?.toList() ?: emptyList()
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        val t = thread
        t?.looper?.let { Handler(it).post { grid?.close(); grid = null; t.quitSafely() } }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun note(n: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Importar do Google Fotos", NotificationManager.IMPORTANCE_LOW))
        val done = PendingIntent.getActivity(this, 1, Intent(this, PhotosPickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (n == 0) "Lendo a tela: abra Pessoas e animais" else "$n ${if (n == 1) "pessoa encontrada" else "pessoas encontradas"}")
            .setContentText("Role devagar até o fim. Depois toque em Terminei.")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(done)
            .addAction(Notification.Action.Builder(null, "Terminei", done).build())
            .build()
    }

    override fun onDestroy() { finishCapture(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "io.alema.visualcontext.PHOTOS_STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CH = "photos_import"
        private const val NOTE = 51
        @Volatile var running = false; private set
        @Volatile var count = 0; private set
        fun stop(ctx: Context) { if (running) ctx.startService(Intent(ctx, PhotosCaptureService::class.java).setAction(ACTION_STOP)) }
    }
}
