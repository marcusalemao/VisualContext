package io.alema.qrreader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import io.alema.qrcore.QrResult

/** Opened from the phone notification. Does exactly the one thing, then closes. */
class ActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val x = intent
        runCatching {
            when (x.getStringExtra(PhoneHandoff.EXTRA_KIND)) {
                "wifi" -> joinWifi(x)
                "link" -> x.getStringExtra(PhoneHandoff.EXTRA_URL)?.let { open(it) }
                "copy" -> copy(x.getStringExtra(PhoneHandoff.EXTRA_TEXT).orEmpty(), "Copiado. Cole no app do banco ou onde quiser.")
                "contact" -> saveContact(x)
            }
        }.onFailure { Toast.makeText(this, "Não deu para abrir: ${it.message}", Toast.LENGTH_LONG).show() }
        finish()
    }

    private fun joinWifi(x: Intent) {
        val ssid = x.getStringExtra(PhoneHandoff.EXTRA_SSID) ?: return
        val pass = x.getStringExtra(PhoneHandoff.EXTRA_PASS)
        val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (x.getBooleanExtra(PhoneHandoff.EXTRA_HIDDEN, false)) b.setIsHiddenSsid(true)
        when (PhoneHandoff.securityOf(x.getStringExtra(PhoneHandoff.EXTRA_SEC))) {
            QrResult.Wifi.Security.OPEN -> Unit
            QrResult.Wifi.Security.SAE -> pass?.let { b.setWpa3Passphrase(it) }
            QrResult.Wifi.Security.WEP, QrResult.Wifi.Security.EAP -> {
                // Android has no simple add-network path for these: copy the password and open Wi-Fi settings.
                pass?.let { copy(it, "Senha copiada. Escolha a rede \"$ssid\" e cole.") }
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                return
            }
            QrResult.Wifi.Security.WPA -> pass?.let { b.setWpa2Passphrase(it) }
        }
        // The system asks "Save this network?": the person confirms on the phone, the app never joins by itself.
        val list = arrayListOf(b.build())
        startActivity(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list))
    }

    private fun open(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun copy(text: String, toast: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Leitor QR", text))
        Toast.makeText(this, toast, Toast.LENGTH_LONG).show()
    }

    private fun saveContact(x: Intent) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            x.getStringExtra(PhoneHandoff.EXTRA_NAME)?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            x.getStringExtra(PhoneHandoff.EXTRA_PHONE)?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            x.getStringExtra(PhoneHandoff.EXTRA_EMAIL)?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            x.getStringExtra(PhoneHandoff.EXTRA_ORG)?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            x.getStringExtra(PhoneHandoff.EXTRA_TITLE)?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
            x.getStringExtra(PhoneHandoff.EXTRA_URL)?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
        }
        startActivity(i)
    }
}
