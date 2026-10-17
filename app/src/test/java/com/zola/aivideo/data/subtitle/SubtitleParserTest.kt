package com.zola.aivideo.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SubtitleParser] 单元测试：纯 JVM。 */
class SubtitleParserTest {

    @Test
    fun `srt 基本解析`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            你好

            2
            00:00:04,000 --> 00:00:06,000
            世界

        """.trimIndent()
        val segs = SubtitleParser.parseSrt(srt)
        assertEquals(2, segs.size)
        assertEquals(1000L, segs[0].startMs)
        assertEquals(3500L, segs[0].endMs)
        assertEquals("你好", segs[0].text)
        assertEquals(0L, segs[0].id)
        assertEquals(1L, segs[1].id)
    }

    @Test
    fun `srt 去掉行内标签并容忍缺失序号`() {
        val srt = "00:00:01,000 --> 00:00:02,000\n<i>斜体</i>文本\n"
        val segs = SubtitleParser.parseSrt(srt)
        assertEquals(1, segs.size)
        assertEquals("斜体文本", segs[0].text)
    }

    @Test
    fun `srt 非法时间块被跳过`() {
        val srt = """
            1
            00:00:05,000 --> 00:00:03,000
            时间倒置

            2
            00:00:01,000 --> 00:00:02,000
            正常

        """.trimIndent()
        val segs = SubtitleParser.parseSrt(srt)
        assertEquals(1, segs.size)
        assertEquals("正常", segs[0].text)
    }

    @Test
    fun `vtt 基本解析含 header`() {
        val vtt = """
            WEBVTT

            00:00.000 --> 00:02.500
            Hello

            00:03.000 --> 01:02:03.456
            带小时的时间

        """.trimIndent()
        val segs = SubtitleParser.parseVtt(vtt)
        assertEquals(2, segs.size)
        assertEquals(0L, segs[0].startMs)
        assertEquals(2500L, segs[0].endMs)
        assertEquals(3_723_456L, segs[1].endMs)
    }

    @Test
    fun `vtt 跳过 NOTE 注释`() {
        val vtt = "WEBVTT\n\nNOTE 这是注释\n多行注释\n\n00:01.000 --> 00:02.000\n正文\n"
        val segs = SubtitleParser.parseVtt(vtt)
        assertEquals(1, segs.size)
        assertEquals("正文", segs[0].text)
    }

    @Test
    fun `ass 解析 dialogue 行`() {
        val ass = """
            [Script Info]
            Title: test

            [V4+ Styles]
            Format: Name, Fontname

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,第一行{\i1}斜体{\i0}
            Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,第二行\N换行

        """.trimIndent()
        val segs = SubtitleParser.parseAss(ass)
        assertEquals(2, segs.size)
        assertEquals(1000L, segs[0].startMs)
        assertEquals(3500L, segs[0].endMs)
        assertEquals("第一行斜体", segs[0].text)
        assertEquals("第二行\n换行", segs[1].text)
    }

    @Test
    fun `ass 无 Events 段返回空`() {
        assertTrue(SubtitleParser.parseAss("[Script Info]\nTitle: x\n").isEmpty())
    }

    @Test
    fun `按后缀自动选择解析器`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHi\n"
        assertEquals(1, SubtitleParser.parse("a.SRT", srt).size)
        assertEquals(1, SubtitleParser.parse("b.vtt", "WEBVTT\n\n00:01.000 --> 00:02.000\nHi\n").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `未知后缀抛异常`() {
        SubtitleParser.parse("a.txt", "hello")
    }
}
