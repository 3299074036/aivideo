package com.zola.aivideo.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.asr.AsrModelCatalog
import com.zola.aivideo.data.asr.AsrPreferences
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.download.ModelDownloadState
import com.zola.aivideo.data.ffmpeg.FFmpegService
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
 * 模型管理 ViewModel。
 *
 * 职责：下载状态聚合展示、下载/暂停/继续/删除、模型切换、仅 Wi-Fi 开关、
 * FFmpeg 版本展示（M1 报告承诺的诊断行）。
 */
@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val prefs: AsrPreferences,
    ffmpegService: FFmpegService,
) : ViewModel() {

    val entries: List<AsrModelCatalog.Entry> = AsrModelCatalog.entries

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> =
        downloadManager.states

    val selectedModel: StateFlow<AsrModel> =
        prefs.selectedModel.stateIn(viewModelScope, SharingStarted.Eagerly, AsrModel.DEFAULT)

    val wifiOnly: StateFlow<Boolean> =
        prefs.wifiOnlyDownload.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** FFmpeg 版本（诊断用，真机验证 M1 集成是否生效）。 */
    val ffmpegVersion: String? = ffmpegService.getVersion()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** 点"下载/继续"：先做网络检查，再交给下载管理器。 */
    fun onDownload(entry: AsrModelCatalog.Entry) {
        viewModelScope.launch {
            if (!downloadManager.isOnline()) {
                _errors.emit("当前无网络连接，请联网后重试")
                return@launch
            }
            if (prefs.isWifiOnlyDownload() && !downloadManager.isWifiConnected()) {
                _errors.emit("已开启「仅 Wi-Fi 下载」，当前为移动数据；可在下方关闭该开关")
                return@launch
            }
            downloadManager.startDownload(entry)
        }
    }

    fun onPause(entry: AsrModelCatalog.Entry) {
        downloadManager.pauseDownload(entry)
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

    fun onWifiOnlyChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setWifiOnlyDownload(enabled) }
    }

    fun stateOf(model: AsrModel): ModelDownloadState =
        downloadStates.value[model.id] ?: ModelDownloadState.NotDownloaded
}
