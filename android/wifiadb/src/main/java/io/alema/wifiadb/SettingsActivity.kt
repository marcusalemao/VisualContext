package io.alema.wifiadb

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Phone screen: the two commands from the last time the plugin ran on the glasses, ready to copy. */
class SettingsActivity : Activity() {
    private lateinit var col: LinearLayout
    private val green = Color.rgb(0, 255, 136)
    private val soft = Color.rgb(143, 223, 181)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) // pairing code on screen
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        col.removeAllViews()
        col.addView(label("Instalar pelo Wi-Fi", 26f, green))
        col.addView(label("Instala apps nos óculos a partir do computador, sem cabo. O computador e os óculos precisam estar no mesmo Wi-Fi."))
        col.addView(label("Nos óculos: toque triplo → Nexus → Instalar pelo Wi-Fi. Os comandos aparecem lá e aqui."))
        val pair = Store.pair(this)
        val connect = Store.connect(this)
        if (pair == null && connect == null) {
            col.addView(label("Ainda sem comandos. Abra o plugin nos óculos primeiro.", 14f, green))
        }
        pair?.let { col.addView(label("1ª vez (pareamento, só uma vez por computador; o código vale 2 minutos):")); col.addView(command(it)) }
        if (pair == null && connect != null) col.addView(label("Código de pareamento expirado: nos óculos, toque no plugin para gerar outro.", 12f))
        connect?.let { col.addView(label("Toda vez:")); col.addView(command(it)) }
        col.addView(label("Depois: adb install nome-do-app.apk", 14f))
        col.addView(label("Para desligar: nos óculos, abra o plugin e deslize. A porta muda toda vez que liga.", 12f))
    }

    private fun label(t: String, size: Float = 14f, c: Int = soft) =
        TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 24, 0, 8) }

    private fun command(cmd: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply { text = cmd; textSize = 16f; setTextColor(green); setTextIsSelectable(true) })
        addView(Button(context).apply {
            text = "Copiar"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", cmd))
                Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
