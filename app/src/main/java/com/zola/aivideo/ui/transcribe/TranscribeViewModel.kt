package com.zola.aivideo.ui.transcribe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.asr.AsrModelCatalog
import com.zola.aivideo.data.asr.AsrPreferences
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.asr.TranscribeAudioUseCase
import com.zola.aivideo.data.asr.TranscribeStage
import com.zola.aivideo.data.asr.TranscriptionResultHolder
import com.zola.aivideo.domain.asr.AsrModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 转写页 UI 状态。 */
sealed interface TranscribeUiState {
    /** 检查模型中。 */
    data object Checking : TranscribeUiState

    /** 所选模型未下载：引导去模型管理页。 */
    data class NeedModel(val modelName: String) : TranscribeUiState

    /** 二次确认：前台运行提醒。 */
    data class Confirm(val modelName: String) : TranscribeUiState

    /** 运行中：阶段文案 + 进度（null 表示不定）。 */
    data class Running(val stageText: String, val progress: Float?) : TranscribeUiState

    /** 失败：原因 + 重试。 */
    data class Error(val reason: String) : TranscribeUiState
}

/**
 * 转写 ViewModel：前台协程驱动转写（M2）。
 *
 * 说明：转写跑在 viewModelScope 前台协程，退后台可能被系统回收——
 * 二次确认页已明确提醒用户保持 App 在前台。WorkManager 统一调度是 M5 的事。
 */
@HiltViewModel
class TranscribeViewModel @Inject constructor(
    private val useCase: TranscribeAudioUseCase,
    private val downloadManager: ModelDownloadManager,
    private val prefs: AsrPreferences,
    private val holder: TranscriptionResultHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TranscribeUiState>(TranscribeUiState.Checking)
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    private val _doneEvent = MutableSharedFlow<Unit>()
    /** 转写成功：由界面导航到字幕编辑页。 */
    val doneEvent: SharedFlow<Unit> = _doneEvent.asSharedFlow()

    private var job: Job? = null
    private var startedFor: String? = null

    /** 入口：界面 onResume/LaunchedEffect 调用（幂等）。 */
    fun start(videoPath: String) {
        if (startedFor == videoPath) return
        startedFor = videoPath
        viewModelScope.launch {
            _uiState.value = TranscribeUiState.Checking
            val model = prefs.getSelectedModel()
            if (!downloadManager.isInstalled(AsrModelCatalog.entryOf(model))) {
                _uiState.value = TranscribeUiState.NeedModel(displayNameOf(model))
            } else {
                _uiState.value = TranscribeUiState.Confirm(displayNameOf(model))
            }
        }
    }

    /** 二次确认通过：开始转写。 */
    fun confirmStart(videoPath: String) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val segments = useCase.transcribe(videoPath) { stage ->
                    _uiState.value = TranscribeUiState.Running(
                        stageText = stageText(stage),
                        progress = stageProgress(stage),
                    )
                }
                if (segments.isEmpty()) {
                    _uiState.value = TranscribeUiState.Error("未识别出有效语音：视频可能是纯音乐或音量过低")
                } else {
                    holder.put(videoPath, segments)
                    _doneEvent.emit(Unit)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = TranscribeUiState.Error("已取消转写")
                throw e
            } catch (e: Exception) {
                _uiState.value = TranscribeUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** 取消转写。 */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** 从错误态重试：回到确认页。 */
    fun retry(videoPath: String) {
        startedFor = null
        start(videoPath)
    }

    private fun stageText(stage: TranscribeStage): String = when (stage) {
        is TranscribeStage.Decoding ->
            "正在解码音频…${(stage.progress * 100).toInt()}%"
        is TranscribeStage.Transcribing ->
            "正在识别语音…（第 ${stage.index}/${stage.total} 段）"
        TranscribeStage.Segmenting ->
            "正在整理字幕…"
    }

    private fun stageProgress(stage: TranscribeStage): Float? = when (stage) {
        is TranscribeStage.Decoding -> 0.15f * stage.progress
        is TranscribeStage.Transcribing ->
            if (stage.total > 0) 0.15f + 0.75f * (stage.index.toFloat() / stage.total) else null
        TranscribeStage.Segmenting -> 0.95f
    }

    private fun displayNameOf(model: AsrModel): String = when (model) {
        AsrModel.TINY_INT8 -> "Whisper Tiny"
        AsrModel.SENSE_VOICE -> "SenseVoice"
        AsrModel.SMALL_INT8 -> "Whisper Small"
    }
}
