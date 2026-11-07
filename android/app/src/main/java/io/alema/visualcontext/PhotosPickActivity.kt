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
    private val ticks = HashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 96, 40, 64) }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
        if (PhotosCaptureService.running) PhotosCaptureService.stop(this)
        render()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (PhotosCaptureService.running) PhotosCaptureService.stop(this); col.postDelayed({ render() }, 600) }

    private fun render() {
        col.removeAllViews()
        val found = PhotosGrid.last
        if (found.isEmpty()) intro() else pick(found)
    }

    private fun intro() {
        col.addView(label("Importar do Google Fotos", 20f, green))
        col.addView(label("O Google Fotos já sabe o nome das pessoas que aparecem nas suas fotos. O VisualContext lê essa tela, com a sua permissão, e aprende os rostos. Nada sai do celular até você escolher quem entra."))
        col.addView(label("Como funciona", 15f, green))
        col.addView(label("1. Toque em Começar e permita a captura de tela.\n2. O Google Fotos abre. Vá em Coleções → Pessoas e animais.\n3. Role devagar até o fim da lista.\n4. Toque em Terminei, na notificação.\n5. Escolha quem entra. Pronto."))
        col.addView(label("Quem não tem nome no Google Fotos fica de fora. Você mesmo também: desmarque o seu rosto na hora de escolher.", 13f))
        if (PhotosCaptureService.running) {
            col.addView(label("Lendo a tela agora: ${PhotosCaptureService.count} pessoas até aqui.", 15f, green))
            col.addView(Button(this).apply { text = "Terminei"; setOnClickListener { PhotosCaptureService.stop(this@PhotosPickActivity); col.postDelayed({ render() }, 800) } })
            return
        }
        col.addView(Button(this).apply {
            text = "Começar"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTE)
                else askCapture()
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_NOTE) askCapture()
    }

    private fun askCapture() {
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Activity result API kept simple here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) { col.addView(label("Sem a permissão de captura não dá para ler o Google Fotos.", 14f, green)); return }
        PhotosGrid.last = emptyList()
        startForegroundService(Intent(this, PhotosCaptureService::class.java).putExtra(PhotosCaptureService.EXTRA_CODE, resultCode).putExtra(PhotosCaptureService.EXTRA_DATA, data))
        val photos = packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
        col.postDelayed({
            if (photos != null) startActivity(photos) else { col.addView(label("Não achei o Google Fotos neste celular.", 14f, green)); PhotosCaptureService.stop(this) }
        }, 700)
    }

    private fun pick(found: List<PhotosGrid.Found>) {
        val known = try { LocalFaces.all(this).mapNotNull { it.name?.let(PhotosGrid::norm) }.toSet() } catch (_: Exception) { emptySet() }
        col.addView(label("${found.size} pessoas encontradas", 20f, green))
        col.addView(label("Todas vêm marcadas. Desmarque quem não quiser (e você mesmo). Nome cortado (…) ou errado? Segure o dedo na pessoa para corrigir.", 13f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(this).apply { text = "Marcar todos"; setOnClickListener { ticks.values.forEach { it.isChecked = true } } })
        bar.addView(Button(this).apply { text = "Limpar"; setOnClickListener { ticks.values.forEach { it.isChecked = false } } })
        col.addView(bar)
        col.addView(Button(this).apply { text = "↺ Capturar de novo"; setOnClickListener { PhotosGrid.last = emptyList(); render(); askCapture() } })
        val grid = GridLayout(this).apply { columnCount = 3 }
        val cell = (resources.displayMetrics.widthPixels - 80) / 3
        ticks.clear()
        for (f in found) {
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
                text = f.name + if (PhotosGrid.norm(f.name) in known) "\n(já tem rosto)" else ""
                setTextColor(soft); textSize = 12f; isChecked = true; buttonTintList = android.content.res.ColorStateList.valueOf(green)
            }
            box.setOnClickListener { cb.toggle() }
            // Long press: fix the name (cut names, nicknames).
            val rename = View.OnLongClickListener {
                val et = android.widget.EditText(this).apply { setText(f.name); setSelection(f.name.length) }
                android.app.AlertDialog.Builder(this).setTitle("Nome").setView(et)
                    .setPositiveButton("OK") { _, _ -> val n = et.text.toString().trim(); if (n.length > 1) { ticks.remove(f.name); f.name = n; cb.text = n; ticks[n] = cb; cb.isChecked = true } }
                    .setNegativeButton("Cancelar", null).show()
                true
            }
            box.setOnLongClickListener(rename); cb.setOnLongClickListener(rename)
            box.addView(cb)
            ticks[f.name] = cb
            grid.addView(box)
        }
        col.addView(grid)
        val status = label("", 14f, green)
        col.addView(Button(this).apply {
            text = "Guardar os marcados"
            setOnClickListener { b -> b.isEnabled = false; save(found.filter { ticks[it.name]?.isChecked == true }, status) }
        })
        col.addView(status)
        col.addView(Button(this).apply { text = "↺ Capturar de novo"; setOnClickListener { PhotosGrid.last = emptyList(); render(); askCapture() } })
    }

    /** Spec 5b step 4: faces from Google Photos recognise right away, marked as such; the first real encounter confirms. */
    private fun save(list: List<PhotosGrid.Found>, status: TextView) {
        if (list.isEmpty()) { status.text = "Nenhuma pessoa marcada."; return }
        io.execute {
            var ok = 0; var offline = 0
            val all = LocalFaces.all(this)
            for ((i, f) in list.withIndex()) {
                runOnUiThread { status.text = "Guardando ${i + 1} de ${list.size}…" }
                val r = Backend.post(this, JSONObject().put("action", "enroll_face").put("name", f.name).put("arc", org.json.JSONArray(f.descriptor.map { it.toDouble() }))
                    .put("thumb", f.thumb).put("source", "google_photos").put("description", "rosto do Google Fotos"))
                val pid = r?.let { try { JSONObject(it).optString("person_id").ifEmpty { null } } catch (_: Exception) { null } }
                if (r == null) offline++
                val now = System.currentTimeMillis()
                all += LocalFaces.Entry(java.util.UUID.randomUUID().toString(), f.name, pid, f.descriptor, f.thumb, now, 0, "rosto do Google Fotos")
                ok++
            }
            LocalFaces.save(this, all)
            PhotosGrid.last = emptyList()
            runOnUiThread {
                status.text = "$ok ${if (ok == 1) "pessoa guardada" else "pessoas guardadas"}." + if (offline > 0) " $offline ficaram só neste celular (sem internet agora)." else " Já aparecem no Manager."
                col.addView(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } })
            }
        }
    }

    private fun label(t: String, size: Float = 14f, c: Int = soft) = TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 20, 0, 8) }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    companion object { private const val REQ_CAPTURE = 71; private const val REQ_NOTE = 72 }
}
