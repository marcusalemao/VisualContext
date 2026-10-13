package io.alema.zap

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusUi
import java.util.Locale

/**
 * Phone-side screen, built with the same kit as Relay so it looks like the
 * rest of Nexus. Order: what Zap is and how to use it, then each access it
 * needs (what it is for, what happens without it), then contacts and voice.
 */
class ZapSettingsActivity : Activity() {
    private val pt = Locale.getDefault().language == "pt"
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
        content = NexusUi.contentColumn(this)
        val root = NexusUi.fixedRoot(this).apply {
            addView(
                NexusUi.pluginHeader(
                    this@ZapSettingsActivity,
                    R.drawable.nexus_glyph_zap,
                    "Zap",
                    t("Nova mensagem no WhatsApp, pela voz", "New WhatsApp messages, by voice"),
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(this@ZapSettingsActivity, content),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
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
        content.removeAllViews()

        section(t("Como usar", "How to use"))
        infoCard(
            t("Escrever para alguém pelos óculos", "Write to someone from the glasses"),
            t(
                "1. Nos óculos, abra o menu do Nexus e escolha Zap.\n" +
                    "2. Toque em \"Ditar mensagem\" e diga para quem e o quê, numa frase só:\n" +
                    "    \"mensagem pro Bruno, tô chegando\".\n" +
                    "3. Confira o texto. Toque em Enviar, ou em Ouvir para escutar antes.\n\n" +
                    "Também dá para escolher alguém da lista e ditar só a mensagem. Nada é enviado sem você tocar em Enviar.",
                "1. On the glasses, open the Nexus menu and pick Zap.\n" +
                    "2. Tap \"Dictate message\" and say who and what, in one sentence:\n" +
                    "    \"message Bruno, on my way\".\n" +
                    "3. Check the text. Tap Send, or Listen to hear it first.\n\n" +
                    "You can also pick someone from the list and dictate only the message. Nothing is sent until you tap Send.",
            ),
        )

        section(t("Acesso", "Access"))
        val whatsapp = ZapSender.whatsAppPackage(this) != null
        if (!whatsapp) {
            infoCard(
                t("WhatsApp não encontrado", "WhatsApp not found"),
                t("Instale o WhatsApp neste celular para usar o Zap.", "Install WhatsApp on this phone to use Zap."),
            )
        }
        val notifications = ZapSendService.isNotificationAccessGranted(this)
        accessCard(
            granted = notifications,
            title = t("Saber quem te escreve", "Know who messages you"),
            body = if (notifications) {
                t("Ligado. Quem te manda mensagem no WhatsApp entra na sua lista do Zap.", "On. People who message you on WhatsApp join your Zap list.")
            } else {
                t(
                    "Monta a sua lista de pessoas a partir de quem te manda mensagem no WhatsApp. Guarda só o nome e o número, nunca o texto.",
                    "Builds your people list from who messages you on WhatsApp. Keeps only the name and number, never the text.",
                )
            },
            intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
        val autoSend = ZapSendService.isEnabled(this)
        accessCard(
            granted = autoSend,
            title = t("Enviar sem pegar o celular", "Send without picking up the phone"),
            body = if (autoSend) {
                t("Ligado. O Zap aperta Enviar no WhatsApp depois que você confirma nos óculos.", "On. Zap presses Send in WhatsApp after you confirm on the glasses.")
            } else {
                t(
                    "Aperta o botão Enviar do WhatsApp por você, depois que você confirma nos óculos. Sem isso, a conversa abre no celular com o texto pronto e você toca Enviar.",
                    "Presses WhatsApp's Send button for you, after you confirm on the glasses. Without it, the chat opens on the phone with the text ready and you tap Send.",
                )
            },
            intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
        val background = ZapSender.canStartFromBackground(this)
        accessCard(
            granted = background,
            title = t("Funcionar com o celular no bolso", "Work with the phone in your pocket"),
            body = if (background) {
                t("Ligado. O Zap consegue abrir o WhatsApp mesmo com o celular guardado.", "On. Zap can open WhatsApp even with the phone put away.")
            } else {
                t(
                    "O Android só deixa o Zap abrir o WhatsApp em segundo plano com a permissão \"Sobrepor a outros apps\".",
                    "Android only lets Zap open WhatsApp in the background with the \"Display over other apps\" permission.",
                )
            },
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
        if (!notifications || !autoSend) {
            infoCard(
                t("O botão está cinza?", "Is the switch greyed out?"),
                t(
                    "O Android bloqueia essas duas permissões em apps instalados por fora da loja. Para liberar: abra os ajustes do Zap no Android, toque nos três pontinhos no canto de cima e escolha \"Permitir configurações restritas\". Depois volte aqui.",
                    "Android blocks these two permissions for apps installed outside the store. To allow them: open Zap's Android app info, tap the three dots at the top and choose \"Allow restricted settings\". Then come back here.",
                ),
                action = t("Abrir ajustes do Zap no Android", "Open Zap's Android app info") to
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        }

        section(t("Pessoas", "People"))
        val settings = ZapSettings(this)
        switchCard(
            title = t("Usar a agenda do celular", "Use the phone's contacts"),
            subtitle = t("Para escrever também para quem ainda não te mandou mensagem", "To also write to people who haven't messaged you yet"),
            checked = settings.importAddressBook() && ZapContacts(this).hasPermission(),
        ) { on ->
            if (on && !ZapContacts(this).hasPermission()) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            } else {
                settings.setImportAddressBook(on)
            }
        }
        gap(8)
        content.addView(
            NexusUi.textButton(this, t("Apagar a lista aprendida", "Clear the learned list"), danger = true).apply {
                setOnClickListener {
                    ZapPeople(this@ZapSettingsActivity).forgetAll()
                    render()
                }
            },
            NexusUi.block(),
        )

        section(t("Voz", "Voice"))
        val nexus = packageManager.getLaunchIntentForPackage(NEXUS_PACKAGE)
        infoCard(
            t("Usa a voz do Rokid Nexus", "Uses Rokid Nexus voice"),
            t(
                "O Zap não tem ajuste de voz próprio. Para entender o que você fala: Nexus › Settings › Speech. Para ler em voz alta: Nexus › Settings › Voice. Se algum não funcionar, teste e troque lá.",
                "Zap has no voice settings of its own. To understand what you say: Nexus › Settings › Speech. To read aloud: Nexus › Settings › Voice. If either doesn't work, test and change it there.",
            ),
            action = nexus?.let { t("Abrir o Rokid Nexus", "Open Rokid Nexus") to it },
        )
        gap(24)
    }

    // --- cards in the Nexus style ------------------------------------------

    private fun section(label: String) {
        if (content.childCount > 0) gap(24)
        content.addView(NexusUi.sectionRow(this, label), NexusUi.block())
        gap(10)
    }

    private fun gap(dp: Int) = content.addView(BusTheme.gap(this, dp))

    private fun infoCard(title: String, body: String, action: Pair<String, Intent>? = null) {
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.cardTitle(this@ZapSettingsActivity, title))
                addView(BusTheme.gap(this@ZapSettingsActivity, 5))
                addView(NexusUi.cardBody(this@ZapSettingsActivity, body))
                if (action != null) {
                    addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                    addView(
                        NexusUi.outlinePillButton(this@ZapSettingsActivity, action.first).apply {
                            setOnClickListener { runCatching { startActivity(action.second) } }
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                }
            },
            NexusUi.block(),
        )
        gap(8)
    }

    private fun accessCard(granted: Boolean, title: String, body: String, intent: Intent) {
        infoCard(
            title = title,
            body = body,
            action = (if (granted) t("Revisar", "Review") else t("Permitir", "Allow")) to intent,
        )
    }

    private fun switchCard(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
        content.addView(
            NexusUi.card(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    LinearLayout(this@ZapSettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(NexusUi.rowTitle(this@ZapSettingsActivity, title))
                        addView(BusTheme.gap(this@ZapSettingsActivity, 4))
                        addView(NexusUi.rowSub(this@ZapSettingsActivity, subtitle))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(NexusUi.switch(this@ZapSettingsActivity).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _, value -> onChanged(value) }
                })
            },
            NexusUi.block(),
        )
    }

    private fun t(portuguese: String, english: String) = if (pt) portuguese else english

    private companion object {
        const val REQUEST_CONTACTS = 7
        const val NEXUS_PACKAGE = "com.anezium.rokidbus.phone"
    }
}
