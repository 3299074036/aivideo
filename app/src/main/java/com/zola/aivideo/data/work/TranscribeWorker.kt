package com.zola.aivideo.data.work

import android.content.Context
import androidx.work.WorkerParameters
import com.zola.aivideo.data.asr.TranscribeAudioUseCase
import com.zola.aivideo.data.asr.TranscribeStage
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * 转写 Worker（M5）：本地视频 → 端侧 ASR → transcript.json。
 *
 * 无需用户介入：模型在转写前由用例检查（未下载抛 IllegalStateException，
 * 文案可直接展示；用户去模型管理页下载后重试本阶段）。
 * 成功后下一阶段（翻译）置 NEEDS_INPUT，等用户配置翻译参数。
 */
class TranscribeWorker(
    appContext: Context,
    params: WorkerParameters,
    repository: TaskRepository,
    private val useCase: TranscribeAudioUseCase,
    private val files: TaskFiles,
) : LongTaskWorker(appContext, params, TaskStage.TRANSCRIBE, repository) {

    override suspend fun doStageWork(): Result {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")
        val videoPath = task.sourceVideoPath
        if (videoPath.isBlank()) throw IllegalStateException("源视频路径为空")

        // 进度上报 scope：transcribe 的 onStage 是非挂起回调，在这里异步双写进度
        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // 去重：相邻回调进度差 <1% 不上报，避免通知栏刷屏
        val lastReported = AtomicReference(0f)
        try {
            val segments = useCase.transcribe(videoPath) { stage ->
                // 阶段回调转进度：解码 0~15%，转写 15%~90%，分句 90%~100%
                val p = when (stage) {
                    is TranscribeStage.Decoding -> stage.progress * 0.15f
                    is TranscribeStage.Transcribing ->
                        if (stage.total > 0) 0.15f + 0.75f * stage.index / stage.total else 0.15f
                    TranscribeStage.Segmenting -> 0.92f
                }.coerceIn(0f, 1f)
                if (p - lastReported.get() >= 0.01f || p >= 1f) {
                    lastReported.set(p)
                    progressScope.launch { reportProgress(p, "识别") }
                }
            }

            if (segments.isEmpty()) throw IllegalStateException("未识别出任何语音内容")
            val outFile = files.saveTranscript(taskId, segments.sortedBy { it.startMs })
            repository.setStageDone(taskId, TaskStage.TRANSCRIBE, outFile.absolutePath)
            // 下一阶段待用户确认（翻译配置页）
            repository.setStageNeedsInput(taskId, TaskStage.TRANSLATE)
            return Result.success()
        } finally {
            progressScope.cancel()
        }
    }

    /** 手写 Factory（不用 @HiltWorker，见 AiVideoWorkerFactory 注释）。 */
    class Factory @Inject constructor(
        private val repository: TaskRepository,
        private val useCase: TranscribeAudioUseCase,
        private val files: TaskFiles,
    ) {
        fun create(
            appContext: Context,
            params: WorkerParameters,
        ): TranscribeWorker = TranscribeWorker(appContext, params, repository, useCase, files)
    }
}
