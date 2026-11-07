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
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        Look.insets(col)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    override fun onResume() { super.onResume(); if (getSharedPreferences("visualcontext", MODE_PRIVATE).getBoolean("prep_on", false)) Prep.schedule(this) }

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
    private var facesOpen = false

    private fun render() {
        col.removeAllViews()
        col.setPadding(dp(16), dp(40), dp(16), dp(32) + Look.nav(col))
        // The full logo (symbol + wordmark); long-press = developer mode.
        col.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo_full)
            adjustViewBounds = true
            contentDescription = "VisualContext"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            setOnLongClickListener { dev = !dev; render(); true }
        })

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
            val status = TextView(this).apply { text = "verificando…"; textSize = 16f; setTextColor(green) }
            c1.addView(status); check(status)
            Backend.email(this).takeIf { it.isNotEmpty() }?.let { c1.p("Conta: $it", 13f) }
            c1.btn("Abrir o Manager", true) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Backend.managerLink(this)))) }
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
        c4b.p(if (on) "● Ligado · ${TalkCounter.counts(this).size} pessoas contadas" else "○ Desligado", 14f).setTextColor(if (on) green else soft)
        c4b.btn(if (on) "Desligar (acesso às notificações)" else "Ligar (acesso às notificações)", !on) { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
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
        c5.addView(android.widget.CheckBox(this).apply {
            text = "Avisar antes dos compromissos (lê a agenda do celular)"; setTextColor(soft); textSize = 14f; buttonTintList = android.content.res.ColorStateList.valueOf(green)
            isChecked = checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED && getSharedPreferences("visualcontext", MODE_PRIVATE).getBoolean("prep_on", false)
            setOnCheckedChangeListener { _, on ->
                getSharedPreferences("visualcontext", MODE_PRIVATE).edit().putBoolean("prep_on", on).apply()
                if (on) requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.POST_NOTIFICATIONS), 11)
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
        c6.addView(android.widget.CheckBox(this).apply {
            text = "Mandar receitas médicas também (dado de saúde)"; setTextColor(soft); textSize = 14f; isChecked = NotesOut.health(context); buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setOnCheckedChangeListener { _, on -> NotesOut.setHealth(context, on) }
        })

        // 7. Assistant
        val c7 = card()
        c7.h("Assistente (Gemini)")
        c7.p("Chave do Google AI Studio. Fica só neste celular; não cole em nenhum chat.", 13f)
        val gk = c7.keyRow(Backend.geminiKey(this), "AIza…",
            save = { Backend.saveGemini(this, it, Backend.voiceName(this)) },
            test = { k -> // a real call, never the cached model name
                try {
                    val c = (java.net.URL(LiveProtocol.MODELS_URL).openConnection() as java.net.HttpURLConnection).apply { setRequestProperty("x-goog-api-key", k); connectTimeout = 10_000 }
                    when (c.responseCode) { in 200..299 -> null; 400, 401, 403 -> "Chave recusada pelo Google."; 429 -> "Limite de uso do Google atingido."; else -> "Erro ${c.responseCode} do Google." }
                } catch (e: Exception) { "Sem internet." } })
        val voices = listOf("Kore", "Aoede", "Puck", "Charon")
        c7.p("Voz de quem fala com você no Live. Toque em ▶ para ouvir antes de escolher.", 13f)
        val vg = android.widget.RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        val vmsg = TextView(this).apply { textSize = 12f; setTextColor(soft) }
        voices.forEachIndexed { i, v ->
            vg.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(android.widget.RadioButton(context).apply { id = 300 + i; text = v; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = v == Backend.voiceName(context)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                addView(Button(context).apply { text = "▶ Ouvir"; isAllCaps = false; textSize = 13f; setTextColor(green); stateListAnimator = null
                    background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.TRANSPARENT); setStroke(dp(1), green) }
                    minHeight = dp(36); minimumHeight = dp(36); setPadding(dp(14), 0, dp(14), 0)
                    setOnClickListener { vmsg.text = "Gerando a voz $v…"; isEnabled = false
                        io.execute { val why = VoicePreview.play(this@SettingsActivity, v); runOnUiThread { isEnabled = true; vmsg.text = why ?: "" } } }
                })
            })
        }
        // RadioGroup only unchecks siblings that are direct children, so do it by hand.
        val rbs = (0 until voices.size).map { vg.findViewById<android.widget.RadioButton>(300 + it) }
        rbs.forEachIndexed { i, rb -> rb.setOnClickListener { rbs.forEach { it.isChecked = it === rb }; Backend.saveVoice(this, voices[i]) } }
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
            for ((name, label) in list.take(8)) pv.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                val rb = android.widget.RadioButton(context).apply { text = label; textSize = 14f; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = name == cur
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { rbs.forEach { it.isChecked = it === this }; tv.use(name) } }
                rbs.add(rb); addView(rb)
                addView(Button(context).apply { text = "▶ Ouvir"; isAllCaps = false; textSize = 13f; setTextColor(green); stateListAnimator = null
                    background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.TRANSPARENT); setStroke(dp(1), green) }
                    minHeight = dp(36); minimumHeight = dp(36); setPadding(dp(14), 0, dp(14), 0)
                    setOnClickListener { val keep = Voice.phoneVoice(this@SettingsActivity); tv.use(name); tv.say("Oi. Eu sou uma voz do seu celular. Falo mesmo sem internet.", interrupt = true); Voice.setPhoneVoice(this@SettingsActivity, keep); if (keep.isNotEmpty()) tv.use(keep) }
                })
            })
            if (Voice.phoneVoice(this).isEmpty() && cur.isNotEmpty()) Voice.setPhoneVoice(this, cur)
        }, 900)
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
        ck.p(if (skills.isEmpty()) "O assistente aprende com você: peça algo duas vezes, ou diga \"aprende isso\", e ele oferece criar uma habilidade (sua carteira de ações, notícias de um assunto, falar com o Claude). Da próxima vez é só pedir." else "${skills.size} ${if (skills.size == 1) "habilidade" else "habilidades"}. Segure uma para apagar.", 13f)
        for (sk in skills) ck.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8))
            addView(TextView(context).apply { text = "✦ " + sk.name; textSize = 16f; setTextColor(green) })
            addView(TextView(context).apply { text = sk.triggers.take(2).joinToString(" · ") { "\"$it\"" }; textSize = 13f; setTextColor(soft) })
            setOnLongClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Apagar a habilidade ${sk.name}?").setPositiveButton("Apagar") { _, _ -> Skills.remove(this@SettingsActivity, sk.slug); render() }.setNegativeButton("Cancelar", null).show(); true
            }
        })
        ck.p("Falar com outro assistente: sua chave do OpenRouter (sk-or-, qualquer modelo), da OpenAI (sk-) ou da Anthropic (sk-ant-). Fica só neste celular. É uma pergunta solta ao modelo: a memória do VisualContext não vai junto.", 13f)
        lateinit var mdl: EditText
        ck.keyRow(Claude.key(this), "sk-or-… / sk-… / sk-ant-…",
            save = { Claude.setKey(this, it); mdl.hint = Claude.defaultModel(it) },
            test = { _ -> Claude.test(this) })
        ck.p("Modelo (vazio = o padrão da chave). No OpenRouter pode ser qualquer um: anthropic/claude-sonnet-5.5, openai/gpt-5, google/gemini-2.5-pro…", 12f)
        mdl = field(getSharedPreferences("visualcontext", MODE_PRIVATE).getString("claude_model", "") ?: "", false).apply { hint = Claude.defaultModel(Claude.key(this@SettingsActivity)) }
        ck.addView(mdl)
        mdl.setOnFocusChangeListener { _, has -> if (!has) Claude.setModel(this, mdl.text.toString()) }
        mdl.setOnEditorActionListener { _, _, _ -> Claude.setModel(this, mdl.text.toString()); Toast.makeText(this, "✓ Modelo salvo", Toast.LENGTH_SHORT).show(); false }

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
        if (fromContacts.isNotEmpty()) c.addView(TextView(this).apply {
            text = "Desfazer a importação dos contatos (${fromContacts.size} fotos)"; textSize = 14f; setTextColor(Color.rgb(255, 120, 120)); setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Tirar deste celular as ${fromContacts.size} fotos que vieram dos contatos? Quem você já usou nos óculos continua com as outras fotos.")
                    .setPositiveButton("Tirar") { _, _ -> val ids = fromContacts.map { it.id }.toSet(); LocalFaces.save(this@SettingsActivity, LocalFaces.all(this@SettingsActivity).filterNot { it.id in ids }); render() }
                    .setNegativeButton("Cancelar", null).show()
            }
        })
        c.btn(if (facesOpen) "Fechar a lista" else if (fresh > 0) "Dar nome a quem falta" else "Ver e editar", fresh > 0 && !facesOpen) { facesOpen = !facesOpen; render() }
        if (!facesOpen) return
        // Search: with hundreds of people the list needs it.
        val search = field(facesQuery, false).apply { hint = "buscar nome"; setHintTextColor(Color.rgb(90, 130, 108)) }
        c.addView(search)
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

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        val thumbs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setOnClickListener { showLooks(g) } }
        g.take(3).forEachIndexed { i, e ->
            thumbs.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = if (i == 0) 0 else -dp(14) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { val b = Base64.decode(e.thumb, Base64.DEFAULT); setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.size)) } catch (_: Exception) {}
                clipToOutline = true; outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: android.view.View, o: android.graphics.Outline) { o.setOval(0, 0, v.width, v.height) } }
            })
        }
        row.addView(thumbs, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        val origin = g.mapNotNull { it.desc }.firstOrNull()?.let { d -> d.replace("rosto do contatos", "dos contatos").replace("rosto do ", "do ") } ?: ""
        row.addView(TextView(this).apply {
            textSize = 13f; setTextColor(soft); setOnClickListener { showLooks(g) }
            text = (if (g.size > 1) "${g.size} fotos" else "1 foto") + (if (origin.isNotEmpty()) " · $origin" else "") + (if (g.any { it.personId != null }) " · na memória" else "")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        box.addView(row)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        if (g[0].name != null) actions.addView(TextView(this).apply {
            text = "＋ fotos"; textSize = 14f; setTextColor(green); setPadding(0, dp(8), dp(24), dp(8))
            setOnClickListener { addTo = g[0].name; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_ADD) }
        })
        actions.addView(TextView(this).apply {
            text = "ver todas"; textSize = 14f; setTextColor(green); setPadding(0, dp(8), dp(24), dp(8)); setOnClickListener { showLooks(g) }
        })
        actions.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(TextView(this).apply {
            text = "Apagar"; textSize = 14f; setTextColor(Color.rgb(255, 120, 120)); setPadding(dp(16), dp(8), 0, dp(8))
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Apagar ${g[0].name ?: "este rosto"} deste celular?")
                    .setPositiveButton("Apagar") { _, _ -> g.forEach { LocalFaces.remove(this@SettingsActivity, it.id) }; render() }
                    .setNegativeButton("Cancelar", null).show()
            }
        })
        box.addView(actions)
        box.addView(android.view.View(this).apply { setBackgroundColor(Color.rgb(28, 64, 44)) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) })
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
        Toast.makeText(this, "Lendo ${uris.size} ${if (uris.size == 1) "foto" else "fotos"}…", Toast.LENGTH_SHORT).show()
        io.execute {
            val engine = try { ArcFaceEngine(this) } catch (_: Throwable) { null } ?: return@execute
            val pid = LocalFaces.all(this).firstOrNull { it.name.equals(name, true) && it.personId != null }?.personId
            var ok = 0
            for (u in uris) {
                val bmp = try { contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } ?: continue
                val small = if (maxOf(bmp.width, bmp.height) > 1600) android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width * 1600 / maxOf(bmp.width, bmp.height), bmp.height * 1600 / maxOf(bmp.width, bmp.height), true) else bmp
                // The biggest face in the photo is the person (a group photo: crop it first).
                val f = engine.describe(small).firstOrNull() ?: continue
                val pad = f.w * 0.25f
                val x = (f.x - pad).toInt().coerceAtLeast(0); val y = (f.y - pad).toInt().coerceAtLeast(0)
                val w = (f.w + 2 * pad).toInt().coerceAtMost(small.width - x); val h = (f.h + 2 * pad).toInt().coerceAtMost(small.height - y)
                val thumb = android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createBitmap(small, x, y, w, h), 128, 128, true)
                    .let { Base64.encodeToString(java.io.ByteArrayOutputStream().also { o -> it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, o) }.toByteArray(), Base64.NO_WRAP) }
                val e = LocalFaces.add(this, f.descriptor, thumb)
                LocalFaces.update(this, e.id) { it.name = name; it.personId = pid; it.desc = "foto que você adicionou" }
                Moments.send(this, org.json.JSONObject().put("action", "enroll_face").put("name", name).put("arc", org.json.JSONArray(f.descriptor.map { it.toDouble() }))
                    .put("thumb", thumb).put("source", "manual").put("description", "foto que você adicionou").apply { if (pid != null) put("person_id", pid) })
                ok++
            }
            engine.close()
            runOnUiThread { Toast.makeText(this, if (ok == 0) "Não achei rosto nessas fotos" else "✓ $ok ${if (ok == 1) "foto adicionada" else "fotos adicionadas"} a $name", Toast.LENGTH_LONG).show(); render() }
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
        val sb = btn("Salvar", true) {
            val v = f.text.toString().trim()
            if (v.length < 12) { Toast.makeText(context, "Cole a chave primeiro", Toast.LENGTH_SHORT).show(); return@btn }
            save(v); cur = v; f.setText(""); f.hint = "colar outra chave…"
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
        // Side by side: move the two buttons from the card into the row.
        removeView(sb); removeView(tb)
        for ((i, b) in listOf(sb, tb).withIndex()) row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { topMargin = dp(8); if (i == 0) rightMargin = dp(8) })
        return f
    }

    private fun field(v: String, secret: Boolean) = EditText(this).apply {
        setText(v); setTextColor(green); textSize = 14f; isSingleLine = true
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    companion object { private const val REQ_ADD = 61 }
}
