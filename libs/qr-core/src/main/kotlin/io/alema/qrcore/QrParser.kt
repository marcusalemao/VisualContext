package io.alema.qrcore

import java.net.URI

object QrParser {

    fun parse(raw: String): QrResult {
        val s = raw.trim()
        if (s.isEmpty()) return QrResult.Text("")
        return when {
            s.startsWith("WIFI:", ignoreCase = true) -> parseWifi(s)
            s.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVcard(s)
            s.startsWith("MECARD:", ignoreCase = true) -> parseMecard(s)
            s.startsWith("000201") -> parsePix(s)
            else -> parseUrlOrText(s)
        }
    }

    // ---------- Wi-Fi: WIFI:T:WPA;S:name;P:pass;H:true;; ----------

    private fun parseWifi(s: String): QrResult {
        val fields = splitUnescaped(s.substring(5), ';')
            .mapNotNull { f ->
                val i = indexOfUnescaped(f, ':')
                if (i <= 0) null else f.substring(0, i).uppercase() to unescape(f.substring(i + 1))
            }.toMap()
        val ssid = fields["S"].orEmpty()
        if (ssid.isEmpty()) return QrResult.Invalid("wifi", "missing SSID", s)
        val pass = fields["P"]?.takeIf { it.isNotEmpty() }
        val t = fields["T"].orEmpty().uppercase()
        val security = when {
            t == "NOPASS" -> QrResult.Wifi.Security.OPEN
            t == "WEP" -> QrResult.Wifi.Security.WEP
            t == "SAE" || t == "WPA3" -> QrResult.Wifi.Security.SAE
            t.contains("EAP") || fields.containsKey("E") -> QrResult.Wifi.Security.EAP
            t.startsWith("WPA") -> QrResult.Wifi.Security.WPA
            pass == null -> QrResult.Wifi.Security.OPEN
            else -> QrResult.Wifi.Security.WPA
        }
        if (security != QrResult.Wifi.Security.OPEN && pass == null && security != QrResult.Wifi.Security.EAP)
            return QrResult.Invalid("wifi", "missing password", s)
        return QrResult.Wifi(
            ssid = ssid,
            password = if (security == QrResult.Wifi.Security.OPEN) null else pass,
            security = security,
            hidden = fields["H"].equals("true", ignoreCase = true),
        )
    }

    private fun splitUnescaped(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { cur.append(c).append(s[i + 1]); i += 2; continue }
            if (c == sep) { out += cur.toString(); cur.clear() } else cur.append(c)
            i++
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.filter { it.isNotEmpty() }
    }

