package com.zola.aivideo.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.llm.ChatMessage
import com.zola.aivideo.data.llm.LlmClient
import com.zola.aivideo.data.llm.LlmConfig
import com.zola.aivideo.data.llm.LlmCredentialStore
import com.zola.aivideo.data.llm.LlmEngineId
import com.zola.aivideo.data.llm.LlmModelCatalog
import com.zola.aivideo.data.llm.LlmPreferences
import com.zola.aivideo.data.llm.LlmProvider
import com.zola.aivideo.data.llm.LlmProviderCatalog
import com.zola.aivideo.data.llm.LocalLlmEngine
import com.zola.aivideo.data.llm.TranslationTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** LLM 设置页状态。 */
data class LlmSettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val providerId: String = LlmProviderCatalog.CUSTOM.id,
    val engine: LlmEngineId = LlmEngineId.API,
    /** 本地引擎当前选中的模型展示名（M7）。 */
    val localModelDisplayName: String = "",
    val hasSavedKey: Boolean = false,
    val testing: Boolean = false,
    val testMessage: String? = null,
    val testSuccess: Boolean = false,
    val saving: Boolean = false,
) {
    /** 当前服务商预设。 */
    val provider: LlmProvider get() = LlmProviderCatalog.byId(providerId)
}

/**
 * LLM 设置 ViewModel（M3，M7 加服务商预设）。
 *
 * - baseUrl/model/provider 走 DataStore；apiKey 走 EncryptedSharedPreferences
 * - 点选预设一键填入地址与推荐模型（不自动保存，点"保存"落盘）
 * - 测试连接发一句真实短翻译（英→中），验证整条链路
 */
