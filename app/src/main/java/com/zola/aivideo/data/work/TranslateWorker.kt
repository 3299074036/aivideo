package com.zola.aivideo.data.work

import android.content.Context
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.zola.aivideo.data.llm.LlmConfig
import com.zola.aivideo.data.llm.LlmCredentialStore
import com.zola.aivideo.data.llm.LlmPreferences
import com.zola.aivideo.data.llm.SubtitleTranslator
import com.zola.aivideo.data.llm.TranslateProgress
import com.zola.aivideo.data.llm.TranslateRequest
import com.zola.aivideo.data.task.PersistedTranslateOptions
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
 * 翻译 Worker（M5）：transcript.json → LLM 翻译 → translated.json。
 *
 * 用户配置（目标语言/风格/术语表/双语）来自任务实体的 `translateOptionsJson`
 *（用户在翻译配置页确认后写入）；LLM Key 在运行时从 EncryptedSharedPreferences
 * 现取，**不落盘**。
 * 成功后下一阶段（配音）置 NEEDS_INPUT，等用户配置配音参数。
 */
class TranslateWorker(
    appContext: Context,
    params: WorkerParameters,
    repository: TaskRepository,
    private val files: TaskFiles,
    private val gson: Gson,
    private val translator: SubtitleTranslator,
    private val llmPreferences: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
) : LongTaskWorker(appContext, params, TaskStage.TRANSLATE, repository) {

    override suspend fun doStageWork(): Result {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在（id=$taskId）")

        val segments = files.loadTranscript(taskId)
        if (segments.isEmpty()) throw IllegalStateException("转写产物缺失，请重试识别阶段")

        val optionsJson = task.translateOptionsJson
            ?: throw IllegalStateException("翻译参数缺失，请重新配置")
        val persisted = PersistedTranslateOptions.fromJson(gson, optionsJson)
            ?: throw IllegalStateException("翻译参数损坏，请重新配置")

        // Key 现取现用，不落盘
        val config = LlmConfig(
            baseUrl = llmPreferences.getBaseUrl(),
            apiKey = credentialStore.getApiKey(),
            model = llmPreferences.getModel(),
        )
        if (!config.isComplete()) throw IllegalStateException("LLM 配置不完整，请先在设置页填写")

        val request = TranslateRequest(
            segments = segments,
            targetLanguage = persisted.toTargetLanguage(),
            style = persisted.toStyle(),
            useGlossary = persisted.useGlossary,
            bilingual = persisted.bilingual,
            llmConfig = config,
        )

        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val lastReported = AtomicReference(0f)
        try {
            val outcome = translator.translate(request) { progress ->
                val p = when (progress) {
                    is TranslateProgress.Chunk ->
                        if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                    is TranslateProgress.Streaming -> lastReported.get()
                    is TranslateProgress.Stage -> lastReported.get()
                }.coerceIn(0f, 1f)
                if (p - lastReported.get() >= 0.01f || p >= 1f) {
                    lastReported.set(p)
                    progressScope.launch { reportProgress(p, "翻译") }
                }
            }
            val outFile = files.saveTranslated(taskId, outcome.segments)
            repository.setStageDone(taskId, TaskStage.TRANSLATE, outFile.absolutePath)
            // 下一阶段待用户确认（配音配置页）
            repository.setStageNeedsInput(taskId, TaskStage.DUB)
            return Result.success()
        } finally {
            progressScope.cancel()
        }
    }

    /** 手写 Factory（不用 @HiltWorker，见 AiVideoWorkerFactory 注释）。 */
    class Factory @Inject constructor(
        private val repository: TaskRepository,
        private val files: TaskFiles,
        private val gson: Gson,
        private val translator: SubtitleTranslator,
        private val llmPreferences: LlmPreferences,
        private val credentialStore: LlmCredentialStore,
    ) {
        fun create(
            appContext: Context,
            params: WorkerParameters,
        ): TranslateWorker = TranslateWorker(
            appContext, params, repository, files, gson,
            translator, llmPreferences, credentialStore,
        )
    }
}
