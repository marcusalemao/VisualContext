package io.alema.visualcontext

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Phone-side settings: backend address + device token, and a connection test. */
class SettingsActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val green = Color.rgb(0, 255, 136)
        val soft = Color.rgb(143, 223, 181)
        fun label(t: String, size: Float = 14f, c: Int = soft) = TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 24, 0, 8) }
        fun field(v: String, secret: Boolean) = EditText(this).apply {
            setText(v); setTextColor(green); setHintTextColor(soft); textSize = 14f; isSingleLine = true
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val url = field(Backend.url(this), false)
        val token = field(Backend.token(this), true).apply { hint = "VC_DEVICE_TOKEN" }
        val status = label("", 14f, green)
        val save = Button(this).apply {
            text = "Salvar e testar"
            setOnClickListener {
                Backend.save(this@SettingsActivity, url.text.toString(), token.text.toString())
                status.text = "testando…"
                io.execute {
                    val r = Backend.people(applicationContext)
                    runOnUiThread {
                        status.text = when (r) {
                            is Backend.Result.People -> "● conectado — ${r.count} pessoas"
                            is Backend.Result.Failed -> "○ ${r.reason}"
                        }
                    }
                }
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(label("VisualContext", 26f, green))
            addView(label("Plugin do Rokid Nexus · v0.1 — mostra um card de teste nos óculos com a sua memória."))
            addView(label("Token do aparelho"))
            addView(token)
            addView(label("Endereço do backend"))
            addView(url)
            addView(save)
            addView(status)
            addView(label("Nos óculos: abra o Nexus (toque triplo) → VisualContext. Toque = atualizar, voltar = fechar.", 12f))
        }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        col.gravity = Gravity.TOP
    }
}
