package com.zola.aivideo.data.work

import com.google.gson.Gson
import com.zola.aivideo.data.subtitle.SrtWriter
import com.zola.aivideo.data.synthesize.SynthesizeOptions
import com.zola.aivideo.data.synthesize.SynthesizeUseCase
import com.zola.aivideo.data.task.PersistedTranslateOptions
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 合成阶段执行器：源视频 + 配音轨 + 字幕 → 成品 mp4 → MediaStore。
 *
 * 从 SynthesizeWorker 提取（2026-10-17 去 WorkManager 化）。
 *
 * 用户配置（字幕模式/编码预设/码率档）来自任务实体的 `synthesizeOptionsJson`
 * （用户在合成配置页确认后写入）。双语开关从翻译配置里取。
 * 成功后任务置 DONE（由 TaskSnapshot 推导）。
 */
@Singleton
class SynthesizeStageRunner @Inject constructor(
    private val repository: TaskRepository,
    private val files: TaskFiles,
    private val gson: Gson,
    private val useCase: SynthesizeUseCase,
) : StageRunner {

    override val stage: TaskStage = TaskStage.SYNTHESIZE

    override suspend fun run(
        taskId: Long,
        onProgress: (Float, String) -> Unit,
    ) {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")

        val translated = files.loadTranslated(taskId)
        if (translated.isEmpty()) throw IllegalStateException("翻译产物缺失，请重试翻译阶段")
        val dubWav = files.dubTrackWav(taskId)
        if (!dubWav.exists()) throw IllegalStateException("配音轨缺失，请重试配音阶段")

        val optionsJson = task.synthesizeOptionsJson
            ?: throw IllegalStateException("合成参数缺失，请重新配置")
        val options = runCatching { gson.fromJson(optionsJson, SynthesizeOptions::class.java) }.getOrNull()
            ?: throw IllegalStateException("合成参数损坏，请重新配置")

        val translateOptions = task.translateOptionsJson?.let {
            PersistedTranslateOptions.fromJson(gson, it)
        }
        val bilingual = translateOptions?.bilingual ?: false

        // 修复（2026-10-17）：task.name 可能自带 .mp4 后缀（如从文件名导入），
        // 去掉再拼，避免 "xxx.mp4.mp4"
        val rawName = SrtWriter.sanitizeFileName(task.name).ifBlank { "aivideo_$taskId" }
        val displayName = rawName.removeSuffix(".mp4").removeSuffix(".MP4")

        // M6：用户在合成配置页选用了素材替换版视频，且产物存在 → 用它作为合成源
        val videoPath = resolveVideoSource(
            useReplacedVideo = options.useReplacedVideo,
            sourceVideoPath = task.sourceVideoPath,
            replacedVideo = files.replacedMp4(taskId),
        )

        var lastReported = 0f
        val result = useCase.synthesize(
            videoPath = videoPath,
            dubTrackWav = dubWav.absolutePath,
            segments = translated,
            bilingual = bilingual,
            options = options,
            outputMp4 = files.outputMp4(taskId),
            srtFile = files.subsSrt(taskId),
            displayName = displayName,
            onProgress = { p ->
                if (p - lastReported >= 0.01f || p >= 1f) {
                    lastReported = p
                    onProgress(p, "合成")
                }
            },
        )
        // synthesizeOutput 存 MediaStore Uri（有）或私有目录路径（无）
        val finalRef = result.exportedUri ?: result.outputFile.absolutePath
        repository.setStageDone(taskId, TaskStage.SYNTHESIZE, finalRef)
    }

    companion object {
        /**
         * 合成源选择（M6）：纯函数，可单元测试。
         *
         * 用户选用替换版且文件存在 → 用 replaced.mp4；否则回落源视频
         * （替换产物被删等异常情况不阻断合成）。
         */
        fun resolveVideoSource(
            useReplacedVideo: Boolean,
            sourceVideoPath: String,
            replacedVideo: File,
        ): String = if (useReplacedVideo && replacedVideo.exists()) {
            replacedVideo.absolutePath
        } else {
            sourceVideoPath
        }
    }
}
