package io.alema.visualcontext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMatchTest {
    private fun vec(seed: Int, shift: Float = 0f) = FloatArray(128) { ((it * 31 + seed * 17) % 23) / 60f - 0.18f + shift }
    private fun near(v: FloatArray, by: Float) = FloatArray(128) { v[it] + if (it % 2 == 0) by else -by }

    @Test fun sameFaceMatches() {
        val a = vec(1)
        val r = FaceMatch.best(near(a, 0.02f), listOf(FaceMatch.Known("p:a", a), FaceMatch.Known("p:b", vec(5))))
        assertTrue(r is FaceMatch.Result.Match); assertEquals("p:a", (r as FaceMatch.Result.Match).key)
    }

    @Test fun strangerIsUnknown() {
        val r = FaceMatch.best(vec(9, 0.2f), listOf(FaceMatch.Known("p:a", vec(1))))
        assertTrue(r is FaceMatch.Result.Unknown)
    }

    @Test fun twoCloseCandidatesAreUnsure() {
        val a = vec(1)
        val r = FaceMatch.best(a, listOf(FaceMatch.Known("p:a", near(a, 0.03f)), FaceMatch.Known("p:b", near(a, 0.035f))))
        assertTrue(r is FaceMatch.Result.Unsure)
    }

    @Test fun samePersonTwiceIsNotAmbiguous() {
        val a = vec(1)
        val r = FaceMatch.best(a, listOf(FaceMatch.Known("p:a", near(a, 0.02f)), FaceMatch.Known("p:a", near(a, 0.03f)), FaceMatch.Known("p:b", vec(5))))
        assertTrue(r is FaceMatch.Result.Match)
    }

    @Test fun parsesStoredEmbedding() {
        val v = vec(3)
        val back = FaceMatch.parse(FaceMatch.format(v))!!
        assertEquals(0.0, FaceMatch.distance(v, back), 1e-6)
        assertNull(FaceMatch.parse("[1,2,3]")); assertNull(FaceMatch.parse(null))
    }
}
