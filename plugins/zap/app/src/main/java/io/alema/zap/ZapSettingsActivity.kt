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
        if (requestCode == REQUEST_PHOTOS || requestCode == REQUEST_CALENDAR || requestCode == REQUEST_NOTIFY) render()
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
                    "• Toque numa conversa: as mensagens aparecem em lista, a mais nova em cima, com uma barra marcando a escolhida. Toque numa mensagem para ouvir o áudio, ver a foto, ler em voz alta, ler inteira ou responder. \"Responder\" também fica no topo da lista.\n" +
                    "• Nova mensagem, no topo: escolha alguém da lista ou toque em \"Dizer o nome\". Primeiro diga só o nome; se houver nomes parecidos, escolha na lista. Depois diga a mensagem.\n" +
                    "• Antes de sair, o texto aparece para você conferir. Nada é enviado sem você tocar em Enviar.\n\n" +
                    "As conversas aparecem conforme as mensagens chegam com o Zap ligado. Mensagens que você manda para você mesmo não contam: o WhatsApp não avisa delas.",
                "On the glasses, open the Nexus menu and pick Zap. It opens on your chats, like WhatsApp on the phone.\n\n" +
                    "• Tap a chat: its messages show as a list, newest on top, a bar marking the chosen one. Tap a message to play the voice note, see the photo, hear it read, read it whole or reply. \"Reply\" is also at the top of the list.\n" +
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
            loses = t(
                "As conversas param de chegar ao Zap: nos óculos, só aparece o que já estava guardado. Na próxima tela, desligue o Zap.",
                "Chats stop reaching Zap: the glasses only show what was already saved. On the next screen, turn Zap off.",
            ),
        )
        val autoSend = ZapSendService.isEnabled(this)
        permissionCard(
            granted = autoSend,
            title = t("Acessibilidade", "Accessibility"),
            why = t(
                "Para enviar sem pegar o celular: o Zap aperta Enviar no WhatsApp depois que você confirma nos óculos. Sem isso, o WhatsApp abre no celular com o texto pronto.",
                "To send without picking up the phone: Zap presses Send in WhatsApp after you confirm on the glasses. Without it, WhatsApp opens on the phone with the text ready.",
            ) + t(
                "\n\nAlguns apps de banco não abrem com a Acessibilidade de outros apps ligada. Se acontecer, pause pelo atalho \"Zap\" no Painel rápido (o painel que você arrasta para baixo, onde ficam o Wi-Fi, o Bluetooth e outros atalhos) e religue depois.",
                "\n\nSome banking apps won't open while another app's Accessibility is on. If that happens, pause it with the \"Zap\" tile in Quick Settings (the panel you pull down, with Wi-Fi, Bluetooth and other shortcuts) and turn it back on later.",
            ),
            how = t(
                "Na próxima tela, toque em \"Aplicativos instalados\", depois em \"Zap — enviar pelo WhatsApp\", e ligue. Não precisa criar atalho.",
                "On the next screen, tap \"Installed apps\", then \"Zap — send with WhatsApp\", and turn it on. No shortcut needed.",
            ),
            intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            loses = t(
                "O Zap para de apertar Enviar sozinho: a conversa abre no celular com o texto pronto e você toca Enviar. Para religar, o Android pede que você passe pela tela de Acessibilidade.",
                "Zap stops pressing Send by itself: the chat opens on the phone with the text ready and you tap Send. To turn it back on, Android asks you to go through the Accessibility screen.",
            ),
            onDisable = { if (!ZapSendService.pause()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
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
            loses = t(
                "Com o celular bloqueado ou no bolso, o Zap não consegue mais abrir o WhatsApp para enviar. Na próxima tela, desligue o Zap.",
                "With the phone locked or in a pocket, Zap can no longer open WhatsApp to send. On the next screen, turn Zap off.",
            ),
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
            intent = appInfo(),
            loses = t(
                "Você não poderá anexar fotos nem receber o \"Enviar pelo Zap?\". Na próxima tela, toque em Permissões → Fotos e vídeos → Não permitir.",
                "You won't be able to attach photos or get \"Send with Zap?\". On the next screen, tap Permissions → Photos and videos → Don't allow.",
            ),
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
            loses = t("Os áudios do WhatsApp não tocam mais nos óculos.", "WhatsApp voice messages won't play on the glasses anymore."),
            onDisable = {
                contentResolver.persistedUriPermissions.forEach {
                    runCatching { contentResolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
            },
        )
        permissionCard(
            granted = ZapContacts(this).hasPermission(),
            title = t("Contatos", "Contacts"),
            why = t(
                "Para responder mesmo quando o WhatsApp esconde o número de alguém, e para escrever a quem ainda não te mandou mensagem. O Zap só lê nome e número; nada sai do celular.",
                "To reply even when WhatsApp hides someone's number, and to write to people who haven't messaged you yet. Zap reads only name and number; nothing leaves the phone.",
            ),
            how = t("Toque em \"Permitir\".", "Tap \"Allow\"."),
            onAllow = { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS) },
            intent = appInfo(),
            loses = t(
                "Quando o WhatsApp esconder o número de alguém, o Zap não vai conseguir responder, e a sua agenda some da lista de pessoas. Na próxima tela, toque em Permissões → Contatos → Não permitir.",
                "When WhatsApp hides someone's number, Zap won't be able to reply, and your contacts leave the people list. On the next screen, tap Permissions → Contacts → Don't allow.",
            ),
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

        aiSection()
        reactionsSection()

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
        photoTiming()?.let { infoCard(t("Última foto oferecida", "Last photo offered"), it) }

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
                "O Zap monta a lista sozinho com quem te manda mensagem no WhatsApp, mais a agenda do celular (permissão Contatos, acima). Também dá para importar um arquivo de contatos.",
                "Zap builds the list by itself from who messages you on WhatsApp, plus the phone's contacts (Contacts permission, above). You can also import a contacts file.",
            ),
        )
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

    /** Where the wait went for the last photo offer: Nexus' sync, then Zap. */
    private fun photoTiming(): String? {
        val prefs = getSharedPreferences("zap_photo_offer", MODE_PRIVATE)
        val taken = prefs.getLong(ZapPhotoWatcher.KEY_TAKEN, 0L)
        val arrived = prefs.getLong(ZapPhotoWatcher.KEY_ARRIVED, 0L)
        val noticed = prefs.getLong(ZapPhotoWatcher.KEY_NOTICED, 0L)
        val shown = prefs.getLong(ZapPhotoWatcher.KEY_SHOWN, 0L)
        if (noticed == 0L) return null
        fun secs(from: Long, to: Long) = if (from > 0 && to >= from) "${(to - from + 500) / 1000} s" else "?"
        return t(
            "Tirada às ${time(taken.takeIf { it > 0 } ?: arrived)}.\n" +
                "• Dos óculos até o celular (sincronização do Nexus): ${secs(taken, arrived)}\n" +
                "• Até aparecer na lente (Zap): ${secs(arrived, shown.takeIf { it >= noticed } ?: noticed)}",
            "Taken at ${time(taken.takeIf { it > 0 } ?: arrived)}.\n" +
                "• Glasses to phone (Nexus sync): ${secs(taken, arrived)}\n" +
                "• Until it showed on the lens (Zap): ${secs(arrived, shown.takeIf { it >= noticed } ?: noticed)}",
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

    // --- summary with AI (0.13) --------------------------------------------

    /**
     * Summaries need an AI, and Zap pays for nobody's: the wearer brings a key
     * (Gemini's is free) or, later, the Easy plan. Said plainly here, with what
     * leaves the phone and where it goes.
     */
    private fun aiSection() {
        section(t("Resumo com IA", "AI summary"))
        val on = ZapAi.enabled(this)
        switchCard(
            title = t("Resumir quando chegam várias mensagens", "Summarize when several messages arrive"),
            subtitle = t(
                "Ao abrir nos óculos uma conversa com 3 ou mais mensagens novas (ou 2 áudios seguidos), aparece o resumo e o que fazer: responder com um toque, pôr na agenda, lembrar de responder. Só a conversa aberta vai para a IA que você escolher, com a sua chave. Desligado, o Zap não usa a internet.",
                "When you open a chat on the glasses with 3+ new messages (or 2 voice notes in a row), you get the summary and what to do: a one-tap reply, add to calendar, remind me to reply. Only the open chat goes to the AI you choose, with your key. Off, Zap doesn't use the internet.",
            ),
            checked = on,
        ) { value ->
            ZapAi.setEnabled(this, value)
            render()
        }
        if (!on) return

        val provider = ZapAi.provider(this)
        choiceCard(
            title = t("IA", "AI"),
            subtitle = t(
                "Gemini ouve também os áudios do WhatsApp. As outras resumem só o texto.",
                "Gemini also listens to WhatsApp voice notes. The others summarize text only.",
            ),
            options = ZapAi.Provider.values().map { it.ordinal },
            label = { ZapAi.Provider.values()[it].label.substringAfter(' ').ifBlank { ZapAi.Provider.values()[it].label } },
            current = provider.ordinal,
        ) { ZapAi.setProvider(this, ZapAi.Provider.values()[it]) }
        keyCard(provider)

        val calendar = ZapCalendar.allowed(this)
        permissionCard(
            granted = calendar,
            title = t("Agenda", "Calendar"),
            why = t(
                "Para \"Pôr na agenda\": o compromisso vai direto para a agenda do celular (Google, Samsung), com aviso 15 min antes.",
                "For \"Add to calendar\": the event goes straight into the phone's calendar (Google, Samsung), with an alert 15 min before.",
            ),
            how = t("Toque em Ativar e permita o acesso à Agenda.", "Tap Turn on and allow Calendar access."),
            intent = appInfo(),
            onAllow = { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), REQUEST_CALENDAR) },
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissionCard(
                granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                title = t("Notificações", "Notifications"),
                why = t(
                    "Para \"Lembrar de responder\" avisar também no celular, caso os óculos estejam desligados.",
                    "So \"Remind me to reply\" also shows on the phone, in case the glasses are off.",
                ),
                how = t("Toque em Ativar e permita as notificações.", "Tap Turn on and allow notifications."),
                intent = appInfo(),
                onAllow = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY) },
            )
        }
        if (provider.hearsAudio && !ZapVoiceNotes.hasAccess(this)) {
            infoCard(
                t("Áudios no resumo", "Voice notes in the summary"),
                t(
                    "Para o Gemini ouvir os áudios, o Zap precisa da pasta de áudios do WhatsApp (a mesma de \"Ouvir este áudio\", em Permissões). Sem ela, o resumo avisa quantos áudios ficaram de fora.",
                    "For Gemini to hear voice notes, Zap needs WhatsApp's voice notes folder (the same as \"Play this voice message\", in Permissions). Without it, the summary says how many were left out.",
                ),
            )
        }
    }

    /** The key: masked when saved, a field to paste a new one, Test and Delete. Where to get a free one, for Gemini. */
    private fun keyCard(provider: ZapAi.Provider) {
        val masked = ZapAi.maskedKey(this)
        val field = NexusUi.field(this, t("Cole a chave aqui", "Paste the key here")).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val status = NexusUi.rowSub(this, masked?.let { t("Chave salva: $it", "Saved key: $it") } ?: t("Nenhuma chave ainda", "No key yet"))
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.rowTitle(this@ZapSettingsActivity, t("Chave de IA · ${provider.label}", "AI key · ${provider.label}")))
                addView(BusTheme.gap(this@ZapSettingsActivity, 4))
                addView(status)
                addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                addView(
                    LinearLayout(this@ZapSettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        fun pill(label: String, primary: Boolean, onClick: () -> Unit) = addView(
                            (if (primary) NexusUi.pillButton(this@ZapSettingsActivity, label) else NexusUi.outlinePillButton(this@ZapSettingsActivity, label))
                                .apply { setOnClickListener { onClick() } },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = NexusUi.dp(this@ZapSettingsActivity, 6) },
                        )
                        pill(t("SALVAR", "SAVE"), primary = true) {
                            val typed = field.text?.toString().orEmpty()
                            if (typed.isNotBlank()) ZapAi.setKey(this@ZapSettingsActivity, typed)
                            render()
                        }
                        pill(t("TESTAR", "TEST"), primary = false) {
                            status.text = t("Testando…", "Testing…")
                            ZapAi.test(this@ZapSettingsActivity) { error ->
                                runOnUiThread {
                                    status.text = if (error == null) t("✓ Funcionou: a IA respondeu", "✓ It works: the AI answered")
                                    else io.alema.zap.core.ZapText().briefError(error)
                                }
                            }
                        }
                        if (masked != null) {
                            pill(t("APAGAR", "DELETE"), primary = false) {
                                ZapAi.setKey(this@ZapSettingsActivity, null)
                                render()
                            }
                        }
                    },
                )
                if (provider == ZapAi.Provider.GEMINI) {
                    addView(BusTheme.gap(this@ZapSettingsActivity, 8))
                    addView(
                        NexusUi.textButton(this@ZapSettingsActivity, t("Pegar uma chave grátis no Google AI Studio", "Get a free key at Google AI Studio")).apply {
                            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) } }
                        },
                    )
                }
                addView(BusTheme.gap(this@ZapSettingsActivity, 6))
                addView(NexusUi.rowSub(this@ZapSettingsActivity, t("Modelo: ${ZapAi.model(this@ZapSettingsActivity)}", "Model: ${ZapAi.model(this@ZapSettingsActivity)}")))
            },
            NexusUi.block(),
        )
        gap(8)
    }

    // --- quick reactions (0.13) ----------------------------------------------

    /** Up to 7 emojis for "React" on the glasses, in the order tapped here, and a skin tone for hands. */
    private fun reactionsSection() {
        section(t("Reações rápidas", "Quick reactions"))
        val chosen = settings.reactionEmojis().ifEmpty { io.alema.zap.core.Reactions.defaults(pt).map { it.emoji } }
        val tone = settings.skinTone()
        val palette = io.alema.zap.core.Reactions.palette(pt)
        content.addView(
            NexusUi.card(this).apply {
                addView(NexusUi.rowTitle(this@ZapSettingsActivity, t("Emojis para reagir (${chosen.size}/${io.alema.zap.core.Reactions.MAX})", "Emojis to react with (${chosen.size}/${io.alema.zap.core.Reactions.MAX})")))
                addView(BusTheme.gap(this@ZapSettingsActivity, 4))
                addView(
                    NexusUi.rowSub(
                        this@ZapSettingsActivity,
                        t(
                            "Nos óculos, toque numa mensagem e escolha Reagir: o emoji vai como mensagem. Toque aqui para tirar ou pôr; a ordem é a dos toques.",
                            "On the glasses, tap a message and choose React: the emoji goes as a message. Tap here to add or remove; the order is the tap order.",
                        ),
                    ),
                )
                addView(BusTheme.gap(this@ZapSettingsActivity, 10))
                palette.chunked(4).forEach { row ->
                    addView(
                        LinearLayout(this@ZapSettingsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            row.forEach { reaction ->
                                val on = reaction.emoji in chosen
                                val label = io.alema.zap.core.Reactions.withTone(reaction, tone).emoji
                                val button = if (on) NexusUi.pillButton(this@ZapSettingsActivity, label) else NexusUi.outlinePillButton(this@ZapSettingsActivity, label)
                                button.isAllCaps = false
                                button.textSize = 20f
                                button.contentDescription = reaction.word
                                button.setOnClickListener {
                                    val next = if (on) chosen - reaction.emoji else chosen + reaction.emoji
                                    if (next.size <= io.alema.zap.core.Reactions.MAX && next.isNotEmpty()) settings.setReactionEmojis(next)
                                    render()
                                }
                                addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginEnd = NexusUi.dp(this@ZapSettingsActivity, 6)
                                    bottomMargin = NexusUi.dp(this@ZapSettingsActivity, 6)
                                })
                            }
                        },
                    )
                }
            },
            NexusUi.block(),
        )
        gap(8)
        val tones = io.alema.zap.core.SkinTone.values().toList()
        choiceCard(
            title = t("Tom de pele", "Skin tone"),
            subtitle = t(
                "Para as mãos (👍 🙏 👏…). Nos óculos tudo fica verde, mas quem recebe vê a cor certa.",
                "For hands (👍 🙏 👏…). On the glasses everything is green, but the person receiving sees the right color.",
            ),
            options = tones.map { it.ordinal },
            label = { "👍" + tones[it].modifier },
            current = tone.ordinal,
        ) { settings.setSkinTone(tones[it]) }
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
        /** What stops working without it, said before turning it off. */
        loses: String? = null,
        /** Turns it off when Zap can; otherwise the Android screen opens. */
        onDisable: (() -> Unit)? = null,
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
                } else if (loses != null) {
                    addView(BusTheme.gap(this@ZapSettingsActivity, 6))
                    addView(
                        NexusUi.textButton(this@ZapSettingsActivity, t("Desligar", "Turn off")).apply {
                            setOnClickListener { confirmOff(title, loses) { runCatching { onDisable?.invoke() ?: intent?.let { startActivity(it) } } } }
                        },
                    )
                }
            },
            NexusUi.block(),
        )
        gap(8)
    }

    private fun confirmOff(title: String, loses: String, off: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(t("Desligar $title?", "Turn off $title?"))
            .setMessage(loses)
            .setPositiveButton(t("Desligar", "Turn off")) { _, _ -> off(); render() }
            .setNegativeButton(t("Cancelar", "Cancel"), null)
            .show()
    }

    private fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

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
        const val REQUEST_CALENDAR = 11
        const val REQUEST_NOTIFY = 12
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
    }
}
