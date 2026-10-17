package com.zola.aivideo.data.llm

import com.zola.aivideo.domain.model.TranscriptSegment
import kotlin.math.ceil

/**
 * 长文稿 map-reduce 切分器（M3，独立实现）：纯 Kotlin，可 JVM 单元测试。
 *
 * 策略（技术方案 §2.3）：
 * - 估算全文 token ≤ [TOTAL_TOKEN_LIMIT]（12000）时不切分，一次翻译
 * - 超限则按 [CHUNK_TOKEN_LIMIT]（8000）切分，逐 chunk 翻译再合并
 * - 每个 chunk 携带全局连续的分段序号，合并时天然对齐
 *
 * Token 估算为保守启发式：CJK 字符按 0.8 token、其他字符按 0.35 token 计，
 * 宁可多估（多切一刀）也不超限。每段额外 +16 token 覆盖编号与时间戳开销。
 */
object TranslationChunker {
    const val TOTAL_TOKEN_LIMIT = 12000
    const val CHUNK_TOKEN_LIMIT = 8000

    /** 保守估算一段文本的 token 数。 */
    fun estimateTokens(text: String): Int {
        var sum = 0.0
        for (c in text) {
            sum += if (isCjk(c)) 0.8 else 0.35
        }
        return ceil(sum).toInt()
    }

    /** 估算一批分段的总 token（含编号/时间戳开销）。 */
    fun estimateSegmentsTokens(segments: List<TranscriptSegment>): Int =
        segments.sumOf { estimateTokens(it.text) + PER_SEGMENT_OVERHEAD }

    /** 是否需要切分。 */
    fun needsChunking(segments: List<TranscriptSegment>): Boolean =
        estimateSegmentsTokens(segments) > TOTAL_TOKEN_LIMIT

    /**
     * 按 token 上限切分。保证：
     * - 每个 chunk 非空、顺序不变、合起来恰好是原列表
     * - 单个超大分段独占一个 chunk（不拆分段，只记录日志含义）
     */
    fun chunk(segments: List<TranscriptSegment>): List<List<TranscriptSegment>> {
        if (segments.isEmpty()) return emptyList()
        if (!needsChunking(segments)) return listOf(segments)
        val chunks = mutableListOf<MutableList<TranscriptSegment>>()
        var current = mutableListOf<TranscriptSegment>()
        var currentTokens = 0
        for (seg in segments) {
            val segTokens = estimateTokens(seg.text) + PER_SEGMENT_OVERHEAD
            if (current.isNotEmpty() && currentTokens + segTokens > CHUNK_TOKEN_LIMIT) {
                chunks.add(current)
                current = mutableListOf()
                currentTokens = 0
            }
            current.add(seg)
            currentTokens += segTokens
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    private const val PER_SEGMENT_OVERHEAD = 16

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c) ?: return false
        return block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block === Character.UnicodeBlock.HIRAGANA ||
            block === Character.UnicodeBlock.KATAKANA ||
            block === Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block === Character.UnicodeBlock.HANGUL_JAMO ||
            block === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block === Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    }
}
