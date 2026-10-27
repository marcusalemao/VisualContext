package io.alema.vclive

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Phone screen: paste the Gemini key here (never in a chat), pick who speaks, see where the sound goes. */
class LiveSettingsActivity : Activity() {
    private lateinit var route: TextView
    private var preview: VoicePreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No FLAG_SECURE: the key field is masked, and Marcus needs screenshots of this screen.
        val green = Color.rgb(0, 255, 136)
        val soft = Color.rgb(143, 223, 181)
        fun label(t: String, size: Float = 14f, c: Int = soft) = TextView(this).apply { text = t; textSize = size; setTextColor(c); setPadding(0, 24, 0, 8) }
        val key = EditText(this).apply {
            hint = "AIza…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(LiveConfig.key(context)); setTextColor(green)
        }
        val voices = listOf("Kore", "Aoede", "Puck", "Charon", "Fenrir", "Leda", "Orus", "Zephyr")
        val hear = label("", 12f, green)
        val voiceGroup = RadioGroup(this).apply {
            voices.forEachIndexed { i, v -> addView(RadioButton(context).apply { id = 100 + i; text = v; setTextColor(soft); isChecked = v == LiveConfig.voiceName(context) }) }
        }
        // Picking a voice plays it right away and keeps it (no need to press Salvar for that).
        voiceGroup.setOnCheckedChangeListener { _, id ->
            val name = voices.getOrElse(id - 100) { "Kore" }
            LiveConfig.save(this, key.text.toString(), name, LiveConfig.voice(this))
            (preview ?: VoicePreview(this) { hear.text = it }.also { preview = it }).play(key.text.toString(), name)
        }
        val hearBtn = Button(this).apply {
            text = "Ouvir esta voz"
            setOnClickListener {
                val name = voices.getOrElse(voiceGroup.checkedRadioButtonId - 100) { "Kore" }
                (preview ?: VoicePreview(context) { hear.text = it }.also { preview = it }).play(key.text.toString(), name)
            }
        }
        val aiBtn = android.widget.Switch(this).apply {
            text = "Abrir com o botão de IA (pressão longa na haste)"
            setTextColor(soft)
            isChecked = LiveConfig.aiButton(context)
            setOnCheckedChangeListener { _, on -> LiveConfig.setAiButton(context, on) }
        }
        val whoGroup = RadioGroup(this).apply {
            LiveConfig.Voice.values().forEachIndexed { i, v -> addView(RadioButton(context).apply { id = 200 + i; text = v.label; setTextColor(soft); isChecked = v == LiveConfig.voice(context) }) }
        }
        route = label("", 14f, green)
        val save = Button(this).apply {
            text = "Salvar"
            setOnClickListener {
                val name = voices.getOrElse(voiceGroup.checkedRadioButtonId - 100) { "Kore" }
                val who = LiveConfig.Voice.values().getOrElse(whoGroup.checkedRadioButtonId - 200) { LiveConfig.Voice.GEMINI }
                LiveConfig.save(context, key.text.toString(), name, who)
                Toast.makeText(context, "Salvo", Toast.LENGTH_SHORT).show()
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(label("VisualContext Live (teste)", 24f, green))
            addView(label("Gemini Live dentro do Nexus: o microfone e a câmera são dos óculos; a conversa com o Gemini sai do celular."))
            addView(label("Chave do Gemini (Google AI Studio)", 14f, green)); addView(key)
            addView(label("Fica só neste celular. Não cole em nenhum chat.", 12f))
            addView(label("Quem fala a resposta", 14f, green)); addView(whoGroup)
            addView(label("O teste: com \"Voz do Gemini\", a voz sai nos óculos? Se sair no celular, troque para \"Voz do Nexus\".", 12f))
            addView(label("Timbre do Gemini", 14f, green)); addView(voiceGroup); addView(hearBtn); addView(hear)
            addView(label("Assistente", 14f, green)); addView(aiBtn)
            addView(label("Ligado: a pressão longa na haste abre o VisualContext Live no lugar do assistente da Rokid. Vale na próxima vez que o plugin abrir nos óculos. O Nexus pode pedir para você confirmar.", 12f))
            addView(save)
            addView(route)
            addView(label("Nos óculos: toque triplo → Nexus → VisualContext Live. Toque = perguntar; durante a resposta, toque = interromper; ouvindo, toque = mandar foto.", 12f))
        }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(3, 12, 6)) })
    }

    override fun onPause() { super.onPause(); preview?.stop() }

    override fun onResume() {
        super.onResume()
        route.text = "Agora: " + LiveConfig.route(this)
    }
}
