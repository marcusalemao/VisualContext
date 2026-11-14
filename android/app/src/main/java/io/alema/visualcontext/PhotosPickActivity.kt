package io.alema.visualcontext

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.alema.facecore.FaceMatch
import io.alema.facecore.LocalFaces
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Spec 5b, the whole flow on one screen:
 * explain → screen-capture permission → open Google Photos → the user scrolls "People and pets" →
 * "Terminei" (notification) brings them back here → pick who comes in (all ticked, children too) → save.
 */
class PhotosPickActivity : Activity() {
    private val green = Color.rgb(0, 255, 136)
    private val soft = Color.rgb(143, 223, 181)
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 96, 40, 64) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        if (PhotosCaptureService.running) PhotosCaptureService.stop(this)
        PhotosAuto.stop()
        Look.insets(col)
        render()
        sharedImages(intent)?.let { readImages(it) }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float { var d = 0f; for (i in a.indices) d += a[i] * b[i]; return d } // unit vectors: dot = cosine

    /** Same buttons as Settings: filled = the main action of the step, outline = the rest. */
    private fun restyle(v: android.view.View = col) {
        if (v is Button) {
            val t = v.text.toString(); Look.button(v, t.startsWith("Guardar") || t.startsWith("Importar contatos") || t.startsWith("Capturar enquanto") || t == "Terminei")
            (v.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { if (it.topMargin < Look.dp(this, 10)) { it.topMargin = Look.dp(this, 10); v.layoutParams = it } }
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) restyle(v.getChildAt(i))
    }

    /** Phone contacts with a photo: one face each, the name is the contact's. */
    /**
     * Phone contacts with a photo, the people you deal with first (Marcus, 07/11: "não pegar todos de uma vez"):
     * favourites, the ones you call most (call log, if you allow it) and people already in your memory come
     * ticked; everyone else comes unticked, below. Group photos and tiny pictures are left out.
     */
    private fun readContacts() {
        col.removeAllViews(); col.addView(label("Lendo os contatos com foto…", 18f, green))
        val prog = Look.Progress(this, "Lendo os contatos com foto", 0)
        io.execute {
            val g = PhotosGrid(this)
            var bad = 0
            try {
                // How much you deal with each number: calls in the last 180 days (only with permission).
                val calls = HashMap<String, Int>()
                if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) try {
                    contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI, arrayOf(android.provider.CallLog.Calls.CACHED_NAME),
                        "${android.provider.CallLog.Calls.DATE} > ?", arrayOf((System.currentTimeMillis() - 180L * 86_400_000).toString()), null)?.use { c ->
                        while (c.moveToNext()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { calls[it] = (calls[it] ?: 0) + 1 }
                    }
                } catch (_: Exception) {}
                val memory = (Backend.peopleFull(this) ?: emptyList()).map { it.name.trim().lowercase() }.toSet()
                // Who you talk to most on WhatsApp, shared by the Zap app (counts only, never the messages; off by default there).
                val zap = HashMap<String, Int>()
                try {
                    contentResolver.query(android.net.Uri.parse("content://io.alema.zap.frequent/people"), arrayOf("name", "messages_30d"), null, null, null)?.use { c ->
                        while (c.moveToNext()) c.getString(0)?.trim()?.lowercase()?.let { zap[it] = c.getInt(1) }
                    }
                } catch (_: Exception) {}
                // Our own count (no Zap needed), merged: the bigger number wins.
                TalkCounter.counts(this).forEach { (k, v) -> if (v > (zap[k] ?: 0)) zap[k] = v }
                contentResolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, android.provider.ContactsContract.Contacts.PHOTO_URI, android.provider.ContactsContract.Contacts.STARRED),
                    "${android.provider.ContactsContract.Contacts.PHOTO_URI} IS NOT NULL", null, null)?.use { c ->
                    var n = 0
                    while (c.moveToNext()) {
                        val name = c.getString(0)?.trim() ?: continue
                        val uri = c.getString(1) ?: continue
                        if (name.length < 2 || name.any { it.isDigit() }) continue
                        val bmp = try { contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } ?: continue
                        val f = g.readOne(bmp, name); bmp.recycle()
                        if (f == null) { bad++; continue }
                        val star = c.getInt(2) == 1; val nc = calls[name] ?: 0; val mem = name.lowercase() in memory; val zm = zap[name.lowercase()] ?: 0
                        f.score = (if (star) 100 else 0) + nc * 5 + zm + (if (mem) 50 else 0) + (if (name.split(' ').size > 1) 2 else 0)
                        f.why = listOfNotNull(if (star) "⭐ favorito" else null, if (zm > 0) "💬 $zm mensagens" else null, if (nc > 0) "$nc ${if (nc == 1) "ligação" else "ligações"}" else null, if (mem) "na memória" else null).joinToString(" · ")
                        if (++n % 10 == 0) runOnUiThread { col.removeAllViews(); col.addView(label("Lendo os contatos com foto… $n", 18f, green)) }
                    }
                }
                val sorted = g.found.sortedByDescending { it.score }
                // Ticked: whoever has a reason (favourite, calls, memory), at most 40; the rest wait unticked.
                sorted.forEachIndexed { i, f -> f.tick = f.score > 2 && i < 40 }
                PhotosGrid.last = sorted; PhotosGrid.lastNameless = emptyList()
                contactsSkipped = bad
            } catch (_: Throwable) { } finally { g.close(); prog.done() }
            runOnUiThread { if (PhotosGrid.last.isEmpty()) { render(); col.addView(label("Nenhum contato com foto de rosto boa.", 14f, green)) } else render() }
        }
    }

    private var contactsSkipped = 0

    /** A long screenshot shared from the gallery ("Compartilhar → VisualContext"). */
    private fun sharedImages(i: Intent?): List<android.net.Uri>? = when (i?.action) {
        Intent.ACTION_SEND -> (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))?.let { listOf(it) }
        Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        else -> null
    }

    /**
     * Option without screen sharing: one long screenshot (Samsung "captura com rolagem", or several prints) of
     * "People and pets". A tall picture is read in screen-sized slices that overlap, so no name is cut in half.
     */
    private fun readImages(uris: List<android.net.Uri>) {
        col.removeAllViews()
        col.addView(label("Lendo o print…", 18f, green))
        val prog = Look.Progress(this, "Lendo ${uris.size} ${if (uris.size == 1) "imagem" else "imagens"}", uris.size)
        io.execute {
            val g = PhotosGrid(this)
            try {
                for ((i, u) in uris.withIndex()) {
                    prog.step(i, "recortando rostos e lendo nomes")
                    // Region by region, so a very long print never has to fit in memory at once.
                    val dec = contentResolver.openInputStream(u)?.use { android.graphics.BitmapRegionDecoder.newInstance(it) } ?: continue
                    val w = dec.width; val total = dec.height
                    val sliceH = (w * 2).coerceAtMost(total)
                    var top = 0
                    while (true) {
                        val h = minOf(sliceH, total - top)
                        dec.decodeRegion(android.graphics.Rect(0, top, w, top + h), null)?.let { part -> g.read(part); part.recycle() }
                        if (top + h >= total) break
                        top += (sliceH * 0.6f).toInt()
                    }
                    dec.recycle()
                }
                PhotosGrid.last = g.found.toList()
                PhotosGrid.lastNameless = g.nameless.toList()
            } catch (_: Throwable) { } finally { g.close(); prog.done() }
            runOnUiThread { if (PhotosGrid.last.isEmpty() && PhotosGrid.lastNameless.isEmpty()) { render(); col.addView(label("Não achei rostos com nome nesse print. Confira se é a tela Pessoas e animais.", 14f, green)) } else render() }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); sharedImages(intent)?.let { readImages(it); return }; if (PhotosCaptureService.running) PhotosCaptureService.stop(this); col.postDelayed({ render() }, 600) }

    private fun render() {
        col.removeAllViews()
        val found = PhotosGrid.last
        if (found.isEmpty() && PhotosGrid.lastNameless.isEmpty()) intro() else pick(found)
        restyle()
    }

    private fun intro() {
        col.addView(label("Importar rostos", 20f, green))
        col.addView(label("De qualquer lista com foto e nome. O VisualContext lê, e nada sai do celular até você escolher quem entra."))
        // Prints first (Marcus, 11/11): everybody knows how to take one, and they read better than a screen recording.
        col.addView(label("Por prints (recomendado)", 15f, green))
        col.addView(label("1. Abra uma lista com rosto e nome: Google Fotos → Pessoas e animais, conexões do LinkedIn, seguidores do Instagram, um grupo do WhatsApp.\n2. Tire um print longo (com rolagem), ou vários prints seguidos, rolando entre um e outro.\n3. Toque abaixo e escolha os prints de uma vez, ou compartilhe os prints com o VisualContext.\n\nBasta a tela da lista: não precisa entrar em cada pessoa. Os óculos juntam rostos melhores com o tempo, a cada encontro.", 13f))
        col.addView(Button(this).apply { text = "Escolher prints"; setOnClickListener { source = "print"
            startActivityForResult(Look.pickPhotos(if (android.os.Build.VERSION.SDK_INT >= 33) 30 else 12), REQ_IMAGE)
        } })
        col.addView(label("Rostos sem nome aparecem à parte, para você dar nome se conhecer. Desmarque o seu próprio rosto na hora de escolher.", 13f))
        // Phone contacts: the photo is already there, no screenshot needed.
        col.addView(label("Contatos do celular", 15f, green))
        col.addView(label("Os contatos com foto de rosto boa. Vêm marcados primeiro os favoritos, com quem você mais liga (se permitir ver o histórico de chamadas) e quem já está na sua memória. Fotos de grupo e muito pequenas ficam de fora.", 13f))
        col.addView(Button(this).apply { text = "Importar contatos com foto"; setOnClickListener {
            source = "contatos"
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_CALL_LOG), REQ_CONTACTS)
            else readContacts()
        } })
        if (PhotosCaptureService.running) {
            col.addView(label("Lendo a tela agora: ${PhotosCaptureService.count} pessoas até aqui.", 15f, green))
            col.addView(Button(this).apply { text = "Terminei"; setOnClickListener { PhotosCaptureService.stop(this@PhotosPickActivity); col.postDelayed({ render() }, 800) } })
            return
        }
        // One way to record, for any app (Marcus, 11/11): the source doesn't matter, only a face next to a name.
        col.addView(label("Ou navegue enquanto eu leio a tela", 15f, green))
        col.addView(label("Toque abaixo, permita a captura de tela e abra qualquer app com rostos e nomes (Google Fotos, LinkedIn, Instagram, WhatsApp…). Role devagar; eu vou guardando. No fim, toque em Terminei, na notificação, e escolha quem entra.", 13f))
        col.addView(Button(this).apply { text = "Ler a tela enquanto navego"; setOnClickListener {
            source = "tela"; openPkg = ""; useApp = false; auto = false
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTE)
            else askCapture()
        } })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_WRITE) { val ok = grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }; pendingStatus?.let { save(pendingSave, it, ok) }; return }
        if (requestCode == REQ_CONTACTS) { if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) readContacts(); return }
        if (requestCode == REQ_NOTE) askCapture()
    }

    private var useApp = false
    /** Where the faces come from, said on each face ("rosto do LinkedIn"). */
    private var source = "Google Fotos"
    /** For LinkedIn/Facebook/Instagram: the app to open once the capture starts ("" = the user opens it). */
    private var openPkg: String? = null
    private fun originLabel() = when (source) { "contatos" -> "rosto dos contatos"; "print" -> "rosto de um print"; "tela" -> "rosto lido na tela"; else -> "rosto do $source" }
    private var auto = false

    private fun askCapture() {
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Activity result API kept simple here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_IMAGE) {
            if (resultCode != RESULT_OK || data == null) return
            val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            if (uris.isNotEmpty()) readImages(uris)
            return
        }
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) { col.addView(label("Sem a permissão de captura não dá para ler a tela. Use os prints.", 14f, green)); return }
        PhotosGrid.last = emptyList()
        startForegroundService(Intent(this, PhotosCaptureService::class.java).putExtra(PhotosCaptureService.EXTRA_CODE, resultCode).putExtra(PhotosCaptureService.EXTRA_DATA, data))
        // Marcus, 07/11: on the web, in landscape at ~80% zoom, names are almost never cut (the app cuts them).
        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://photos.google.com/people")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chrome = Intent(web).setPackage("com.android.chrome")
        val app = packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
        col.postDelayed({
            try {
                val other = openPkg
                if (other != null) {
                    if (other.isNotEmpty()) packageManager.getLaunchIntentForPackage(other)?.let { startActivity(it) }
                        ?: android.widget.Toast.makeText(this, "$source não está instalado: abra pelo navegador", android.widget.Toast.LENGTH_LONG).show()
                    else moveTaskToBack(true)
                    return@postDelayed
                }
                startActivity(if (useApp && app != null) app else if (chrome.resolveActivity(packageManager) != null) chrome else web)
                if (auto && !PhotosAuto.start()) android.widget.Toast.makeText(this, "Ligue a rolagem automática em Acessibilidade e tente de novo", android.widget.Toast.LENGTH_LONG).show()
            }
            catch (_: Exception) { col.addView(label("Não consegui abrir o Google Fotos.", 14f, green)); PhotosCaptureService.stop(this) }
        }, 700)
    }

    /** Every face on the screen, named or not, with its tick (only ticked faces with a name are saved). */
    private val picked = LinkedHashMap<PhotosGrid.Found, CheckBox>()

    private fun grid(list: List<PhotosGrid.Found>, named: Boolean): GridLayout {
        val known = try { LocalFaces.all(this).mapNotNull { it.name?.let(PhotosGrid::norm) }.toSet() } catch (_: Exception) { emptySet() }
        val grid = GridLayout(this).apply { columnCount = 3 }
        val cell = (resources.displayMetrics.widthPixels - 80) / 3
        for (f in list) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(8, 16, 8, 16)
                layoutParams = GridLayout.LayoutParams().apply { width = cell }
            }
            box.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(cell - 40, cell - 40)
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { val b = Base64.decode(f.thumb, Base64.DEFAULT); setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.size)) } catch (_: Exception) {}
            })
            val cb = CheckBox(this).apply {
                text = if (f.name.isEmpty()) "dar nome" else f.name + (if (f.why.isNotEmpty()) "\n" + f.why else "") + if (PhotosGrid.norm(f.name) in known) "\n(já tem rosto)" else ""
                setTextColor(soft); textSize = 12f; isChecked = named && f.tick; buttonTintList = android.content.res.ColorStateList.valueOf(green)
            }
            val rename = View.OnLongClickListener { askName(f, cb); true }
            if (named) { box.setOnClickListener { cb.toggle() } } else { box.setOnClickListener { askName(f, cb) }; cb.setOnClickListener { if (f.name.isEmpty()) { cb.isChecked = false; askName(f, cb) } } }
            box.setOnLongClickListener(rename); cb.setOnLongClickListener(rename)
            box.addView(cb)
            picked[f] = cb
            grid.addView(box)
        }
        return grid
    }

    private fun askName(f: PhotosGrid.Found, cb: CheckBox) {
        val et = android.widget.EditText(this).apply { setText(f.name); setSelection(f.name.length); hint = "nome" }
        android.app.AlertDialog.Builder(this).setTitle("Quem é?").setView(et)
            .setPositiveButton("OK") { _, _ -> val n = et.text.toString().trim(); if (n.length > 1) { f.name = n; cb.text = n; cb.isChecked = true } }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pick(found: List<PhotosGrid.Found>) {
        col.addView(label("${found.size} pessoas com nome", 20f, green))
        col.addView(label(if (source == "contatos") "Marcados: ${found.count { it.tick }} (favoritos, com quem você mais liga, quem já está na memória). Os outros vêm desmarcados, abaixo." + (if (contactsSkipped > 0) " $contactsSkipped fotos ficaram de fora (grupo, muito pequenas ou sem rosto)." else "")
            else "Todas vêm marcadas. Desmarque quem não quiser (e você mesmo). Nome cortado (…) ou errado? Segure o dedo na pessoa para corrigir.", 13f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(this).apply { text = "Marcar todos"; setOnClickListener { picked.forEach { (f, c) -> if (f.name.isNotEmpty()) c.isChecked = true } } })
        bar.addView(Button(this).apply { text = "Limpar"; setOnClickListener { picked.values.forEach { it.isChecked = false } } })
        col.addView(bar)
        col.addView(Button(this).apply { text = "↺ Capturar de novo"; setOnClickListener { PhotosGrid.last = emptyList(); PhotosGrid.lastNameless = emptyList(); render(); askCapture() } })
        picked.clear()
        col.addView(grid(found, true))
        val nl = PhotosGrid.lastNameless
        if (nl.isNotEmpty()) {
            col.addView(label("Sem nome no Google Fotos (${nl.size})", 16f, green))
            col.addView(label("Conhece? Toque e digite o nome. Só entra quem ganhar um nome aqui.", 13f))
            col.addView(grid(nl, false))
        }
        val toContacts = CheckBox(this).apply {
            text = "Também pôr a foto na agenda do celular (só contatos que já existem, sem foto, e com nome completo único)"
            setTextColor(soft); textSize = 13f; isChecked = getSharedPreferences("visualcontext", MODE_PRIVATE).getBoolean("photos_to_contacts", true)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setOnCheckedChangeListener { _, on -> getSharedPreferences("visualcontext", MODE_PRIVATE).edit().putBoolean("photos_to_contacts", on).apply() }
        }
        if (source != "contatos") col.addView(toContacts)
        val status = label("", 14f, green)
        col.addView(Button(this).apply {
            text = "Guardar os marcados"
            setOnClickListener { b ->
                val list = picked.filter { (f, c) -> c.isChecked && f.name.isNotEmpty() }.keys.toList()
                pendingSave = list; pendingStatus = status; b.isEnabled = false; b.alpha = 0.45f
                val wantContacts = toContacts.isChecked && source != "contatos"
                if (wantContacts && (checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED))
                    requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS), REQ_WRITE)
                else save(list, status, wantContacts)
            }
        })
        col.addView(status)
        col.addView(Button(this).apply { text = "↺ Capturar de novo"; setOnClickListener { PhotosGrid.last = emptyList(); PhotosGrid.lastNameless = emptyList(); render(); askCapture() } })
    }

    /** Spec 5b step 4: faces from Google Photos recognise right away, marked as such; the first real encounter confirms. */
    private var pendingSave: List<PhotosGrid.Found> = emptyList()
    private var pendingStatus: TextView? = null

    private fun save(list: List<PhotosGrid.Found>, status: TextView, toContacts: Boolean = false) {
        if (list.isEmpty()) { status.text = "Nenhuma pessoa marcada."; return }
        val prog = Look.Progress(this, "Guardando ${list.size} ${if (list.size == 1) "pessoa" else "pessoas"}", list.size)
        io.execute {
            var ok = 0; var offline = 0
            val all = LocalFaces.all(this)
            for ((i, f) in list.withIndex()) {
                prog.step(i, f.name)
                runOnUiThread { status.text = "Guardando ${i + 1} de ${list.size}…" }
                val r = Backend.post(this, JSONObject().put("action", "enroll_face").put("name", f.name).put("arc", org.json.JSONArray(f.descriptor.map { it.toDouble() }))
                    .put("thumb", f.thumb).put("source", "import_" + source.lowercase().replace(' ', '_')).put("description", originLabel()))
                val pid = r?.let { try { JSONObject(it).optString("person_id").ifEmpty { null } } catch (_: Exception) { null } }
                if (r == null) offline++
                val now = System.currentTimeMillis()
                if (all.none { e -> e.name.equals(f.name, true) && e.descriptor.size == f.descriptor.size && dot(e.descriptor, f.descriptor) >= 0.93f })
                    all += LocalFaces.Entry(java.util.UUID.randomUUID().toString(), f.name, pid, f.descriptor, f.thumb, now, 0, originLabel())
                ok++
            }
            LocalFaces.save(this, all)
            // Two birds: the same face also enriches the phone's contacts (spec 5b).
            var set = 0; var had = 0; var twins = 0
            if (toContacts) for (f in list) {
                val jpg = f.photo ?: continue
                when (ContactPhotos.put(this, f.name, jpg)) { ContactPhotos.Result.SET -> set++; ContactPhotos.Result.HAS_PHOTO -> had++; ContactPhotos.Result.SAME_NAME -> twins++; else -> Unit }
            }
            val agenda = if (!toContacts) "" else "\nAgenda: $set fotos novas" + (if (had > 0) " · $had já tinham foto" else "") + (if (twins > 0) " · $twins com nome repetido ou incompleto (ficaram de fora)" else "")
            PhotosGrid.last = emptyList(); PhotosGrid.lastNameless = emptyList()
            prog.done(); runOnUiThread {
                status.text = "$ok ${if (ok == 1) "pessoa guardada" else "pessoas guardadas"}." + if (offline > 0) " $offline ficaram só neste celular (sem internet agora)." else " Já aparecem no Manager." + agenda
                col.addView(Look.button(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } }, true))
            }
        }
    }

    private fun label(t: String, size: Float = 14f, c: Int = soft) = TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 20, 0, 8) }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    companion object { private const val REQ_CAPTURE = 71; private const val REQ_NOTE = 72; private const val REQ_IMAGE = 73; private const val REQ_CONTACTS = 74; private const val REQ_WRITE = 75 }
}
