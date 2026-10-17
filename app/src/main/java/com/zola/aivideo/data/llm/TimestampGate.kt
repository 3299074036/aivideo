package com.zola.aivideo.data.llm

import com.zola.aivideo.domain.model.TranscriptSegment
import kotlin.math.abs

/**
 * LLM 翻译输出行解析（与 prompt 模板的分段行协议对应）：
 *   [序号] (开始时间-->结束时间) 译文
 * 时间格式 mm:ss.mmm 或 h:mm:ss.mmm。
 */
data class ParsedTranslation(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object TranslationLineParser {
    private val LINE_REGEX =
        Regex("""^\[(\d+)]\s*\(([\d:.]+)-->?([\d:.]+)\)\s?(.*)$""")

    /** 解析 LLM 输出全文为分段翻译；无法解析的行会被跳过（由门禁报缺失）。 */
    fun parse(output: String): List<ParsedTranslation> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val m = LINE_REGEX.matchEntire(line) ?: return@mapNotNull null
                val index = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val start = parseTimestamp(m.groupValues[2]) ?: return@mapNotNull null
                val end = parseTimestamp(m.groupValues[3]) ?: return@mapNotNull null
                ParsedTranslation(index, start, end, m.groupValues[4].trim())
            }
            .toList()

    /** "mm:ss.mmm" / "h:mm:ss.mmm" → 毫秒；非法返回 null。 */
    fun parseTimestamp(text: String): Long? {
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    minutes * 60_000 + seconds * 1000 + millis
                }
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    hours * 3_600_000 + minutes * 60_000 + seconds * 1000 + millis
                }
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}

/** 门禁失败项（用于修正重试的问题描述与测试断言）。 */
sealed interface GateFailure {
    /** 分段数不一致。 */
    data class CountMismatch(val expected: Int, val actual: Int) : GateFailure
    /** 序号缺失。 */
    data class MissingIndex(val index: Int) : GateFailure
    /** 时间戳漂移超限。 */
    data class TimestampDrift(
        val index: Int,
        val expectedStartMs: Long,
        val actualStartMs: Long,
    ) : GateFailure
    /** 译文为空。 */
    data class EmptyText(val index: Int) : GateFailure

    fun describe(): String = when (this) {
        is CountMismatch -> "分段数不一致：期望 $expected 段，实际 $actual 段"
        is MissingIndex -> "缺少第 $index 段"
        is TimestampDrift -> "第 $index 段时间戳漂移超限（期望 $expectedStartMs ms，实际 $actualStartMs ms）"
        is EmptyText -> "第 $index 段译文为空"
    }
}

data class GateResult(
    val ok: Boolean,
    val failures: List<GateFailure>,
)

/**
 * 时间戳质量门禁（M3，独立实现）：纯 Kotlin，可 JVM 单元测试。
 *
 * 校验：
 * 1. 输出分段数 == 输入分段数
 * 2. 每个序号都存在
 * 3. 回显时间戳与原文的漂移在 ±[DRIFT_TOLERANCE_MS]（5 秒）内
 * 4. 译文非空
 */
object TimestampGate {
    const val DRIFT_TOLERANCE_MS = 5000L

    fun validate(
        original: List<TranscriptSegment>,
        parsed: List<ParsedTranslation>,
        globalOffset: Int,
    ): GateResult {
        val failures = mutableListOf<GateFailure>()
        val expectedCount = original.size
        if (parsed.size != expectedCount) {
            failures.add(GateFailure.CountMismatch(expectedCount, parsed.size))
        }
        val byIndex = parsed.associateBy { it.index }
        original.forEachIndexed { i, seg ->
            val index = globalOffset + i + 1
            val p = byIndex[index]
            if (p == null) {
                failures.add(GateFailure.MissingIndex(index))
            } else {
                if (abs(p.startMs - seg.startMs) > DRIFT_TOLERANCE_MS ||
                    abs(p.endMs - seg.endMs) > DRIFT_TOLERANCE_MS
                ) {
                    failures.add(
                        GateFailure.TimestampDrift(index, seg.startMs, p.startMs),
                    )
                }
                if (p.text.isBlank()) {
                    failures.add(GateFailure.EmptyText(index))
                }
            }
        }
        return GateResult(failures.isEmpty(), failures)
    }
}
