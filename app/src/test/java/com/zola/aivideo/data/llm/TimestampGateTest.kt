package com.zola.aivideo.data.llm

import com.zola.aivideo.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TimestampGate] 与 [TranslationLineParser] 单元测试：纯 JVM。 */
class TimestampGateTest {

    private fun seg(id: Long, startMs: Long, endMs: Long, text: String = "原文$id") =
        TranscriptSegment(id, startMs, endMs, text)

    @Test
    fun `行解析正常`() {
        val out = "[1] (00:00.000-->00:02.500) Hello\n[2] (01:02:03.456-->01:02:05.000) World\n"
        val parsed = TranslationLineParser.parse(out)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].index)
        assertEquals(0L, parsed[0].startMs)
        assertEquals(2500L, parsed[0].endMs)
        assertEquals("Hello", parsed[0].text)
        assertEquals(3_723_456L, parsed[1].startMs)
    }

    @Test
    fun `行解析跳过非法行`() {
        val out = "这是解释文字\n[1] (00:00.000-->00:02.500) OK\n"
        val parsed = TranslationLineParser.parse(out)
        assertEquals(1, parsed.size)
    }

    @Test
    fun `时间戳解析`() {
        assertEquals(62_500L, TranslationLineParser.parseTimestamp("01:02.500"))
        assertEquals(3_723_456L, TranslationLineParser.parseTimestamp("1:02:03.456"))
        assertEquals(null, TranslationLineParser.parseTimestamp("not-a-time"))
    }

    @Test
    fun `门禁全通过`() {
        val original = listOf(seg(0, 0, 2500), seg(1, 3000, 5000))
        val parsed = listOf(
            ParsedTranslation(1, 0, 2500, "Hi"),
            ParsedTranslation(2, 3000, 5000, "There"),
        )
        val r = TimestampGate.validate(original, parsed, globalOffset = 0)
        assertTrue(r.ok)
        assertTrue(r.failures.isEmpty())
    }

    @Test
    fun `分段数不一致`() {
        val original = listOf(seg(0, 0, 2500), seg(1, 3000, 5000))
        val parsed = listOf(ParsedTranslation(1, 0, 2500, "Hi"))
        val r = TimestampGate.validate(original, parsed, globalOffset = 0)
        assertFalse(r.ok)
        assertTrue(r.failures.any { it is GateFailure.CountMismatch })
        assertTrue(r.failures.any { it is GateFailure.MissingIndex })
    }

    @Test
    fun `时间戳漂移超 5 秒`() {
        val original = listOf(seg(0, 0, 2500))
        val parsed = listOf(ParsedTranslation(1, 10_000, 12_500, "Hi"))
        val r = TimestampGate.validate(original, parsed, globalOffset = 0)
        assertFalse(r.ok)
        val drift = r.failures.filterIsInstance<GateFailure.TimestampDrift>()
        assertEquals(1, drift.size)
        assertEquals(1, drift[0].index)
    }

    @Test
    fun `漂移在容差内通过`() {
        val original = listOf(seg(0, 0, 2500))
        val parsed = listOf(ParsedTranslation(1, 4000, 6500, "Hi"))
        val r = TimestampGate.validate(original, parsed, globalOffset = 0)
        assertTrue(r.ok)
    }

    @Test
    fun `空译文失败`() {
        val original = listOf(seg(0, 0, 2500))
        val parsed = listOf(ParsedTranslation(1, 0, 2500, "   "))
        val r = TimestampGate.validate(original, parsed, globalOffset = 0)
        assertFalse(r.ok)
        assertTrue(r.failures.any { it is GateFailure.EmptyText })
    }

    @Test
    fun `全局偏移序号正确`() {
        val original = listOf(seg(5, 5000, 6000))
        val parsed = listOf(ParsedTranslation(6, 5000, 6000, "Hi"))
        val r = TimestampGate.validate(original, parsed, globalOffset = 5)
        assertTrue(r.ok)
    }
}
