package com.zola.aivideo.ui.translate

import com.zola.aivideo.data.llm.TargetLanguage
import com.zola.aivideo.data.llm.TranslateRequest
import com.zola.aivideo.data.llm.TranslateStyle
import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.domain.model.TranscriptSegment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 翻译流程内存传递器（M3）：字幕编辑页 → 翻译配置页 → 翻译进度页 → 结果页。
 *
 * 与 M2 的 TranscriptionResultHolder 同模式：@Singleton 注入，界面间传大对象
 * 避免导航参数体积限制。M5 任务中心落地后持久化到 Room。
 */
@Singleton
class TranslationFlowHolder @Inject constructor() {
    @Volatile
    var sourceSegments: List<TranscriptSegment> = emptyList()
        private set

    @Volatile
    var lastRequest: TranslateRequest? = null
        private set

    @Volatile
    var lastOutcome: List<TranslatedSegment> = emptyList()
        private set

    /** 字幕来源描述（如"转写结果"或文件名），结果页展示用。 */
    @Volatile
    var sourceLabel: String = ""
        private set

    fun putSource(segments: List<TranscriptSegment>, label: String) {
        sourceSegments = segments
        sourceLabel = label
        lastRequest = null
        lastOutcome = emptyList()
    }

    fun putRequest(request: TranslateRequest) {
        lastRequest = request
    }

    fun putOutcome(outcome: List<TranslatedSegment>) {
        lastOutcome = outcome
    }
}

/** 翻译配置（界面层）：目标语言/风格/开关。 */
data class TranslateOptions(
    val targetLanguage: TargetLanguage = TargetLanguage.English,
    val style: TranslateStyle = TranslateStyle.COLLOQUIAL,
    val useGlossary: Boolean = true,
    val bilingual: Boolean = false,
)
