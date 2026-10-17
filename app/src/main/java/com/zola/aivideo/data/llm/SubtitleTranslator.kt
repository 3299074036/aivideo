package com.zola.aivideo.data.llm

import com.zola.aivideo.domain.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** 翻译后的一段：原文分段 + 译文 + 门禁结论。 */
data class TranslatedSegment(
    val original: TranscriptSegment,
    /** 最终译文（双语模式下为"原文\n译文"）。 */
    val displayText: String,
    /** 纯译文（不含原文，用于导出与配音）。 */
    val translatedText: String,
    /** 门禁是否通过；false 时 UI 标注「时间点待核实」。 */
    val verified: Boolean,
)

data class TranslateRequest(
    val segments: List<TranscriptSegment>,
    val targetLanguage: TargetLanguage,
    val style: TranslateStyle,
    /** 是否启用术语表（注入 prompt + 后处理替换）。 */
    val useGlossary: Boolean,
    /** 双语字幕：输出"原文\n译文"对照。 */
    val bilingual: Boolean,
    val llmConfig: LlmConfig,
)

/** 翻译进度回调（UI 展示用）。 */
sealed interface TranslateProgress {
    /** 阶段文案。 */
    data class Stage(val text: String) : TranslateProgress
    /** chunk 进度。 */
    data class Chunk(val done: Int, val total: Int) : TranslateProgress
    /** 当前 chunk 已收到的字符数（流式）。 */
    data class Streaming(val charsReceived: Int) : TranslateProgress
}

data class TranslationOutcome(
    val segments: List<TranslatedSegment>,
    /** 是否触发过修正重试。 */
    val repairedOnce: Boolean,
    /** 门禁最终未通过的分段数。 */
    val unverifiedCount: Int,
)

/**
 * 字幕翻译编排用例（M3，独立实现）。
 *
 * 流程：map-reduce 切分 → 逐 chunk 流式翻译 → 行解析 → 时间戳门禁 →
 * 失败则一次 LLM 修正重试 → 仍失败则标记「时间点待核实」（保留最佳译文，
 * 不丢段，保证 SRT 连续性；UI 可手动修正）→ 术语表后处理 → 双语组装。
 *
 * 协程取消即中断底层 SSE 请求。
 */
@Singleton
class SubtitleTranslator @Inject constructor(
    private val llmClient: LlmClient,
    private val promptLoader: PromptLoader,
    private val glossaryRepository: GlossaryRepository,
) {
    suspend fun translate(
        request: TranslateRequest,
        onProgress: (TranslateProgress) -> Unit = {},
    ): TranslationOutcome {
        require(request.segments.isNotEmpty()) { "没有可翻译的分段" }
        require(request.llmConfig.isComplete()) { "LLM 配置不完整" }

        val glossaryTerms = if (request.useGlossary) {
            glossaryRepository.getByTargetLang(request.targetLanguage.displayName)
        } else {
            emptyList()
        }
        val glossarySection = promptLoader.buildGlossarySection(glossaryTerms)

        onProgress(TranslateProgress.Stage("准备翻译…"))
        val chunks = TranslationChunker.chunk(request.segments)
        onProgress(TranslateProgress.Stage("共 ${chunks.size} 组，开始流式翻译…"))

        val translatedByIndex = mutableMapOf<Int, ParsedTranslation>()
        // 本次翻译中门禁未通过的分段序号（1-based 全局）：方法内局部状态，禁止放成员变量
        val unverifiedIndices = mutableSetOf<Int>()
        var repairedOnce = false
        var offset = 0
        chunks.forEachIndexed { chunkIdx, chunk ->
            coroutineContext.ensureActive()
            onProgress(TranslateProgress.Chunk(chunkIdx, chunks.size))
            val chunkInfo = if (chunks.size > 1) "第 ${chunkIdx + 1}/${chunks.size} 组" else "全文"
            val messages = promptLoader.buildMessages(
                chunk = chunk,
                globalOffset = offset,
                targetLanguage = request.targetLanguage,
                style = request.style,
                glossarySection = glossarySection,
                chunkInfo = chunkInfo,
            )
            val raw = StringBuilder()
            try {
                llmClient.streamChat(request.llmConfig, messages) { delta ->
                    raw.append(delta)
                    onProgress(TranslateProgress.Streaming(raw.length))
                }
            } catch (e: CancellationException) {
                throw e
            }

            var parsed = TranslationLineParser.parse(raw.toString())
            var gate = TimestampGate.validate(chunk, parsed, offset)
            if (!gate.ok && !repairedOnce) {
                // 一次修正重试
                onProgress(TranslateProgress.Stage("第 ${chunkIdx + 1} 组校验未通过，尝试修正…"))
                repairedOnce = true
                val issues = gate.failures.joinToString("\n") { it.describe() }
                val repairMessages =
                    promptLoader.buildRepairMessages(issues, chunk, offset)
                val repairRaw = StringBuilder()
                llmClient.streamChat(request.llmConfig, repairMessages) { delta ->
                    repairRaw.append(delta)
                    onProgress(TranslateProgress.Streaming(repairRaw.length))
                }
                parsed = TranslationLineParser.parse(repairRaw.toString())
                gate = TimestampGate.validate(chunk, parsed, offset)
            }
            // 门禁最终结论：通过的入库；未通过的保留最佳译文并标记
            val byIndex = parsed.associateBy { it.index }
            chunk.forEachIndexed { i, seg ->
                val index = offset + i + 1
                val p = byIndex[index]
                val failed = gate.failures.any {
                    (it is GateFailure.MissingIndex && it.index == index) ||
                        (it is GateFailure.TimestampDrift && it.index == index) ||
                        (it is GateFailure.EmptyText && it.index == index)
                } || gate.failures.any { it is GateFailure.CountMismatch }
                if (p != null && !failed) {
                    translatedByIndex[index] = p
                } else if (p != null) {
                    // 有译文但门禁未过：保留并标记
                    translatedByIndex[index] = p.copy(text = p.text.ifBlank { seg.text })
                    unverifiedIndices.add(index)
                } else {
                    // 完全缺失：用原文占位并标记，SRT 不丢段
                    translatedByIndex[index] = ParsedTranslation(
                        index = index,
                        startMs = seg.startMs,
                        endMs = seg.endMs,
                        text = seg.text,
                    )
                    unverifiedIndices.add(index)
                }
            }
            offset += chunk.size
        }

        onProgress(TranslateProgress.Stage("后处理…"))
        val result = request.segments.mapIndexed { i, seg ->
            val index = i + 1
            val rawText = translatedByIndex[index]?.text.orEmpty().ifBlank { seg.text }
            val postProcessed = applyGlossaryPostProcess(rawText, glossaryTerms)
            val display = if (request.bilingual) "${seg.text}\n$postProcessed" else postProcessed
            TranslatedSegment(
                original = seg,
                displayText = display,
                translatedText = postProcessed,
                verified = index !in unverifiedIndices,
            )
        }
        return TranslationOutcome(
            segments = result,
            repairedOnce = repairedOnce,
            unverifiedCount = unverifiedIndices.size,
        )
    }

    /**
     * 术语表后处理二次替换，委托 [GlossaryPostProcessor]（纯函数，可单元测试）。
     */
    fun applyGlossaryPostProcess(
        translatedText: String,
        terms: List<GlossaryTerm>,
    ): String = GlossaryPostProcessor.apply(translatedText, terms)
}
