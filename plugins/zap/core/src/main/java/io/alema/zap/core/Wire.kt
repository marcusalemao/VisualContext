package io.alema.zap.core

/**
 * The standalone link between the phone and Zap's own glasses app.
 *
 * The phone keeps all the logic; the glasses app is a plain screen that draws
 * what it is sent and sends back what the wearer did. Both ends speak small
 * JSON lines (one message per line, versioned), so the link can be Bluetooth,
 * Wi-Fi or anything that carries text. Photos travel separately as bytes,
 * named by [Screen.Picture.photoKey].
 *
 * Pure Kotlin with its own tiny JSON reader, so the same file runs on the
 * phone, the glasses and in tests.
 */
object Wire {
    const val VERSION = 1

    /** What the wearer did on the glasses. */
    sealed interface Input {
        object Tap : Input
        object Back : Input
        /** Swipe forward / backward. */
        data class Move(val delta: Int) : Input
        /** Speech the glasses recognised themselves (when the phone isn't the one listening). */
        data class Said(val text: String, val final: Boolean) : Input
        /** The glasses want a photo's bytes. */
        data class NeedPhoto(val key: String) : Input
    }

    // --- phone → glasses ---------------------------------------------------------

    fun encode(screen: Screen): String = screenJson(screen).json

    private fun screenJson(screen: Screen): Raw = when (screen) {
        is Screen.Card -> obj(
            "v" to VERSION, "t" to "card", "title" to screen.title, "sub" to screen.subtitle,
            "back" to screen.handlesBack,
            "rows" to screen.rows.map { row ->
                obj("x" to row.text, "s" to row.sub, "b" to row.badge, "tone" to row.tone.name, "sel" to row.selected.takeIf { it })
            },
        )
        is Screen.Reader -> obj(
            "v" to VERSION, "t" to "reader", "title" to screen.title, "foot" to screen.footer, "key" to screen.contentKey,
            "seg" to screen.segments.map { seg -> obj("k" to seg.kind.name, "x" to seg.text, "e" to seg.emphasis.takeIf { it }) },
        )
        is Screen.Picture -> obj(
            "v" to VERSION, "t" to "pic", "key" to screen.photoKey, "title" to screen.title,
            "cap" to screen.caption, "foot" to screen.footer, "missing" to screenJson(screen.missing),
        )
    }

    fun decodeScreen(line: String): Screen? = runCatching {
        val m = Json.parse(line) as Map<*, *>
        if ((m["v"] as? Number)?.toInt() != VERSION) return null
        screenOf(m)
    }.getOrNull()

    private fun screenOf(m: Map<*, *>): Screen? = when (m["t"]) {
        "card" -> Screen.Card(
            title = m.str("title") ?: "",
            rows = (m["rows"] as? List<*>).orEmpty().mapNotNull { r ->
                (r as? Map<*, *>)?.let {
                    Row(
                        text = it.str("x") ?: "",
                        sub = it.str("s"),
                        badge = it.str("b"),
                        tone = runCatching { Tone.valueOf(it.str("tone") ?: "NORMAL") }.getOrDefault(Tone.NORMAL),
                        selected = it["sel"] == true,
                    )
                }
            },
            subtitle = m.str("sub"),
            handlesBack = m["back"] != false,
        )
        "reader" -> Screen.Reader(
            title = m.str("title") ?: "",
            segments = (m["seg"] as? List<*>).orEmpty().mapNotNull { s ->
                (s as? Map<*, *>)?.let {
                    val kind = runCatching { Segment.Kind.valueOf(it.str("k") ?: "") }.getOrNull() ?: return@let null
                    Segment(kind, it.str("x") ?: "", it["e"] == true)
                }
            },
            footer = m.str("foot"),
            contentKey = m.str("key") ?: "",
        )
        "pic" -> m.str("key")?.let { key ->
            val missing = (m["missing"] as? Map<*, *>)?.let(::screenOf) as? Screen.Card ?: Screen.Card("", emptyList())
            Screen.Picture(
                photoKey = key,
                title = m.str("title") ?: "",
                caption = m.str("cap"),
                footer = m.str("foot"),
                missing = missing,
            )
        }
        else -> null
    }

