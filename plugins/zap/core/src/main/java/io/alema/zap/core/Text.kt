package io.alema.zap.core

import java.text.Normalizer

/** Text helpers shared by the matcher and the parser. Pure, locale-independent. */
internal object Text {
    private val MARKS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")

    /** "Léo D'Ávila" -> "leo d avila". Accents, case and punctuation never decide a match. */
    fun fold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()
        .replace(NON_WORD, " ")
        .replace(SPACES, " ")
        .trim()

    fun tokens(value: String): List<String> = fold(value).split(' ').filter(String::isNotEmpty)

    /** Plain Levenshtein distance; names are short, so the O(n·m) table is fine. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * How a folded word sounds, roughly, in Portuguese, Spanish and English
     * names: speech-to-text writes "Marcos" for "Marcus", "Filipe" for
     * "Phillipe", "Kaio" for "Caio". Two words with the same key are treated
     * as the same name.
     */
    fun soundKey(word: String): String {
        var w = fold(word).replace(" ", "")
        if (w.isEmpty()) return w
        w = w.replace("ph", "f").replace("th", "t").replace("sh", "x").replace("ch", "x")
            .replace("qu", "c").replace("k", "c").replace("w", "v").replace("y", "i")
            .replace("z", "s").replace("ss", "s")
        if (w.startsWith("h")) w = w.drop(1)
        w = w.replace(Regex("(.)\\1+"), "$1") // double letters: "Matteus" -> "mateus"
        // Unstressed endings drift: Marcus/Marcos, Mateus/Mateos, Kelly/Keli.
        w = w.replace(Regex("[ou](s?)$"), "o$1").replace(Regex("[ei]$"), "i")
        return w
    }

    fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - distance(a, b).toDouble() / longest
    }
}
