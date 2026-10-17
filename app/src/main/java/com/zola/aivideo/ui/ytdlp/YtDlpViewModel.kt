package com.zola.aivideo.ui.ytdlp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.ytdlp.YtDlpChannel
import com.zola.aivideo.data.ytdlp.YtDlpDownloadState
import com.zola.aivideo.data.ytdlp.YtDlpDownloadTask
import com.zola.aivideo.data.ytdlp.YtDlpDownloader
import com.zola.aivideo.data.ytdlp.YtDlpEnvironment
import com.zola.aivideo.data.ytdlp.YtDlpPreferences
import com.zola.aivideo.data.ytdlp.YtDlpUpdate
import com.zola.aivideo.data.ytdlp.YtDlpUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 链接下载页 UI 状态。 */
data class YtDlpUiState(
    /** Python 解释器是否已随 APK 内置。 */
    val hasInterpreter: Boolean = false,
    /** yt-dlp 归档是否已就绪。 */
    val hasArchive: Boolean = false,
    val installedTag: String = "",
    val localVersion: String? = null,
    val channel: YtDlpChannel = YtDlpChannel.STABLE,
    val checking: Boolean = false,
    val availableUpdate: YtDlpUpdate? = null,
    val checkError: String? = null,
    val checkMessage: String? = null,
    val downloadingUpdate: Boolean = false,
    val updateProgress: Float = 0f,
    val urlsInput: String = "",
    val tasks: List<YtDlpDownloadTask> = emptyList(),
    val batchRunning: Boolean = false,
)

/**
 * 链接下载 ViewModel（M6）。
 *
 * 上半：yt-dlp 独立更新通道（检查/下载/安装归档）；
 * 下半：批量下载队列（顺序执行，逐条回调状态）。
 *
 * 前置要求：解释器随 APK 内置 + yt-dlp 归档已下载；缺失时界面给出明确指引，
 * 不抛原生崩溃。
 */
@HiltViewModel
class YtDlpViewModel @Inject constructor(
    private val prefs: YtDlpPreferences,
    private val environment: YtDlpEnvironment,
    private val updater: YtDlpUpdater,
    private val downloader: YtDlpDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YtDlpUiState())
    val uiState: StateFlow<YtDlpUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null

    init {
        refreshEnv()
        // 自动检查（每日一次节流 + 开关）
        if (prefs.autoCheck) {
            checkForUpdate(force = false)
        }
    }

    fun refreshEnv() {
        _uiState.value = _uiState.value.copy(
            hasInterpreter = environment.hasInterpreter(),
            hasArchive = environment.hasArchive(),
            installedTag = prefs.installedTag,
            channel = prefs.channel,
        )
        if (environment.hasArchive()) {
            viewModelScope.launch {
                val v = updater.localVersion()
                _uiState.value = _uiState.value.copy(localVersion = v)
            }
        }
    }

    fun setChannel(channel: YtDlpChannel) {
        prefs.channel = channel
        _uiState.value = _uiState.value.copy(
            channel = channel,
            availableUpdate = null,
            checkMessage = null,
            checkError = null,
        )
    }

    fun setUrlsInput(v: String) {
        _uiState.value = _uiState.value.copy(urlsInput = v)
    }

    /** 检查更新（force=true 为用户手动触发，忽略节流）。 */
    fun checkForUpdate(force: Boolean = true) {
        val s = _uiState.value
        if (s.checking || s.downloadingUpdate) return
        viewModelScope.launch {
            _uiState.value = s.copy(checking = true, checkError = null, checkMessage = null)
            val result = updater.checkForUpdate(force)
            _uiState.value = _uiState.value.copy(checking = false)
            result.fold(
                onSuccess = { update ->
                    _uiState.value = _uiState.value.copy(
                        availableUpdate = update,
                        checkMessage = if (update == null) {
                            if (prefs.hasInstalled()) "已是最新版本（${prefs.installedTag}）"
                            else "未发现已安装版本"
                        } else {
                            null
                        },
                    )
                },
                onFailure = { e ->
                    // 节流内的自动检查返回 null 也算一种"无更新"（静默）
                    _uiState.value = _uiState.value.copy(
                        checkError = if (force) e.message else null,
                    )
                },
            )
        }
    }

    /** 下载并安装更新。 */
    fun downloadUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        val s = _uiState.value
        if (s.downloadingUpdate) return
        // 移动数据二次确认由 UI 层在调用前处理（这里只做开关读取，供 UI 判断）
        viewModelScope.launch {
            _uiState.value = s.copy(downloadingUpdate = true, updateProgress = 0f)
            val result = updater.downloadUpdate(update) { p ->
                _uiState.value = _uiState.value.copy(updateProgress = p)
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        downloadingUpdate = false,
                        availableUpdate = null,
                        checkMessage = "yt-dlp 已更新到 ${update.tag}",
                    )
                    refreshEnv()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        downloadingUpdate = false,
                        checkError = e.message,
                    )
                },
            )
        }
    }

    /** 是否需要移动数据二次确认（开关开且不在 Wi-Fi 下由 UI 判断，这里只暴露开关）。 */
    fun isWifiOnly(): Boolean = prefs.wifiOnly

    fun setWifiOnly(v: Boolean) {
        prefs.wifiOnly = v
    }

    /** 开始批量下载：输入框每行一个 URL，顺序执行。 */
    fun startBatch() {
        val s = _uiState.value
        if (s.batchRunning) return
        if (!s.hasInterpreter || !s.hasArchive) {
            _uiState.value = s.copy(
                checkError = "环境未就绪：" +
                    if (!s.hasInterpreter) "Python 运行环境未内置"
                    else "yt-dlp 尚未下载",
            )
            return
        }
        val urls = s.urlsInput.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            _uiState.value = s.copy(checkError = "请先输入至少一个视频链接（每行一个）")
            return
        }
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                batchRunning = true,
                checkError = null,
                tasks = urls.map { YtDlpDownloadTask(url = it) },
            )
            urls.forEachIndexed { index, url ->
                val task = downloader.download(url) { t ->
                    _uiState.value = _uiState.value.copy(
                        tasks = _uiState.value.tasks.toMutableList().also { list ->
                            list[index] = t
                        },
                    )
                }
                _uiState.value = _uiState.value.copy(
                    tasks = _uiState.value.tasks.toMutableList().also { list ->
                        list[index] = task
                    },
                )
            }
            _uiState.value = _uiState.value.copy(batchRunning = false)
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        batchJob = null
        _uiState.value = _uiState.value.copy(batchRunning = false)
    }

    /** 完成项的文件（供 UI 经 FileProvider 分享到导入页）。 */
    fun doneFile(index: Int): File? =
        _uiState.value.tasks.getOrNull(index)
            ?.takeIf { it.state == YtDlpDownloadState.DONE }
            ?.file?.takeIf { it.exists() }
}
