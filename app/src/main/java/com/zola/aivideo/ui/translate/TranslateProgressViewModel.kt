package com.zola.aivideo.ui.translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.llm.SubtitleTranslator
import com.zola.aivideo.data.llm.TranslateProgress
import com.zola.aivideo.data.llm.TranslatedSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 翻译进度状态。 */
data class TranslateProgressUiState(
    val stageText: String = "准备中…",
    val chunkDone: Int = 0,
    val chunkTotal: Int = 1,
    val charsReceived: Int = 0,
    val running: Boolean = true,
    val error: String? = null,
    val cancelled: Boolean = false,
)

/**
 * 翻译进度 ViewModel（M3）。
 *
 * 进入页面即开始翻译（request 已由配置页写入 holder）。
 * 取消 → 中断协程 → 底层 SSE 请求同步取消。
 */
@HiltViewModel
class TranslateProgressViewModel @Inject constructor(
    private val holder: TranslationFlowHolder,
    private val translator: SubtitleTranslator,
) : ViewModel() {

    var uiState by mutableStateOf(TranslateProgressUiState())
        private set

    /** 翻译完成的结果（成功时由 UI 观察并跳转）。 */
    var doneResult: List<TranslatedSegment>? by mutableStateOf(null)
        private set

    var repairedOnce by mutableStateOf(false)
        private set

    var unverifiedCount by mutableStateOf(0)
        private set

    private var job: Job? = null

    fun start() {
        if (job != null) return
        val request = holder.lastRequest
        if (request == null) {
            uiState = uiState.copy(running = false, error = "翻译请求丢失，请返回重试")
            return
        }
        job = viewModelScope.launch {
            try {
                val outcome = translator.translate(request) { progress ->
                    when (progress) {
                        is TranslateProgress.Stage ->
                            uiState = uiState.copy(stageText = progress.text)
                        is TranslateProgress.Chunk ->
                            uiState = uiState.copy(
                                chunkDone = progress.done,
                                chunkTotal = progress.total,
                            )
                        is TranslateProgress.Streaming ->
                            uiState = uiState.copy(charsReceived = progress.charsReceived)
                    }
                }
                repairedOnce = outcome.repairedOnce
                unverifiedCount = outcome.unverifiedCount
                holder.putOutcome(outcome.segments)
                doneResult = outcome.segments
                uiState = uiState.copy(running = false)
            } catch (e: CancellationException) {
                uiState = uiState.copy(running = false, cancelled = true)
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    running = false,
                    error = e.message ?: "翻译失败",
                )
            }
        }
    }

    /** 用户取消：中断翻译。 */
    fun cancel() {
        job?.cancel()
        uiState = uiState.copy(running = false, cancelled = true)
    }

    fun retry() {
        job = null
        doneResult = null
        uiState = TranslateProgressUiState()
        start()
    }
}
