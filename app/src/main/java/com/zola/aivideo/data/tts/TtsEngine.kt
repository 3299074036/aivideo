package com.zola.aivideo.data.tts

/**
 * TTS 引擎抽象（M4）：端侧 Kokoro 与云 TTS 实现同一接口，配音流程只依赖接口。
 */
interface TtsEngine {
    /** 引擎 ID：local / cloud。 */
    val engineId: TtsEngineId

    /** 是否可用（端侧：模型已下载且引擎已初始化；云端：已配置 Key）。 */
    suspend fun isAvailable(): Boolean

    /**
     * 合成一段文本。
     *
     * @param text 待合成文本（单字幕分段，已做长度截断保护）
     * @param voiceId 音色标识（端侧：sid 字符串；云端：音色名）
     * @param speed 语速倍率
     * @return 合成音频（float 采样，采样率见 [SynthesizedSpeech.sampleRate]）
     */
    suspend fun synthesize(text: String, voiceId: String, speed: Float): SynthesizedSpeech
}

/** 单段合成结果。 */
data class SynthesizedSpeech(
    /** float 采样（-1f..1f）。 */
    val samples: FloatArray,
    /** 采样率（Hz）。Kokoro 原生 24000；云端按实际返回。 */
    val sampleRate: Int,
) {
    /** 时长（毫秒）。 */
    val durationMs: Long get() = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesizedSpeech) return false
        return samples.contentEquals(other.samples) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate
}
