package io.alema.zap.core

/** One candidate for a spoken name, best first. */
data class ContactMatch(val contact: ZapContact, val score: Double)

/**
 * Finds the contact a spoken name refers to.
 *
 * Speech-to-text gives lowercase words with no accents guaranteed and
 * sometimes a slightly wrong spelling ("bruna" for "Bruno" is a real risk),
 * so every query word is matched against every name word by exact match,
 * prefix ("bru" -> "Bruno") or edit similarity. A match must cover every
 * word the wearer said; extra words in the contact's name are fine
 * ("Bruno" finds "Bruno Costa").
 */
class ContactMatcher(contacts: List<ZapContact>) {
    private val indexed = contacts.map { it to Text.tokens(it.name) }.filter { it.second.isNotEmpty() }

    fun match(spoken: String, limit: Int = 5): List<ContactMatch> {
        val query = Text.tokens(spoken).filterNot { it in FILLER }
        if (query.isEmpty()) return emptyList()
        return indexed
            .mapNotNull { (contact, nameTokens) -> score(query, nameTokens, contact)?.let { ContactMatch(contact, it) } }
            .sortedWith(
                compareByDescending<ContactMatch> { it.score }
                    .thenByDescending { it.contact.onWhatsApp }
                    .thenByDescending { it.contact.lastUsedMs }
                    .thenBy { it.contact.name },
            )
            .take(limit)
    }

    /**
     * True when the best match is clearly ahead of the next one, so the
     * wearer can be spared choosing from a list.
     */
    fun isConfident(matches: List<ContactMatch>): Boolean {
        val best = matches.firstOrNull() ?: return false
        if (best.score < CONFIDENT) return false
        val second = matches.getOrNull(1) ?: return true
        return best.score - second.score >= MARGIN
    }

    /** Good enough to go on without showing a list, when it is the only candidate. */
    fun isStrong(match: ContactMatch): Boolean = match.score >= CONFIDENT

    /**
     * The name step: the wearer was asked for a name only, but people say
     * "oi Beto" or "Beto, tudo bem". Leading greetings and command words are
     * dropped, then the longest run of first words that still names someone
     * wins ("ana paula" before "ana"). Returns what was taken as the name.
     */
    fun findName(utterance: String, limit: Int = 5): Pair<String, List<ContactMatch>> {
        val words = Text.tokens(utterance).dropWhile { it in LEAD_WORDS || it in FILLER }
        for (n in minOf(words.size, MAX_NAME_WORDS) downTo 1) {
            val query = words.take(n).joinToString(" ")
            val found = match(query, limit)
            if (found.isNotEmpty()) return query to found
        }
        return words.joinToString(" ") to emptyList()
    }

    private fun score(query: List<String>, name: List<String>, contact: ZapContact): Double? {
        var total = 0.0
        val used = BooleanArray(name.size)
        for (word in query) {
            var bestIndex = -1
            var best = 0.0
            name.forEachIndexed { index, part ->
                if (used[index]) return@forEachIndexed
                val s = wordScore(word, part)
                if (s > best) {
                    best = s
                    bestIndex = index
                }
            }
            if (bestIndex < 0 || best < WORD_FLOOR) return null
            used[bestIndex] = true
            total += best
        }
        var result = total / query.size
        if (wordScore(query.first(), name.first()) >= 0.99) result += FIRST_NAME_BONUS
        if (contact.onWhatsApp) result += WHATSAPP_BONUS
        if (contact.starred) result += STARRED_BONUS
        return result.coerceAtMost(1.0)
    }

    private fun wordScore(spoken: String, part: String): Double = when {
        spoken == part -> 1.0
        Text.soundKey(spoken) == Text.soundKey(part) -> 0.95
        spoken.length >= 3 && part.startsWith(spoken) -> 0.9
        else -> Text.similarity(spoken, part).let { if (it >= 0.7) it * 0.85 else 0.0 }
    }

    companion object {
        /** Words people say around a name that are never part of it. */
        val FILLER = setOf(
            "o", "a", "os", "as", "pro", "pra", "para", "pros", "pras", "ao", "aos", "com",
            "the", "to", "for", "el", "la", "al", "para",
        )
        /** Said before a name and never part of it: greetings and "send a message to". */
        val LEAD_WORDS = setOf(
            "oi", "ola", "ei", "eai", "e", "ai", "opa", "alo", "bom", "boa", "dia", "tarde", "noite",
            "mensagem", "manda", "mande", "mandar", "envia", "envie", "enviar", "escreve", "escrever",
            "fala", "falar", "uma", "um",
            "hi", "hey", "hello", "send", "message", "text", "write", "a",
            "hola", "mensaje", "manda", "envia",
        )
        private const val MAX_NAME_WORDS = 4
        private const val WORD_FLOOR = 0.55
        private const val CONFIDENT = 0.85
        private const val MARGIN = 0.08
        private const val FIRST_NAME_BONUS = 0.02
        private const val WHATSAPP_BONUS = 0.02
        private const val STARRED_BONUS = 0.01
    }
}
