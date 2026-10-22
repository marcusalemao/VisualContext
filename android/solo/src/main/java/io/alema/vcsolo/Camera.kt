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
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.detector.Detector
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
    @Volatile private var qrStatus: ((QrHint.State) -> Unit)? = null
    private var cam: androidx.camera.core.Camera? = null
    private var previewing = false
    @Volatile var bound = false
        private set

    /** With [preview], the wearer sees the camera live on the glasses (used to aim at a QR). */
    fun start(onReady: (Boolean) -> Unit) = start(null, onReady)

    fun start(preview: PreviewView?, onReady: (Boolean) -> Unit) {
        if (bound && (preview == null || previewing)) { onReady(true); return }
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
                cam = if (preview != null) {
                    val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, analysis)
                } else {
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                }
                previewing = preview != null
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
        previewing = false
        cam = null
        qrListener = null
        qrStatus = null
        pendingPhoto?.invoke(null); pendingPhoto = null
    }

    /** Next frame as a JPEG, longest side ≤ 768 px (what the Live API wants). */
    fun photo(cb: (ByteArray?) -> Unit) { pendingPhoto = cb }

    fun scanQr(status: (QrHint.State) -> Unit, cb: (String) -> Unit) { qrStatus = status; qrListener = cb }

    /** Digital zoom: the Rokid lens is wide, so codes look farther than they are. Returns the ratio applied. */
    fun zoomTo(ratio: Float): Float {
        val c = cam ?: return 1f
        val st = c.cameraInfo.zoomState.value
        val r = ratio.coerceIn(st?.minZoomRatio ?: 1f, st?.maxZoomRatio ?: 1f)
        c.cameraControl.setZoomRatio(r)
        return r
    }

    val zoom: Float get() = cam?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

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
                val text = readQr(img)
                if (text != null) q(text) else qrStatus?.invoke(QrHint.of(locate(img), img.width, img.height))
            }
        } finally {
            img.close()
        }
    }

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    private fun readQr(img: ImageProxy): String? {
        val src = luminance(img)
        return try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text } catch (_: Exception) { null } finally { reader.reset() }
    }

    /** Where ZXing sees the QR's finder patterns, even when it can't read the code yet. */
    private fun locate(img: ImageProxy): List<Pair<Float, Float>> = try {
        val bits = BinaryBitmap(HybridBinarizer(luminance(img))).blackMatrix
        Detector(bits).detect(mapOf(DecodeHintType.TRY_HARDER to true)).points.map { it.x to it.y }
    } catch (_: Exception) { emptyList() }

    private fun luminance(img: ImageProxy): PlanarYUVLuminanceSource {
        val y = img.planes[0]
        val buf = y.buffer.duplicate().apply { rewind() }
        val data = ByteArray(buf.remaining()).also { buf.get(it) }
        return PlanarYUVLuminanceSource(data, y.rowStride, img.height, 0, 0, img.width, img.height, false)
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
