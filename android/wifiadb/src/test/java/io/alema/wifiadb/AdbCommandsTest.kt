package io.alema.wifiadb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdbCommandsTest {
    private val ok = AdbInfo("192.168.0.42", 41235, 37011, "482915", wifiConnected = true, enabled = true)

    @Test fun commands() {
        assertEquals("adb pair 192.168.0.42:37011 482915", AdbCommands.pair(ok))
        assertEquals("adb connect 192.168.0.42:41235", AdbCommands.connect(ok))
    }

    @Test fun noPairingWithoutCode() {
        assertNull(AdbCommands.pair(ok.copy(pairingCode = null)))
    }

    @Test fun noWifiSaysSo() {
        assertEquals("os óculos estão sem Wi-Fi", AdbCommands.hudLines(ok.copy(wifiConnected = false))[0])
    }

    @Test fun hudShowsBothCommandsAndCode() {
        val l = AdbCommands.hudLines(ok)
        assertTrue(l.any { it.contains("adb pair") })
        assertTrue(l.any { it.contains("adb connect") })
        assertTrue(l.contains("código: 482915"))
    }
}
