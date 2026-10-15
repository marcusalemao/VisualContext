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
                    t("Seu WhatsApp nos óculos", "Your WhatsApp on the glasses") + " · v${BuildConfig.VERSION_NAME}",
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE) {
            data?.data?.takeIf { resultCode == RESULT_OK }?.let { ZapVoiceNotes.remember(this, it) }
            render()
            return
        }
        if (requestCode != REQUEST_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes().take(MAX_FILE_BYTES).toByteArray() }?.toString(Charsets.UTF_8)
        }.getOrNull()
        val entries = text?.let { io.alema.zap.core.ContactFile.parse(it) }.orEmpty()
        val added = ZapImported(this).add(entries)
        android.widget.Toast.makeText(
            this,
            when {
                text == null -> t("Não consegui abrir o arquivo", "Couldn't open the file")
                entries.isEmpty() -> t("Nenhum contato com nome e celular no arquivo", "No contacts with name and phone in the file")
                else -> t("$added contato(s) novo(s), ${entries.size} no arquivo", "$added new contact(s), ${entries.size} in the file")
            },
            android.widget.Toast.LENGTH_LONG,
        ).show()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHOTOS) render()
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
                    "• Toque numa conversa para ler: ela abre inteira, cada mensagem completa, e você desliza para rolar. Toque de novo para Responder, Ouvir de novo, Enviar foto e ver as fotos.\n" +
                    "• Nova mensagem, no topo: escolha alguém da lista ou toque em \"Dizer o nome\". Primeiro diga só o nome; se houver nomes parecidos, escolha na lista. Depois diga a mensagem.\n" +
                    "• Antes de sair, o texto aparece para você conferir. Nada é enviado sem você tocar em Enviar.\n\n" +
                    "As conversas aparecem conforme as mensagens chegam com o Zap ligado. Mensagens que você manda para você mesmo não contam: o WhatsApp não avisa delas.",
                "On the glasses, open the Nexus menu and pick Zap. It opens on your chats, like WhatsApp on the phone.\n\n" +
                    "• Tap a chat to read it: it opens whole, every message in full, and you swipe to scroll. Tap again for Reply, Listen again, Send a photo and the photos.\n" +
                    "• New message, at the top: pick someone from the list or tap \"Say the name\". Say just the name first; if names are alike, pick from the list. Then say the message.\n" +
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
            title = t("Acesso às notificações", "Notification access"),
            body = if (notifications) {
                t("Ligado. Quem te manda mensagem no WhatsApp entra na sua lista do Zap.", "On. People who message you on WhatsApp join your Zap list.")
            } else {
                t(
                    "Mostra suas conversas do WhatsApp nos óculos e monta sua lista de pessoas com quem te escreve. As mensagens ficam só neste celular, criptografadas.",
                    "Shows your WhatsApp chats on the glasses and builds your people list from who writes to you. Messages stay on this phone only, encrypted.",
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
                        "Nenhuma notificação do WhatsApp chegou ao Zap ainda. Peça para alguém te mandar uma mensagem (mensagens para você mesmo não geram notificação). Se mesmo assim nada aparecer, desligue e ligue de novo o acesso às notificações do Zap.",
                        "No WhatsApp notification has reached Zap yet. Ask someone to message you (messages to yourself don't notify). If still nothing, turn Zap's notification access off and on again.",
                    )
                    health.lastCaptured() == 0L -> t(
                        "O WhatsApp avisou o Zap às ${time(health.lastSeen())}, mas o Zap não conseguiu ler a mensagem. Me mande um print desta tela.",
                        "WhatsApp notified Zap at ${time(health.lastSeen())}, but Zap couldn't read the message. Send me a screenshot of this screen.",
                    ) + (health.lastError()?.let { "\n\n($it)" } ?: "")
                    else -> t(
                        "Funcionando. Última mensagem às ${time(health.lastCaptured())}; $chats conversa(s) guardada(s).",
                        "Working. Last message at ${time(health.lastCaptured())}; $chats chat(s) saved.",
                    ) + (health.lastIdKind()?.takeIf { BuildConfig.DEBUG }?.let { "\n(id: $it)" } ?: "")
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
        choiceCard(
            title = t("Quando chegar mensagem", "When a message arrives"),
            subtitle = t(
                "Aviso: quem escreveu e o começo, por 4 segundos. Com voz: a mensagem inteira na frente de tudo, lida em voz alta na hora — nos óculos ou fones, nunca no alto-falante do celular. Se o Relay também avisa o WhatsApp, desligue um dos dois.",
                "Notice: who wrote and the start, for 4 seconds. With voice: the whole message over everything, read aloud at once — on the glasses or earbuds, never the phone's speaker. If Relay also shows WhatsApp, turn one of them off.",
            ),
            options = listOf(ZapSettings.ARRIVAL_OFF, ZapSettings.ARRIVAL_NOTICE, ZapSettings.ARRIVAL_READ),
            label = {
                when (it) {
                    ZapSettings.ARRIVAL_OFF -> t("Nada", "Nothing")
                    ZapSettings.ARRIVAL_NOTICE -> t("Aviso", "Notice")
                    else -> t("Com voz", "With voice")
                }
            },
            current = settings.arrivalMode(),
        ) { settings.setArrivalMode(it) }
        // Videos need their own Android permission: someone who allowed photos in 0.5 is asked again.
        // "Selected photos only" (Android 14) hides new glasses shots too, so it is called out.
        val access = ZapGallery.access(this)
        val photos = ZapGallery.hasPhotosAndVideos(this) && access == ZapGallery.Access.FULL
        switchCard(
            title = t("Enviar fotos e vídeos", "Send photos and videos"),
            subtitle = when (access) {
                ZapGallery.Access.PARTIAL -> t(
                    "Acesso parcial: o Zap só vê as fotos que você escolheu, e não vê as novas dos óculos. Toque e escolha \"Permitir tudo\".",
                    "Partial access: Zap only sees the photos you picked, not new ones from the glasses. Tap and choose \"Allow all\".",
                )
                else -> t(
                    "Em \"Enviar foto ou vídeo\", primeiro aparece o que você fotografou com os óculos (o Zap pede ao Nexus para trazer na hora), depois o que está no celular. O Zap só abre o que você escolher.",
                    "In \"Send photo or video\", what you shot with the glasses comes first (Zap asks Nexus to bring it over right then), then what's on the phone. Zap only opens what you pick.",
                )
            },
            checked = photos,
        ) { on ->
            if (on && !photos) {
                requestPermissions(ZapGallery.permissions(), REQUEST_PHOTOS)
            } else if (!on && photos) {
                // Android only lets the wearer take a permission back in its own settings.
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        val voice = ZapVoiceNotes.hasAccess(this)
        switchCard(
            title = t("Ouvir áudios do WhatsApp", "Play WhatsApp voice messages"),
            subtitle = if (voice) t(
                "Ligado. Na conversa, \"Ouvir áudio\" toca nos óculos (ou no fone) — nunca no alto-falante do celular.",
                "On. In a chat, \"Play voice message\" plays on the glasses (or earbuds) — never the phone's speaker.",
            ) else t(
                "O Android vai abrir a pasta de áudios do WhatsApp: toque em \"Usar esta pasta\" e \"Permitir\". O Zap só lê essa pasta.",
                "Android opens WhatsApp's voice notes folder: tap \"Use this folder\" and \"Allow\". Zap reads only that folder.",
            ),
            checked = voice,
        ) { on ->
            if (on && !voice) {
                runCatching { startActivityForResult(ZapVoiceNotes.pickIntent(), REQUEST_VOICE) }
            } else if (!on && voice) {
                contentResolver.persistedUriPermissions.forEach {
                    runCatching { contentResolver.releasePersistableUriPermission(it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                render()
            }
        }
        val offer = settings.offerNewPhotos()
        switchCard(
            title = t("Oferecer fotos tiradas nos óculos", "Offer photos taken with the glasses"),
            subtitle = t(
                "Tirou uma foto com o botão dos óculos? Ela aparece na lente com \"Enviar pelo Zap?\" e as duas últimas pessoas com quem você falou. Para chegar na hora, o Zap muda a sincronização de fotos do Nexus para \"Sempre\" (só a primeira vez que você abrir o Zap nos óculos).",
                "Took a photo with the glasses button? It shows on the lens with \"Send with Zap?\" and the last two people you talked to. To arrive right away, Zap sets Nexus photo sync to \"Always\" (only the first time you open Zap on the glasses).",
            ),
            checked = offer,
        ) { on -> settings.setOfferNewPhotos(on) }
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
            label = { "$it" },
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
        infoCard(
            t("Sua lista de pessoas", "Your people list"),
            t(
                "O Zap monta a lista sozinho com quem te manda mensagem no WhatsApp. Para escrever também para quem ainda não te escreveu, use a agenda do celular ou importe um arquivo de contatos.",
                "Zap builds the list by itself from who messages you on WhatsApp. To also write to people who haven't messaged you yet, use the phone's contacts or import a contacts file.",
            ),
        )
        switchCard(
            title = t("Usar a agenda do celular", "Use the phone's contacts"),
            subtitle = t("Os contatos ficam no celular; o Zap só lê nome e número", "Contacts stay on the phone; Zap reads only name and number"),
            checked = settings.importAddressBook() && ZapContacts(this).hasPermission(),
        ) { on ->
            if (on && !ZapContacts(this).hasPermission()) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            } else {
                settings.setImportAddressBook(on)
            }
        }
        val imported = ZapImported(this).count()
        infoCard(
            t("Importar arquivo de contatos", "Import a contacts file"),
            t(
                "Um arquivo .vcf (exportado pelo app Contatos ou pelo Google Contatos) ou uma planilha .csv com nome e celular. Só o nome e o número são guardados." +
                    if (imported > 0) "\n\n$imported contato(s) importado(s)." else "",
                "A .vcf file (exported from the Contacts app or Google Contacts) or a .csv sheet with name and phone. Only the name and number are kept." +
                    if (imported > 0) "\n\n$imported contact(s) imported." else "",
            ),
            action = t("Escolher arquivo", "Choose file") to Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/x-vcard", "text/vcard", "text/csv", "text/comma-separated-values", "text/plain")),
            requestCode = REQUEST_FILE,
        )
        gap(8)
        if (imported > 0) {
            content.addView(
                NexusUi.textButton(this, t("Apagar contatos importados", "Delete imported contacts"), danger = true).apply {
                    setOnClickListener {
                        ZapImported(this@ZapSettingsActivity).clear()
                        render()
                    }
                },
                NexusUi.block(),
            )
        }
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
        // No "open Nexus" button: these settings are reached from inside Nexus, and Nexus does not
        // expose its Speech/Voice screens to other apps, so a button could only land on Nexus's home.
        infoCard(
            t("Usa a voz do Rokid Nexus", "Uses Rokid Nexus voice"),
            t(
                "O Zap não tem ajuste de voz próprio: entende e lê em voz alta com o que você escolheu no Nexus. Para mudar, volte aos ajustes do Nexus: Speech (entender o que você fala) e Voice (ler em voz alta).",
                "Zap has no voice settings of its own: it listens and reads aloud with what you chose in Nexus. To change it, go back to Nexus settings: Speech (understanding you) and Voice (reading aloud).",
            ),
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

    private fun infoCard(title: String, body: String, action: Pair<String, Intent>? = null, requestCode: Int? = null) {
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.cardTitle(this@ZapSettingsActivity, title))
                addView(BusTheme.gap(this@ZapSettingsActivity, 5))
                addView(NexusUi.cardBody(this@ZapSettingsActivity, body))
                if (action != null) {
                    addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                    addView(
                        NexusUi.outlinePillButton(this@ZapSettingsActivity, action.first).apply {
                            setOnClickListener {
                                runCatching {
                                    if (requestCode != null) startActivityForResult(action.second, requestCode) else startActivity(action.second)
                                }
                            }
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
                            // Same case on both pill styles (the outline one capitalises by itself).
                            val text = label(value).uppercase()
                            val button = if (value == current) {
                                NexusUi.pillButton(this@ZapSettingsActivity, text)
                            } else {
                                NexusUi.outlinePillButton(this@ZapSettingsActivity, text)
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
        const val REQUEST_FILE = 8
        const val REQUEST_PHOTOS = 9
        const val REQUEST_VOICE = 10
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
    }
}
