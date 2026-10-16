package io.alema.zap.core

/**
 * Reads a contacts file the wearer picked: a vCard (.vcf, what phones and
 * Google Contacts export) or a CSV with a name column and a phone column
 * (Google, Outlook or a hand-made sheet). Only name + number come out; every
 * other field is ignored and nothing is kept from the file itself.
 */
object ContactFile {

    data class Entry(val name: String, val digits: String)

    fun parse(text: String, defaultCountryCode: String = PhoneNumbers.BRAZIL): List<Entry> {
        val clean = text.removePrefix("﻿")
        val entries = if (clean.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) {
            vcard(clean, defaultCountryCode)
        } else {
            csv(clean, defaultCountryCode)
        }
        return entries.distinctBy { it.digits }
    }

    // --- vCard ---------------------------------------------------------------------

    private fun vcard(text: String, cc: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var name: String? = null
        var phones = mutableListOf<String>()
        // vCard folds long lines: a line starting with a space continues the previous one.
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split('\n')
        for (line in lines) {
            val key = line.substringBefore(':').substringBefore(';').uppercase()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "BEGIN" -> { name = null; phones = mutableListOf() }
                "FN" -> name = unescape(value)
                "N" -> if (name.isNullOrBlank()) name = value.split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)) }.filter { it.isNotBlank() }.joinToString(" ")
                "TEL" -> phones += value
                "END" -> {
                    val n = name?.trim().orEmpty()
                    val digits = phones.firstNotNullOfOrNull { PhoneNumbers.toWhatsAppDigits(it, cc) }
                    if (n.isNotEmpty() && digits != null) out += Entry(n, digits)
                }
            }
        }
        return out
    }

    private fun unescape(v: String) = v.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").trim()

    // --- CSV -----------------------------------------------------------------------

    private val NAME_HEADERS = listOf("name", "nome", "display name", "full name", "nome completo", "first name", "given name", "nombre")
    private val LAST_HEADERS = listOf("last name", "family name", "sobrenome", "apellido")
    private val PHONE_HINTS = listOf("phone", "telefone", "celular", "mobile", "whatsapp", "número", "numero", "tel")

    private fun csv(text: String, cc: String): List<Entry> {
        val rows = rows(text).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val nameCol = NAME_HEADERS.firstNotNullOfOrNull { h -> header.indexOf(h).takeIf { it >= 0 } }
        val lastCol = LAST_HEADERS.firstNotNullOfOrNull { h -> header.indexOf(h).takeIf { it >= 0 } }
        // Google's export names columns "Phone 1 - Value"; "Phone 1 - Label" must not count.
        val phoneCols = header.indices.filter { i -> PHONE_HINTS.any { header[i].contains(it) } && !header[i].contains("label") && !header[i].contains("type") }
        val hasHeader = nameCol != null || phoneCols.isNotEmpty()
        val body = if (hasHeader) rows.drop(1) else rows
        return body.mapNotNull { row ->
            val name: String
            val digits: String?
            if (hasHeader) {
                name = listOfNotNull(nameCol?.let(row::getOrNull), lastCol?.takeIf { it != nameCol }?.let(row::getOrNull))
                    .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                digits = phoneCols.mapNotNull(row::getOrNull)
                    .flatMap { it.split(":::") } // Google packs several numbers in one cell
                    .firstNotNullOfOrNull { PhoneNumbers.toWhatsAppDigits(it, cc) }
            } else {
                // No header: "name, phone".
                name = row.getOrNull(0)?.trim().orEmpty()
                digits = row.drop(1).firstNotNullOfOrNull { PhoneNumbers.toWhatsAppDigits(it, cc) }
            }
            if (name.isEmpty() || digits == null) null else Entry(name, digits)
        }
    }

    /** RFC 4180-ish: quoted fields, doubled quotes, commas or semicolons (Excel in pt-BR uses ";"). */
    private fun rows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
