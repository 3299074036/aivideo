package com.zola.aivideo.ui.dub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.tts.TtsCredentialStore
import com.zola.aivideo.data.tts.TtsPreferences
import com.zola.aivideo.data.tts.TtsProvider
import com.zola.aivideo.data.tts.TtsProviderCatalog
import com.zola.aivideo.data.tts.engine.OpenAiTtsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 云 TTS 设置页 UI 状态。 */
data class TtsSettingsUiState(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val showApiKey: Boolean = false,
    val model: String = "tts-1",
    val voice: String = "alloy",
    val providerId: String = TtsProviderCatalog.CUSTOM.id,
    val wifiOnly: Boolean = true,
    val testing: Boolean = false,
    val testResult: TtsTestResult? = null,
) {
    val canTest: Boolean get() = (apiKeyInput.isNotBlank() || hasApiKey) && baseUrl.isNotBlank()

    /** 当前服务商预设。 */
    val provider: TtsProvider get() = TtsProviderCatalog.byId(providerId)
}

data class TtsTestResult(val success: Boolean, val message: String)

/**
 * 云 TTS 设置页 ViewModel（M4）。
 *
 * - Key 经 TtsCredentialStore 加密保存（与 LLM Key 分开）；输入框不回显已保存的 Key 明文
 * - 测试连接：保存后用当前配置合成一段短文本，验证 HTTP 200 且返回音频
 */
@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val prefs: TtsPreferences,
    private val credentialStore: TtsCredentialStore,
    private val cloudEngine: OpenAiTtsEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsSettingsUiState())
    val uiState: StateFlow<TtsSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                baseUrl = prefs.cloudBaseUrl.first(),
                model = prefs.cloudModel.first(),
                voice = prefs.cloudVoice.first(),
                providerId = prefs.getCloudProvider(),
                wifiOnly = prefs.isWifiOnlyDownload(),
                hasApiKey = credentialStore.hasApiKey(),
            )
        }
    }

    fun setBaseUrl(v: String) {
        _uiState.value = _uiState.value.copy(
            baseUrl = v,
            // 手改地址后视为脱离预设
            providerId = TtsProviderCatalog.CUSTOM.id,
        )
    }
    fun setApiKeyInput(v: String) { _uiState.value = _uiState.value.copy(apiKeyInput = v) }
    fun setModel(v: String) { _uiState.value = _uiState.value.copy(model = v) }
    fun setVoice(v: String) { _uiState.value = _uiState.value.copy(voice = v) }
    fun setWifiOnly(v: Boolean) {
        _uiState.value = _uiState.value.copy(wifiOnly = v)
        viewModelScope.launch { prefs.setWifiOnlyDownload(v) }
    }
    fun toggleShowApiKey() {
        _uiState.value = _uiState.value.copy(showApiKey = !_uiState.value.showApiKey)
    }

    /** 点选服务商预设：一键填入地址、模型与音色（需点"保存"落盘）。 */
    fun selectProvider(provider: TtsProvider) {
        _uiState.value = _uiState.value.copy(
            providerId = provider.id,
            baseUrl = provider.baseUrl.ifBlank { _uiState.value.baseUrl },
            model = provider.model.ifBlank { _uiState.value.model },
            voice = provider.voices.firstOrNull() ?: _uiState.value.voice,
            testResult = null,
        )
    }

    /** 保存 Base URL / 模型 / 音色 / 服务商；Key 非空则覆盖保存。 */
    fun save() {
        viewModelScope.launch {
            val s = _uiState.value
            prefs.setCloudBaseUrl(s.baseUrl)
            prefs.setCloudModel(s.model.trim().ifBlank { "tts-1" })
            prefs.setCloudVoice(s.voice.trim().ifBlank { "alloy" })
            prefs.setCloudProvider(s.providerId)
            if (s.apiKeyInput.isNotBlank()) {
                credentialStore.setApiKey(s.apiKeyInput)
            }
            _uiState.value = _uiState.value.copy(
                apiKeyInput = "",
                hasApiKey = credentialStore.hasApiKey(),
                testResult = TtsTestResult(true, "已保存"),
            )
        }
    }

    /** 测试连接：先保存当前输入，再合成一段短文本验证。 */
    fun testConnection() {
        if (_uiState.value.testing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testing = true, testResult = null)
            try {
                // 先把当前输入落盘，保证测试用的是最新配置
                val s = _uiState.value
                prefs.setCloudBaseUrl(s.baseUrl)
                prefs.setCloudModel(s.model.trim().ifBlank { "tts-1" })
                prefs.setCloudVoice(s.voice.trim().ifBlank { "alloy" })
                prefs.setCloudProvider(s.providerId)
                if (s.apiKeyInput.isNotBlank()) credentialStore.setApiKey(s.apiKeyInput)
                _uiState.value = _uiState.value.copy(
                    apiKeyInput = "",
                    hasApiKey = credentialStore.hasApiKey(),
                )
                val speech = cloudEngine.synthesize("配音测试，连接正常。", prefs.cloudVoice.first(), 1.0f)
                _uiState.value = _uiState.value.copy(
                    testing = false,
                    testResult = TtsTestResult(
                        success = true,
                        "连接成功：返回 ${speech.sampleRate}Hz 音频，" +
                            "${speech.durationMs}ms（费用按服务商标准计）",
                    ),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    testing = false,
                    testResult = TtsTestResult(false, "连接失败：${e.message}"),
                )
            }
        }
    }

    fun clearApiKey() {
        credentialStore.clear()
        _uiState.value = _uiState.value.copy(
            hasApiKey = false,
            apiKeyInput = "",
            testResult = TtsTestResult(true, "API Key 已清除"),
        )
    }
}
