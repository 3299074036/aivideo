package com.zola.aivideo.ui.synthesize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zola.aivideo.data.synthesize.BitrateTier
import com.zola.aivideo.data.synthesize.EncodePreset
import com.zola.aivideo.data.synthesize.SubtitleMode
import com.zola.aivideo.data.synthesize.SynthesizeOptions
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.work.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 合成配置 ViewModel（M5）。
 *
 * 用户选项：字幕模式（烧录/外挂）× 编码预设（原画质/压缩）× 码率档（压缩版）。
 * 确认后把配置持久化到任务实体并调度 SynthesizeWorker。
 *
 * 说明文案：
 * - 烧录模式字幕烧进画面，视频必须重编码；"原画质"档用高码率近似
 * - 外挂模式视频流 copy（原画质档），字幕以外挂 srt 轨存在
 * - 码率档只影响压缩版
 */
@HiltViewModel
class SynthesizeConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val taskFiles: TaskFiles,
    private val taskScheduler: TaskScheduler,
    private val gson: Gson,
) : ViewModel() {

    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L

    var options by mutableStateOf(SynthesizeOptions())
        private set

    /** null 表示可开始；非 null 为阻断文案。 */
    var blockReason by mutableStateOf<String?>(null)
        private set

    /** 确认完成（UI 观察后回退到任务详情页）。 */
    var confirmDone by mutableStateOf(false)
        private set

    var taskName by mutableStateOf("")
        private set

    /** M6：任务是否存在素材替换产物（replaced.mp4），有才展示"使用替换版"开关。 */
    var hasReplacedVideo by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val task = taskRepository.get(taskId)
            if (task == null) {
                blockReason = "任务不存在"
                return@launch
            }
            taskName = task.name
            options = task.synthesizeOptionsJson?.let {
                runCatching { gson.fromJson(it, SynthesizeOptions::class.java) }.getOrNull()
            } ?: SynthesizeOptions()
            val dubWav = taskFiles.dubTrackWav(taskId)
            val translated = taskFiles.loadTranslated(taskId)
            hasReplacedVideo = taskFiles.replacedMp4(taskId).exists()
            // 替换产物被删后，开关自动回落
            if (!hasReplacedVideo && options.useReplacedVideo) {
                options = options.copy(useReplacedVideo = false)
            }
            blockReason = when {
                !dubWav.exists() -> "配音产物缺失，请先重试配音阶段"
                translated.isEmpty() -> "翻译产物缺失，请先重试翻译阶段"
                task.stageState(TaskStage.SYNTHESIZE) == StageState.RUNNING -> "合成正在进行中"
                else -> null
            }
        }
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        options = options.copy(subtitleMode = mode)
    }

    fun setEncodePreset(preset: EncodePreset) {
        options = options.copy(encodePreset = preset)
    }

    fun setBitrateTier(tier: BitrateTier) {
        options = options.copy(bitrateTier = tier)
    }

    /** M6：切换是否使用素材替换后的视频作为合成源。 */
    fun setUseReplacedVideo(use: Boolean) {
        if (use && !hasReplacedVideo) return
        options = options.copy(useReplacedVideo = use)
    }

    /** 确认 → 持久化配置 → 调度合成 Worker。 */
    fun confirm() {
        if (blockReason != null) return
        viewModelScope.launch {
            taskRepository.saveStageOptions(taskId, TaskStage.SYNTHESIZE, gson.toJson(options))
            taskRepository.updateStageState(taskId, TaskStage.SYNTHESIZE, StageState.PENDING)
            taskScheduler.scheduleStage(taskId, TaskStage.SYNTHESIZE)
            confirmDone = true
        }
    }
}
