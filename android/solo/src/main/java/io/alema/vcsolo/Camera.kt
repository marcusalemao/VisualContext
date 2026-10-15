package io.alema.vcsolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * The glasses camera, on only while needed: during setup (to read the QR) and after the first "olhar"
 * in a session. Frames stay in memory; only the photo Gemini asked for leaves the glasses.
 */
class Camera(private val ctx: Context, private val owner: LifecycleOwner) {
    private val exec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    @Volatile private var pendingPhoto: ((ByteArray?) -> Unit)? = null
    @Volatile private var qrListener: ((String) -> Unit)? = null
    @Volatile var bound = false
        private set

    fun start(onReady: (Boolean) -> Unit) {
        if (bound) { onReady(true); return }
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({
            try {
                val p = f.get()
                provider = p
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 960))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(exec) { img -> handle(img) }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                bound = true
                boundAt = System.currentTimeMillis()
                onReady(true)
            } catch (_: Exception) {
                onReady(false)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun stop() {
        provider?.unbindAll()
        bound = false
        qrListener = null
        pendingPhoto?.invoke(null); pendingPhoto = null
    }

    /** Next frame as a JPEG, longest side ≤ 768 px (what the Live API wants). */
    fun photo(cb: (ByteArray?) -> Unit) { pendingPhoto = cb }

    fun scanQr(cb: (String) -> Unit) { qrListener = cb }

    private var lastQrAt = 0L
    @Volatile private var boundAt = 0L

    private fun handle(img: ImageProxy) {
        try {
            // The first frames after the camera opens are dark (auto-exposure still settling).
            if (System.currentTimeMillis() - boundAt > 700) pendingPhoto?.let { cb -> pendingPhoto = null; cb(jpeg(img)) }
            val q = qrListener
            val now = System.currentTimeMillis()
            if (q != null && now - lastQrAt > 300) {
                lastQrAt = now
                readQr(img)?.let { q(it) }
            }
        } finally {
            img.close()
        }
    }

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    private fun readQr(img: ImageProxy): String? {
        val y = img.planes[0]
        val stride = y.rowStride
        val buf = y.buffer.duplicate().apply { rewind() }
        val data = ByteArray(buf.remaining()).also { buf.get(it) }
        val src = PlanarYUVLuminanceSource(data, stride, img.height, 0, 0, img.width, img.height, false)
        return try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text } catch (_: Exception) { null } finally { reader.reset() }
    }

    private fun jpeg(img: ImageProxy): ByteArray? = try {
        val nv21 = nv21(img)
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, img.width, img.height, null).compressToJpeg(Rect(0, 0, img.width, img.height), 85, out)
        val full = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        val scale = 768f / maxOf(full.width, full.height)
        val m = Matrix().apply { postScale(minOf(1f, scale), minOf(1f, scale)); postRotate(img.imageInfo.rotationDegrees.toFloat()) }
        val small = Bitmap.createBitmap(full, 0, 0, full.width, full.height, m, true)
        val o2 = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, o2)
        full.recycle(); if (small !== full) small.recycle()
        o2.toByteArray()
    } catch (_: Exception) { null }

    private fun nv21(img: ImageProxy): ByteArray {
        val w = img.width; val h = img.height
        val out = ByteArray(w * h * 3 / 2)
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        var pos = 0
        for (row in 0 until h) { yB.position(row * yP.rowStride); yB.get(out, pos, w); pos += w }
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            val vi = row * vP.rowStride + col * vP.pixelStride
            val ui = row * uP.rowStride + col * uP.pixelStride
            out[pos++] = vB.get(vi); out[pos++] = uB.get(ui)
        }
        return out
    }

    fun shutdown() { stop(); exec.shutdown() }
}
