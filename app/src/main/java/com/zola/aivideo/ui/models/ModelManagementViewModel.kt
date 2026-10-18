package com.zola.aivideo.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.asr.AsrModelCatalog
import com.zola.aivideo.data.asr.AsrPreferences
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.download.ModelDownloadState
import com.zola.aivideo.data.llm.LlmModelCatalog
import com.zola.aivideo.data.llm.LlmPreferences
import com.zola.aivideo.data.tts.TtsModelCatalog
import com.zola.aivideo.domain.asr.AsrModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模型管理 ViewModel（2026-10-17 整合）。
 *
 * 职责：下载状态聚合展示、下载/暂停/继续/删除、模型切换。
 *
 * 管理三类模型：语音模型（ASR）、语言模型（LLM）、配音模型（TTS/Kokoro），
 * 共用下载器。配音模型从独立页面合并进来（用户要求统一管理）。
 */
@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val prefs: AsrPreferences,
    private val llmPrefs: LlmPreferences,
) : ViewModel() {

    val entries: List<AsrModelCatalog.Entry> = AsrModelCatalog.entries

    /** 语言模型条目（M7）。 */
    val llmEntries: List<LlmModelCatalog.Entry> = LlmModelCatalog.entries

    /** 配音模型条目（Kokoro，2026-10-17 从独立页面合并）。 */
    val ttsEntry: TtsModelCatalog.Entry = TtsModelCatalog.entry

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> =
        downloadManager.states

    val selectedModel: StateFlow<AsrModel> =
        prefs.selectedModel.stateIn(viewModelScope, SharingStarted.Eagerly, AsrModel.DEFAULT)

    /** 当前选中的本地语言模型 id（M7）。 */
    val selectedLlmModel: StateFlow<String> =
        llmPrefs.localModel.stateIn(
            viewModelScope, SharingStarted.Eagerly, LlmModelCatalog.DEFAULT_ID,
        )

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** 点"下载/继续"：先做网络检查，再交给下载管理器。 */
    fun onDownload(entry: AsrModelCatalog.Entry) {
        viewModelScope.launch {
            if (!checkNetworkForDownload()) return@launch
            downloadManager.startDownload(entry)
        }
    }

    /** 语言模型下载（M7）：与语音模型共用下载器。 */
    fun onDownloadLlm(entry: LlmModelCatalog.Entry) {
        viewModelScope.launch {
            if (!checkNetworkForDownload()) return@launch
            downloadManager.startDownload(entry)
        }
    }

    /** 配音模型下载（2026-10-17 合并）：Kokoro 单模型。 */
    fun onDownloadTts() {
        viewModelScope.launch {
            if (!checkNetworkForDownload()) return@launch
            downloadManager.startDownload(ttsEntry)
        }
    }

    /** 页面级下载前网络检查：无网络时拦截（2026-10-17 去掉仅 Wi-Fi 限制）。 */
    private suspend fun checkNetworkForDownload(): Boolean {
        if (!downloadManager.isOnline()) {
            _errors.emit("当前无网络连接，请联网后重试")
            return false
        }
        return true
    }

    fun onPause(entry: AsrModelCatalog.Entry) {
        downloadManager.pauseDownload(entry)
    }

    /** 语言模型暂停（M7）。 */
    fun onPauseLlm(entry: LlmModelCatalog.Entry) {
        downloadManager.pauseDownload(entry)
    }

    /** 配音模型暂停（2026-10-17 合并）。 */
    fun onPauseTts() {
        downloadManager.pauseDownload(ttsEntry)
    }

    fun onDelete(entry: AsrModelCatalog.Entry) {
        viewModelScope.launch {
            if (prefs.getSelectedModel() == entry.model) {
                // 删掉当前选中模型后回退到默认档（若默认也未下载，转写时会引导下载）
                prefs.setSelectedModel(AsrModel.DEFAULT)
            }
            downloadManager.deleteModel(entry)
        }
    }

    /** 语言模型删除（M7）：删掉当前选中后回退默认。 */
    fun onDeleteLlm(entry: LlmModelCatalog.Entry) {
        viewModelScope.launch {
            if (llmPrefs.getLocalModel() == entry.id) {
                llmPrefs.setLocalModel(LlmModelCatalog.DEFAULT_ID)
            }
            downloadManager.deleteModel(entry)
        }
    }

    /** 配音模型删除（2026-10-17 合并）。 */
    fun onDeleteTts() {
        viewModelScope.launch {
            downloadManager.deleteModel(ttsEntry)
        }
    }

    /** 切换模型：仅已下载完成的模型可选。 */
    fun onSelect(model: AsrModel) {
        viewModelScope.launch {
            if (!downloadManager.isInstalled(AsrModelCatalog.entryOf(model))) {
                _errors.emit("该模型尚未下载完成，无法选用")
                return@launch
            }
            prefs.setSelectedModel(model)
        }
    }

    /** 切换本地语言模型（M7）：仅已下载完成的可选。 */
    fun onSelectLlm(modelId: String) {
        viewModelScope.launch {
            val entry = LlmModelCatalog.entryOf(modelId) ?: return@launch
            if (!downloadManager.isInstalled(entry)) {
                _errors.emit("该模型尚未下载完成，无法选用")
                return@launch
            }
            llmPrefs.setLocalModel(modelId)
        }
    }

    fun onWifiOnlyChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setWifiOnlyDownload(enabled) }
    }

    fun stateOf(model: AsrModel): ModelDownloadState =
        downloadStates.value[model.id] ?: ModelDownloadState.NotDownloaded
}
