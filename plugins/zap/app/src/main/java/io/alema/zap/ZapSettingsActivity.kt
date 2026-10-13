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

    private val settings by lazy { ZapSettings(this) }

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
            t("Seu WhatsApp nos óculos", "Your WhatsApp on the glasses"),
            t(
                "Nos óculos, abra o menu do Nexus e escolha Zap. Ele abre nas suas conversas, como o WhatsApp no celular.\n\n" +
                    "• Toque numa conversa para ler. Ali estão Responder e Ouvir de novo.\n" +
                    "• Nova mensagem, no topo: escolha alguém recente ou toque em \"Dizer o nome\" e fale, por exemplo, \"mensagem pro Bruno, tô chegando\".\n" +
                    "• Antes de sair, o texto aparece para você conferir. Nada é enviado sem você tocar em Enviar.\n\n" +
                    "As conversas aparecem conforme as mensagens chegam com o Zap ligado. Mensagens que você manda para você mesmo não contam: o WhatsApp não avisa delas.",
                "On the glasses, open the Nexus menu and pick Zap. It opens on your chats, like WhatsApp on the phone.\n\n" +
                    "• Tap a chat to read it. Reply and Listen again are there.\n" +
                    "• New message, at the top: pick someone recent or tap \"Say the name\" and say, for example, \"message Bruno, on my way\".\n" +
                    "• Before it goes, the text is shown for you to check. Nothing is sent until you tap Send.\n\n" +
                    "Chats appear as messages arrive while Zap is on. Messages you send to yourself don't count: WhatsApp doesn't notify about them.",
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
        if (notifications) {
            val health = ZapHealth(this)
            val chats = ZapInbox.list(this).size
            infoCard(
                t("Conferência", "Check"),
                when {
                    health.lastSeen() == 0L -> t(
                        "Nenhuma notificação do WhatsApp chegou ao Zap ainda. Peça para alguém te mandar uma mensagem (mensagens para você mesmo não geram notificação). Se mesmo assim nada aparecer, desligue e ligue de novo \"Saber quem te escreve\".",
                        "No WhatsApp notification has reached Zap yet. Ask someone to message you (messages to yourself don't notify). If still nothing, turn \"Know who messages you\" off and on again.",
                    )
                    health.lastCaptured() == 0L -> t(
                        "O WhatsApp avisou o Zap às ${time(health.lastSeen())}, mas o Zap não conseguiu ler a mensagem. Me mande um print desta tela.",
                        "WhatsApp notified Zap at ${time(health.lastSeen())}, but Zap couldn't read the message. Send me a screenshot of this screen.",
                    ) + (health.lastError()?.let { "\n\n($it)" } ?: "")
                    else -> t(
                        "Funcionando. Última mensagem às ${time(health.lastCaptured())}; $chats conversa(s) guardada(s).",
                        "Working. Last message at ${time(health.lastCaptured())}; $chats chat(s) saved.",
                    )
                },
            )
        }
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

        section(t("Conversas", "Chats"))
        switchCard(
            title = t("Avisar nos óculos quando chegar", "Show a notice when a message arrives"),
            subtitle = t(
                "Quem escreveu e o começo da mensagem, por 4 segundos. Se o Relay também avisa o WhatsApp, desligue um dos dois.",
                "Who wrote and the start of the message, for 4 seconds. If Relay also shows WhatsApp, turn one of them off.",
            ),
            checked = settings.announceArrivals(),
        ) { settings.setAnnounceArrivals(it) }
        gap(8)
        choiceCard(
            title = t("Guardar mensagens por", "Keep messages for"),
            subtitle = t("Só neste celular, criptografadas", "Only on this phone, encrypted"),
            options = listOf(1, 7, 30),
            label = { if (it == 1) t("1 dia", "1 day") else t("$it dias", "$it days") },
            current = settings.keepDays(),
        ) { settings.setKeepDays(it) }
        choiceCard(
            title = t("Mostrar até", "Show up to"),
            subtitle = t("Conversas na lista dos óculos", "Chats in the glasses list"),
            options = listOf(20, 40, 60),
            label = { t("$it conversas", "$it chats") },
            current = settings.maxConversations(),
        ) { settings.setMaxConversations(it) }
        content.addView(
            NexusUi.textButton(this, t("Apagar as conversas guardadas", "Delete saved chats"), danger = true).apply {
                setOnClickListener {
                    ZapInbox.clear(this@ZapSettingsActivity)
                    render()
                }
            },
            NexusUi.block(),
        )

        section(t("Pessoas", "People"))
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

    /** A row of choices; the current one is the filled pill, like Nexus's own speed picker. */
    private fun choiceCard(
        title: String,
        subtitle: String,
        options: List<Int>,
        label: (Int) -> String,
        current: Int,
        onPick: (Int) -> Unit,
    ) {
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.rowTitle(this@ZapSettingsActivity, title))
                addView(BusTheme.gap(this@ZapSettingsActivity, 4))
                addView(NexusUi.rowSub(this@ZapSettingsActivity, subtitle))
                addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                addView(
                    LinearLayout(this@ZapSettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        options.forEach { value ->
                            val button = if (value == current) {
                                NexusUi.pillButton(this@ZapSettingsActivity, label(value))
                            } else {
                                NexusUi.outlinePillButton(this@ZapSettingsActivity, label(value))
                            }
                            button.setOnClickListener {
                                onPick(value)
                                render()
                            }
                            addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginEnd = NexusUi.dp(this@ZapSettingsActivity, 6)
                            })
                        }
                    },
                )
            },
            NexusUi.block(),
        )
        gap(8)
    }

    private fun time(ms: Long): String =
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))

    private fun t(portuguese: String, english: String) = if (pt) portuguese else english

    private companion object {
        const val REQUEST_CONTACTS = 7
        const val NEXUS_PACKAGE = "com.anezium.rokidbus.phone"
    }
}
