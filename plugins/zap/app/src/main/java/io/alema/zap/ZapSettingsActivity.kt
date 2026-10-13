package io.alema.zap

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/**
 * Phone-side setup. Each permission says, in one line, what it is for and
 * what Zap does without it — nothing is required to start using it.
 */
class ZapSettingsActivity : Activity() {
    private val pt = Locale.getDefault().language == "pt"
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS) {
            ZapSettings(this).setImportAddressBook(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            render()
        }
    }

    private fun render() {
        column.removeAllViews()
        title(if (pt) "Zap — WhatsApp nos óculos" else "Zap — WhatsApp on the glasses")
        note(
            if (pt) "Comece uma mensagem pelos óculos: abra o Zap no Nexus e diga \"mensagem pro Bruno: tô chegando\". Nada é enviado sem você escolher Enviar."
            else "Start a message from the glasses: open Zap in Nexus and say \"message Bruno: on my way\". Nothing is sent until you choose Send.",
        )

        val whatsapp = ZapSender.whatsAppPackage(this) != null
        status(
            ok = whatsapp,
            label = if (pt) "WhatsApp instalado" else "WhatsApp installed",
            detail = if (whatsapp) null else if (pt) "Instale o WhatsApp neste celular." else "Install WhatsApp on this phone.",
        )

        permission(
            ok = ZapSendService.isNotificationAccessGranted(this),
            label = if (pt) "Aprender quem te escreve" else "Learn who messages you",
            detail = if (pt) "Lê só o nome e o número de quem manda mensagem no WhatsApp, nunca o texto. É assim que a sua lista de pessoas se forma."
            else "Reads only the name and number of who messages you on WhatsApp, never the text. That is how your people list forms.",
            action = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )

        permission(
            ok = ZapSendService.isEnabled(this),
            label = if (pt) "Enviar sem tocar no celular" else "Send without touching the phone",
            detail = if (pt) "Aperta Enviar no WhatsApp depois que você confirma nos óculos. Sem isso, a conversa abre no celular com o texto pronto e você toca Enviar."
            else "Presses Send in WhatsApp after you confirm on the glasses. Without it, the chat opens on the phone with the text ready and you tap Send.",
            action = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )

        permission(
            ok = ZapSender.canStartFromBackground(this),
            label = if (pt) "Funcionar com o celular no bolso" else "Work with the phone in your pocket",
            detail = if (pt) "\"Sobrepor a outros apps\": o Android exige isso para abrir o WhatsApp quando o Zap está em segundo plano."
            else "\"Display over other apps\": Android requires it to open WhatsApp while Zap runs in the background.",
            action = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )

        // Voice: Zap keeps no keys. Dictation and the read-back voice follow
        // the engine the wearer picked in Rokid Nexus; Zap only points there.
        val nexus = packageManager.getLaunchIntentForPackage(NEXUS_PACKAGE)
        val voiceRow = row(
            if (pt) "Voz: ditar e ouvir" else "Voice: dictate and listen",
            if (pt) "O Zap usa a voz configurada no Rokid Nexus. Para entender o que você fala: Settings › Glasses › Speech (teste em \"Try it\"). Para ler as mensagens em voz alta: Settings › Glasses › Voice (teste em \"Hear it\"). Se algum não funcionar, troque ali."
            else "Zap uses the voice set up in Rokid Nexus. To understand what you say: Settings › Glasses › Speech (test it under \"Try it\"). To read messages aloud: Settings › Glasses › Voice (test it with \"Hear it\"). If either does not work, change it there.",
            null,
        )
        if (nexus != null) {
            voiceRow.addView(Button(this).apply {
                text = if (pt) "Abrir o Rokid Nexus" else "Open Rokid Nexus"
                setOnClickListener { runCatching { startActivity(nexus) } }
            })
        }
        column.addView(voiceRow)

        val settings = ZapSettings(this)
        toggle(
            checked = settings.importAddressBook() && ZapContacts(this).hasPermission(),
            label = if (pt) "Incluir a agenda do celular" else "Include the phone's contacts",
            detail = if (pt) "Para escrever para quem ainda não te mandou mensagem. Opcional."
            else "To write to people who have not messaged you yet. Optional.",
        ) { on ->
            if (on && !ZapContacts(this).hasPermission()) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            } else {
                settings.setImportAddressBook(on)
            }
        }

        button(if (pt) "Esquecer as pessoas aprendidas" else "Forget learned people") {
            ZapPeople(this).forgetAll()
            render()
        }
    }

    // --- tiny view kit, green on near-black like Nexus ---------------------

    private fun title(value: String) = column.addView(TextView(this).apply {
        text = value
        textSize = 22f
        setTextColor(GREEN)
        setPadding(0, 0, 0, dp(12))
    })

    private fun note(value: String) = column.addView(TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(TEXT)
        setPadding(0, 0, 0, dp(20))
    })

    private fun status(ok: Boolean, label: String, detail: String?) {
        column.addView(row(label, detail, ok))
    }

    private fun permission(ok: Boolean, label: String, detail: String, action: Intent) {
        val row = row(label, detail, ok)
        if (!ok) {
            row.addView(Button(this).apply {
                text = if (pt) "Permitir" else "Allow"
                setOnClickListener { runCatching { startActivity(action) } }
            })
        }
        column.addView(row)
    }

    private fun toggle(checked: Boolean, label: String, detail: String, onChange: (Boolean) -> Unit) {
        val row = row(label, detail, null)
        row.addView(Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })
        column.addView(row)
    }

    private fun button(label: String, onClick: () -> Unit) = column.addView(Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    })

    private fun row(label: String, detail: String?, ok: Boolean?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(16))
        addView(TextView(this@ZapSettingsActivity).apply {
            text = when (ok) {
                true -> "✓  $label"
                false -> "○  $label"
                null -> label
            }
            textSize = 17f
            setTextColor(if (ok == false) TEXT else GREEN)
        })
        if (detail != null) {
            addView(TextView(this@ZapSettingsActivity).apply {
                text = detail
                textSize = 13f
                setTextColor(DIM)
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_CONTACTS = 7
        const val NEXUS_PACKAGE = "com.anezium.rokidbus.phone"
        val GREEN = Color.parseColor("#71FF97")
        val TEXT = Color.parseColor("#D8F5E0")
        val DIM = Color.parseColor("#8FB39A")
    }
}
