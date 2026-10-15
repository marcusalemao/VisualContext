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
        updatedBanner()

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

        // Permissions, named as Android names them, so the wearer recognises them on the next screen.
        // Status shows without a tap; the button only appears when something is still off.
        section(t("Permissões do Android", "Android permissions"))
        val whatsapp = ZapSender.whatsAppPackage(this) != null
        if (!whatsapp) {
            infoCard(
                t("WhatsApp não encontrado", "WhatsApp not found"),
                t("Instale o WhatsApp neste celular para usar o Zap.", "Install WhatsApp on this phone to use Zap."),
            )
        }
        val notifications = ZapSendService.isNotificationAccessGranted(this)
        permissionCard(
            granted = notifications,
            title = t("Acesso a notificações", "Notification access"),
            why = t(
                "Para ver suas conversas do WhatsApp nos óculos. As mensagens ficam só neste celular, criptografadas.",
                "To show your WhatsApp chats on the glasses. Messages stay on this phone only, encrypted.",
            ),
            how = t("Na próxima tela, ligue o Zap.", "On the next screen, turn Zap on."),
            intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
        val autoSend = ZapSendService.isEnabled(this)
        permissionCard(
            granted = autoSend,
            title = t("Acessibilidade", "Accessibility"),
            why = t(
                "Para enviar sem pegar o celular: o Zap aperta Enviar no WhatsApp depois que você confirma nos óculos. Sem isso, o WhatsApp abre no celular com o texto pronto.",
                "To send without picking up the phone: Zap presses Send in WhatsApp after you confirm on the glasses. Without it, WhatsApp opens on the phone with the text ready.",
            ),
            how = t(
                "Na próxima tela, toque em \"Aplicativos instalados\", depois em \"Zap — enviar pelo WhatsApp\", e ligue. Não precisa criar atalho.",
                "On the next screen, tap \"Installed apps\", then \"Zap — send with WhatsApp\", and turn it on. No shortcut needed.",
            ),
            intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
        val background = ZapSender.canStartFromBackground(this)
        permissionCard(
            granted = background,
            title = t("Sobrepor a outros apps", "Display over other apps"),
            why = t(
                "Para funcionar com o celular no bolso: só assim o Android deixa o Zap abrir o WhatsApp com a tela apagada.",
                "To work with the phone in your pocket: only then does Android let Zap open WhatsApp with the screen off.",
            ),
            how = t("Na próxima tela, ligue o Zap.", "On the next screen, turn Zap on."),
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
        // Videos need their own Android permission; "Selected photos only" (Android 14) hides new glasses shots.
        val access = ZapGallery.access(this)
        val photos = ZapGallery.hasPhotosAndVideos(this) && access == ZapGallery.Access.FULL
        permissionCard(
            granted = photos,
            title = t("Fotos e vídeos", "Photos and videos"),
            why = t(
                "Para anexar fotos numa conversa: primeiro as que você tirou com os óculos, depois as do celular. O Zap só abre a que você escolher.",
                "To attach photos in a chat: first the ones you took with the glasses, then the phone's. Zap opens only the one you pick.",
            ),
            how = if (access == ZapGallery.Access.PARTIAL) {
                t(
                    "Hoje está em \"Só fotos selecionadas\": o Zap não vê as fotos novas dos óculos. Escolha \"Permitir tudo\".",
                    "It's on \"Selected photos only\": Zap can't see new glasses photos. Choose \"Allow all\".",
                )
            } else {
                t("Escolha \"Permitir tudo\".", "Choose \"Allow all\".")
            },
            onAllow = { requestPermissions(ZapGallery.permissions(), REQUEST_PHOTOS) },
            intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        val voice = ZapVoiceNotes.hasAccess(this)
        permissionCard(
            granted = voice,
            title = t("Pasta de áudios do WhatsApp", "WhatsApp voice notes folder"),
            why = t(
                "Para ouvir áudios nos óculos ou no fone — nunca no alto-falante do celular. O Zap só lê essa pasta.",
                "To play voice messages on the glasses or earbuds — never the phone's speaker. Zap reads only that folder.",
            ),
            how = t("Na próxima tela, toque em \"Usar esta pasta\" e em \"Permitir\".", "On the next screen, tap \"Use this folder\" and \"Allow\"."),
            onAllow = { runCatching { startActivityForResult(ZapVoiceNotes.pickIntent(), REQUEST_VOICE) } },
            intent = null,
        )
        if (!notifications || !autoSend) {
            infoCard(
                t("O botão está cinza?", "Is the switch greyed out?"),
                t(
                    "O Android bloqueia Acesso a notificações e Acessibilidade em apps instalados por fora da loja. Para liberar: abra os ajustes do Zap no Android, toque nos três pontinhos no canto de cima e escolha \"Permitir configurações restritas\". Depois volte aqui.",
                    "Android blocks Notification access and Accessibility for apps installed outside the store. To allow them: open Zap's Android app info, tap the three dots at the top and choose \"Allow restricted settings\". Then come back here.",
                ),
                action = t("Abrir ajustes do Zap no Android", "Open Zap's Android app info") to
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        }
        // Only speaks up when something is wrong; when messages arrive there is nothing to say.
        if (notifications) problemCard()

        section(t("Avisos nos óculos", "Notices on the glasses"))
        choiceCard(
            title = t("Quando chegar mensagem", "When a message arrives"),
            subtitle = t(
                "Aviso: quem escreveu e o começo, por 4 segundos. Com voz: a mensagem inteira, lida em voz alta na hora — nos óculos ou fones, nunca no alto-falante do celular. Se o Relay também avisa o WhatsApp, desligue um dos dois.",
                "Notice: who wrote and the start, for 4 seconds. With voice: the whole message, read aloud at once — on the glasses or earbuds, never the phone's speaker. If Relay also shows WhatsApp, turn one of them off.",
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
        switchCard(
            title = t("Oferecer fotos tiradas nos óculos", "Offer photos taken with the glasses"),
            subtitle = t(
                "Tirou uma foto com o botão dos óculos? Ela aparece na lente com \"Enviar pelo Zap?\". Para chegar na hora, o Zap muda a sincronização de fotos do Nexus para \"Sempre\".",
                "Took a photo with the glasses button? It shows on the lens with \"Send with Zap?\". To arrive right away, Zap sets Nexus photo sync to \"Always\".",
            ),
            checked = settings.offerNewPhotos(),
        ) { on -> settings.setOfferNewPhotos(on) }

        section(t("Tela dos óculos", "Glasses display"))
        choiceCard(
            title = t("Área visível nos óculos", "Visible area on the glasses"),
            subtitle = t(
                "Se a parte de baixo da lente fica difícil de ver, o Zap não põe nada importante lá. Para subir a imagem toda, use também o app Hi Rokid: Def. dos óculos → Def. ecrã óculos → Área visível óculos.",
                "If the bottom of the lens is hard to see, Zap keeps everything important above it. To move the whole image up, also use the Hi Rokid app: Glasses settings → Display → Visible area.",
            ),
            options = listOf(100, 85, ZapSettings.DEFAULT_VISIBLE, 60),
            label = { if (it == 100) t("Toda", "All") else "$it%" },
            current = settings.visiblePercent(),
        ) { settings.setVisiblePercent(it) }
        switchCard(
            title = t("Contraste extra nos avisos", "Extra contrast for notices"),
            subtitle = t(
                "Os avisos do Zap aparecem sobre um fundo escuro, destacados do que estiver atrás.",
                "Zap's notices show over a dark backdrop, standing out from whatever is behind.",
            ),
            checked = settings.extraContrast(),
        ) { on -> settings.setExtraContrast(on) }

        section(t("Conversas guardadas", "Saved chats"))
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

        section(t("Contatos", "Contacts"))
        infoCard(
            t("Sua lista de contatos", "Your contacts list"),
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
        gap(8)
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
        // No "open Nexus" button: Nexus does not expose its Speech/Voice screens to other apps.
        infoCard(
            t("Usa a voz do Rokid Nexus", "Uses Rokid Nexus voice"),
            t(
                "O Zap entende e lê em voz alta com o que você escolheu no Nexus. Para mudar, vá aos ajustes do Nexus: Speech (entender o que você fala) e Voice (ler em voz alta).",
                "Zap listens and reads aloud with what you chose in Nexus. To change it, go to Nexus settings: Speech (understanding you) and Voice (reading aloud).",
            ),
        )
        gap(24)
    }

    /**
     * Android's installer says nothing useful after an update, so Zap says it once:
     * "Updated to 0.9.2". The very first install says nothing (the guide is right below).
     */
    private fun updatedBanner() {
        val prefs = getSharedPreferences("zap_install", MODE_PRIVATE)
        val last = prefs.getString("version", null)
        val now = BuildConfig.VERSION_NAME
        if (last == now) return
        prefs.edit().putString("version", now).apply()
        if (last == null) return
        infoCard(
            t("✓ Zap atualizado para $now", "✓ Zap updated to $now"),
            t(
                "Antes estava a $last. Nos óculos, se o Nexus pedir, aprove o Zap de novo: as novidades podem precisar de permissões novas.",
                "It was $last. On the glasses, if Nexus asks, approve Zap again: new features may need new permissions.",
            ),
        )
    }

    /** Only when messages are not reaching Zap; silent when all is well. */
    private fun problemCard() {
        val health = ZapHealth(this)
        val text = when {
            health.lastSeen() == 0L -> t(
                "Nenhuma notificação do WhatsApp chegou ao Zap ainda. Peça para alguém te mandar uma mensagem (mensagens para você mesmo não geram notificação). Se mesmo assim nada aparecer, desligue e ligue de novo o Acesso a notificações do Zap.",
                "No WhatsApp notification has reached Zap yet. Ask someone to message you (messages to yourself don't notify). If still nothing, turn Zap's notification access off and on again.",
            )
            health.lastCaptured() == 0L -> t(
                "O WhatsApp avisou o Zap às ${time(health.lastSeen())}, mas o Zap não conseguiu ler a mensagem. Mande um print desta tela para o suporte.",
                "WhatsApp notified Zap at ${time(health.lastSeen())}, but Zap couldn't read the message. Send a screenshot of this screen to support.",
            ) + (health.lastError()?.let { "\n\n($it)" } ?: "")
            else -> return
        }
        infoCard(t("Ainda não chegou mensagem", "No message yet"), text)
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

    /**
     * A permission by its Android name. On: a check mark and what it is for, no button
     * (tapping the card still opens the Android screen). Off: why, what to do next, and Allow.
     */
    private fun permissionCard(
        granted: Boolean,
        title: String,
        why: String,
        how: String,
        intent: Intent?,
        onAllow: (() -> Unit)? = null,
    ) {
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.cardTitle(this@ZapSettingsActivity, (if (granted) "✓ " else "") + title))
                addView(BusTheme.gap(this@ZapSettingsActivity, 5))
                addView(NexusUi.cardBody(this@ZapSettingsActivity, if (granted) why else "$why\n\n$how"))
                if (!granted) {
                    addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                    addView(
                        NexusUi.pillButton(this@ZapSettingsActivity, t("Ativar", "Turn on")).apply {
                            setOnClickListener {
                                runCatching { onAllow?.invoke() ?: intent?.let { startActivity(it) } }
                            }
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                } else if (intent != null) {
                    setOnClickListener { runCatching { startActivity(intent) } }
                }
            },
            NexusUi.block(),
        )
        gap(8)
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
