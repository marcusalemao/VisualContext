package io.alema.visualcontext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/** CameraX frames → upright JPEG / Bitmap. Frames stay in memory; nothing is written to disk. */
object Frames {
    fun jpeg(img: ImageProxy, quality: Int = 85): ByteArray? {
        val nv21 = nv21(img) ?: return null
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, img.width, img.height, null).compressToJpeg(Rect(0, 0, img.width, img.height), quality, out)
        val rot = img.imageInfo.rotationDegrees
        if (rot == 0) return out.toByteArray()
        val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        val o2 = ByteArrayOutputStream(); r.compress(Bitmap.CompressFormat.JPEG, quality, o2)
        return o2.toByteArray()
    }

    fun bitmap(jpeg: ByteArray, maxEdge: Int = 960): Bitmap? {
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(full.width, full.height))
        return if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
    }

    fun shrink(jpeg: ByteArray, maxEdge: Int, quality: Int = 80): ByteArray? {
        val b = bitmap(jpeg, maxEdge) ?: return null
        val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun nv21(img: ImageProxy): ByteArray? {
        if (img.format != ImageFormat.YUV_420_888) return null
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        val w = img.width; val h = img.height
        val out = ByteArray(w * h * 3 / 2)
        var o = 0
        val yb = y.buffer; val yrs = y.rowStride
        for (row in 0 until h) { yb.position(row * yrs); yb.get(out, o, w); o += w }
        val ub = u.buffer; val vb = v.buffer
        val cw = w / 2; val ch = h / 2
        val urs = u.rowStride; val ups = u.pixelStride; val vrs = v.rowStride; val vps = v.pixelStride
        for (row in 0 until ch) for (col in 0 until cw) {
            out[o++] = vb.get(row * vrs + col * vps)
            out[o++] = ub.get(row * urs + col * ups)
        }
        return out
    }
}
