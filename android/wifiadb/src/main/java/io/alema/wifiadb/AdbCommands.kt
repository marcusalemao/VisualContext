package io.alema.wifiadb

/**
 * What the wearer types on the computer. Plain Kotlin (no Android), so it is unit-tested.
 * The glasses' Wi-Fi debugging gives two ports: one only for pairing (with a 6-digit code, first time only)
 * and one for connecting. Both change every time debugging is turned on.
 */
data class AdbInfo(
    val host: String?,
    val connectPort: Int?,
    val pairingPort: Int?,
    val pairingCode: String?,
    val wifiConnected: Boolean,
    val enabled: Boolean,
    val error: String? = null,
)

object AdbCommands {
    fun pair(i: AdbInfo): String? =
        if (i.host != null && i.pairingPort != null && !i.pairingCode.isNullOrBlank()) "adb pair ${i.host}:${i.pairingPort} ${i.pairingCode}" else null

    fun connect(i: AdbInfo): String? =
        if (i.host != null && i.connectPort != null) "adb connect ${i.host}:${i.connectPort}" else null

    /** HUD lines: short, numbered, the code on its own so it is easy to read aloud. */
    fun hudLines(i: AdbInfo): List<String> = when {
        i.error != null -> listOf("não deu para ligar", i.error.take(34), "toque: tentar de novo")
        !i.wifiConnected -> listOf("os óculos estão sem Wi-Fi", "ligue o Wi-Fi dos óculos", "no mesmo Wi-Fi do computador")
        !i.enabled -> listOf("ligando a depuração…")
        else -> listOfNotNull(
            pair(i)?.let { "1ª vez: $it" },
            connect(i)?.let { "depois: $it" },
            i.pairingCode?.let { "código: $it" },
            "comandos também no celular",
        )
    }
}
