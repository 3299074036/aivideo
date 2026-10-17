package com.zola.aivideo.data.tts

/**
 * 配音配置（M4 定义，M5 移到 data 层）。
 *
 * data 层的 Worker 需要读写它（任务实体的 dubOptionsJson），
 * 因此从 ui 层移到这里；字段与语义保持不变。
 */
data class DubOptions(
    val voiceSid: Int = TtsVoiceCatalog.DEFAULT_SID,
    val cloudVoice: String = "alloy",
    val speed: Float = 1.0f,
    val engineId: TtsEngineId = TtsEngineId.LOCAL,
    val strategy: DubStrategy = DubStrategy.REPLACE,
    val mixOriginalRatio: Float = 0.3f,
)
