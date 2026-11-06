package io.alema.facecore

import java.text.Normalizer

/**
 * Finds a person's name in what was said, so the card learns it without any button:
 * "essa é a Ana", "esse é o Pedro Reis", "meu nome é Bruno", "eu sou o Rafael",
 * "prazer, Carla", "o nome dele é João", "pode me chamar de Bia", "this is Anna", "I'm Peter".
 * Plain Kotlin, unit-tested. Works on lower-case transcripts too (the result is capitalised).
 */
object NameSpotter {
    private val TRIGGERS = listOf(
        "essa e a", "essa e", "esse e o", "esse e", "esta e a", "este e o", "aqui e a", "aqui e o",
        "meu nome e", "o meu nome e", "eu sou a", "eu sou o", "me chamo", "pode me chamar de", "prazer eu sou o", "prazer eu sou a",
        "prazer sou o", "prazer sou a", "prazer", "o nome dele e", "o nome dela e", "ele se chama", "ela se chama",
        "esse aqui e o", "essa aqui e a", "this is", "my name is", "i am", "i'm", "im", "call me",
    ).sortedByDescending { it.length }

    /** Words that end a name ("Ana, tudo bem"), and words that are never names ("eu sou de São Paulo"). */
    private val STOP = setOf(
        "e", "que", "aqui", "tudo", "bem", "da", "do", "de", "dos", "das", "minha", "meu", "sua", "seu", "amiga", "amigo",
        "prazer", "muito", "obrigado", "obrigada", "oi", "ola", "entao", "ne", "tipo", "assim", "trabalho", "sou", "a", "o",
        "um", "uma", "voce", "ele", "ela", "and", "the", "from", "nice", "to", "meet", "you", "here", "my", "friend",
    )
    private val NOT_NAMES = setOf(
        "brasileiro", "brasileira", "designer", "engenheiro", "engenheira", "medico", "medica", "professor", "professora",
        "novo", "nova", "eu", "ele", "ela", "voce", "isso", "aquele", "aquela", "muito", "bom", "boa", "fornecedor",
        "fornecedora", "cliente", "chefe", "dono", "dona", "aqui", "seu", "sua", "assim", "tipo", "happy", "fine", "good",
        "sorry", "here", "not", "from", "a", "an", "the",
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    /** The last name introduced in [text], or null. */
    fun find(text: String): String? {
        // Keep the original words (with accents) aligned with a normalised copy for matching.
        // Words and punctuation as separate tokens: a name never crosses a comma or a full stop.
        val words = Regex("[\\p{L}\\p{N}'’-]+|[,.;:!?]").findAll(text).map { it.value }.toList()
        val low = words.map { norm(it) }
        var found: String? = null
        var i = 0
        while (i < low.size) {
            val t = TRIGGERS.firstOrNull { trig -> val p = trig.split(' '); i + p.size <= low.size && (p.indices).all { low[i + it] == p[it] } }
            if (t == null) { i++; continue }
            var start = i + t.split(' ').size
            if (start < words.size && isPunct(words[start])) start++ // "prazer, Carla"
            val name = mutableListOf<String>()
            var j = start
            while (j < words.size && name.size < 3) {
                val w = low[j]
                if (isPunct(w) || w in STOP || w.length < 2 || w.any { it.isDigit() }) break
                if (name.isEmpty() && w in NOT_NAMES) break
                name += words[j]
                j++
            }
            // "da Silva" / "de Souza": allow one particle inside a surname.
            if (name.size in 1..2 && j + 1 < words.size && low[j] in setOf("da", "de", "do", "dos", "das") && !isPunct(low[j + 1]) && low[j + 1] !in STOP && low[j + 1] !in NOT_NAMES) {
                name += words[j]; name += words[j + 1]
            }
            if (name.isNotEmpty()) found = name.joinToString(" ") { cap(it) }
            i = maxOf(j, i + 1)
        }
        return found
    }

    private fun isPunct(w: String) = w.length == 1 && w[0] in ",.;:!?"

    private fun cap(w: String): String {
        val lowerParticles = setOf("da", "de", "do", "dos", "das")
        if (w.lowercase() in lowerParticles) return w.lowercase()
        return w.lowercase().replaceFirstChar { it.uppercase() }
    }
}
