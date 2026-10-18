package com.zola.aivideo.data.tts

/**
 * 云 TTS 服务商预设（M7）。
 *
 * 全部为 OpenAI 兼容接口（/v1/audio/speech），点选后一键填入 Base URL / 模型 / 音色；
 * Key 仍由用户自备（BYOK），此处只给官网链接与计费提示。
 * 注意：MiniMax、豆包 Seed-TTS 等为私有协议，不兼容本接口，不在本预设内（v2 再议）。
 */
data class TtsProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    /** 音色候选（第一个为推荐，点选预设时自动填入）。 */
    val voices: List<String>,
    /** 获取 Key 的官网链接。 */
    val keyUrl: String,
    /** 计费/额度一句话提示。 */
    val keyHint: String,
)

object TtsProviderCatalog {

    val SILICONFLOW = TtsProvider(
        id = "siliconflow",
        displayName = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        model = "FunAudioLLM/CosyVoice2-0.5B",
        voices = listOf(
            "FunAudioLLM/CosyVoice2-0.5B:diana",
            "FunAudioLLM/CosyVoice2-0.5B:alex",
        ),
        keyUrl = "https://cloud.siliconflow.cn/",
        keyHint = "可与 LLM 设置共用一个 Key；中文音色多，配音推荐",
    )

    val OPENAI = TtsProvider(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "tts-1",
        voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
        keyUrl = "https://platform.openai.com/",
        keyHint = "需境外支付，仅作备用",
    )

    /** 自定义：任何 OpenAI 兼容的 /v1/audio/speech 接口，走原有手填流程。 */
    val CUSTOM = TtsProvider(
        id = "custom",
        displayName = "自定义",
        baseUrl = "",
        model = "",
        voices = emptyList(),
        keyUrl = "",
        keyHint = "任何 OpenAI 兼容的 /v1/audio/speech 接口",
    )

    val all: List<TtsProvider> =
        listOf(SILICONFLOW, OPENAI, CUSTOM)

    fun byId(id: String?): TtsProvider =
        all.firstOrNull { it.id == id } ?: CUSTOM
}
