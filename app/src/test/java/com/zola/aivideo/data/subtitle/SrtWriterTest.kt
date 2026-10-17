package com.zola.aivideo.data.subtitle

import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SrtWriter] 单元测试：纯 JVM。 */
class SrtWriterTest {

    private fun translated(id: Long, startMs: Long, endMs: Long, src: String, dst: String) =
        TranslatedSegment(
            original = TranscriptSegment(id, startMs, endMs, src),
            displayText = dst,
            translatedText = dst,
            verified = true,
        )

    @Test
    fun `srt 格式正确`() {
        val segs = listOf(
            translated(0, 1000, 3500, "你好", "Hello"),
            translated(1, 3_723_000, 3_725_500, "世界", "World"),
        )
        val srt = SrtWriter.toSrt(segs, bilingual = false)
        assertTrue(srt.contains("1\n00:00:01,000 --> 00:00:03,500\nHello"))
        assertTrue(srt.contains("2\n01:02:03,000 --> 01:02:05,500\nWorld"))
    }

    @Test
    fun `双语模式原文译文对照`() {
        val segs = listOf(translated(0, 0, 2000, "你好", "Hello"))
        val srt = SrtWriter.toSrt(segs, bilingual = true)
        assertTrue(srt.contains("你好\nHello"))
    }

    @Test
    fun `时间戳格式化`() {
        assertEquals("00:00:00,000", SrtWriter.formatSrtTimestamp(0))
        assertEquals("00:01:02,500", SrtWriter.formatSrtTimestamp(62_500))
        assertEquals("01:02:03,456", SrtWriter.formatSrtTimestamp(3_723_456))
        assertEquals("00:00:00,000", SrtWriter.formatSrtTimestamp(-100))
    }

    @Test
    fun `文件名非法字符过滤`() {
        assertEquals("a_b_c", SrtWriter.sanitizeFileName("a/b:c"))
        assertEquals("subtitle", SrtWriter.sanitizeFileName("   "))
        assertEquals("name", SrtWriter.sanitizeFileName("name..."))
        assertEquals("正常文件名", SrtWriter.sanitizeFileName("正常文件名"))
    }
}
