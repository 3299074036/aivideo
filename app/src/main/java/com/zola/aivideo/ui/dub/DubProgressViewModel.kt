package com.zola.aivideo.ui.dub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.tts.DubSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 合成进度 UI 状态。 */
data class DubProgressUiState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val phase: String = "准备中",
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/**
 * 合成进度 ViewModel（M4）：前台协程串行合成，可取消。
 *
 * 说明：配音合成在前台协程执行（退后台可能被系统回收；M5 任务中心落地后
 * 改走 WorkManager 长任务）。
 */
@HiltViewModel
class DubProgressViewModel @Inject constructor(
    private val holder: DubFlowHolder,
    private val synthesizer: DubSynthesizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DubProgressUiState())
    val uiState: StateFlow<DubProgressUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        start()
    }

    fun cancel() {
        job?.cancel()
    }

    private fun start() {
        if (job?.isActive == true) return
        val options = holder.options
        val inputs = holder.toDubInputs()
        if (inputs.isEmpty()) {
            _uiState.value = DubProgressUiState(error = "没有可配音的分段")
            return
        }
        _uiState.value = DubProgressUiState(running = true, total = inputs.size)
        job = viewModelScope.launch {
            try {
                val result = synthesizer.synthesizeDub(
                    segments = inputs,
                    voiceSid = options.voiceSid,
                    cloudVoice = options.cloudVoice,
                    speed = options.speed,
                    engineId = options.engineId,
                    strategy = options.strategy,
                    mixOriginalRatio = options.mixOriginalRatio,
                    videoPath = holder.videoPath,
                    onProgress = { done, total, phase ->
                        _uiState.value = _uiState.value.copy(
                            done = done,
                            total = total,
                            phase = phase,
                        )
                    },
                )
                holder.putResult(result)
                _uiState.value = _uiState.value.copy(running = false, finished = true)
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    running = false,
                    cancelled = true,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    running = false,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }
}
