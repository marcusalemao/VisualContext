package io.alema.facecore

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Face engine 2 (0.3): Google ML Kit finds the face and its eyes, nose and mouth (on the phone),
 * the face is aligned to the standard 112×112 ArcFace pose, and MobileFaceNet-ArcFace (ONNX)
 * gives a 512-d vector. Much better than face-api.js with faces turned, far away or in poor light.
 * Blocking — call from a background thread. Nothing leaves the phone.
 */
class ArcFaceEngine(ctx: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = ctx.assets.open("arcface/w600k_mbf.onnx").use { env.createSession(it.readBytes(), OrtSession.SessionOptions()) }
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.06f)
            .build(),
    )

    /** Faces in the picture, biggest first; each with its 512-d unit vector. */
    fun describe(bmp: Bitmap): List<Face> {
        val faces = try { Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0))) } catch (_: Exception) { return emptyList() }
        return faces.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }.mapNotNull { f ->
            val pts = listOf(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)
                .map { f.getLandmark(it)?.position ?: return@mapNotNull null }
            // Template order is by position in the picture: left eye, right eye, nose, left mouth, right mouth.
            val eyes = listOf(pts[0], pts[1]).sortedBy { it.x }
            val mouth = listOf(pts[3], pts[4]).sortedBy { it.x }
            val src = listOf(eyes[0], eyes[1], pts[2], mouth[0], mouth[1]).map { PointF(it.x, it.y) }
            val aligned = align(bmp, src)
            val v = embed(aligned)
            aligned.recycle()
            val b = f.boundingBox
            Face(v, b.left.toFloat(), b.top.toFloat(), b.width().toFloat(), b.height().toFloat(), 1f)
        }
    }

    private fun align(bmp: Bitmap, src: List<PointF>): Bitmap {
        val m = Similarity.fit(src, TEMPLATE)
        val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun embed(face: Bitmap): FloatArray {
        val px = IntArray(112 * 112).also { face.getPixels(it, 0, 112, 0, 0, 112, 112) }
        val buf = FloatBuffer.allocate(3 * 112 * 112)
        for (c in 0 until 3) for (p in px) {
            val v = when (c) { 0 -> (p shr 16) and 0xff; 1 -> (p shr 8) and 0xff; else -> p and 0xff }
            buf.put((v - 127.5f) / 127.5f)
        }
        buf.rewind()
        OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, 112, 112)).use { t ->
            session.run(mapOf(session.inputNames.first() to t)).use { r ->
                @Suppress("UNCHECKED_CAST")
                val v = (r[0].value as Array<FloatArray>)[0]
                val n = sqrt(v.fold(0.0) { a, x -> a + x * x }).toFloat().coerceAtLeast(1e-6f)
                return FloatArray(v.size) { v[it] / n }
            }
        }
    }

    fun close() { detector.close(); session.close() }

    companion object {
        /** InsightFace's reference landmarks for a 112×112 aligned face. */
        val TEMPLATE = listOf(
            PointF(38.2946f, 51.6963f), PointF(73.5318f, 51.5014f), PointF(56.0252f, 71.7366f),
            PointF(41.5493f, 92.3655f), PointF(70.7299f, 92.2041f),
        )
    }
}

/** Least-squares similarity (rotation + uniform scale + shift), like skimage's SimilarityTransform. */
object Similarity {
    fun params(src: List<PointF>, dst: List<PointF>): FloatArray {
        val n = src.size
        val sx = src.sumOf { it.x.toDouble() } / n; val sy = src.sumOf { it.y.toDouble() } / n
        val dx = dst.sumOf { it.x.toDouble() } / n; val dy = dst.sumOf { it.y.toDouble() } / n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val ax = src[i].x - sx; val ay = src[i].y - sy
            val bx = dst[i].x - dx; val by = dst[i].y - dy
            num1 += ax * bx + ay * by; num2 += ax * by - ay * bx; den += ax * ax + ay * ay
        }
        val a = num1 / den; val b = num2 / den
        val tx = dx - (a * sx - b * sy); val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    fun fit(src: List<PointF>, dst: List<PointF>): Matrix {
        val (a, b, tx, ty) = params(src, dst).toList()
        return Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
    }
}