    private fun indexOfUnescaped(s: String, ch: Char): Int {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == ch) return i
            i++
        }
        return -1
    }

    private fun unescape(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) { b.append(s[i + 1]); i += 2 } else { b.append(s[i]); i++ }
        }
        return b.toString()
    }

    // ---------- Pix BR Code (EMV MPM, tag-length-value) ----------

    private fun parsePix(s: String): QrResult {
        val tags = readTlv(s) ?: return QrResult.Invalid("pix", "malformed TLV", s)
        val crcTag = tags["63"] ?: return QrResult.Invalid("pix", "missing CRC", s)
        val crcStart = s.lastIndexOf("6304")
        if (crcStart < 0 || crcStart + 8 != s.length) return QrResult.Invalid("pix", "CRC not at the end", s)
        val expected = crc16(s.substring(0, crcStart + 4))
        if (!crcTag.equals(expected, ignoreCase = true)) return QrResult.Invalid("pix", "CRC mismatch", s)

        val account = (26..51).asSequence()
            .mapNotNull { tags[it.toString()] }
            .mapNotNull { readTlv(it) }
            .firstOrNull { it["00"].equals("br.gov.bcb.pix", ignoreCase = true) }
            ?: return QrResult.Invalid("pix", "not a Pix code", s)
        val additional = tags["62"]?.let { readTlv(it) }
        val dyn = account["25"]
        return QrResult.Pix(
            key = account["01"],
            dynamicUrl = dyn?.let { if (it.startsWith("http")) it else "https://$it" },
            amount = tags["54"],
            merchantName = tags["59"],
            merchantCity = tags["60"],
            txid = additional?.get("05")?.takeIf { it != "***" },
            description = account["02"],
            raw = s,
        )
    }

    /** Reads one level of EMV TLV into a map (id -> value). Null when lengths don't add up. */
    internal fun readTlv(s: String): Map<String, String>? {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val id = s.substring(i, i + 2)
            val len = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (i + 4 + len > s.length) return null
            out[id] = s.substring(i + 4, i + 4 + len)
            i += 4 + len
        }
        return out
    }

    /** CRC16/CCITT-FALSE (poly 0x1021, init 0xFFFF), as the Pix standard requires. Uppercase hex. */
    fun crc16(data: String): String {
        var crc = 0xFFFF
        for (byte in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return "%04X".format(crc)
    }

    // ---------- Contacts ----------

    private fun parseVcard(s: String): QrResult {
        val lines = s.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .fold(mutableListOf<String>()) { acc, line ->
                if ((line.startsWith(" ") || line.startsWith("\t")) && acc.isNotEmpty())
                    acc[acc.size - 1] = acc.last() + line.substring(1)
                else acc += line
                acc
            }
        var fn: String? = null; var n: String? = null; var org: String? = null; var title: String? = null
        val phones = mutableListOf<String>(); val emails = mutableListOf<String>(); val urls = mutableListOf<String>()
        for (line in lines) {
            val colon = line.indexOf(':'); if (colon <= 0) continue
            val key = line.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase()
            val value = vUnescape(line.substring(colon + 1)).trim()
            if (value.isEmpty()) continue
            when (key) {
                "FN" -> fn = value
                "N" -> n = value.split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)) }
                    .filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
                "TEL" -> phones += value.removePrefix("tel:")
                "EMAIL" -> emails += value
                "ORG" -> org = value.split(';').first().ifBlank { null }
                "TITLE" -> title = value
                "URL" -> urls += value
            }
        }
        return QrResult.Contact(fn ?: n, phones, emails, org, title, urls, QrResult.Contact.Source.VCARD)
    }

    private fun vUnescape(v: String) =
        v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseMecard(s: String): QrResult {
        val fields = splitUnescaped(s.substring(7), ';').mapNotNull { f ->
            val i = indexOfUnescaped(f, ':'); if (i <= 0) null else f.substring(0, i).uppercase() to unescape(f.substring(i + 1))
        }
        val name = fields.firstOrNull { it.first == "N" }?.second?.let { n ->
            val parts = n.split(','); if (parts.size == 2) "${parts[1].trim()} ${parts[0].trim()}".trim() else n
        }
        return QrResult.Contact(
            name = name,
            phones = fields.filter { it.first == "TEL" }.map { it.second },
            emails = fields.filter { it.first == "EMAIL" }.map { it.second },
            org = fields.firstOrNull { it.first == "ORG" }?.second,
            title = null,
            urls = fields.filter { it.first == "URL" }.map { it.second },
            source = QrResult.Contact.Source.MECARD,
        )
    }

    // ---------- Links ----------

    private fun parseUrlOrText(s: String): QrResult {
        val uri = runCatching { URI(s) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return QrResult.Text(s)
        val host = uri.host.lowercase().removePrefix("www.")
        val path = uri.path.orEmpty()
        if ((host == "linkedin.com" || host.endsWith(".linkedin.com")) && path.startsWith("/in/"))
            return QrResult.Contact(null, emptyList(), emptyList(), null, null, listOf(s), QrResult.Contact.Source.LINKEDIN)
        if (host == "wa.me") {
            val phone = path.trim('/').takeIf { p -> p.isNotEmpty() && p.all { it.isDigit() } }
            if (phone != null)
                return QrResult.Contact(null, listOf("+$phone"), emptyList(), null, null, listOf(s), QrResult.Contact.Source.WHATSAPP)
        }
        return QrResult.Link(s, host)
    }
}
