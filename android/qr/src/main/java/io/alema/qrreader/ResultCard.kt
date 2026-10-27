package io.alema.qrreader

import io.alema.qrcore.QrResult

/** What the phone can do with a result, after the wearer taps on the glasses. */
sealed interface PhoneAction {
    val label: String

    data class JoinWifi(val wifi: QrResult.Wifi) : PhoneAction { override val label = "Conectar o celular ao Wi-Fi" }
    data class OpenLink(val url: String) : PhoneAction { override val label = "Abrir o link no celular" }
    data class CopyText(val text: String, override val label: String) : PhoneAction
    data class SaveContact(val contact: QrResult.Contact) : PhoneAction { override val label = "Salvar o contato no celular" }
}

data class Card(
    val title: String,
    val lines: List<String>,
    val footer: String,
    val action: PhoneAction?,
    /** Warnings get a different footer and never offer a one-tap action that could cost money. */
    val warning: Boolean = false,
)

/**
 * Turns a parsed QR into the HUD card. Plain Kotlin, no Android, so it is unit-tested.
 * Text is short on purpose: the glasses show 3 lines.
 */
object ResultCard {
    private const val SEND = "toque: mandar p/ celular · voltar: ler outro"

    fun of(r: QrResult): Card = when (r) {
        is QrResult.Wifi -> Card(
            title = "Wi-Fi",
            lines = listOfNotNull(
                r.ssid,
                r.password?.let { "senha: $it" } ?: "rede aberta, sem senha",
                if (r.hidden) "rede oculta" else null,
            ),
            footer = SEND,
            action = PhoneAction.JoinWifi(r),
        )
        is QrResult.Link -> Card(
            title = "Link",
            lines = listOf(r.host, shorten(r.url)),
            footer = SEND,
            action = PhoneAction.OpenLink(r.url),
        )
        is QrResult.Pix -> pix(r)
        is QrResult.Contact -> Card(
            title = when (r.source) {
                QrResult.Contact.Source.LINKEDIN -> "LinkedIn"
                QrResult.Contact.Source.WHATSAPP -> "WhatsApp"
                else -> "Contato"
            },
            lines = listOfNotNull(
                r.name ?: r.urls.firstOrNull()?.let(::shorten) ?: r.phones.firstOrNull(),
                listOfNotNull(r.title, r.org).joinToString(" · ").ifBlank { null },
                r.phones.firstOrNull()?.takeIf { r.name != null } ?: r.emails.firstOrNull(),
            ),
            footer = SEND,
            // A bare LinkedIn link has nothing to save yet: open the profile instead.
            action = if (r.source == QrResult.Contact.Source.LINKEDIN && r.name == null && r.urls.isNotEmpty())
                PhoneAction.OpenLink(r.urls.first()) else PhoneAction.SaveContact(r),
        )
        is QrResult.Text -> Card(
            title = "Texto",
            lines = wrap(r.text, 3),
            footer = SEND,
            action = PhoneAction.CopyText(r.text, "Copiar o texto no celular"),
        )
        is QrResult.Invalid -> Card(
            title = if (r.kind == "pix") "⚠ Pix inválido" else "⚠ QR inválido",
            lines = listOf(
                when (r.reason) {
                    "CRC mismatch" -> "o código foi alterado"
                    "CRC not at the end", "missing CRC" -> "código Pix incompleto"
                    "missing SSID" -> "Wi-Fi sem nome de rede"
                    "missing password" -> "Wi-Fi sem senha"
                    else -> "não dá para confiar"
                },
                if (r.kind == "pix") "não pague por este código" else "peça outro código",
            ),
            footer = "voltar: ler outro",
            action = null,
            warning = true,
        )
    }

    private fun pix(r: QrResult.Pix): Card {
        val who = r.merchantName?.trim()?.ifBlank { null } ?: "sem nome"
        val amount = r.amount?.let { money(it) } ?: "valor: você digita"
        return Card(
            title = "Pix",
            lines = listOf(
                "o código diz: $who",
                listOfNotNull(amount, r.merchantCity?.trim()?.ifBlank { null }).joinToString(" · "),
                if (r.isDynamic) "cobrança online: confira no app do banco" else "confira o nome no app do banco",
            ),
            footer = "toque: copiar Pix no celular · voltar: ler outro",
            // Never pays: only copies "Pix Copia e Cola" so the bank app shows the real payee.
            action = PhoneAction.CopyText(r.raw, "Copiar o Pix Copia e Cola"),
        )
    }

    /** "2.50" → "R$ 2,50"; anything odd is shown as it came. */
    fun money(raw: String): String {
        val v = raw.trim().toBigDecimalOrNull() ?: return "R$ $raw"
        val s = v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        val (int, dec) = s.split('.').let { it[0] to it.getOrElse(1) { "00" } }
        val grouped = int.reversed().chunked(3).joinToString(".").reversed()
        return "R$ $grouped,$dec"
    }

    private fun shorten(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").let { if (it.length > 34) it.take(33) + "…" else it }

    private fun wrap(text: String, max: Int): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > 30) { out += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
            if (out.size == max) break
        }
        if (out.size < max && cur.isNotEmpty()) out += cur.toString()
        return out.take(max)
    }
}
