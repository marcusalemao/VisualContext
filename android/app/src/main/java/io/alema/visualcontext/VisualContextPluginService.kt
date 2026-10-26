package io.alema.visualcontext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusImage
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSnapshotCallbacks
import com.anezium.rokidbus.client.plugin.NexusSnapshotError
import com.anezium.rokidbus.client.plugin.NexusSnapshotSession
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.ImageSurfaceContract
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * VisualContext 0.2 — "Quem é?".
 * Tap: photo from the glasses → faces found and compared on the phone → short person card
 * (name, how you know each other, last time). New face → "Pessoa nova · toque: guardar".
 * Back: close. The photo never leaves the phone for recognition.
 */
class VisualContextPluginService : NexusPluginService() {
    private enum class State { IDLE, LOOKING, NEW_FACE }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var surface: NexusSurfaceSession? = null
    private var snapshot: NexusSnapshotSession? = null
    private var engine: FaceEngine? = null
    private var arc: ArcFaceEngine? = null
    private var revision = 0
    private var state = State.IDLE
    @Volatile private var people: List<Backend.Person> = emptyList()
    @Volatile private var peopleAt = 0L
    private var newFace: Pair<FloatArray, String>? = null

    override fun onNexusOpen() {
        surface = nexusSurfaceSession(SURFACE_ID)
        if (Backend.engine(this) == "arc") io.execute { if (arc == null) arc = try { ArcFaceEngine(applicationContext) } catch (_: Throwable) { null } }
        else if (engine == null) engine = FaceEngine(applicationContext).also { it.warm() }
        state = State.IDLE
        show("VisualContext", listOf("olhe para a pessoa", "e toque"), "toque: quem é? · voltar: fechar")
        loadPeople(force = false)
    }

    override fun onNexusClose() { surface?.hide(); surface = null; snapshot?.cancel() }