    // --- glasses → phone ---------------------------------------------------------

    fun encode(input: Input): String = inputJson(input).json

    private fun inputJson(input: Input): Raw = when (input) {
        Input.Tap -> obj("v" to VERSION, "in" to "tap")
        Input.Back -> obj("v" to VERSION, "in" to "back")
        is Input.Move -> obj("v" to VERSION, "in" to "move", "d" to input.delta)
        is Input.Said -> obj("v" to VERSION, "in" to "said", "x" to input.text, "final" to input.final)
        is Input.NeedPhoto -> obj("v" to VERSION, "in" to "photo", "key" to input.key)
    }

    fun decodeInput(line: String): Input? = runCatching {
        val m = Json.parse(line) as Map<*, *>
        if ((m["v"] as? Number)?.toInt() != VERSION) return null
        when (m["in"]) {
            "tap" -> Input.Tap
            "back" -> Input.Back
            "move" -> Input.Move((m["d"] as? Number)?.toInt()?.coerceIn(-1, 1) ?: 0)
            "said" -> Input.Said(m.str("x") ?: "", m["final"] == true)
            "photo" -> m.str("key")?.let { Input.NeedPhoto(it) }
            else -> null
        }
    }.getOrNull()

    // --- tiny JSON ---------------------------------------------------------------

    private fun Map<*, *>.str(key: String) = this[key] as? String

    /** Already-encoded JSON to embed as is. */
    private class Raw(val json: String)

    /** A JSON object; null values are left out to keep lines short. */
    private fun obj(vararg pairs: Pair<String, Any?>): Raw =
        Raw(pairs.filter { it.second != null }.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${value(v)}" })

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is String -> quote(v)
        is Boolean, is Int, is Long -> v.toString()
        is List<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> quote(v.toString())
    }

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    /** Just enough JSON to read what [encode] writes (and ordinary JSON besides). */
    internal object Json {
        fun parse(text: String): Any? = Reader(text).run { val v = value(); skip(); require(i == s.length); v }

        private class Reader(val s: String) {
            var i = 0
            fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun value(): Any? {
                skip()
                require(i < s.length)
                return when (val c = s[i]) {
                    '{' -> obj()
                    '[' -> arr()
                    '"' -> str()
                    't' -> lit("true", true)
                    'f' -> lit("false", false)
                    'n' -> lit("null", null)
                    else -> if (c == '-' || c.isDigit()) num() else error("bad json at $i")
                }
            }
            fun lit(word: String, v: Any?): Any? { require(s.startsWith(word, i)); i += word.length; return v }
            fun num(): Number {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
                val t = s.substring(start, i)
                return t.toLongOrNull() ?: t.toDouble()
            }
            fun str(): String {
                require(s[i] == '"'); i++
                val b = StringBuilder()
                while (true) {
                    val c = s[i++]
                    when (c) {
                        '"' -> return b.toString()
                        '\\' -> when (val e = s[i++]) {
                            'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e)
                        }
                        else -> b.append(c)
                    }
                }
            }
            fun arr(): List<Any?> {
                i++; val out = mutableListOf<Any?>(); skip()
                if (s[i] == ']') { i++; return out }
                while (true) {
                    out += value(); skip()
                    when (s[i++]) { ',' -> continue; ']' -> return out; else -> error("bad array") }
                }
            }
            fun obj(): Map<String, Any?> {
                i++; val out = linkedMapOf<String, Any?>(); skip()
                if (s[i] == '}') { i++; return out }
                while (true) {
                    skip(); val k = str(); skip(); require(s[i++] == ':')
                    out[k] = value(); skip()
                    when (s[i++]) { ',' -> continue; '}' -> return out; else -> error("bad object") }
                }
            }
        }
    }
}