@HiltViewModel
class LlmSettingsViewModel @Inject constructor(
    private val prefs: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
    private val llmClient: LlmClient,
    private val localLlmEngine: LocalLlmEngine,
) : ViewModel() {

    var uiState by mutableStateOf(LlmSettingsUiState())
        private set

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            val baseUrl = prefs.getBaseUrl()
            val model = prefs.getModel()
            val providerId = prefs.getProvider()
            val engine = prefs.getLlmEngine()
            val savedKey = credentialStore.getApiKey()
            val localModelId = prefs.getLocalModel()
            uiState = uiState.copy(
                baseUrl = baseUrl,
                model = model,
                providerId = providerId,
                engine = engine,
                localModelDisplayName =
                    LlmModelCatalog.entryOf(localModelId)?.displayName ?: localModelId,
                // 已存 Key 只显示掩码占位，不回显明文
                apiKey = if (savedKey.isNotBlank()) MASKED_KEY_PLACEHOLDER else "",
                hasSavedKey = savedKey.isNotBlank(),
            )
        }
    }

    fun onBaseUrlChange(v: String) {
        uiState = uiState.copy(
            baseUrl = v, testMessage = null,
            // 手改地址后视为脱离预设
            providerId = LlmProviderCatalog.CUSTOM.id,
        )
    }
    fun onApiKeyChange(v: String) { uiState = uiState.copy(apiKey = v, testMessage = null) }
    fun onModelChange(v: String) { uiState = uiState.copy(model = v, testMessage = null) }

    /** 点选服务商预设：一键填入地址与推荐模型（需点"保存"落盘）。 */
    fun selectProvider(provider: LlmProvider) {
        uiState = uiState.copy(
            providerId = provider.id,
            baseUrl = provider.baseUrl.ifBlank { uiState.baseUrl },
            model = provider.models.firstOrNull() ?: uiState.model,
            testMessage = null,
        )
    }

    /** 切换引擎（M7）：API / 本地，立即落盘，翻译时即时生效。 */
    fun setEngine(engine: LlmEngineId) {
        uiState = uiState.copy(engine = engine, testMessage = null)
        viewModelScope.launch { prefs.setLlmEngine(engine) }
    }

    /** 保存：Key 为掩码占位时表示未改动，不覆盖。 */
    fun save() {
        val s = uiState
        if (s.baseUrl.isBlank() || s.model.isBlank()) {
            uiState = s.copy(testMessage = "请填写完整地址和模型名", testSuccess = false)
            return
        }
        uiState = s.copy(saving = true)
        viewModelScope.launch {
            prefs.setBaseUrl(s.baseUrl)
            prefs.setModel(s.model)
            prefs.setProvider(s.providerId)
            if (s.apiKey != MASKED_KEY_PLACEHOLDER) {
                if (s.apiKey.isBlank()) credentialStore.clear()
                else credentialStore.setApiKey(s.apiKey)
            }
            val hasKey = credentialStore.hasApiKey()
            uiState = uiState.copy(
                saving = false,
                hasSavedKey = hasKey,
                apiKey = if (hasKey) MASKED_KEY_PLACEHOLDER else "",
                testMessage = "已保存",
                testSuccess = true,
            )
        }
    }

    /** 清除已存 Key。 */
    fun clearKey() {
        credentialStore.clear()
        uiState = uiState.copy(apiKey = "", hasSavedKey = false, testMessage = "密钥已清除")
    }

    /**
     * 测试：按当前引擎分流。
     * - API 引擎：先保存当前输入，再发一句真实短翻译验证。
     * - 本地引擎：直接用本机模型跑一句真实翻译（模型未下载会提示去模型管理页）。
     */
    fun testConnection() {
        testJob?.cancel()
        val s = uiState
        if (s.engine == LlmEngineId.LOCAL) {
            uiState = s.copy(testing = true, testMessage = null)
            testJob = viewModelScope.launch {
                try {
                    val start = System.currentTimeMillis()
                    val sb = StringBuilder()
                    localLlmEngine.streamChat(TEST_MESSAGES) { sb.append(it) }
                    val text = sb.toString().trim()
                    val elapsed = System.currentTimeMillis() - start
                    uiState = uiState.copy(
                        testing = false,
                        testMessage = if (text.isEmpty()) "本地模型返回空译文" else
                            "本地测试成功：\"Hello, how are you?\" → \"$text\"（${elapsed}ms）",
                        testSuccess = text.isNotEmpty(),
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    uiState = uiState.copy(
                        testing = false,
                        testMessage = e.message ?: "本地测试失败",
                        testSuccess = false,
                    )
                }
            }
            return
        }
        // 先把当前输入落盘，保证测试用的是最新配置
        viewModelScope.launch {
            prefs.setBaseUrl(s.baseUrl)
            prefs.setModel(s.model)
            prefs.setProvider(s.providerId)
            if (s.apiKey != MASKED_KEY_PLACEHOLDER) {
                if (s.apiKey.isBlank()) credentialStore.clear()
                else credentialStore.setApiKey(s.apiKey)
            }
            val key = if (s.apiKey == MASKED_KEY_PLACEHOLDER) credentialStore.getApiKey() else s.apiKey
            val config = LlmConfig(s.baseUrl, key, s.model)
            if (!config.isComplete()) {
                uiState = uiState.copy(testMessage = "请先填写完整地址、Key 和模型名", testSuccess = false)
                return@launch
            }
            uiState = uiState.copy(testing = true, testMessage = null)
            testJob = viewModelScope.launch {
                when (val r = llmClient.testTranslation(config)) {
                    is TranslationTestResult.Success ->
                        uiState = uiState.copy(
                            testing = false,
                            testMessage = "测试成功：\"Hello, how are you?\" → \"${r.translated}\"（${r.elapsedMs}ms）",
                            testSuccess = true,
                        )
                    is TranslationTestResult.Failed ->
                        uiState = uiState.copy(
                            testing = false,
                            testMessage = r.reason,
                            testSuccess = false,
                        )
                }
            }
        }
    }

    companion object {
        /** 已存 Key 的掩码占位：用户不改动时不覆盖原值。 */
        const val MASKED_KEY_PLACEHOLDER = "••••••••"

        /** 测试翻译用消息（API / 本地共用同一套）。 */
        private val TEST_MESSAGES = listOf(
            ChatMessage.system("把用户输入翻译成中文，只输出译文，不要解释。"),
            ChatMessage.user("Hello, how are you?"),
        )
    }
}