    override fun onDestroy() {
        engine?.destroy(); engine = null
        arc?.close(); arc = null
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when (state) {
                State.IDLE -> look()
                State.LOOKING -> Unit
                State.NEW_FACE -> keepNewFace()
            }
            KeyEvent.KEYCODE_BACK -> if (state == State.NEW_FACE) {
                newFace = null; state = State.IDLE
                show("VisualContext", listOf("não guardei"), "toque: quem é? · voltar: fechar")
            } else { surface?.hide(); surface = null }
        }
    }

    private fun loadPeople(force: Boolean) {
        if (!force && SystemClock.elapsedRealtime() - peopleAt < 5 * 60_000 && people.isNotEmpty()) return
        io.execute { Backend.peopleFull(applicationContext)?.let { people = it; peopleAt = SystemClock.elapsedRealtime() } }
    }

    private fun look() {
        state = State.LOOKING
        show("VisualContext", listOf("olhando…"), "")
        val s = nexusSnapshotSession(object : NexusSnapshotCallbacks {
            override fun onSnapshotCaptured(jpeg: ByteArray) { io.execute { analyse(jpeg) } }
            override fun onSnapshotError(error: NexusSnapshotError) {
                main.post { idle("a câmera não respondeu", error.name.lowercase()) }
            }
        })
        snapshot = s
        if (s?.capture() != NexusSdkResult.SENT) idle("câmera não liberada", "Nexus → Plugins → VisualContext")
    }

    /** io thread: shrink to 640 px (faster on the phone, enough for faces at conversation distance). */
    private fun analyse(jpeg: ByteArray) {
        val full = try { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) } catch (_: Exception) { null }
        if (full == null) { main.post { idle("foto ilegível", null) }; return }
        val scale = minOf(1f, 640f / maxOf(full.width, full.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
        if (Backend.engine(this) == "arc") {
            val a = arc ?: try { ArcFaceEngine(applicationContext).also { arc = it } } catch (_: Throwable) { null }
            if (a == null) { main.post { idle("motor de rostos não abriu", null) }; return }
            val face = a.describe(bmp).firstOrNull()
            if (face == null) main.post { idle("não vi rosto", "chegue mais perto ou olhe direto") }
            else identify(face, crop(bmp, face))
            return
        }
        val small = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        val b64 = Base64.encodeToString(small, Base64.NO_WRAP)
        main.post {
            val e = engine ?: return@post idle("motor de rostos parado", null)
            e.describe(b64) { r ->
                when (r) {
                    is FaceEngine.Result.Failed -> idle("não consegui analisar", r.reason.take(40))
                    is FaceEngine.Result.Faces -> {
                        val face = r.faces.firstOrNull()
                        if (face == null) idle("não vi rosto", "chegue mais perto ou olhe direto")
                        else io.execute { identify(face, crop(bmp, face)) }
                    }
                }
            }
        }
    }

    private fun crop(bmp: Bitmap, f: FaceEngine.Face): String = try {
        val pad = f.w * 0.25f
        val x = (f.x - pad).toInt().coerceIn(0, bmp.width - 1)
        val y = (f.y - pad).toInt().coerceIn(0, bmp.height - 1)
        val w = (f.w + 2 * pad).toInt().coerceAtMost(bmp.width - x)
        val h = (f.h + 2 * pad).toInt().coerceAtMost(bmp.height - y)
        val c = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, x, y, w, h), 128, 128 * h / maxOf(w, 1), true)
        Base64.encodeToString(ByteArrayOutputStream().also { c.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { "" }

    /** io thread. */
    private fun identify(face: FaceEngine.Face, thumb: String) {
        loadPeople(force = false)
        val ppl = people
        val local = LocalFaces.all(applicationContext)
        val size = face.descriptor.size
        val known = (ppl.mapNotNull { p -> p.embedding?.let { FaceMatch.Known("p:${p.id}", it) } } +
            local.filter { it.name != null || it.personId != null }.map { FaceMatch.Known(it.personId?.let { id -> "p:$id" } ?: "l:${it.id}", it.descriptor) })
            .filter { it.descriptor.size == size }
        val result = FaceMatch.best(face.descriptor, known)
        main.post {
            when (result) {
                is FaceMatch.Result.Match -> personCard(result.key, ppl, local, unsure = false)
                is FaceMatch.Result.Unsure -> personCard(result.key, ppl, local, unsure = true)
                FaceMatch.Result.Unknown -> {
                    newFace = face.descriptor to thumb
                    state = State.NEW_FACE
                    show("Pessoa nova", listOf("ainda não está na memória"), "toque: guardar · voltar: ignorar")
                }
            }
        }
    }

    private fun personCard(key: String, ppl: List<Backend.Person>, local: List<LocalFaces.Entry>, unsure: Boolean) {
        state = State.IDLE
        val p = if (key.startsWith("p:")) ppl.find { it.id == key.removePrefix("p:") } else null
        val l = local.filter { "p:${it.personId}" == key || "l:${it.id}" == key }
        val name = p?.name ?: l.firstOrNull()?.name ?: "?"
        val how = p?.let { listOf(it.relation, it.company).filter { s -> s.isNotBlank() }.joinToString(" · ") }.orEmpty()
        val previous = l.maxOfOrNull { it.lastSeen }?.takeIf { it > 0 }
        val last = when {
            p != null && p.lastSeenContext.isNotBlank() -> "última vez: " + listOf(shortDate(p.lastSeenDate), p.lastSeenContext).filter { it.isNotBlank() }.joinToString(", ")
            previous != null -> "visto em " + SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(previous))
            else -> ""
        }
        val lines = listOfNotNull(how.takeIf { it.isNotBlank() }, last.takeIf { it.isNotBlank() }, if (unsure) "não tenho certeza" else null)
        show(if (unsure) "$name?" else name, lines.ifEmpty { listOf("na sua memória") }, "toque: olhar de novo · voltar: fechar")
        // Remember this sighting on the phone (the Manager gets it when Beto's sync exists).
        io.execute { l.forEach { e -> LocalFaces.update(applicationContext, e.id) { it.lastSeen = System.currentTimeMillis() } } }
    }

    private fun keepNewFace() {
        val (d, thumb) = newFace ?: return
        newFace = null
        state = State.IDLE
        io.execute { LocalFaces.add(applicationContext, d, thumb) }
        show("Guardado", listOf("dê o nome no celular:", "app VisualContext"), "toque: quem é? · voltar: fechar")
    }

    private fun shortDate(iso: String): String = if (iso.length >= 10) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else iso

    private fun idle(line1: String, line2: String?) {
        state = State.IDLE
        show("VisualContext", listOfNotNull(line1, line2), "toque: tentar de novo · voltar: fechar")
    }

    /** Whole card as one image when the glasses support it (sharpest result); text card otherwise. */
    private fun show(title: String, lines: List<String>, footer: String) {
        val s = surface ?: return
        revision++
        if (nexusClient?.supportsImageSurface == true) {
            val png = HudCard.render(title, lines, footer)
            val image = NexusImage(
                contentKey = "vc-card-$revision",
                mimeType = ImageSurfaceContract.MIME_PNG,
                pixelWidth = HudCard.WIDTH,
                pixelHeight = HudCard.HEIGHT,
                handlesBack = true,
            )
            if (s.showImage(image, png) == NexusSdkResult.SENT) return
        }
        s.showCard(NexusCard(title = title, lines = lines, footer = footer, contentKey = "vc-text-$revision", handlesBack = true))
    }

    private companion object {
        const val SURFACE_ID = "main"
    }
}
