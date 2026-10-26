package io.alema.visualcontext

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Phone screen. The user never types a token: in the VisualContext Manager they tap
 * "Conectar óculos", which opens visualcontext://conectar?t=TOKEN on this phone
 * (or shows it as a QR on a computer). Here they only see the status.
 * Developer mode (long-press the title) keeps the manual fields for us.
 */
class SettingsActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val green = Color.rgb(0, 255, 136)
    private val soft = Color.rgb(143, 223, 181)
    private lateinit var col: LinearLayout
    private var dev = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(intent: Intent?) {
        val token = DeepLink.token(intent?.data)
        if (token == null) { render(); return }
        intent?.data = null
        AlertDialog.Builder(this)
            .setTitle("Conectar os óculos")
            .setMessage("Ligar este celular à sua memória do VisualContext?")
            .setPositiveButton("Conectar") { _, _ -> Backend.save(this, Backend.url(this), token); render() }
            .setNegativeButton("Cancelar") { _, _ -> render() }
            .show()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(label("VisualContext", 26f, green).apply {
            setOnLongClickListener { dev = !dev; render(); true }
        })
        val status = label("verificando…", 16f, green)
        if (Backend.token(this).isEmpty()) {
            col.addView(label("Ainda não conectado.", 16f, green))
            col.addView(label("No VisualContext (site ou app), toque em \"Conectar óculos\". Este celular se conecta sozinho."))
        } else {
            col.addView(status)
            check(status)
            col.addView(Button(this).apply {
                text = "Desconectar"
                setOnClickListener { Backend.save(this@SettingsActivity, Backend.url(context), ""); render() }
            })
        }
        col.addView(Button(this).apply {
            text = "Abrir o VisualContext"
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DeepLink.MANAGER))) }
        })
        col.addView(label("Nos óculos: toque triplo → Nexus → VisualContext.", 12f))
        if (dev) developer()
    }

    private fun check(status: TextView) = io.execute {
        val r = Backend.people(applicationContext)
        runOnUiThread {
            status.text = when (r) {
                is Backend.Result.People -> "● conectado · ${r.count} pessoas na memória"
                is Backend.Result.Failed -> "○ sem conexão agora (${r.reason})"
            }
        }
    }

    /** Hidden: manual token and backend address, for testing only. */
    private fun developer() {
        col.addView(label("Modo desenvolvedor", 14f, green))
        val url = field(Backend.url(this), false)
        val token = field(Backend.token(this), true)
        col.addView(label("Token do aparelho", 12f)); col.addView(token)
        col.addView(label("Endereço do backend", 12f)); col.addView(url)
        col.addView(Button(this).apply {
            text = "Salvar"
            setOnClickListener { Backend.save(this@SettingsActivity, url.text.toString(), token.text.toString()); render() }
        })
    }

    private fun label(t: String, size: Float = 14f, c: Int = soft) =
        TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 24, 0, 8) }

    private fun field(v: String, secret: Boolean) = EditText(this).apply {
        setText(v); setTextColor(green); textSize = 14f; isSingleLine = true
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
}
