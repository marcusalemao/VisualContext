package io.alema.visualcontext

import io.alema.facecore.LocalFaces
import io.alema.facecore.ArcFaceEngine
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
    @Volatile private var people: List<Backend.Person> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
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
            .setTitle("Conectar os óculos")
            .setMessage("Ligar este celular à sua memória do VisualContext?")
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
        col.setPadding(dp(16), dp(40), dp(16), dp(32))
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
            c1.h("Conectar à sua memória")
            c1.p("Cole o código do aparelho (começa com vc_). Depois disso, o Manager e o backup funcionam neste celular.")
            val t = field("", true).apply { hint = "vc_…" }
            c1.addView(t)
            c1.btn("Conectar", true) {
                val v = t.text.toString().trim()
                if (DeepLink.parse(v) == null) Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show()
                else { Backend.save(this, Backend.url(this), v); render() }
            }
        } else {
            val status = TextView(this).apply { text = "verificando…"; textSize = 16f; setTextColor(green) }
            c1.addView(status); check(status)
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
        val gk = field(Backend.geminiKey(this), true).apply { hint = "AIza…" }
        c7.addView(gk)
        val voices = listOf("Kore", "Aoede", "Puck", "Charon")
        c7.p("Voz de quem fala com você no Live", 13f)
        val vg = android.widget.RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL
            voices.forEachIndexed { i, v -> addView(android.widget.RadioButton(context).apply { id = 300 + i; text = v; setTextColor(soft); buttonTintList = android.content.res.ColorStateList.valueOf(green); isChecked = v == Backend.voiceName(context) }) } }
        c7.addView(vg)
        c7.btn("Salvar chave e voz", false) { Backend.saveGemini(this, gk.text.toString(), voices.getOrElse(vg.checkedRadioButtonId - 300) { "Kore" }); Toast.makeText(this, "✓ Salvo", Toast.LENGTH_SHORT).show() }

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
        c.btn(if (facesOpen) "Fechar a lista" else if (fresh > 0) "Dar nome a quem falta" else "Ver e editar", fresh > 0 && !facesOpen) { facesOpen = !facesOpen; render() }
        if (!facesOpen) return
        for (g in groups) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(4)) }
            val thumbs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            g.take(3).forEachIndexed { i, e ->
                thumbs.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = if (i == 0) 0 else -dp(16) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    try { val b = Base64.decode(e.thumb, Base64.DEFAULT); setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.size)) } catch (_: Exception) {}
                    clipToOutline = true; outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: android.view.View, o: android.graphics.Outline) { o.setOval(0, 0, v.width, v.height) } }
                })
            }
            thumbs.setOnClickListener { showLooks(g) }
            row.addView(thumbs, LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
            val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val saved = TextView(this).apply { textSize = 12f; setTextColor(green) }
            val name = AutoCompleteTextView(this).apply {
                setText(g[0].name ?: ""); hint = "quem é?"; setTextColor(green); setHintTextColor(Color.rgb(90, 130, 108)); isSingleLine = true; threshold = 1; textSize = 16f
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
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
            side.addView(name)
            side.addView(TextView(this).apply {
                textSize = 12f; setTextColor(soft)
                text = (if (g.size > 1) "${g.size} fotos" else "1 foto") + (if (g.any { it.personId != null }) " · ligado à memória" else "") + (g[0].desc?.let { " · $it" } ?: "")
            })
            side.addView(saved)
            if (g[0].name != null) side.addView(TextView(this).apply {
                text = "＋ adicionar fotos · ver todas"; textSize = 13f; setTextColor(green); setPadding(0, dp(4), 0, 0)
                setOnClickListener { addTo = g[0].name; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_ADD) }
                setOnLongClickListener { showLooks(g); true }
            })
            row.addView(side)
            row.addView(Button(this).apply {
                text = "Apagar"; isAllCaps = false; setTextColor(Color.rgb(255, 120, 120)); setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity).setMessage("Apagar ${g[0].name ?: "este rosto"} deste celular?")
                        .setPositiveButton("Apagar") { _, _ -> g.forEach { LocalFaces.remove(this@SettingsActivity, it.id) }; render() }
                        .setNegativeButton("Cancelar", null).show()
                }
            })
            c.addView(row)
        }
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

    private fun field(v: String, secret: Boolean) = EditText(this).apply {
        setText(v); setTextColor(green); textSize = 14f; isSingleLine = true
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    companion object { private const val REQ_ADD = 61 }
}
