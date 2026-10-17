package com.zola.aivideo.data.subtitle

import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.domain.model.TranscriptSegment
import java.util.Locale

/**
 * SRT 导出（M3，独立实现）：纯 Kotlin，可 JVM 单元测试。
 *
 * 标准格式：
 *   1
 *   00:00:00,000 --> 00:00:02,500
 *   译文
 *
 * 双语模式下每段写两行（原文 + 译文）。
 * 文件名非法字符过滤在调用方（SAF CreateDocument 前）处理，见 [sanitizeFileName]。
 */
object SrtWriter {

    /** 已翻译分段 → .srt 文本。 */
    fun toSrt(segments: List<TranslatedSegment>, bilingual: Boolean): String =
        toSrtRaw(segments.map { it.original }, segments.map {
            if (bilingual) "${it.original.text}\n${it.translatedText}" else it.translatedText
        })

    /** 原始分段 → .srt 文本（独立模式导出原文用）。 */
    fun toSrtOriginal(segments: List<TranscriptSegment>): String =
        toSrtRaw(segments, segments.map { it.text })

    private fun toSrtRaw(segments: List<TranscriptSegment>, texts: List<String>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { i, seg ->
            sb.append(i + 1).append('\n')
            sb.append(formatSrtTimestamp(seg.startMs))
                .append(" --> ")
                .append(formatSrtTimestamp(seg.endMs))
                .append('\n')
            sb.append(texts[i].ifBlank { seg.text }).append("\n\n")
        }
        return sb.toString()
    }

    /** 毫秒 → "HH:MM:SS,mmm"。 */
    fun formatSrtTimestamp(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        val h = v / 3_600_000
        val m = (v % 3_600_000) / 60_000
        val s = (v % 60_000) / 1000
        val milli = v % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }

    /**
     * 文件名非法字符过滤（Windows/Android 通用非法字符）。
     * 空结果时返回默认名。
     */
    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trim('.')
        return cleaned.ifBlank { "subtitle" }
    }
}
