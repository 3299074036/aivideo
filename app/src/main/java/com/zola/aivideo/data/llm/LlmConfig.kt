package com.zola.aivideo.data.llm

/**
 * LLM 配置（M3，BYOK：用户自备 Key）。
 *
 * - baseUrl：OpenAI 兼容接口地址，如 https://api.openai.com（末尾斜杠会被规范化去掉）
 * - apiKey：只在内存中持有；持久化走 [LlmCredentialStore]（EncryptedSharedPreferences）
 * - model：模型名，如 gpt-4o-mini / deepseek-chat，由用户填写
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    /** 规范化后的 baseUrl：去尾部斜杠。 */
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')

    /** 调用 chat completions 的完整 URL。 */
    val chatCompletionsUrl: String get() = "$normalizedBaseUrl/v1/chat/completions"

    /** 调用 models 列表的完整 URL（测试连接用）。 */
    val modelsUrl: String get() = "$normalizedBaseUrl/v1/models"

    fun isComplete(): Boolean =
        normalizedBaseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** 目标语言：6 种预设 + 自定义（用户手填语言名）。 */
sealed interface TargetLanguage {
    val displayName: String

    data object Chinese : TargetLanguage { override val displayName = "中文" }
    data object English : TargetLanguage { override val displayName = "英语" }
    data object Japanese : TargetLanguage { override val displayName = "日语" }
    data object Korean : TargetLanguage { override val displayName = "韩语" }
    data object French : TargetLanguage { override val displayName = "法语" }
    data object German : TargetLanguage { override val displayName = "德语" }
    data object Spanish : TargetLanguage { override val displayName = "西班牙语" }
    data class Custom(val name: String) : TargetLanguage {
        override val displayName: String get() = name.ifBlank { "自定义" }
    }

    companion object {
        val presets: List<TargetLanguage> =
            listOf(Chinese, English, Japanese, Korean, French, German, Spanish)
    }
}

/** 翻译风格。 */
enum class TranslateStyle(val displayName: String, val instruction: String) {
    LITERAL(
        displayName = "直译",
        instruction = "直译：忠实原文措辞与句式，尽量逐字对应。",
    ),
    FREE(
        displayName = "意译",
        instruction = "意译：在准确传达原意的前提下，用目标语言的地道表达重新组织。",
    ),
    COLLOQUIAL(
        displayName = "口语化",
        instruction = "口语化：像日常说话一样自然简短，适合视频配音朗读，避免书面腔。",
    ),
}
