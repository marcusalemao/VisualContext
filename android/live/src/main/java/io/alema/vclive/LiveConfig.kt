package io.alema.vclive

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Settings typed on the phone (it has a keyboard): Gemini key, voice, and who speaks the answer. */
object LiveConfig {
    private const val PREFS = "vc_live"

    enum class Voice(val label: String) {
        GEMINI("Voz do Gemini (som do celular)"),
        NEXUS("Voz do Nexus (lê o texto nos óculos)"),
    }

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun key(ctx: Context): String = p(ctx).getString("gemini", "") ?: ""
    fun voiceName(ctx: Context): String = p(ctx).getString("voice_name", "Kore") ?: "Kore"
    fun voice(ctx: Context): Voice = Voice.values().firstOrNull { it.name == p(ctx).getString("voice", null) } ?: Voice.GEMINI
    fun aiButton(ctx: Context): Boolean = p(ctx).getBoolean("ai_button", false)
    fun setAiButton(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("ai_button", on).apply()
    fun model(ctx: Context): String? = p(ctx).getString("resolved_model", null)
    fun saveModel(ctx: Context, m: String?) = p(ctx).edit().putString("resolved_model", m).apply()
    fun save(ctx: Context, key: String, voiceName: String, voice: Voice) {
        p(ctx).edit().putString("gemini", key.trim()).putString("voice_name", voiceName).putString("voice", voice.name)
            .remove("resolved_model").apply()
    }

    /**
     * Where the phone's assistant audio goes right now. If the glasses are the phone's Bluetooth audio device,
     * Gemini's voice should come out of the glasses; otherwise it comes out of the phone speaker.
     */
    fun route(ctx: Context): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        return if (bt != null) "som: ${bt.productName} (Bluetooth)" else "som: alto-falante do celular"
    }
}
