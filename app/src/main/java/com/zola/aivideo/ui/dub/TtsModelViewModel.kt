package com.zola.aivideo.ui.dub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.download.ModelDownloadState
import com.zola.aivideo.data.tts.TtsModelCatalog
import com.zola.aivideo.data.tts.TtsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TTS 模型管理 ViewModel（M4）：Kokoro 模型下载/暂停/删除。
 *
 * 与 ModelManagementViewModel 同模式（均由下载管理器聚合状态），
 * 仅 Wi-Fi 开关与 TTS 设置页共用 [TtsPreferences]。
 */
@HiltViewModel
class TtsModelViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val prefs: TtsPreferences,
) : ViewModel() {

    val entry = TtsModelCatalog.entry

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> =
        downloadManager.states

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun onDownload() {
        viewModelScope.launch {
            if (!downloadManager.isOnline()) {
                _errors.emit("当前无网络连接，请联网后重试")
                return@launch
            }
            if (prefs.isWifiOnlyDownload() && !downloadManager.isWifiConnected()) {
                _errors.emit("已开启「仅 Wi-Fi 下载」，当前为移动数据；可在 TTS 设置页关闭该开关")
                return@launch
            }
            downloadManager.startDownload(entry)
        }
    }

    fun onPause() {
        downloadManager.pauseDownload(entry)
    }

    fun onDelete() {
        viewModelScope.launch {
            downloadManager.deleteModel(entry)
            _errors.emit("模型已删除")
        }
    }
}
