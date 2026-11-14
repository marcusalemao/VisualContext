package io.alema.qrreader

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Phone screen: what the plugin does, and the one permission it needs (notifications). */
class SettingsActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val green = Color.rgb(0, 255, 136)
        val soft = Color.rgb(143, 223, 181)
        fun label(t: String, size: Float = 14f, c: Int = soft) =
            TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 24, 0, 8) }
        status = label("", 14f, green)
        val allow = Button(this).apply {
            text = "Permitir notificações"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(label("Leitor QR", 26f, green))
            addView(label("Plugin do Rokid Nexus. Olhe para um QR code com os óculos: Wi-Fi, link, Pix ou contato aparecem no visor."))
            addView(label("Toque nos óculos para mandar para o celular. Aqui chega uma notificação; tocando nela, o celular entra no Wi-Fi (você confirma), abre o link, copia o Pix ou salva o contato."))
            addView(label("Pix: o app nunca paga. Ele só copia o Pix Copia e Cola, para o app do banco mostrar quem recebe de verdade."))
            addView(label("Privacidade: as fotos são lidas na memória do celular e descartadas na hora. O app não tem permissão de internet."))
            addView(allow)
            addView(status)
            addView(label("Nos óculos: toque triplo → Nexus → Leitor QR. Voltar = ler outro / fechar.", 12f))
        }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (PhoneHandoff.canNotify(this)) "● notificações liberadas" else "○ notificações bloqueadas: toque em Permitir"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onResume()
    }
}
