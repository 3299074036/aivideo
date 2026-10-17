package com.zola.aivideo.data.tts.engine

import com.google.gson.Gson
import com.zola.aivideo.data.asr.audio.PcmDecoder
import com.zola.aivideo.data.tts.AudioResample
import com.zola.aivideo.data.tts.SynthesizedSpeech
import com.zola.aivideo.data.tts.TtsCredentialStore
import com.zola.aivideo.data.tts.TtsEngine
import com.zola.aivideo.data.tts.TtsEngineId
import com.zola.aivideo.data.tts.TtsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 云 TTS 引擎（M4）：OpenAI 兼容 `/v1/audio/speech`（BYOK）。
 *
 * - Key 存 TtsCredentialStore（加密），与 LLM Key 分开字段
 * - 请求 response_format=wav；返回字节落盘后经 PcmDecoder（MediaCodec）
 *   解码为 16kHz 单声道，再线性重采样到 [TARGET_SAMPLE_RATE]（与端侧统一时间轴）
 * - 费用：按各家标准按量计费，调用前由 UI 二次确认（用户自理）
 */
@Singleton
class OpenAiTtsEngine @Inject constructor(
    @Named("tts") private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val credentialStore: TtsCredentialStore,
    private val prefs: TtsPreferences,
    private val pcmDecoder: PcmDecoder,
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val engineId: TtsEngineId = TtsEngineId.CLOUD

    companion object {
        /** 配音时间轴统一采样率（与 Kokoro 一致）。 */
        const val TARGET_SAMPLE_RATE = 24000

        /**
         * 构造 /v1/audio/speech 请求：纯函数，可单元测试。
         */
        fun buildSpeechRequest(
            baseUrl: String,
            apiKey: String,
            model: String,
            voice: String,
            text: String,
            speed: Float,
            gson: Gson,
        ): Request {
            val url = baseUrl.trimEnd('/') + "/audio/speech"
            val body = gson.toJson(
                mapOf(
                    "model" to model,
                    "input" to text,
                    "voice" to voice,
                    "response_format" to "wav",
                    "speed" to speed.coerceIn(0.25f, 4.0f),
                ),
            )
            return Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        }
    }

    override suspend fun isAvailable(): Boolean =
        credentialStore.hasApiKey() && prefs.cloudBaseUrl.first().isNotBlank()

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        speed: Float,
    ): SynthesizedSpeech = withContext(Dispatchers.IO) {
        val apiKey = credentialStore.getApiKey()
        require(apiKey.isNotBlank()) { "云 TTS 未配置 API Key，请先在 TTS 设置页填写" }
        val clean = text.trim().take(OfflineKokoroTtsEngine.MAX_TEXT_CHARS)
        require(clean.isNotEmpty()) { "合成文本为空" }
        val baseUrl = prefs.cloudBaseUrl.first()
        val model = prefs.cloudModel.first()

        val request = buildSpeechRequest(baseUrl, apiKey, model, voiceId, clean, speed, gson)
        val workDir = File(context.filesDir, "tts_tmp").apply { mkdirs() }
        val rawFile = File(workDir, "cloud_${System.nanoTime()}.wav")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "云 TTS 请求失败（HTTP ${response.code}）：" +
                            response.body?.string()?.take(200).orEmpty(),
                    )
                }
                val body = response.body ?: throw IllegalStateException("云 TTS 返回空响应体")
                rawFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            // 解码为 16kHz 单声道 PCM，再重采样到 24kHz 统一时间轴
            val pcmFile = File(workDir, "cloud_${System.nanoTime()}.pcm")
            try {
                pcmDecoder.decodeToPcmFile(rawFile.absolutePath, pcmFile.absolutePath)
                val shorts = pcmFile.readBytes().let { bytes ->
                    ShortArray(bytes.size / 2) { i ->
                        ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
                    }
                }
                val mono16k = FloatArray(shorts.size) { i -> shorts[i] / 32768f }
                val samples24k = AudioResample.linear(mono16k, 16000, TARGET_SAMPLE_RATE)
                SynthesizedSpeech(samples24k, TARGET_SAMPLE_RATE)
            } finally {
                runCatching { pcmFile.delete() }
            }
        } finally {
            runCatching { rawFile.delete() }
        }
    }
}
