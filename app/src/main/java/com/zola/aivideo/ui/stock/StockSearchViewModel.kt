package com.zola.aivideo.ui.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zola.aivideo.data.stock.StockClient
import com.zola.aivideo.data.stock.StockClip
import com.zola.aivideo.data.stock.StockCredentialStore
import com.zola.aivideo.data.stock.StockPreferences
import com.zola.aivideo.data.stock.StockProvider
import com.zola.aivideo.data.task.TaskFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 已下载素材条目。 */
data class DownloadedClip(
    val file: File,
    val clip: StockClip?,
)

/** 素材搜索页 UI 状态。 */
data class StockSearchUiState(
    val taskId: Long = 0L,
    val query: String = "",
    val provider: StockProvider = StockProvider.PEXELS,
    val searching: Boolean = false,
    val results: List<StockClip> = emptyList(),
    val searchError: String? = null,
    /** clipId → 下载进度（0f~1f），下载中。 */
    val downloading: Map<String, Float> = emptyMap(),
    val downloaded: List<DownloadedClip> = emptyList(),
    val downloadError: String? = null,
)

/**
 * 素材搜索 ViewModel（M6，任务模式）。
 *
 * - 搜索：走当前提供方 + 已保存 Key
 * - 下载：落盘到 tasks/<id>/stock/，同时保存元信息 sidecar JSON；
 *   下载完成即追加一条"仅下载"的授权记录（片段起止为 null）
 */
@HiltViewModel
class StockSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prefs: StockPreferences,
    private val credentialStore: StockCredentialStore,
    private val client: StockClient,
    private val taskFiles: TaskFiles,
    private val gson: Gson,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StockSearchUiState(
            taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L,
            provider = prefs.provider,
        ),
    )
    val uiState: StateFlow<StockSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshDownloaded()
    }

    fun setQuery(v: String) {
        _uiState.value = _uiState.value.copy(query = v, searchError = null)
    }

    fun setProvider(p: StockProvider) {
        prefs.provider = p
        _uiState.value = _uiState.value.copy(provider = p)
    }

    fun search() {
        val s = _uiState.value
        if (s.searching || s.taskId <= 0L) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = s.copy(searching = true, searchError = null, results = emptyList())
            val key = credentialStore.getKey(s.provider)
            val result = client.search(s.provider, key, s.query)
            _uiState.value = _uiState.value.copy(
                searching = false,
                results = result.getOrNull().orEmpty(),
                searchError = result.exceptionOrNull()?.message,
            )
        }
    }

    /** 下载某条结果；完成后刷新已下载列表。 */
    fun download(clip: StockClip) {
        val s = _uiState.value
        if (s.downloading.containsKey(clip.clipId) || s.taskId <= 0L) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloading = _uiState.value.downloading + (clip.clipId to 0f),
                downloadError = null,
            )
            val dest = taskFiles.stockClipFile(
                s.taskId,
                clip.provider.name,
                clip.clipId,
            )
            val result = client.download(clip.videoUrl, dest) { p ->
                _uiState.value = _uiState.value.copy(
                    downloading = _uiState.value.downloading + (clip.clipId to p),
                )
            }
            result.fold(
                onSuccess = { file ->
                    // 元信息 sidecar（替换页需要作者/授权信息）
                    taskFiles.stockClipMetaFile(s.taskId, clip.provider.name, clip.clipId)
                        .writeText(gson.toJson(clip))
                    // 授权存档：仅下载（未替换片段）
                    taskFiles.appendLicense(
                        s.taskId,
                        com.zola.aivideo.data.stock.StockLicenseRecord(
                            provider = clip.provider.name.lowercase(),
                            clipId = clip.clipId,
                            clipPageUrl = clip.licensePageUrl,
                            authorName = clip.authorName,
                            authorUrl = clip.authorUrl,
                            licenseName = clip.licenseName,
                            fileName = file.name,
                            replacedSegmentStartSec = null,
                            replacedSegmentEndSec = null,
                            recordedAt = System.currentTimeMillis(),
                        ),
                    )
                    _uiState.value = _uiState.value.copy(
                        downloading = _uiState.value.downloading - clip.clipId,
                    )
                    refreshDownloaded()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        downloading = _uiState.value.downloading - clip.clipId,
                        downloadError = "下载失败：${e.message}",
                    )
                },
            )
        }
    }

    fun refreshDownloaded() {
        val taskId = _uiState.value.taskId
        if (taskId <= 0L) return
        _uiState.value = _uiState.value.copy(
            downloaded = taskFiles.listStockClips(taskId).map { (f, c) ->
                DownloadedClip(f, c)
            },
        )
    }
}
