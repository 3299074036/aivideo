package com.zola.aivideo.data.llm

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云端 API 引擎（M7）：原有 LlmClient 链路的薄封装。
 *
 * 配置（Base URL / Key / 模型名）每次调用时从 [LlmPreferences] /
 * [LlmCredentialStore] 读取，与设置页落盘的值保持一致。
 */
@Singleton
class ApiLlmEngine @Inject constructor(
    private val llmClient: LlmClient,
    private val prefs: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
) : LlmEngine {

    override val requiresApiConfig: Boolean = true

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ) {
        val config = LlmConfig(
            baseUrl = prefs.getBaseUrl(),
            apiKey = credentialStore.getApiKey(),
            model = prefs.getModel(),
        )
        require(config.isComplete()) { "LLM 配置不完整，请先在 LLM 设置页填写" }
        llmClient.streamChat(config, messages, onDelta)
    }
}
