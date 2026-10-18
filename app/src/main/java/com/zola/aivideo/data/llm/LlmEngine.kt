package com.zola.aivideo.data.llm

/**
 * LLM 引擎抽象（M7）。
 *
 * 翻译管线（[SubtitleTranslator]）只依赖本接口，不感知 API 还是本地：
 * - [ApiLlmEngine]：走 OpenAI 兼容云端接口（原有链路，行为不变）
 * - [LocalLlmEngine]：本机 llama.cpp 推理（M3 实现，M2 为占位）
 */
interface LlmEngine {
    /** 是否需要 API 配置（Base URL / Key / 模型名）；本地引擎为 false。 */
    val requiresApiConfig: Boolean

    /**
     * 流式对话。每个 delta 文本块经 [onDelta] 回调；协程取消即中断。
     * @throws LlmException 失败时抛带中文原因的异常
     */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    )
}

/** LLM 引擎选择：云端 API / 本地模型。 */
enum class LlmEngineId(val id: String, val displayName: String) {
    API("api", "API（联网）"),
    LOCAL("local", "本地模型"),
    ;

    companion object {
        fun fromId(id: String?): LlmEngineId =
            entries.firstOrNull { it.id == id } ?: API
    }
}
