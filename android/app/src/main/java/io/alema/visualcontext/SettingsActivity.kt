package io.alema.visualcontext

import io.alema.facecore.LocalFaces
import io.alema.facecore.ArcFaceEngine
import io.alema.livecore.LiveProtocol
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.graphics.BitmapFactory
import android.text.InputType
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
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
    private val phoneTts by lazy { Voice(this) }
    override fun onDestroy() { super.onDestroy(); runCatching { phoneTts.close() } }
    @Volatile private var people: List<Backend.Person> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Heat.start(this)
        Where.loadKey(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        Look.insets(col)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("visualcontext", MODE_PRIVATE).getBoolean("prep_on", false)) Prep.schedule(this)
        Update.check(this) { runOnUiThread { if (!isFinishing) render() } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 11) Prep.schedule(this)
    }

    private fun handle(intent: Intent?) {
        val token = DeepLink.token(intent?.data)
        if (token == null) { render(); return }
        intent?.data = null
        AlertDialog.Builder(this)
            .setTitle("Entrar")
            .setMessage("Ligar este celular à sua conta do VisualContext?")
            .setPositiveButton("Conectar") { _, _ -> Backend.save(this, Backend.url(this), token); render() }
            .setNegativeButton("Cancelar") { _, _ -> render() }
            .show()
    }

    // ---------- look: Nexus grammar, more air, clear hierarchy (Marcus, 07/11: grey buttons looked disabled) ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val ink = Color.rgb(3, 12, 6)
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(18))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(8, 22, 14)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(28, 64, 44)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
        col.addView(this)
    }
    private fun LinearLayout.h(t: String) = addView(TextView(context).apply { text = t; textSize = 18f; setTextColor(green); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4)) })
    private fun LinearLayout.p(t: String, size: Float = 14f) = TextView(context).apply { text = t; textSize = size; setTextColor(soft); setLineSpacing(0f, 1.15f); setPadding(0, dp(2), 0, dp(10)) }.also { addView(it) }
    private fun LinearLayout.btn(t: String, primary: Boolean, cb: () -> Unit) = Button(context).apply {
        text = t; isAllCaps = false; textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; stateListAnimator = null
        setTextColor(if (primary) ink else green); minHeight = dp(48)
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); if (primary) setColor(green) else { setColor(Color.TRANSPARENT); setStroke(dp(2), green) } }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        setOnClickListener { cb() }
    }.also { addView(it) }
    /** An on/off option: the words on the left, a switch on the right, as in Android's own settings (Marcus, 09/11). */
    private fun sw() = android.widget.Switch(this).apply {
        textSize = 14f; setTextColor(soft); setPadding(0, dp(8), 0, dp(8)); switchPadding = dp(14)
        thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(green, Color.rgb(170, 180, 175)))
        trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.argb(110, 0, 255, 136), Color.argb(70, 255, 255, 255)))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    /** Green fill only for the action that needs doing now; done things look like the others (Marcus, 09/11). */
    private fun Button.primary(on: Boolean) {
        setTextColor(if (on) ink else green)
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); if (on) setColor(green) else { setColor(Color.TRANSPARENT); setStroke(dp(2), green) } }
    }
    private var facesOpen = false

    private fun render() {
        col.removeAllViews()
        col.setPadding(dp(16), dp(40), dp(16), dp(32) + Look.nav(col))
        // The full logo (symbol + wordmark); long-press = developer mode.
        // Marcus, 09/11: the same logo, about a third of the width, with "by alema.io" and the version under it.
        col.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo_full)
            adjustViewBounds = true
            contentDescription = "VisualContext"
            layoutParams = LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
            setOnLongClickListener { dev = !dev; render(); true }
        })
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2); bottomMargin = dp(16) }
            addView(TextView(context).apply { text = "by alema.io"; textSize = 12f; setTextColor(soft); setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://alema.io"))) } })
            addView(TextView(context).apply {
                text = "v$ver"; textSize = 11f; setTextColor(soft); typeface = android.graphics.Typeface.MONOSPACE; setPadding(dp(8), dp(1), dp(8), dp(1))
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(99).toFloat(); setStroke(dp(1), Color.argb(70, 255, 255, 255)) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) }
            })
        })

        // New version (Marcus, 14/11): the beta APK is installed by hand, so the app says when there is a newer one.
        Update.pending(this)?.let { u ->
            val pt = java.util.Locale.getDefault().language == "pt"
            val cu = card()
            cu.h(if (pt) "Nova versão ${u.name}" else "New version ${u.name}")
            if (u.notes.isNotBlank()) cu.p(u.notes)
            cu.btn(if (pt) "Baixar e instalar" else "Download and install", true) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url))) }
            cu.btn(if (pt) "Depois" else "Later", false) { Update.dismiss(this, u); render() }
        }

        // 1. Connection
        val c1 = card()
        if (Backend.token(this).isEmpty()) {
            c1.h("Entrar")
            c1.p("Use seu e-mail, sem senha. Sua memória fica na sua conta e volta em qualquer celular.")
            val mail = field(Backend.email(this), false).apply { hint = "seu@email.com"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT }
            c1.addView(mail)
            val code = field("", false).apply { hint = "código (se vier no e-mail)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; visibility = android.view.View.GONE }
            c1.addView(code)
            val msg = TextView(this).apply { textSize = 13f; setTextColor(soft) }
            c1.addView(msg)
            var sent = false
            lateinit var go: Button
            go = c1.btn("Enviar código", true) {
                val e = mail.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { msg.text = "Confira o e-mail."; return@btn }
                go.isEnabled = false
                if (!sent) {
                    msg.text = "Enviando…"
                    io.execute {
                        val r = Login.sendCode(e)
                        runOnUiThread {
                            go.isEnabled = true
                            if (r is Login.Out.Ok) {
                                sent = true; Backend.saveEmail(this, e)
                                code.visibility = android.view.View.VISIBLE; code.requestFocus(); go.text = "Entrar"
                                msg.text = "Enviamos um e-mail para $e. Toque no link do e-mail neste celular: o app conecta sozinho. Se vier um código, digite aqui. Veja também o spam."
                            } else msg.text = (r as Login.Out.Fail).why
                        }
                    }
                } else {
                    val k = code.text.toString().filter { it.isDigit() }
                    if (k.length < 6) { msg.text = "Digite os 6 números."; go.isEnabled = true; return@btn }
                    msg.text = "Entrando…"
                    io.execute {
                        val r = Login.verify(e, k, android.os.Build.MODEL ?: "celular")
                        runOnUiThread {
                            go.isEnabled = true
                            if (r is Login.Out.Ok) { Backend.save(this, Backend.url(this), r.value); Backend.saveEmail(this, e); render() }
                            else msg.text = (r as Login.Out.Fail).why
                        }
                    }
                }
            }
            c1.p("Outro e-mail? Edite acima e toque em Enviar código de novo.", 12f)
            // Invite code (Marcus, 09/11): comes with the download link in the approval message; no e-mail to wait for.
            c1.p("Recebeu um código de convite (VC-XXXX-XXXX)? Entre com ele:", 13f)
            val inv = field("", false).apply { hint = "VC-XXXX-XXXX"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
            c1.addView(inv)
            val invMsg = TextView(this).apply { textSize = 13f; setTextColor(soft) }
            c1.addView(invMsg)
            lateinit var invGo: Button
            invGo = c1.btn("Tenho um código de convite", false) {
                val v = inv.text.toString().trim()
                if (v.filter { it.isLetterOrDigit() }.length < 8) { invMsg.text = "Digite o código como veio: VC-XXXX-XXXX."; return@btn }
                invGo.isEnabled = false; invMsg.text = "Entrando…"
                io.execute {
                    val (r, email) = Login.redeem(v, android.os.Build.MODEL ?: "celular")
                    runOnUiThread {
                        invGo.isEnabled = true
                        if (r is Login.Out.Ok) { Backend.save(this, Backend.url(this), r.value); if (email.isNotBlank()) Backend.saveEmail(this, email); render() }
                        else invMsg.text = (r as Login.Out.Fail).why
                    }
                }
            }
            mail.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(x: android.text.Editable?) { if (sent) { sent = false; go.text = "Enviar código"; code.visibility = android.view.View.GONE } }
                override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            if (dev) {
                c1.p("Desenvolvedor: colar um código vc_.", 12f)
                val t = field("", true).apply { hint = "vc_…" }
                c1.addView(t)
                c1.btn("Conectar com código", false) {
                    val v = t.text.toString().trim()
                    if (DeepLink.parse(v) == null) Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show()
                    else { Backend.save(this, Backend.url(this), v); render() }
                }
            }
        } else {
            if (Backend.token(this).isNotEmpty()) io.execute { val before = Backend.role(this) + Backend.tester(this); Backend.syncMe(this); if (Backend.role(this) + Backend.tester(this) != before) runOnUiThread { render() } }
        val status = TextView(this).apply { text = "verificando…"; textSize = 16f; setTextColor(green) }
            c1.addView(status); check(status)
            Backend.email(this).takeIf { it.isNotEmpty() }?.let { c1.p("Conta: $it", 13f) }
            // Testers (Marcus, 09/11): what is left of the test, in one line.
            Backend.tester(this)?.let { t ->
                val l = t.optJSONObject("limits") ?: org.json.JSONObject(); val d = t.optJSONObject("today") ?: org.json.JSONObject()
                c1.p((if (Backend.role(this) == "super") "Super tester" else "Beta tester") + " · faltam ${t.optInt("days_left")} dias · hoje ${d.optInt("calls")} de ${l.optInt("calls_day")} pedidos · ${d.optInt("live_seconds") / 60} de ${l.optInt("live_min_day")} min de assistente", 13f).setTextColor(green)
            }
            c1.btn("Abrir o Manager", false) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Backend.managerLink(this)))) }
            c1.p("Sua memória no navegador: pergunte qualquer coisa, prepare conversas, edite pessoas, importe contatos.", 13f)
        }

        // 2. Use now
        val c2 = card()
        c2.h("Emulador")
        c2.p("A tela do celular vira a lente dos óculos: câmera, microfone e GPS. Pessoas, Gemini Live, Lugares e QR.")
        c2.btn("Abrir o emulador", true) { startActivity(Intent(this, EmulatorActivity::class.java)) }

        // 3. Faces (collapsible)
        faces()

        // 4. Import
        val c4 = card()
        c4.h("Importar rostos")
        c4.p("Do Google Fotos, dos contatos do celular, do LinkedIn, Facebook ou Instagram: quem tem foto e nome passa a ser reconhecido pelos óculos.")
        c4.btn("Importar rostos", false) { startActivity(Intent(this, PhotosPickActivity::class.java)) }

        // 4b. Who you talk to most
        val c4b = card()
        c4b.h("Com quem você mais fala")
        c4b.p("Conta as mensagens que chegam no WhatsApp, Telegram, Signal e SMS, por pessoa, nos últimos 30 dias. Só a contagem: o texto nunca é guardado, e grupos ficam de fora. Ajuda a importar primeiro quem importa e a preparar conversas. Com o Zap instalado, ele já traz 7 dias de histórico.", 13f)
        val on = TalkCounter.enabled(this)
        c4b.addView(sw().apply {
            text = if (on) "Contando · ${TalkCounter.counts(this@SettingsActivity).size} pessoas" else "Contar mensagens (acesso às notificações)"; isChecked = on
            setOnCheckedChangeListener { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        // Cross-sell (Marcus, 07/11): Zap brings the last 7 days at once, and WhatsApp on the glasses.
        if (packageManager.resolveContentProvider("io.alema.zap.frequent", 0) == null) c4b.addView(TextView(this).apply {
            text = "Ainda não tem o Zap? Ele traz os últimos 7 dias de uma vez e põe o WhatsApp nos óculos. Baixar →"
            textSize = 14f; setTextColor(green); setPadding(0, dp(12), 0, 0)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://z.alema.io"))) }
        })
        if (on) c4b.addView(TextView(this).apply { text = "Apagar a contagem"; textSize = 14f; setTextColor(Color.rgb(255, 120, 120)); setPadding(0, dp(10), 0, 0); setOnClickListener { TalkCounter.clear(this@SettingsActivity); render() } })

        // 5. Prep
        val c5 = card()
        c5.h("Preparação")
        c5.p("10 minutos antes de um compromisso com alguém da sua memória, um aviso abre o que você precisa saber.")
        c5.addView(sw().apply {
            text = "Avisar antes dos compromissos (lê a agenda do celular)"; setTextColor(soft); textSize = 14f; buttonTintList = android.content.res.ColorStateList.valueOf(green)
            isChecked = checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED && getSharedPreferences("visualcontext", MODE_PRIVATE).getBoolean("prep_on", false)
            setOnCheckedChangeListener { _, on ->
                getSharedPreferences("visualcontext", MODE_PRIVATE).edit().putBoolean("prep_on", on).apply()
                if (on) requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.POST_NOTIFICATIONS), 11)
            }
        })

        // 6. Notes
        val c6 = card()
        c6.h("Notas")
        c6.p("\"Lembra disso\" olhando um papel, cartão de visita, crachá ou receita: vai para a memória e, com um toque na notificação, para o seu app de notas.")
        val apps = listOf("keep" to "Google Keep", "samsung" to "Samsung Notes", "ask" to "Perguntar sempre", "none" to "Só na memória")
        c6.addView(android.widget.RadioGroup(this).apply {
            apps.forEachIndexed { i, (k, l) -> addView(android.widget.RadioButton(context).apply {
                id = 400 + i; text = l; setTextColor(soft); textSize = 14f; isChecked = NotesOut.target(context) == k; buttonTintList = android.content.res.ColorStateList.valueOf(green)
                isEnabled = k !in listOf("keep", "samsung") || NotesOut.installed(context, if (k == "keep") NotesOut.KEEP else NotesOut.SAMSUNG)
            }) }
            setOnCheckedChangeListener { _, id ->
                NotesOut.setTarget(context, apps[id - 400].first)
                if (android.os.Build.VERSION.SDK_INT >= 33 && apps[id - 400].first != "none") requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
            }
        })
        c6.addView(sw().apply {
            text = "Mandar receitas médicas também (dado de saúde)"; setTextColor(soft); textSize = 14f; isChecked = NotesOut.health(context); buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setOnCheckedChangeListener { _, on -> NotesOut.setHealth(context, on) }
        })

        // 7. Assistant
        val c7 = card()
        c7.h("Assistente (Gemini)")
        if (Backend.role(this) != "beta") {
        c7.p("Chave do Google AI Studio: é ela que faz o Live, a leitura de papéis, a preparação e as habilidades funcionarem. É grátis para começar. Fica só neste celular; não cole em nenhum chat.", 13f)
        c7.btn("Pegar a chave no Google AI Studio ↗", false) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) }
        c7.p("Lá: entre com sua conta Google → \"Create API key\" → copie e cole aqui.", 12f)
        }
        if (Backend.role(this) == "beta") c7.p("✓ Incluído pelo app: você não precisa de chave.", 14f).setTextColor(green)
        else {
        if (Backend.role(this) == "super") {
            c7.p(if (Backend.ownGeminiKey(this).isEmpty()) "✓ Usando a chave do app. Se quiser, teste com a sua:" else "Usando a SUA chave.", 13f).setTextColor(green)
            if (Backend.ownGeminiKey(this).isNotEmpty()) c7.btn("Voltar para a chave do app", false) { Backend.clearGemini(this); render() }
        }
        c7.keyRow(Backend.ownGeminiKey(this), "AIza…",
            save = { Backend.saveGemini(this, it, Backend.voiceName(this)) },
            test = { k -> // a real call, never the cached model name
                try {
                    val c = (java.net.URL(LiveProtocol.MODELS_URL).openConnection() as java.net.HttpURLConnection).apply { setRequestProperty("x-goog-api-key", k); connectTimeout = 10_000 }
                    when (c.responseCode) { in 200..299 -> null; 400, 401, 403 -> "Chave recusada pelo Google."; 429 -> "Limite de uso do Google atingido."; else -> "Erro ${c.responseCode} do Google." }
                } catch (e: Exception) { "Sem internet." } })
        }
        val voices = listOf("Kore", "Aoede", "Puck", "Charon")
        c7.p("Voz de quem fala com você no Live. Toque em ▶ para ouvir antes de escolher.", 13f)
        val vg = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val vmsg = TextView(this).apply { textSize = 12f; setTextColor(soft) }
        val grbs = ArrayList<android.widget.RadioButton>()
        for (v in voices) vg.addView(voiceRow(v, v == Backend.voiceName(this), grbs, pick = { Backend.saveVoice(this, v) }) { b ->
            vmsg.text = "Gerando a voz $v…"; b.isEnabled = false
            io.execute { val why = VoicePreview.play(this@SettingsActivity, v); runOnUiThread { b.isEnabled = true; vmsg.text = why ?: "" } }
        })
        c7.addView(vg); c7.addView(vmsg)
        // Phone voice: offline, free, instant. Used for everything outside the Live (names, warnings, cards, skills) and when the network is gone.
        c7.p("Voz do celular (offline, sem custo): usada fora do Live — nomes, avisos, dicas, habilidades — e quando faltar internet.", 13f)
        val pv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c7.addView(pv)
        val pmsg = TextView(this).apply { textSize = 12f; setTextColor(soft); text = "carregando vozes do celular…" }
        c7.addView(pmsg)
        val tv = phoneTts
        pv.postDelayed({
            val list = tv.choices(); val cur = Voice.phoneVoice(this).ifEmpty { list.firstOrNull()?.first ?: "" }
            if (list.isEmpty()) { pmsg.text = "Nenhuma voz em português instalada. Ajustes do Android → Conversão de texto em voz."; return@postDelayed }
            pmsg.text = ""
            val rbs = ArrayList<android.widget.RadioButton>()
            for ((name, label) in list.take(8)) pv.addView(voiceRow(label, name == cur, rbs, pick = { tv.use(name) }) { _ ->
                val keep = Voice.phoneVoice(this@SettingsActivity); tv.use(name); tv.say("Oi. Eu sou uma voz do seu celular. Falo mesmo sem internet.", interrupt = true); Voice.setPhoneVoice(this@SettingsActivity, keep); if (keep.isNotEmpty()) tv.use(keep)
            })
            if (Voice.phoneVoice(this).isEmpty() && cur.isNotEmpty()) Voice.setPhoneVoice(this, cur)
        }, 900)
        c7.addView(sw().apply {
            text = "Desligar o Live (modo só celular): os óculos reconhecem, transcrevem e avisam pela voz do celular, mas o assistente não responde perguntas. Não tem a ver com a voz escolhida acima."
            textSize = 14f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Backend.offlineOnly(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Backend.setOfflineOnly(this@SettingsActivity, on) }
        })
        c7.addView(sw().apply {
            text = "Escuta offline do celular: o reconhecedor do próprio celular ouve as conversas (para guardar o encontro) e perguntas simples. Desligada, só o Gemini ouve, quando você abre com o toque longo na haste — sem os dois disputando o microfone. Também liga e desliga no botão Escuta, na barra dos óculos."
            textSize = 14f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Backend.earsOn(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Backend.setEarsOn(this@SettingsActivity, on) }
        })
        c7.addView(sw().apply {
            text = "Escuta contínua (recomendado): você só fala. Perguntas simples (\"que horas são\", \"onde deixei as chaves\", \"o que fiz hoje\") o celular responde na hora; qualquer outra coisa dita ao assistente abre o Gemini, que responde e, se você não disser mais nada, sai sozinho e volta a escutar. O toque longo na haste continua abrindo o Gemini direto. Desligado, só o toque longo funciona."
            textSize = 13f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Backend.standby(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Backend.setStandby(this@SettingsActivity, on) }
        })
        c7.addView(sw().apply {
            text = "Dicas na lente: o que dá para dizer ao abrir o assistente e opções (Ler, Guardar, Keep, Agenda) quando você aponta para um texto."
            textSize = 13f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Backend.hints(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Backend.setHints(this@SettingsActivity, on) }
        })
        c7.addView(sw().apply {
            text = "Agenda automática: compromisso combinado numa conversa (com dia e hora) entra sozinho na agenda, com quem, onde e os detalhes; a lente mostra Desfazer. Sem hora certa, fica como sugestão no Manager."
            textSize = 13f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Plans.auto(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Plans.setAuto(this@SettingsActivity, on); if (on) requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR), 12) }
        })
        if (Backend.role(this) != "beta") c7.addView(sw().apply {
            text = "Mapa de calor (Microsoft Clarity): grava onde você toca nas telas do app, para melhorar o desenho. O vídeo da câmera, a lente e as chaves nunca são gravados. Vale a partir da próxima abertura do app."
            textSize = 13f; setTextColor(soft); isChecked = Heat.on(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Heat.set(this@SettingsActivity, on); if (on) Heat.start(this@SettingsActivity) }
        })
        c7.p("Idiomas que eu falo: o que você diz só é escrito nestas línguas, nunca em outra.", 13f)
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chosen = Backend.languages(this).toMutableList()
        for ((code, label) in Backend.LANGS) langRow.addView(sw().apply {
            text = label; textSize = 14f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = code in chosen
            setOnCheckedChangeListener { b, on ->
                if (on) { if (code !in chosen) chosen.add(code) } else chosen.remove(code)
                if (chosen.isEmpty()) { chosen.add(code); b.isChecked = true } // at least one
                Backend.setLanguages(this@SettingsActivity, Backend.LANGS.map { it.first }.filter { it in chosen })
            }
        })
        c7.addView(langRow)
        c7.p("Saída: como os óculos falam com você", 13f)
        val outs = listOf("tela" to "Só tela", "tela_voz" to "Tela + voz", "voz" to "Só voz (óculos sem tela)")
        c7.addView(android.widget.RadioGroup(this).apply {
            outs.forEachIndexed { i, (k, l) -> addView(android.widget.RadioButton(context).apply { id = 500 + i; text = l; setTextColor(soft); textSize = 14f; buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = Voice.output(context) == k }) }
            setOnCheckedChangeListener { _, id -> Voice.setOutput(context, outs[id - 500].first) }
        })

        // 8. Skills (spec 1r)
        val ck = card()
        ck.h("Habilidades")
        val skills = Skills.all(this)
        ck.p(if (skills.isEmpty()) "O assistente aprende com você: peça algo duas vezes, ou diga \"aprende isso\", e ele oferece criar uma habilidade (sua carteira de ações, notícias de um assunto). Da próxima vez é só pedir." else "${skills.size} ${if (skills.size == 1) "habilidade" else "habilidades"}. Segure uma para apagar.", 13f)
        for (sk in skills) ck.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8))
            addView(TextView(context).apply { text = "✦ " + sk.name; textSize = 16f; setTextColor(green) })
            addView(TextView(context).apply { text = sk.triggers.take(2).joinToString(" · ") { "\"$it\"" }; textSize = 13f; setTextColor(soft) })
            setOnLongClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Apagar a habilidade ${sk.name}?").setPositiveButton("Apagar") { _, _ -> Skills.remove(this@SettingsActivity, sk.slug); render() }.setNegativeButton("Cancelar", null).show(); true
            }
        })
        ck.p("As habilidades usam a chave do Gemini acima. Não precisa de outra.", 12f)
        // "Outro assistente" (Claude / ChatGPT / Grok by API key) retired, Marcus 09/11: little use; the Gemini key does everything.
        // Places (Marcus, 08/11): Google knows the small places; OpenStreetMap is the free fallback.
        val cg = card()
        cg.h("Lugares (opcional)")
        cg.p("Sem chave, os lugares vêm do OpenStreetMap (grátis, mas faltam muitos). Com uma chave do Google Maps (Places API New), os encontros ganham o nome do lugar como o Google conhece: \"Pop Vegan\", \"Smart Fit\".", 13f)
        cg.btn("Chaves do Google Cloud ↗", false) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.cloud.google.com/apis/credentials"))) }
        cg.p("Na chave: restrinja a \"Places API (New)\". Ative essa API no projeto. Fica só neste celular.", 12f)
        cg.keyRow(Where.googleKey, "AIza… (Google Maps)", save = { Where.setKey(this, it) }, test = { k -> Where.testGoogle(k) })
        // Music (Marcus, 07/11): AudD recognises what is playing; Google's sound search has no API.
        val cm = card()
        cm.h("Música (opcional)")
        cm.p("\"Que música é essa?\" nos óculos. Sem chave, o celular abre a pesquisa por som do Google (o resultado fica lá). Com uma chave do AudD (audd.io) ou do ACRCloud (acrcloud.com), o nome aparece na lente e vira lembrança. Com as duas, o app pergunta às duas ao mesmo tempo e mostra a melhor resposta (o ACRCloud costuma conhecer mais música brasileira).", 13f)
        if (Backend.role(this) == "beta") { cm.p("✓ Incluído pelo app: AudD e ACRCloud já funcionam.", 14f).setTextColor(green) } else {
        if (Backend.role(this) == "super" && (Music.key(this).isNotEmpty() || Music.ownAcr(this))) cm.btn("Voltar para as chaves do app", false) { Music.setKey(this, ""); Music.setAcr(this, "", "", ""); render() }
        cm.btn("Pegar a chave no AudD ↗", false) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://dashboard.audd.io/"))) }
        cm.keyRow(Music.key(this), "chave do AudD", save = { Music.setKey(this, it) }, test = { k ->
            try {
                val c = (java.net.URL("https://api.audd.io/?api_token=" + java.net.URLEncoder.encode(k, "UTF-8") + "&url=https://audd.tech/example.mp3").openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 25_000 }
                val j = org.json.JSONObject(c.inputStream.bufferedReader().readText())
                if (j.optString("status") == "success") null else (j.optJSONObject("error")?.optString("error_message") ?: "Chave recusada pelo AudD.")
            } catch (_: Exception) { "Sem internet." } })
        // ACRCloud (Marcus, 09/11): asked together with AudD; the scoreboard shows who gets it right.
        cm.p("ACRCloud já vem incluído no app: não precisa de conta. Só preencha abaixo se quiser usar a sua própria conta do ACRCloud.", 12f)
        cm.btn("Abrir o console do ACRCloud ↗", false) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.acrcloud.com/"))) }
        val acrState = TextView(this).apply { textSize = 13f; setTextColor(if (Music.ownAcr(this@SettingsActivity)) soft else green)
            text = if (Music.ownAcr(this@SettingsActivity)) "ACRCloud próprio salvo: ${Music.acrHost(this@SettingsActivity)} · ${mask(Music.acrKey(this@SettingsActivity))}" else "✓ ACRCloud do app" }
        cm.addView(acrState)
        val fh = field(Music.acrHost(this), false).apply { hint = "Host (identify-…acrcloud.com)" }; cm.addView(fh)
        val fk = field("", true).apply { hint = if (Music.acrKey(this@SettingsActivity).isEmpty()) "Access Key" else "colar outra Access Key…" }; cm.addView(fk)
        val fs = field("", true).apply { hint = if (Music.acrSecret(this@SettingsActivity).isEmpty()) "Access Secret" else "colar outro Access Secret…" }; cm.addView(fs)
        cm.btn("Salvar e testar ACRCloud", false) {
            val h = fh.text.toString().trim(); val k = fk.text.toString().trim().ifEmpty { Music.acrKey(this) }; val sct = fs.text.toString().trim().ifEmpty { Music.acrSecret(this) }
            if (h.isEmpty() || k.isEmpty() || sct.isEmpty()) { Toast.makeText(this, "Preencha host, key e secret", Toast.LENGTH_SHORT).show(); return@btn }
            Music.setAcr(this, h, k, sct); fk.setText(""); fs.setText(""); acrState.setTextColor(soft); acrState.text = "Testando ACRCloud…"
            io.execute { val why = Music.testAcr(Music.acrHost(this), k, sct); runOnUiThread {
                if (why == null) { acrState.setTextColor(green); acrState.text = "✓ ACRCloud funciona (${Music.acrHost(this)} · ${mask(k)})" }
                else { acrState.setTextColor(Color.rgb(255, 120, 100)); acrState.text = "✗ ACRCloud: $why" } } }
        }
        }
        val sc = Music.score(this)
        if (sc.values.sum() > 0) cm.p("Placar até agora: as duas acertaram juntas ${sc["ambos"]}× · só AudD ${sc["audd"]}× · só ACRCloud ${sc["acr"]}× · discordaram ${sc["divergiram"]}× · nenhuma ${sc["nenhum"]}×", 12f)

        if (Backend.token(this).isNotEmpty()) {
            val c8 = card()
            c8.btn("Desconectar este celular", false) { Backend.save(this, Backend.url(this), ""); render() }
        }
        if (dev) developer()
    }

    private fun check(status: TextView) = io.execute {
        Backend.peopleFull(applicationContext)?.let { people = it }
        val r = Backend.people(applicationContext)
        runOnUiThread {
            status.text = when (r) {
                is Backend.Result.People -> "● Conectado · ${r.count} pessoas na memória"
                is Backend.Result.Failed -> "○ Sem conexão agora (${r.reason})"
            }
        }
    }

    /**
     * Faces taught on this phone, one row per person (several faces of the same person together).
     * Folded by default. Names save by themselves when you leave the field or tap Done, with a visible ✓.
     */
    private fun faces() {
        val all = LocalFaces.all(this)
        if (all.isEmpty()) return
        val groups = all.groupBy { e -> e.name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "#" + e.id }
            .values.sortedWith(compareBy<List<LocalFaces.Entry>> { it[0].name != null }.thenByDescending { g -> g.maxOf { it.created } })
        val fresh = groups.count { it[0].name == null }
        val c = card()
        c.h("Rostos")
        c.p("${groups.size - fresh} pessoas com nome" + (if (fresh > 0) " · $fresh sem nome" else "") + " · ${all.size} fotos")
        val fromContacts = all.filter { (it.desc ?: "").contains("contatos") }
        if (fromContacts.isNotEmpty()) {
            c.p("As ${fromContacts.size} fotos que vieram dos contatos podem ser tiradas de uma vez:", 13f)
            c.addView(Look.chip(this, "↶ Desfazer a importação dos contatos", soft) {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Tirar deste celular as ${fromContacts.size} fotos que vieram dos contatos? Quem você já usou nos óculos continua com as outras fotos.")
                    .setPositiveButton("Tirar") { _, _ -> val ids = fromContacts.map { it.id }.toSet(); LocalFaces.save(this@SettingsActivity, LocalFaces.all(this@SettingsActivity).filterNot { it.id in ids }); render() }
                    .setNegativeButton("Cancelar", null).show()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        }
        run { // who is the wearer: that face is "você", never an encounter (Marcus, 07/11)
            val me = Backend.meName(this)
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
            line.addView(TextView(this).apply { text = if (me.isEmpty()) "Quem é você na lista? Ainda não marcado." else "★ Você: $me"; textSize = 14f; setTextColor(if (me.isEmpty()) Color.rgb(255, 170, 90) else Color.rgb(255, 214, 90)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Look.chip(this, if (me.isEmpty()) "Marcar" else "Trocar", soft) {
                val names = all.mapNotNull { it.name }.distinct().sortedBy { it.lowercase() }
                if (names.isEmpty()) { Toast.makeText(this, "Dê nome a um rosto seu primeiro", Toast.LENGTH_SHORT).show(); return@chip }
                val opts = (names + "Nenhum (desmarcar)").toTypedArray()
                AlertDialog.Builder(this).setTitle("Qual rosto é o seu?").setItems(opts) { _, i -> Backend.setMeName(this, if (i == names.size) "" else names[i]); render() }.setNegativeButton("Cancelar", null).show()
            })
            c.addView(line)
            val gone = LocalFaces.dedupe(this)
            if (gone > 0) Toast.makeText(this, "$gone ${if (gone == 1) "foto repetida tirada" else "fotos repetidas tiradas"}", Toast.LENGTH_SHORT).show()
        }
        c.btn(if (facesOpen) "Fechar a lista" else if (fresh > 0) "Dar nome a quem falta" else "Ver e editar", fresh > 0 && !facesOpen) { facesOpen = !facesOpen; render() }
        if (!facesOpen) return
        // Search: with hundreds of people the list needs it.
        val search = field(facesQuery, false).apply {
            hint = "🔍  Buscar por nome"; setHintTextColor(Color.rgb(110, 160, 130)); textSize = 16f
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.rgb(8, 24, 15)); setStroke(dp(1), Color.rgb(28, 84, 56)) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        c.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12); bottomMargin = dp(6) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)
        fun fill() {
            list.removeAllViews()
            val q = facesQuery.trim().lowercase()
            val shown = groups.filter { q.isEmpty() || (it[0].name ?: "").lowercase().contains(q) }
            for (g in shown.take(60)) list.addView(faceRow(g))
            if (shown.size > 60) list.addView(TextView(this).apply { text = "+ ${shown.size - 60} pessoas: busque pelo nome"; textSize = 13f; setTextColor(soft); setPadding(0, dp(12), 0, 0) })
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { facesQuery = e?.toString() ?: ""; fill() }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c2: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c2: Int, d: Int) {}
        })
        fill()
    }

    private var facesQuery = ""

    /**
     * One person: the name on top, full width (long names fit); under it the photos, where they came from,
     * "＋ fotos" and Apagar. Names save by themselves with a visible ✓.
     */
    private fun faceRow(g: List<LocalFaces.Entry>): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(10)) }
        val saved = TextView(this).apply { textSize = 12f; setTextColor(green) }
        val name = AutoCompleteTextView(this).apply {
            setText(g[0].name ?: ""); hint = "quem é?"; setTextColor(green); setHintTextColor(Color.rgb(90, 130, 108)); isSingleLine = true; threshold = 1; textSize = 17f
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, people.map { it.name }.distinct()))
        }
        fun commit() {
            val n = name.text.toString().trim().ifEmpty { null }
            if (n == g[0].name) return
            val match = people.firstOrNull { it.name.equals(n, ignoreCase = true) }
            g.forEach { e -> LocalFaces.update(this, e.id) { it.name = match?.name ?: n; it.personId = match?.id } }
            g.forEach { it.name = match?.name ?: n }
            saved.text = if (n == null) "✓ sem nome" else if (match != null) "✓ salvo · ligado à memória" else "✓ salvo"
        }
        name.setOnEditorActionListener { v, _, _ -> commit(); v.clearFocus(); (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0); true }
        name.setOnFocusChangeListener { _, has -> if (!has) commit() }
        name.setOnItemClickListener { _, _, _, _ -> commit() }
        box.addView(name); box.addView(saved)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        val thumbs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setOnClickListener { showLooks(g) } }
        val shown = g.sortedByDescending { it.created }.take(3)
        shown.forEachIndexed { i, e ->
            thumbs.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = if (i == 0) 0 else -dp(16) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { val b = Base64.decode(e.thumb, Base64.DEFAULT); setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.size)) } catch (_: Exception) {}
                clipToOutline = true; outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: android.view.View, o: android.graphics.Outline) { o.setOval(0, 0, v.width, v.height) } }
                // A thin dark ring so overlapping faces read as separate circles.
                foreground = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(2), Color.rgb(3, 12, 6)) }
            })
        }
        row.addView(thumbs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(14) })
        val origins = g.mapNotNull { it.desc }.map { d -> d.replace("rosto do contatos", "dos contatos").replace("rosto do ", "do ").replace("variação vista pelos óculos", "dos óculos") }.distinct()
        row.addView(TextView(this).apply {
            textSize = 13f; setTextColor(soft); setOnClickListener { showLooks(g) }
            text = (if (g.size > 1) "${g.size} fotos" else "1 foto") + (when (origins.size) { 0 -> ""; 1 -> " · " + origins[0]; else -> " · ${origins.size} origens" }) + (if (g.any { it.personId != null }) " · na memória" else " · só neste celular")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        box.addView(row)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(2)) }
        val lp = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) } }
        if (g[0].name != null) actions.addView(Look.chip(this, "＋ Fotos") { addTo = g[0].name; startActivityForResult(Look.pickPhotos(if (android.os.Build.VERSION.SDK_INT >= 33) 30 else 12), REQ_ADD) }, lp())
        actions.addView(Look.chip(this, "Ver todas") { showLooks(g) }, lp())
        if (Backend.meName(this).isNotEmpty() && nameKey(g[0].name ?: "") == nameKey(Backend.meName(this))) actions.addView(TextView(this).apply { text = "★ você"; textSize = 13f; setTextColor(Color.rgb(255, 214, 90)); setPadding(0, 0, dp(10), 0) }, lp())
        actions.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(Look.chip(this, "Apagar", Color.rgb(255, 120, 120)) {
            AlertDialog.Builder(this@SettingsActivity).setMessage("Apagar ${g[0].name ?: "este rosto"} deste celular?")
                .setPositiveButton("Apagar") { _, _ -> g.forEach { LocalFaces.remove(this@SettingsActivity, it.id) }; render() }
                .setNegativeButton("Cancelar", null).show()
        })
        box.addView(actions)
        box.addView(android.view.View(this).apply { setBackgroundColor(Color.rgb(28, 64, 44)) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(14) })
        return box
    }

    // ---------- several looks per person (beard, no beard, glasses…) ----------
    private var addTo: String? = null

    /** Every stored look of a person; long-press one to remove it. */
    private fun showLooks(g: List<LocalFaces.Entry>) {
        val grid = android.widget.GridLayout(this).apply { columnCount = 4; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val dlg = AlertDialog.Builder(this).setTitle("${g[0].name ?: "Sem nome"} · ${g.size} ${if (g.size == 1) "foto" else "fotos"}")
            .setView(ScrollView(this).apply { addView(grid) }).setPositiveButton("Fechar", null).create()
        for (e in g.sortedByDescending { it.created }) grid.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)); scaleType = ImageView.ScaleType.CENTER_CROP
                try { val b = Base64.decode(e.thumb, Base64.DEFAULT); setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.size)) } catch (_: Exception) {}
            })
            addView(TextView(context).apply { text = (e.desc ?: "").removePrefix("rosto do ").take(14); textSize = 9f; setTextColor(soft) })
            setOnLongClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Tirar esta foto?").setPositiveButton("Tirar") { _, _ -> LocalFaces.remove(this@SettingsActivity, e.id); dlg.dismiss(); render() }.setNegativeButton("Cancelar", null).show(); true
            }
        })
        dlg.show()
    }

    @Deprecated("simple result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_ADD || resultCode != RESULT_OK || data == null) return
        val name = addTo ?: return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        val prog = Look.Progress(this, "Lendo ${uris.size} ${if (uris.size == 1) "foto" else "fotos"} de $name", uris.size)
        io.execute {
            val engine = try { ArcFaceEngine(this) } catch (_: Throwable) { null } ?: run { prog.done(); return@execute }
            var dup = 0
            val pid = LocalFaces.all(this).firstOrNull { it.name.equals(name, true) && it.personId != null }?.personId
            var ok = 0
            for ((i, u) in uris.withIndex()) {
                prog.step(i, "procurando o rosto")
                val bmp = try { contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } ?: continue
                val small = if (maxOf(bmp.width, bmp.height) > 1600) android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width * 1600 / maxOf(bmp.width, bmp.height), bmp.height * 1600 / maxOf(bmp.width, bmp.height), true) else bmp
                // The biggest face in the photo is the person (a group photo: crop it first).
                val f = engine.describe(small).firstOrNull() ?: continue
                val pad = f.w * 0.25f
                val x = (f.x - pad).toInt().coerceAtLeast(0); val y = (f.y - pad).toInt().coerceAtLeast(0)
                val w = (f.w + 2 * pad).toInt().coerceAtMost(small.width - x); val h = (f.h + 2 * pad).toInt().coerceAtMost(small.height - y)
                val thumb = android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createBitmap(small, x, y, w, h), 128, 128, true)
                    .let { Base64.encodeToString(java.io.ByteArrayOutputStream().also { o -> it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, o) }.toByteArray(), Base64.NO_WRAP) }
                if (LocalFaces.isDuplicate(this, name, f.descriptor)) { dup++; continue } // the same picture again
                val e = LocalFaces.add(this, f.descriptor, thumb)
                LocalFaces.update(this, e.id) { it.name = name; it.personId = pid; it.desc = "foto que você adicionou" }
                Moments.send(this, org.json.JSONObject().put("action", "enroll_face").put("name", name).put("arc", org.json.JSONArray(f.descriptor.map { it.toDouble() }))
                    .put("thumb", thumb).put("source", "manual").put("description", "foto que você adicionou").apply { if (pid != null) put("person_id", pid) })
                ok++
            }
            engine.close(); prog.step(uris.size, "pronto"); prog.done()
            runOnUiThread { Toast.makeText(this, if (ok == 0 && dup > 0) "Essas fotos já estavam guardadas" else if (ok == 0) "Não achei rosto nessas fotos" else "✓ $ok ${if (ok == 1) "foto adicionada" else "fotos adicionadas"} a $name", Toast.LENGTH_LONG).show(); render() }
        }
    }

    /** Hidden: manual token and backend address, for testing only. */
    private fun developer() {
        col.addView(label("Modo desenvolvedor", 14f, green))
        col.addView(Button(this).apply {
            fun t() = if (Backend.engine(context) == "arc") "Plugin Nexus: ArcFace · trocar" else "Plugin Nexus: face-api · trocar"
            text = t()
            setOnClickListener { Backend.setEngine(context, if (Backend.engine(context) == "arc") "faceapi" else "arc"); text = t() }
        })
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

    /** One line per voice: radio (fills the width) + a ▶ Ouvir chip at the right, the same in both lists. */
    private fun voiceRow(label: String, checked: Boolean, group: MutableList<android.widget.RadioButton>, pick: () -> Unit, play: (Button) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
        val rb = android.widget.RadioButton(this).apply { text = label; textSize = 14f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = checked }
        rb.setOnClickListener { group.forEach { it.isChecked = it === rb }; pick() }
        group.add(rb)
        row.addView(rb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        lateinit var b: Button
        b = Look.chip(this, "▶ Ouvir") { play(b) }
        row.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10) })
        return row
    }

    private fun nameKey(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").split(Regex("\\s+")).filter { it.isNotEmpty() && it !in setOf("de", "da", "do", "dos", "das", "e") }.joinToString(" ")

    /** sk-or-ab…wxyz — enough to tell keys apart, never the whole key. */
    private fun mask(k: String) = if (k.length < 12) "" else k.take(6) + "…" + k.takeLast(4)

    /**
     * A secret with a visible state (Marcus, 07/11: "não sei se entrou, se está salva"): a line that says which
     * key is saved (masked), an empty field to paste a new one, Salvar, and Testar — which runs a real call
     * and says in words whether it worked.
     */
    private fun LinearLayout.keyRow(saved: String, hint: String, save: (String) -> Unit, test: (String) -> String?): EditText {
        var cur = saved
        val state = TextView(context).apply { textSize = 13f; setTextColor(if (cur.isEmpty()) Color.rgb(255, 170, 90) else soft); text = if (cur.isEmpty()) "Nenhuma chave salva." else "Chave salva: ${mask(cur)}" }
        addView(state)
        val f = field("", true).apply { this.hint = if (cur.isEmpty()) hint else "colar outra chave…" }
        addView(f)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(row)
        lateinit var tb: Button
        lateinit var sb: Button
        sb = btn(if (cur.isEmpty()) "Salvar" else "✓ Salva", cur.isEmpty()) {
            val v = f.text.toString().trim()
            if (v.length < 12) { Toast.makeText(context, "Cole a chave primeiro", Toast.LENGTH_SHORT).show(); return@btn }
            save(v); cur = v; f.setText(""); f.hint = "colar outra chave…"; sb.text = "✓ Salva"; sb.primary(false)
            state.setTextColor(soft); state.text = "Chave salva: ${mask(cur)} (ainda não testada)"
        }
        tb = btn("Testar", false) {
            val pending = f.text.toString().trim()
            if (pending.length >= 12 && pending != cur) { Toast.makeText(context, "Salve a chave colada antes de testar", Toast.LENGTH_SHORT).show(); return@btn }
            if (cur.isEmpty()) { Toast.makeText(context, "Nenhuma chave salva", Toast.LENGTH_SHORT).show(); return@btn }
            tb.isEnabled = false; state.text = "Testando ${mask(cur)}…"
            io.execute {
                val why = runCatching { test(cur) }.getOrElse { "Erro: ${it.message}" }
                runOnUiThread {
                    tb.isEnabled = true
                    if (why == null) { state.setTextColor(green); state.text = "✓ Chave ${mask(cur)} funciona" }
                    else { state.setTextColor(Color.rgb(255, 120, 100)); state.text = "✗ ${mask(cur)}: $why" }
                }
            }
        }
        // Something pasted: saving is the thing to do now.
        f.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { val has = !e.isNullOrBlank(); sb.text = if (has || cur.isEmpty()) "Salvar" else "✓ Salva"; sb.primary(has || cur.isEmpty()) }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        // Side by side: move the two buttons from the card into the row.
        removeView(sb); removeView(tb)
        for ((i, b) in listOf(sb, tb).withIndex()) row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { topMargin = dp(8); if (i == 0) rightMargin = dp(8) })
        return f
    }

    private fun field(v: String, secret: Boolean) = EditText(this).apply {
        setText(v); setTextColor(green); textSize = 14f; isSingleLine = true; Heat.mask(this)
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    companion object { private const val REQ_ADD = 61 }
}
