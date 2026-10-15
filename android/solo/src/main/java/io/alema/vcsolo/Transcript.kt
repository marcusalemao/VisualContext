package io.alema.vcsolo

/**
 * What the HUD shows while Gemini talks: the answer so far, cut to the last lines that fit.
 * Plain Kotlin, unit-tested. The visor fits ~3 lines of ~30 characters at the top of the view.
 */
class Transcript(private val maxLines: Int = 3, private val width: Int = 30) {
    private val answer = StringBuilder()

    fun clear() = answer.setLength(0)

    fun add(piece: String) {
        answer.append(piece)
    }

    val text: String get() = answer.toString().replace(Regex("\\s+"), " ").trim()

    /** Last lines of the answer, word-wrapped. */
    fun lines(): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in text.split(' ').filter { it.isNotEmpty() }) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > width) { out += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.takeLast(maxLines)
    }
}
