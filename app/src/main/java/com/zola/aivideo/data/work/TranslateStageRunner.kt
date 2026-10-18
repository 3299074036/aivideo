package com.zola.aivideo.data.work

import com.google.gson.Gson
import com.zola.aivideo.data.llm.LlmConfig
import com.zola.aivideo.data.llm.LlmCredentialStore
import com.zola.aivideo.data.llm.LlmEngineId
import com.zola.aivideo.data.llm.LlmPreferences
import com.zola.aivideo.data.llm.SubtitleTranslator
import com.zola.aivideo.data.llm.TranslateProgress
import com.zola.aivideo.data.llm.TranslateRequest
import com.zola.aivideo.data.task.PersistedTranslateOptions
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 翻译阶段执行器：transcript.json → LLM 翻译 → translated.json。
 *
 * 从 TranslateWorker 提取（2026-10-17 去 WorkManager 化）。
 *
 * 修复（2026-10-17）：原 Worker 硬编码要求 API 配置完整，本地引擎用户走任务
 * 模式必失败。此处按引擎分流——本地引擎跳过 API 校验（SubtitleTranslator 内部
 * 按引擎取数，llmConfig 传空占位）。
 *
 * 用户配置（目标语言/风格/术语表/双语）来自任务实体的 `translateOptionsJson`
 * （用户在翻译配置页确认后写入）；LLM Key 在运行时从 EncryptedSharedPreferences
 * 现取，**不落盘**。
 * 成功后下一阶段（配音）置 NEEDS_INPUT，等用户配置配音参数。
 */
@Singleton
class TranslateStageRunner @Inject constructor(
    private val repository: TaskRepository,
    private val files: TaskFiles,
    private val gson: Gson,
    private val translator: SubtitleTranslator,
    private val llmPreferences: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
) : StageRunner {

    override val stage: TaskStage = TaskStage.TRANSLATE

    override suspend fun run(
        taskId: Long,
        onProgress: (Float, String) -> Unit,
    ) {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")

        val segments = files.loadTranscript(taskId)
        if (segments.isEmpty()) throw IllegalStateException("转写产物缺失，请重试识别阶段")

        val optionsJson = task.translateOptionsJson
            ?: throw IllegalStateException("翻译参数缺失，请重新配置")
        val persisted = PersistedTranslateOptions.fromJson(gson, optionsJson)
            ?: throw IllegalStateException("翻译参数损坏，请重新配置")

        // 按引擎分流：本地引擎不需要 API 配置
        val config = if (llmPreferences.getLlmEngine() == LlmEngineId.LOCAL) {
            LlmConfig("", "", "")
        } else {
            // Key 现取现用，不落盘
            val apiConfig = LlmConfig(
                baseUrl = llmPreferences.getBaseUrl(),
                apiKey = credentialStore.getApiKey(),
                model = llmPreferences.getModel(),
            )
            if (!apiConfig.isComplete()) throw IllegalStateException("LLM 配置不完整，请先在设置页填写")
            apiConfig
        }

        val request = TranslateRequest(
            segments = segments,
            targetLanguage = persisted.toTargetLanguage(),
            style = persisted.toStyle(),
            useGlossary = persisted.useGlossary,
            bilingual = persisted.bilingual,
            llmConfig = config,
        )

        var lastReported = 0f
        val outcome = translator.translate(request) { progress ->
            val p = when (progress) {
                is TranslateProgress.Chunk ->
                    if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                is TranslateProgress.Streaming -> lastReported
                is TranslateProgress.Stage -> lastReported
            }.coerceIn(0f, 1f)
            if (p - lastReported >= 0.01f || p >= 1f) {
                lastReported = p
                onProgress(p, "翻译")
            }
        }
        val outFile = files.saveTranslated(taskId, outcome.segments)
        repository.setStageDone(taskId, TaskStage.TRANSLATE, outFile.absolutePath)
        // 下一阶段待用户确认（配音配置页）
        repository.setStageNeedsInput(taskId, TaskStage.DUB)
    }
}
