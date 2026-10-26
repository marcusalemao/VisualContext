package io.alema.visualcontext

import kotlin.math.sqrt

/**
 * Who is this face? Pure Kotlin so it is unit-tested.
 * Descriptors are face-api.js 128-d vectors — the same kind already stored in the memory's
 * face_embedding field, so old enrolments keep working.
 * Same person ≈ distance < 0.45; different people ≈ 0.55–0.9. We accept below [THRESHOLD]
 * and only when the runner-up (a different person) is clearly farther, so a doubtful face
 * shows as "não tenho certeza" instead of a wrong name.
 */
object FaceMatch {
    const val THRESHOLD = 0.5
    const val MARGIN = 0.06

    data class Known(val key: String, val descriptor: FloatArray)
    sealed class Result {
        data class Match(val key: String, val distance: Double) : Result()
        data class Unsure(val key: String, val distance: Double) : Result()
        object Unknown : Result()
    }

    fun distance(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var s = 0.0
        for (i in a.indices) { val d = (a[i] - b[i]).toDouble(); s += d * d }
        return sqrt(s)
    }

    fun best(face: FloatArray, known: List<Known>): Result {
        // Best distance per person (a person may have several enrolments).
        val perKey = HashMap<String, Double>()
        for (k in known) {
            val d = distance(face, k.descriptor)
            if (d < (perKey[k.key] ?: Double.MAX_VALUE)) perKey[k.key] = d
        }
        val ranked = perKey.entries.sortedBy { it.value }
        val first = ranked.firstOrNull() ?: return Result.Unknown
        if (first.value >= THRESHOLD + 0.08) return Result.Unknown
        val second = ranked.getOrNull(1)?.value ?: Double.MAX_VALUE
        return if (first.value < THRESHOLD && second - first.value >= MARGIN) Result.Match(first.key, first.value)
        else Result.Unsure(first.key, first.value)
    }

    fun parse(text: String?): FloatArray? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().removePrefix("[").removeSuffix("]").split(',')
        if (parts.size != 128) return null
        return try { FloatArray(128) { parts[it].trim().toFloat() } } catch (_: NumberFormatException) { null }
    }

    fun format(d: FloatArray): String = d.joinToString(",", "[", "]")
}
