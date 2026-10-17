package com.zola.aivideo.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.stock.StockClient
import com.zola.aivideo.data.stock.StockCredentialStore
import com.zola.aivideo.data.stock.StockPreferences
import com.zola.aivideo.data.stock.StockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 素材库设置页 UI 状态。 */
data class StockSettingsUiState(
    val provider: StockProvider = StockProvider.PEXELS,
    val pexelsKeyInput: String = "",
    val pixabayKeyInput: String = "",
    val hasPexelsKey: Boolean = false,
    val hasPixabayKey: Boolean = false,
    val showKey: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val saved: Boolean = false,
)

/**
 * 素材库设置 ViewModel（M6）。
 *
 * Pexels / Pixabay API Key 均为 BYOK：输入框不回显已保存 Key 明文，
 * 非空输入才覆盖保存；Key 申请地址见界面文案。
 */
@HiltViewModel
class StockSettingsViewModel @Inject constructor(
    private val prefs: StockPreferences,
    private val credentialStore: StockCredentialStore,
    private val client: StockClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockSettingsUiState())
    val uiState: StateFlow<StockSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(
            provider = prefs.provider,
            hasPexelsKey = credentialStore.hasKey(StockProvider.PEXELS),
            hasPixabayKey = credentialStore.hasKey(StockProvider.PIXABAY),
        )
    }

    fun setProvider(p: StockProvider) {
        prefs.provider = p
        _uiState.value = _uiState.value.copy(provider = p, saved = false)
    }

    fun setPexelsKeyInput(v: String) {
        _uiState.value = _uiState.value.copy(pexelsKeyInput = v, saved = false)
    }

    fun setPixabayKeyInput(v: String) {
        _uiState.value = _uiState.value.copy(pixabayKeyInput = v, saved = false)
    }

    fun toggleShowKey() {
        _uiState.value = _uiState.value.copy(showKey = !_uiState.value.showKey)
    }

    fun save() {
        val s = _uiState.value
        if (s.pexelsKeyInput.isNotBlank()) {
            credentialStore.setKey(StockProvider.PEXELS, s.pexelsKeyInput)
        }
        if (s.pixabayKeyInput.isNotBlank()) {
            credentialStore.setKey(StockProvider.PIXABAY, s.pixabayKeyInput)
        }
        _uiState.value = s.copy(
            pexelsKeyInput = "",
            pixabayKeyInput = "",
            saved = true,
        )
        refresh()
        _uiState.value = _uiState.value.copy(saved = true)
    }

    fun clearKey(provider: StockProvider) {
        credentialStore.setKey(provider, "")
        refresh()
    }

    /** 测试：用当前提供方 + 已保存 Key 做一次真实搜索（query="ocean"，取 1 条）。 */
    fun testSearch() {
        val s = _uiState.value
        if (s.testing) return
        viewModelScope.launch {
            _uiState.value = s.copy(testing = true, testResult = null)
            // 先落盘当前输入，保证测试用最新 Key
            if (s.pexelsKeyInput.isNotBlank()) {
                credentialStore.setKey(StockProvider.PEXELS, s.pexelsKeyInput)
            }
            if (s.pixabayKeyInput.isNotBlank()) {
                credentialStore.setKey(StockProvider.PIXABAY, s.pixabayKeyInput)
            }
            val provider = _uiState.value.provider
            val key = credentialStore.getKey(provider)
            val result = client.search(provider, key, "ocean", perPage = 1)
            _uiState.value = _uiState.value.copy(
                testing = false,
                pexelsKeyInput = "",
                pixabayKeyInput = "",
                testSuccess = result.isSuccess,
                testResult = result.fold(
                    onSuccess = { clips ->
                        "连接成功：${provider.displayName} 返回 ${clips.size} 条结果"
                    },
                    onFailure = { e -> "连接失败：${e.message}" },
                ),
            )
            refresh()
        }
    }
}
