package io.alema.zap.core

/**
 * What the wearer asked for in one sentence.
 *
 * [recipient] is the name as spoken, [matches] the contacts it may mean,
 * [body] the message (null when they only named the person and will dictate
 * the message next).
 */
data class ComposeRequest(
    val recipient: String,
    val matches: List<ContactMatch>,
    val body: String?,
)

/**
 * Understands "mensagem pro Bruno: tô chegando", "manda pro Leo que já vou",
 * "message Sarah saying I'm late", "mensaje para Ana que llego tarde" and the
 * bare "Bruno tô chegando" — in Portuguese, English and Spanish.
 *
 * Speech-to-text rarely gives the colon, so when the sentence has no clear
 * separator the parser asks the address book where the name ends: it tries
 * the longest run of words (up to three) that is confidently a contact, and
 * the rest is the message.
 */
class ComposeParser(private val matcher: ContactMatcher) {

    fun parse(spoken: String): ComposeRequest? {
        val text = spoken.trim().replace(WHITESPACE, " ")
        if (text.isEmpty()) return null
        val rest = stripCommand(text)
        if (rest.isBlank()) return null

        SEPARATED.find(rest)?.let { found ->
            val who = found.groupValues[1].trim()
            val body = found.groupValues[2].trim().ifBlank { null }
            val matches = matcher.match(who)
            if (matches.isNotEmpty()) return ComposeRequest(who, matches, body?.let(::tidy))
        }
        return splitByAddressBook(rest)
    }

    /** Removes "manda uma mensagem pro", "send a message to", "dile a"... in front of the name. */
    private fun stripCommand(text: String): String {
        var rest = text
        COMMAND_PREFIXES.forEach { prefix ->
            val match = prefix.find(rest)
            if (match != null) rest = rest.substring(match.range.last + 1)
        }
        return rest.trim().trimStart(',', ':').trim()
    }

    private fun splitByAddressBook(rest: String): ComposeRequest? {
        val words = rest.split(' ').filter(String::isNotEmpty)
        for (size in minOf(MAX_NAME_WORDS, words.size) downTo 1) {
            val who = words.take(size).joinToString(" ")
            // A name never ends on a filler word: in "Leo tô chegando", "tô"
            // folds to "to", which must start the message, not end the name.
            if (Text.tokens(words[size - 1]).all { it in ContactMatcher.FILLER }) continue
            val matches = matcher.match(who)
            if (matcher.isConfident(matches) || (size == words.size && matches.isNotEmpty())) {
                val body = words.drop(size).joinToString(" ").let(::stripLeadingJoiner).ifBlank { null }
                return ComposeRequest(who, matches, body?.let(::tidy))
            }
        }
        // Nobody matched: still hand back the first word as the name, so the
        // wearer sees "no contact called X" instead of a silent failure.
        val who = words.first()
        return ComposeRequest(who, emptyList(), words.drop(1).joinToString(" ").ifBlank { null }?.let(::tidy))
    }

    private fun stripLeadingJoiner(body: String): String = body.replace(LEADING_JOINER, "").trim()

    private fun tidy(body: String): String = tidyBody(body)

    companion object {
        /** "tô chegando" -> "Tô chegando". Nothing else is rewritten: it is the wearer's message. */
        fun tidyBody(body: String): String {
            val clean = body.trim().trimStart(':', ',', '-').trim()
            if (clean.isEmpty()) return clean
            return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        private const val MAX_NAME_WORDS = 3
        private val WHITESPACE = Regex("\\s+")
        private val OPTIONS = setOf(RegexOption.IGNORE_CASE)

        private val COMMAND_PREFIXES = listOf(
            // Portuguese
            Regex(
                "^(?:(?:manda|mande|envia|envie|escreve|escreva|diz|diga|fala|fale|avisa|avise|pergunta|pergunte)\\s+)?" +
                    "(?:(?:uma|um)\\s+)?(?:mensagem|msg|zap|whats|whatsapp|recado)?\\s*" +
                    "(?:no\\s+(?:zap|whats|whatsapp)\\s+)?(?:pro|pra|para\\s+o|para\\s+a|para|ao|à|o|a)\\s+",
                OPTIONS,
            ),
            // English
            Regex(
                "^(?:(?:send|write|tell|text|message|whatsapp)\\s+)(?:(?:a|an)\\s+)?(?:message\\s+|text\\s+)?(?:to\\s+)?",
                OPTIONS,
            ),
            // Spanish
            Regex(
                "^(?:(?:manda|mándale|mandale|envía|envia|escribe|dile|dí|di|avisa|avísale)\\s+)?" +
                    "(?:un\\s+)?(?:mensaje|whats|whatsapp)?\\s*(?:a|para|al)\\s+",
                OPTIONS,
            ),
        )

        /** Name, then a clear separator, then the message. */
        private val SEPARATED = Regex(
            "^(.+?)(?:\\s*[:,—–-]\\s+|\\s+(?:dizendo|falando|avisando|perguntando|saying|that|diciendo)\\s+(?:que\\s+)?|\\s+que\\s+)(.+)$",
            OPTIONS,
        )

        private val LEADING_JOINER = Regex("^(?:que|dizendo|falando|saying|that|diciendo)\\s+", OPTIONS)
    }
}
