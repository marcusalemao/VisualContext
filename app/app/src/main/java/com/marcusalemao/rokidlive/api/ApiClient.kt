package com.marcusalemao.rokidlive.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP do backend RokidLive (Base44 backend functions).
 * Endpoints validados e no ar desde 14/09/2026.
 *
 * Contrato (POST, JSON). Autenticação: header x-vc-token = VC_DEVICE_TOKEN
 * (o mesmo do fcProxy). Defina com [deviceToken]; vazio = sem header.
 *  - rvFrameIngest { frame_base64, snapshot_base64?, timestamp?, lat?, lng?, audio_transcript_chunk? }
 *      → { ok, detections: {objects, people}, hud_alert? }
 *  - rvAskMemory { question?, item_name?, lat?, lng? } → { ok, answer, hud_alert? }
 *  - rvHudFeed { action: "poll"|"history", limit? } → { ok, notifications: [...] }
 */
object ApiClient {

    // Mesma base do gateway nexusGateway (sufixo '#' é só pro app Assistant; aqui é REST puro)
    private const val BASE = "https://base44.app/api/apps/6a11083db49430b410a8c066/functions/"

    private const val FRAME_W = 640
    private const val FRAME_H = 480
    private const val SNAP_W = 160   // thumbnail do registro de memória
    private const val SNAP_H = 120

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // Gemini pode levar alguns segundos
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** VC_DEVICE_TOKEN. Preenchido pela HudActivity a partir das preferências. */
    @Volatile var deviceToken: String = ""

    /** Envia um frame JPEG (base64 cru, sem prefixo data:) ao pipeline de visão. */
    suspend fun ingestFrame(
        frameBase64: String,
        snapshotBase64: String?,
        lat: Double?, lng: Double?,
        audioTranscript: String? = null
    ): JSONObject? = post("rvFrameIngest", buildJsonObject {
        put("frame_base64", frameBase64)
        snapshotBase64?.let { put("snapshot_base64", it) }
        put("timestamp", System.currentTimeMillis())
        lat?.let { put("lat", it) }
        lng?.let { put("lng", it) }
        audioTranscript?.let { if (it.isNotBlank()) put("audio_transcript_chunk", it) }
    })

    /** Pergunta à memória: "onde deixei a chave?" */
    suspend fun askMemory(question: String, lat: Double? = null, lng: Double? = null): JSONObject? =
        post("rvAskMemory", buildJsonObject {
            put("question", question)
            lat?.let { put("lat", it) }
            lng?.let { put("lng", it) }
        })

    /** Poll de notificações pro HUD (marca como exibidas). */
    suspend fun pollHud(limit: Int = 5): JSONObject? =
        post("rvHudFeed", buildJsonObject {
            put("action", "poll")
            put("limit", limit)
        })

    private fun buildJsonObject(block: JSONObject.() -> Unit): String =
        JSONObject().apply(block).toString()

    private suspend fun post(fn: String, jsonBody: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder()
                    .url(BASE + fn)
                    .post(jsonBody.toRequestBody(JSON))
                deviceToken.takeIf { it.isNotBlank() }?.let { builder.header("x-vc-token", it) }
                val req = builder.build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: return@use null
                    if (!resp.isSuccessful) null else JSONObject(body)
                }
            }.getOrNull()
        }
}
