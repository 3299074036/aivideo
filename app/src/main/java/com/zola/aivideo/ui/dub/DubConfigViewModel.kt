package com.zola.aivideo.ui.dub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.tts.DubOptions
import com.zola.aivideo.data.tts.DubSegmentInput
import com.zola.aivideo.data.tts.DubStrategy
import com.zola.aivideo.data.tts.TtsCredentialStore
import com.zola.aivideo.data.tts.TtsEngineId
import com.zola.aivideo.data.tts.TtsModelCatalog
import com.zola.aivideo.data.tts.TtsPreferences
import com.zola.aivideo.data.tts.TtsVoiceCatalog
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.work.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 配音配置页 UI 状态。 */
data class DubConfigUiState(
    val segmentCount: Int = 0,
    val totalDurationText: String = "",
    val engineId: TtsEngineId = TtsEngineId.LOCAL,
    val localReady: Boolean = false,
    val cloudReady: Boolean = false,
    val voiceSid: Int = TtsVoiceCatalog.DEFAULT_SID,
    val cloudVoice: String = "alloy",
    val speed: Float = 1.0f,
    val strategy: DubStrategy = DubStrategy.REPLACE,
    val mixOriginalRatio: Float = 0.3f,
    val videoPath: String? = null,
    /** 非空时阻挡开始合成（显示原因）。 */
    val blockingReason: String? = null,
)

/**
 * 配音配置页 ViewModel（M4；M5 增加任务模式）。
 *
 * 初始化时：校验分段非空、读取偏好、检查端侧模型就绪与云 Key 配置。
 * 任务模式（taskId > 0，M5）：分段从任务目录的 translated.json 读取，
 * 视频路径用任务的源视频；确认后把配置持久化并调度配音阶段。
 */
@HiltViewModel
class DubConfigViewModel @Inject constructor(
    private val holder: DubFlowHolder,
    private val prefs: TtsPreferences,
    private val credentialStore: TtsCredentialStore,
    private val downloadManager: ModelDownloadManager,
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val taskFiles: TaskFiles,
    private val taskScheduler: TaskScheduler,
    private val gson: Gson,
) : ViewModel() {

    /** 任务模式的任务 id（0 表示普通前台流程）。 */
    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L

    /** 任务模式。 */
    val taskMode: Boolean get() = taskId > 0L

    /** 任务模式下确认完成（UI 观察后回退）。 */
    var taskModeDone by mutableStateOf(false)
        private set

    var uiState by mutableStateOf(DubConfigUiState())
        private set

    init {
        viewModelScope.launch {
            val inputs: List<DubSegmentInput>
            val videoPath: String?
            if (taskMode) {
                val translated = taskFiles.loadTranslated(taskId)
                inputs = translated.mapIndexed { index, seg ->
                    DubSegmentInput(index, seg.original.startMs, seg.original.endMs, seg.translatedText)
                }
                videoPath = taskRepository.get(taskId)?.sourceVideoPath
            } else {
                inputs = holder.toDubInputs()
                videoPath = holder.videoPath
            }
            val totalMs = inputs.maxOfOrNull { it.endMs } ?: 0L
            val totalDurationText = "%d:%02d".format(totalMs / 60000, (totalMs / 1000) % 60)
            val localReady = downloadManager.isInstalled(TtsModelCatalog.entry)
            val cloudReady = credentialStore.hasApiKey()
            uiState = uiState.copy(
                segmentCount = inputs.size,
                totalDurationText = totalDurationText,
                localReady = localReady,
                cloudReady = cloudReady,
                engineId = prefs.engine.first(),
                voiceSid = prefs.voiceSid.first(),
                cloudVoice = prefs.cloudVoice.first(),
                speed = prefs.speechSpeed.first(),
                strategy = prefs.dubStrategy.first(),
                mixOriginalRatio = prefs.mixOriginalRatio.first(),
                videoPath = videoPath,
            )
            refreshBlockingReason()
        }
    }

    fun setEngine(engine: TtsEngineId) {
        uiState = uiState.copy(engineId = engine)
        refreshBlockingReason()
    }

    fun setVoiceSid(sid: Int) {
        uiState = uiState.copy(voiceSid = sid)
    }

    fun setSpeed(speed: Float) {
        uiState = uiState.copy(speed = speed.coerceIn(0.5f, 2.0f))
    }

    fun setStrategy(strategy: DubStrategy) {
        uiState = uiState.copy(strategy = strategy)
        refreshBlockingReason()
    }

    fun setMixOriginalRatio(ratio: Float) {
        uiState = uiState.copy(mixOriginalRatio = ratio.coerceIn(0f, 1f))
    }

    /** 进入合成前把配置落回偏好与 Holder。 */
    fun commitOptions() {
        val s = uiState
        val strategy = if (s.strategy == DubStrategy.MIX && s.videoPath == null) {
            // 无视频路径时混音降级为替换
            DubStrategy.REPLACE
        } else {
            s.strategy
        }
        val options = DubOptions(
            voiceSid = s.voiceSid,
            cloudVoice = s.cloudVoice,
            speed = s.speed,
            engineId = s.engineId,
            strategy = strategy,
            mixOriginalRatio = s.mixOriginalRatio,
        )
        viewModelScope.launch {
            prefs.setEngine(s.engineId)
            prefs.setVoiceSid(s.voiceSid)
            prefs.setSpeechSpeed(s.speed)
            prefs.setDubStrategy(s.strategy)
            prefs.setMixOriginalRatio(s.mixOriginalRatio)
            if (taskMode) {
                // 任务模式：配置落盘 → 调度配音阶段
                taskRepository.saveStageOptions(taskId, TaskStage.DUB, gson.toJson(options))
                taskRepository.updateStageState(taskId, TaskStage.DUB, StageState.PENDING)
                taskScheduler.scheduleStage(taskId, TaskStage.DUB)
                taskModeDone = true
            } else {
                holder.putOptions(options)
            }
        }
    }

    private fun refreshBlockingReason() {
        val s = uiState
        val reason = when {
            s.segmentCount == 0 -> "没有可配音的分段，请返回重新翻译"
            s.engineId == TtsEngineId.LOCAL && !s.localReady ->
                "Kokoro 模型尚未下载，请先下载模型（约 206MB）"
            s.engineId == TtsEngineId.CLOUD && !s.cloudReady ->
                "云 TTS 未配置 API Key，请先配置"
            else -> null
        }
        uiState = s.copy(blockingReason = reason)
    }
}
