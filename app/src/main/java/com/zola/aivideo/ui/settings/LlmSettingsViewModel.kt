package com.zola.aivideo.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.llm.ConnectionTestResult
import com.zola.aivideo.data.llm.LlmClient
import com.zola.aivideo.data.llm.LlmConfig
import com.zola.aivideo.data.llm.LlmCredentialStore
import com.zola.aivideo.data.llm.LlmPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** LLM 设置页状态。 */
data class LlmSettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val hasSavedKey: Boolean = false,
    val testing: Boolean = false,
    val testMessage: String? = null,
    val testSuccess: Boolean = false,
    val saving: Boolean = false,
)

/**
 * LLM 设置 ViewModel（M3）。
 *
 * - baseUrl/model 走 DataStore；apiKey 走 EncryptedSharedPreferences
 * - 测试连接调 GET /v1/models；不发起真实翻译，不花钱
 */
@HiltViewModel
class LlmSettingsViewModel @Inject constructor(
    private val prefs: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
    private val llmClient: LlmClient,
) : ViewModel() {

    var uiState by mutableStateOf(LlmSettingsUiState())
        private set

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            val baseUrl = prefs.getBaseUrl()
            val model = prefs.getModel()
            val savedKey = credentialStore.getApiKey()
            uiState = uiState.copy(
                baseUrl = baseUrl,
                model = model,
                // 已存 Key 只显示掩码占位，不回显明文
                apiKey = if (savedKey.isNotBlank()) MASKED_KEY_PLACEHOLDER else "",
                hasSavedKey = savedKey.isNotBlank(),
            )
        }
    }

    fun onBaseUrlChange(v: String) { uiState = uiState.copy(baseUrl = v, testMessage = null) }
    fun onApiKeyChange(v: String) { uiState = uiState.copy(apiKey = v, testMessage = null) }
    fun onModelChange(v: String) { uiState = uiState.copy(model = v, testMessage = null) }

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

    /** 测试连接：GET /v1/models。 */
    fun testConnection() {
        testJob?.cancel()
        val s = uiState
        val key = if (s.apiKey == MASKED_KEY_PLACEHOLDER) credentialStore.getApiKey() else s.apiKey
        val config = LlmConfig(s.baseUrl, key, s.model)
        if (!config.isComplete()) {
            uiState = s.copy(testMessage = "请先填写完整地址、Key 和模型名", testSuccess = false)
            return
        }
        uiState = s.copy(testing = true, testMessage = null)
        testJob = viewModelScope.launch {
            when (val r = llmClient.testConnection(config)) {
                is ConnectionTestResult.Success ->
                    uiState = uiState.copy(
                        testing = false,
                        testMessage = "连接成功（服务端返回 ${r.modelCount} 个模型）",
                        testSuccess = true,
                    )
                is ConnectionTestResult.Failed ->
                    uiState = uiState.copy(
                        testing = false,
                        testMessage = r.reason,
                        testSuccess = false,
                    )
            }
        }
    }

    companion object {
        /** 已存 Key 的掩码占位：用户不改动时不覆盖原值。 */
        const val MASKED_KEY_PLACEHOLDER = "••••••••"
    }
}
