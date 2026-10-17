package com.zola.aivideo.ui.translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.zola.aivideo.data.llm.GlossaryRepository
import com.zola.aivideo.data.llm.LlmConfig
import com.zola.aivideo.data.llm.LlmCredentialStore
import com.zola.aivideo.data.llm.LlmPreferences
import com.zola.aivideo.data.llm.TargetLanguage
import com.zola.aivideo.data.llm.TranslateStyle
import com.zola.aivideo.data.task.PersistedTranslateOptions
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.work.TaskScheduler
import com.zola.aivideo.domain.model.TranscriptSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 翻译配置 ViewModel（M3；M5 增加任务模式）。
 *
 * - 目标语言/风格/术语表开关/双语开关
 * - 进入翻译前检查 LLM 配置完整性：缺 Key 或地址时提示去 LLM 设置页
 *
 * 任务模式（taskId > 0，M5）：分段从任务目录的 transcript.json 读取；
 * 确认后把配置持久化到任务并调度 TranslateWorker，不走前台翻译流程。
 */
@HiltViewModel
class TranslateConfigViewModel @Inject constructor(
    private val holder: TranslationFlowHolder,
    private val prefs: LlmPreferences,
    private val credentialStore: LlmCredentialStore,
    private val glossaryRepository: GlossaryRepository,
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

    var options by mutableStateOf(TranslateOptions())
        private set

    var customLangName by mutableStateOf("")
        private set

    var segmentCount by mutableStateOf(0)
        private set

    var sourceLabel by mutableStateOf("")
        private set

    /** null 表示检查通过；非 null 为阻断文案。 */
    var blockReason by mutableStateOf<String?>(null)
        private set

    var llmReady by mutableStateOf(false)
        private set

    /** 任务模式下的分段（普通模式走 holder）。 */
    private var taskSegments: List<TranscriptSegment> = emptyList()

    private fun currentSegments(): List<TranscriptSegment> =
        if (taskMode) taskSegments else holder.sourceSegments

    init {
        if (taskMode) {
            taskSegments = taskFiles.loadTranscript(taskId)
            segmentCount = taskSegments.size
            sourceLabel = "任务字幕"
            if (taskSegments.isEmpty()) blockReason = "任务转写产物缺失，请重试识别阶段"
        } else {
            segmentCount = holder.sourceSegments.size
            sourceLabel = holder.sourceLabel
        }
    }

    fun onTargetLanguageChange(lang: TargetLanguage) {
        options = options.copy(targetLanguage = lang)
    }

    fun onCustomLangNameChange(v: String) {
        customLangName = v
    }

    fun onStyleChange(style: TranslateStyle) {
        options = options.copy(style = style)
    }

    fun onUseGlossaryChange(v: Boolean) {
        options = options.copy(useGlossary = v)
    }

    fun onBilingualChange(v: Boolean) {
        options = options.copy(bilingual = v)
    }

    /** 开始翻译前检查；通过返回可用的 LlmConfig。 */
    suspend fun checkAndBuildConfig(): LlmConfig? {
        val segments = currentSegments()
        if (segments.isEmpty()) {
            blockReason = "没有可翻译的字幕分段"
            return null
        }
        val baseUrl = prefs.getBaseUrl()
        val model = prefs.getModel()
        val key = credentialStore.getApiKey()
        val config = LlmConfig(baseUrl, key, model)
        if (!config.isComplete()) {
            blockReason = "请先在「LLM 设置」中填写完整地址、Key 和模型名"
            return null
        }
        llmReady = true
        blockReason = null
        val target = (options.targetLanguage as? TargetLanguage.Custom)
            ?.let { TargetLanguage.Custom(customLangName.trim()) }
            ?: options.targetLanguage
        // 同步回用的最终语言（自定义名）
        options = options.copy(targetLanguage = target)

        if (taskMode) {
            // 任务模式：配置落盘 → 调度 Worker，不走前台翻译流程
            val persisted = PersistedTranslateOptions.from(
                targetLanguage = target,
                customLangName = customLangName.trim(),
                style = options.style,
                useGlossary = options.useGlossary,
                bilingual = options.bilingual,
            )
            taskRepository.saveStageOptions(taskId, TaskStage.TRANSLATE, persisted.toJson(gson))
            taskRepository.updateStageState(taskId, TaskStage.TRANSLATE, StageState.PENDING)
            taskScheduler.scheduleStage(taskId, TaskStage.TRANSLATE)
            taskModeDone = true
            return null
        }

        holder.putRequest(
            com.zola.aivideo.data.llm.TranslateRequest(
                segments = segments,
                targetLanguage = target,
                style = options.style,
                useGlossary = options.useGlossary,
                bilingual = options.bilingual,
                llmConfig = config,
            ),
        )
        return config
    }

    /** 术语表条目数（用于开关旁展示）。 */
    suspend fun glossaryCountFor(lang: TargetLanguage): Int =
        glossaryRepository.getByTargetLang(lang.displayName).size
}
