package io.alema.vcsolo

import io.alema.livecore.LiveProtocol
import io.alema.livecore.LiveSession
import io.alema.livecore.Transcript
import org.json.JSONObject

/**
 * The glasses have no keyboard, so setup comes from a QR made on the computer
 * (visualcontext.alema.io/solo — the page builds the QR in the browser; nothing is sent anywhere).
 * Format: `VCSOLO1:` + JSON {"gemini": key, "save": "none|note|photo", "voice": name, "vc": device token, "model": Live model} — all but "gemini" optional.
 */
data class SoloConfig(
    val geminiKey: String,
    val saveMode: SaveMode = SaveMode.NONE,
    val voice: String = DEFAULT_VOICE,
    val vcToken: String? = null,
    /** Optional override, in case Google renames the Live model before we ship an update. */
    val model: String = LiveProtocol.MODEL,
) {
    companion object { const val DEFAULT_VOICE = "Kore" }
}

enum class SaveMode(val wire: String) {
    NONE("none"), NOTE("note"), PHOTO("photo");
    companion object { fun of(s: String?) = values().firstOrNull { it.wire == s } ?: NONE }
}

object SetupCode {
    const val PREFIX = "VCSOLO1:"

    /** Null when the QR is not ours or the key is clearly wrong; the camera just keeps looking. */
    fun parse(raw: String?): SoloConfig? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val j = try { JSONObject(raw.removePrefix(PREFIX)) } catch (_: Exception) { return null }
        val key = j.optString("gemini").trim()
        if (key.length < 20 || key.any { it.isWhitespace() }) return null
        val voice = j.optString("voice").trim().takeIf { it.matches(Regex("[A-Za-z]{2,20}")) } ?: SoloConfig.DEFAULT_VOICE
        val vc = j.optString("vc").trim().takeIf { it.startsWith("vc_") && it.length > 10 }
        val model = j.optString("model").trim().takeIf { it.matches(Regex("(models/)?[a-z0-9.-]{3,60}")) }
            ?.let { if (it.startsWith("models/")) it else "models/$it" } ?: LiveProtocol.MODEL
        return SoloConfig(key, SaveMode.of(j.optString("save")), voice, vc, model)
    }

    fun encode(c: SoloConfig): String = PREFIX + JSONObject()
        .put("gemini", c.geminiKey).put("save", c.saveMode.wire).put("voice", c.voice)
        .apply { c.vcToken?.let { put("vc", it) }; if (c.model != LiveProtocol.MODEL) put("model", c.model) }
        .toString()
}
