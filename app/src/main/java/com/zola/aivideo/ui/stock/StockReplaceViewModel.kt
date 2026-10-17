package com.zola.aivideo.ui.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zola.aivideo.data.media.MediaMetadataReader
import com.zola.aivideo.data.stock.StockClip
import com.zola.aivideo.data.stock.StockReplaceUseCase
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 素材替换页 UI 状态。 */
data class StockReplaceUiState(
    val taskId: Long = 0L,
    val clip: StockClip? = null,
    val fileName: String = "",
    val sourceDurationSec: Double = 0.0,
    val startSecInput: String = "",
    val endSecInput: String = "",
    val replacing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val done: Boolean = false,
)

/**
 * 素材替换 ViewModel（M6）。
 *
 * 用户输入被替换的时间范围（秒），确认后跑 FFmpeg 三段 concat，
 * 产物为 tasks/<id>/replaced.mp4（合成配置页可选用）。
 */
@HiltViewModel
class StockReplaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val taskFiles: TaskFiles,
    private val metadataReader: MediaMetadataReader,
    private val replaceUseCase: StockReplaceUseCase,
    private val gson: Gson,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StockReplaceUiState(
            taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L,
            // 路由参数经 URLEncoder 编码（见 Routes.stockReplace），这里解码
            fileName = savedStateHandle.get<String>("fileName")
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                .orEmpty(),
        ),
    )
    val uiState: StateFlow<StockReplaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.taskId <= 0L || s.fileName.isBlank()) {
                _uiState.value = s.copy(error = "参数缺失")
                return@launch
            }
            // 素材元信息（sidecar）
            val base = s.fileName.removeSuffix(".mp4")
            val metaFile = taskFiles.stockDir(s.taskId).let {
                java.io.File(it, "$base.json")
            }
            val clip = if (metaFile.exists()) {
                runCatching { gson.fromJson(metaFile.readText(), StockClip::class.java) }
                    .getOrNull()
            } else {
                null
            }
            // 源视频时长（给时间范围默认值与校验）
            val task = repository.get(s.taskId)
            val durationSec = task?.let {
                metadataReader.read(it.sourceVideoPath)?.durationMs
            }?.let { it / 1000.0 } ?: 0.0
            val defaultEnd = if (durationSec > 0) minOf(10.0, durationSec) else 10.0
            _uiState.value = s.copy(
                clip = clip,
                sourceDurationSec = durationSec,
                startSecInput = "0",
                endSecInput = if (defaultEnd > 0) "%.1f".format(defaultEnd) else "",
            )
        }
    }

    fun setStart(v: String) {
        _uiState.value = _uiState.value.copy(startSecInput = v, error = null)
    }

    fun setEnd(v: String) {
        _uiState.value = _uiState.value.copy(endSecInput = v, error = null)
    }

    fun confirm() {
        val s = _uiState.value
        if (s.replacing || s.taskId <= 0L) return
        val start = s.startSecInput.toDoubleOrNull()
        val end = s.endSecInput.toDoubleOrNull()
        if (start == null || end == null || start < 0 || end <= start) {
            _uiState.value = s.copy(error = "时间范围无效（起点≥0，终点>起点）")
            return
        }
        if (s.sourceDurationSec > 0 && end > s.sourceDurationSec) {
            _uiState.value = s.copy(
                error = "终点超出视频时长（${"%.1f".format(s.sourceDurationSec)}s）",
            )
            return
        }
        val clip = s.clip
        if (clip == null) {
            _uiState.value = s.copy(error = "素材元信息缺失，请重新下载")
            return
        }
        val clipFile = java.io.File(taskFiles.stockDir(s.taskId), s.fileName)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(replacing = true, progress = 0f, error = null)
            try {
                replaceUseCase.replace(
                    taskId = s.taskId,
                    clip = clip,
                    clipFile = clipFile,
                    startSec = start,
                    endSec = end,
                    onProgress = { p ->
                        _uiState.value = _uiState.value.copy(progress = p)
                    },
                )
                _uiState.value = _uiState.value.copy(replacing = false, done = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    replacing = false,
                    error = e.message ?: "替换失败",
                )
            }
        }
    }
}
