package com.zola.aivideo.data.work

import com.zola.aivideo.data.asr.TranscribeAudioUseCase
import com.zola.aivideo.data.asr.TranscribeStage
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 转写阶段执行器：本地视频 → 端侧 ASR → transcript.json。
 *
 * 从 TranscribeWorker 提取（2026-10-17 去 WorkManager 化）。
 * 无需用户介入：模型在转写前由用例检查（未下载抛 IllegalStateException，
 * 文案可直接展示；用户去模型管理页下载后重试本阶段）。
 * 成功后下一阶段（翻译）置 NEEDS_INPUT，等用户配置翻译参数。
 */
@Singleton
class TranscribeStageRunner @Inject constructor(
    private val repository: TaskRepository,
    private val useCase: TranscribeAudioUseCase,
    private val files: TaskFiles,
) : StageRunner {

    override val stage: TaskStage = TaskStage.TRANSCRIBE

    override suspend fun run(
        taskId: Long,
        onProgress: (Float, String) -> Unit,
    ) {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")
        val videoPath = task.sourceVideoPath
        if (videoPath.isBlank()) throw IllegalStateException("源视频路径为空")

        // 去重：相邻回调进度差 <1% 不上报
        var lastReported = 0f
        val segments = useCase.transcribe(videoPath) { stage ->
            // 阶段回调转进度：解码 0~15%，转写 15%~90%，分句 90%~100%
            val p = when (stage) {
                is TranscribeStage.Decoding -> stage.progress * 0.15f
                is TranscribeStage.Transcribing ->
                    if (stage.total > 0) 0.15f + 0.75f * stage.index / stage.total else 0.15f
                TranscribeStage.Segmenting -> 0.92f
            }.coerceIn(0f, 1f)
            if (p - lastReported >= 0.01f || p >= 1f) {
                lastReported = p
                onProgress(p, "识别")
            }
        }

        if (segments.isEmpty()) throw IllegalStateException("未识别出任何语音内容")
        val outFile = files.saveTranscript(taskId, segments.sortedBy { it.startMs })
        repository.setStageDone(taskId, TaskStage.TRANSCRIBE, outFile.absolutePath)
        // 下一阶段待用户确认（翻译配置页）
        repository.setStageNeedsInput(taskId, TaskStage.TRANSLATE)
    }
}
