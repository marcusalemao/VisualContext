package io.alema.visualcontext

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Marcus, 08/11: "peço para ouvir uma música e ele pega no meu Spotify ou qualquer aplicativo… sem sair da tela",
 * and "a música não atrapalhar quando eu falar". Plays through Android's standard "play from search" (Spotify,
 * YouTube Music, Deezer and others answer it), controls the player with media keys (no permission), lowers the
 * music while the assistant listens or talks (audio focus "may duck"), and shows synced lyrics from LRCLIB
 * (free, no key) when AudD tells where in the song we are.
 */
object Player {
    const val TOOL = "tocar_musica"
    const val TOOL_CTRL = "controlar_musica"
    const val TOOL_LYRICS = "mostrar_letra"
    private val APPS = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.amazon.mp3", "com.apple.android.music")
    private val main = Handler(Looper.getMainLooper())

    fun tools(): List<JSONObject> = listOf(
        JSONObject().put("name", TOOL).put("description", "Toca uma música, artista, álbum ou playlist no app de música do celular (Spotify ou outro), quando ele pedir \"toca…\", \"quero ouvir…\".")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("pedido", JSONObject().put("type", "STRING").put("description", "o que tocar, como ele disse"))
                .put("artista", JSONObject().put("type", "STRING")).put("musica", JSONObject().put("type", "STRING"))).put("required", org.json.JSONArray().put("pedido"))),
        JSONObject().put("name", TOOL_CTRL).put("description", "Pausa, continua, pula ou volta a música que está tocando no celular.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("acao", JSONObject().put("type", "STRING").put("enum", org.json.JSONArray().put("pausar").put("continuar").put("proxima").put("anterior")))).put("required", org.json.JSONArray().put("acao"))),
        JSONObject().put("name", TOOL_LYRICS).put("description", "Mostra na lente a letra da música (sincronizada quando possível). Use quando ele pedir a letra; para a música do ambiente, chame identificar_musica antes.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
                .put("musica", JSONObject().put("type", "STRING")).put("artista", JSONObject().put("type", "STRING"))
                .put("parar", JSONObject().put("type", "BOOLEAN").put("description", "true para tirar a letra da lente")))),
    )

    fun app(ctx: Context): String? = APPS.firstOrNull { NotesOut.installed(ctx, it) }

    /** Starts playback; the music app comes to the front for a moment, so the caller brings us back. */
    fun play(ctx: Context, query: String, artist: String?, song: String?): String? {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(android.app.SearchManager.QUERY, query)
        if (!song.isNullOrBlank()) i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE).putExtra(MediaStore.EXTRA_MEDIA_TITLE, song)
        else if (!artist.isNullOrBlank()) i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)
        if (!artist.isNullOrBlank()) i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist)
        val pkg = app(ctx)
        if (pkg != null) i.setPackage(pkg)
        return try {
            if (i.resolveActivity(ctx.packageManager) == null) null else { ctx.startActivity(i); pkg ?: "app de música" }
        } catch (_: Exception) { null }
    }

    fun control(ctx: Context, action: String): Boolean {
        val code = when (action) { "pausar" -> KeyEvent.KEYCODE_MEDIA_PAUSE; "continuar" -> KeyEvent.KEYCODE_MEDIA_PLAY; "proxima" -> KeyEvent.KEYCODE_MEDIA_NEXT; "anterior" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> return false }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }

    // ── Ducking: while the assistant is open, other audio plays low (Android lowers it for us). ──
    private var focus: AudioFocusRequest? = null
    fun duck(ctx: Context, on: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (on) {
            if (focus != null) return
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }.build().also { am.requestAudioFocus(it) }
        } else focus?.let { am.abandonAudioFocusRequest(it); focus = null }
    }

    // ── Lyrics (LRCLIB). ──
    data class Line(val ms: Long, val text: String)
    class Lyrics(val title: String, val artist: String, val synced: List<Line>, val plain: List<String>)

    /** Blocking. Null when LRCLIB has nothing. */
    fun lyrics(title: String, artist: String): Lyrics? = try {
        val u = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(title, "UTF-8") + "&artist_name=" + URLEncoder.encode(artist, "UTF-8")
        val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12000; setRequestProperty("User-Agent", "VisualContext (alema.io)") }
        val a = org.json.JSONArray(c.inputStream.bufferedReader().readText())
        val best = (0 until a.length()).map { a.getJSONObject(it) }.sortedByDescending { if (it.optString("syncedLyrics").isNotBlank()) 1 else 0 }.firstOrNull()
        best?.let { b ->
            val rx = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]\s*(.*)""")
            val synced = b.optString("syncedLyrics").lines().mapNotNull { l -> rx.find(l)?.destructured?.let { (m, s, f, t) ->
                Line(m.toLong() * 60_000 + s.toLong() * 1000 + (f.padEnd(3, '0').take(3).toLongOrNull() ?: 0), t.trim()) } }.filter { it.text.isNotEmpty() }
            val plain = b.optString("plainLyrics").lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (synced.isEmpty() && plain.isEmpty()) null else Lyrics(b.optString("trackName", title), b.optString("artistName", artist), synced, plain)
        }
    } catch (_: Exception) { null }

    /** Where the last identified song was, so lyrics can follow it: song position [posMs] at [atElapsed]. */
    @Volatile var lastSong: Pair<String, String>? = null
    @Volatile var lastPosMs = -1L
    @Volatile var lastAt = 0L
    fun heard(title: String, artist: String, timecode: String?) {
        lastSong = title to artist
        lastPosMs = timecode?.split(":")?.mapNotNull { it.toLongOrNull() }?.let { p -> if (p.size == 2) (p[0] * 60 + p[1]) * 1000 else if (p.size == 3) (p[0] * 3600 + p[1] * 60 + p[2]) * 1000 else null } ?: -1L
        lastAt = SystemClock.elapsedRealtime()
    }

    private var ticker: Runnable? = null
    fun stopLyrics() { ticker?.let { main.removeCallbacks(it) }; ticker = null }

    /**
     * Shows the lyrics on the lens through [show] (title, lines). Synced: the current line in the middle, the one
     * before and after around it, following the clock from the last identification. Plain: the first lines.
     */
    fun showLyrics(l: Lyrics, startPosMs: Long, startAt: Long, show: (String, List<String>) -> Unit) {
        stopLyrics()
        val title = "♪ " + l.title
        if (l.synced.isEmpty() || startPosMs < 0) { show(title, l.plain.take(6)); return }
        var last = -2
        val r = object : Runnable { override fun run() {
            val pos = startPosMs + (SystemClock.elapsedRealtime() - startAt)
            val i = l.synced.indexOfLast { it.ms <= pos }
            if (i != last) { last = i; show(title, listOfNotNull(l.synced.getOrNull(i - 1)?.text?.let { "  $it" }, l.synced.getOrNull(i)?.text?.let { "▸ $it" } ?: "…", l.synced.getOrNull(i + 1)?.text?.let { "  $it" })) }
            if (i < l.synced.size - 1 || pos < l.synced.last().ms + 6000) main.postDelayed(this, 250) else ticker = null
        } }
        ticker = r; main.post(r)
    }
}
