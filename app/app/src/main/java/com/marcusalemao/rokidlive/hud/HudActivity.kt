package com.marcusalemao.rokidlive.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.marcusalemao.rokidlive.api.ApiClient
import com.marcusalemao.rokidlive.vision.VisionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Main
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * HUD dos óculos — 640x480, verde fósforo (#00FF66) sobre preto, SEM SCROLL.
 * (Porta do design system validado no client HTML.)
 *
 * Fontes de conteúdo:
 *  1. broadcast HUD_ALERT do VisionService (detecção instantânea)
 *  2. poll rvHudFeed a cada 5s (notificações persistidas no backend)
 *  3. KeyEvents dos óculos (botões físicos) — ações sem tocar na tela
 */
class HudActivity : AppCompatActivity() {

    companion object {
        private const val HUD_W = 640
        private const val HUD_H = 480
        private const val PHOSPHOR = 0xFF00FF66.toInt()
        private const val HUD_POLL_MS = 5000L
        private const val ALERT_TTL_MS = 4000L
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var hudText: TextView? = null
    private var statusText: TextView? = null
    private var alertDismissJob: kotlinx.coroutines.Job? = null

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, i: Intent?) {
            i?.getStringExtra("text")?.let { showAlert(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Token do aparelho: guardado nas preferências. Para definir pela primeira vez:
        // adb shell am start -n com.marcusalemao.rokidlive/.hud.HudActivity --es vc_token <TOKEN>
        val prefs = getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
        intent?.getStringExtra("vc_token")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            prefs.edit().putString("vc_device_token", it).apply()
        }
        ApiClient.deviceToken = prefs.getString("vc_device_token", "").orEmpty()

        // HUD de óculos: sem barra de status, brilho travado, tela ligada.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            0
        )

        setContentView(buildHudLayout())

        registerReceiver(alertReceiver, IntentFilter(VisionService.HUD_ALERT_BROADCAST))

        startVision()
        pollLoop()
    }

    /** Layout programático — HUD AR pede controle absoluto de pixels. */
    private fun buildHudLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            text = "BETO VISION — INICIALIZANDO"
            textSize = 14f
            setTextColor(PHOSPHOR)
            letterSpacing = 0.1f
        }

        hudText = TextView(this).apply {
            text = "Sistema de visão ativo. Monitorando objetos e contexto..."
            textSize = 18f
            setTextColor(PHOSPHOR)
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(TextView(this).apply { minimumHeight = 8 }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(hudText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return root
    }

    private fun startVision() {
        val i = Intent(this, VisionService::class.java).apply { action = VisionService.ACTION_START }
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        statusText?.text = "BETO VISION — TRANSMITINDO 1 FPS"
    }

    private fun stopVision() {
        startService(Intent(this, VisionService::class.java).apply { action = VisionService.ACTION_STOP })
        statusText?.text = "BETO VISION — PARADO"
    }

    /** Poll do feed de notificações (pergunta respondida, memórias, etc.) */
    private fun pollLoop() {
        scope.launch {
            while (true) {
                val resp = ApiClient.pollHud()
                val notifs: JSONArray? = resp?.optJSONArray("notifications")
                if (notifs != null && notifs.length() > 0) {
                    val n = notifs.getJSONObject(0)
                    val text = n.optString("title").ifBlank { n.optString("content_type") }
                    if (text.isNotBlank()) showAlert(text)
                }
                kotlinx.coroutines.delay(HUD_POLL_MS)
            }
        }
    }

    private fun showAlert(text: String) {
        // Sem scroll no HUD: trunca em ~180 chars (aprendizado do client HTML)
        hudText?.text = if (text.length > 180) text.take(177) + "..." else text
        alertDismissJob?.cancel()
        alertDismissJob = scope.launch {
            kotlinx.coroutines.delay(ALERT_TTL_MS)
            hudText?.text = "Sistema de visão ativo..."
        }
    }

    /**
     * KeyEvents: NO Rokid Glasses a interação é pelos botões físicos.
     * Mapear os keycodes reais do hardware (CXR SDK) aqui — sem tocar na tela.
     * TODO: logar os keycodes que chegam nos óculos e mapear:
     *   - botão principal → perguntar "onde deixei X?" (rvAskMemory)
     *   - toque duplo → start/stop streaming
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { askMemoryTest(); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { stopVision(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun askMemoryTest() {
        scope.launch {
            hudText?.text = "Perguntando à memória..."
            val resp = ApiClient.askMemory("onde está minha chave?")
            showAlert(resp?.optString("answer") ?: "Sem resposta do backend.")
        }
    }

    override fun onDestroy() {
        unregisterReceiver(alertReceiver)
        stopVision()
        super.onDestroy()
    }
}
