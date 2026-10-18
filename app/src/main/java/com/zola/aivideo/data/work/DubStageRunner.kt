package com.zola.aivideo.data.work

import com.google.gson.Gson
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.tts.DubOptions
import com.zola.aivideo.data.tts.DubSegmentInput
import com.zola.aivideo.data.tts.DubSynthesizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配音阶段执行器：translated.json → TTS 合成+对齐 → dub_track.wav。
 *
 * 从 DubWorker 提取（2026-10-17 去 WorkManager 化）。
 *
 * 用户配置（音色/语速/引擎/策略/混音比）来自任务实体的 `dubOptionsJson`
 * （用户在配音配置页确认后写入）。
 * 成功后下一阶段（合成）置 NEEDS_INPUT，等用户选择字幕模式与编码预设。
 */
@Singleton
class DubStageRunner @Inject constructor(
    private val repository: TaskRepository,
    private val files: TaskFiles,
    private val gson: Gson,
    private val synthesizer: DubSynthesizer,
) : StageRunner {

    override val stage: TaskStage = TaskStage.DUB

    override suspend fun run(
        taskId: Long,
        onProgress: (Float, String) -> Unit,
    ) {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")

        val translated = files.loadTranslated(taskId)
        if (translated.isEmpty()) throw IllegalStateException("翻译产物缺失，请重试翻译阶段")

        val optionsJson = task.dubOptionsJson
            ?: throw IllegalStateException("配音参数缺失，请重新配置")
        val options = runCatching { gson.fromJson(optionsJson, DubOptions::class.java) }.getOrNull()
            ?: throw IllegalStateException("配音参数损坏，请重新配置")

        // 取纯译文转换为配音输入（与 M4 DubFlowHolder.toDubInputs 同规则）
        val inputs = translated.mapIndexed { index, seg ->
            DubSegmentInput(
                index = index,
                startMs = seg.original.startMs,
                endMs = seg.original.endMs,
                text = seg.translatedText,
            )
        }

        var lastReported = 0f
        val result = synthesizer.synthesizeDub(
            segments = inputs,
            voiceSid = options.voiceSid,
            cloudVoice = options.cloudVoice,
            speed = options.speed,
            engineId = options.engineId,
            strategy = options.strategy,
            mixOriginalRatio = options.mixOriginalRatio,
            videoPath = task.sourceVideoPath,
            onProgress = { done, total, _ ->
                val p = if (total > 0) done.toFloat() / total else 0f
                if (p - lastReported >= 0.01f || p >= 1f) {
                    lastReported = p
                    onProgress(p, "配音")
                }
            },
        )
        // 配音轨落到任务目录（DubSynthesizer 写在 dub_tmp；拷到任务目录做阶段产物）
        val dest = files.dubTrackWav(taskId)
        result.trackFile.copyTo(dest, overwrite = true)
        // 对齐元信息也落盘（抽查/排查用）
        files.dubMetaJson(taskId).writeText(
            gson.toJson(result.outcomes.map {
                mapOf(
                    "index" to it.index,
                    "slotMs" to it.slotMs,
                    "synthMs" to it.synthMs,
                    "action" to it.action.javaClass.simpleName,
                )
            }),
        )
        repository.setStageDone(taskId, TaskStage.DUB, dest.absolutePath)
        // 下一阶段待用户确认（合成配置页：字幕模式与编码预设）
        repository.setStageNeedsInput(taskId, TaskStage.SYNTHESIZE)
    }
}
