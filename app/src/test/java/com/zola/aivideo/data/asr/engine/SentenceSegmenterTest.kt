package com.zola.aivideo.data.asr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分句器单元测试：纯 Kotlin 逻辑。 */
class SentenceSegmenterTest {

    private fun w(text: String, startMs: Long, durMs: Long = 400) =
        WordTiming(text, startMs, startMs + durMs)

    @Test
    fun `中文按标点分句`() {
        val words = listOf(
            w("你好", 0), w("世界", 400), w("。", 800),
            w("今天", 1200), w("天气", 1600), w("不错", 2000), w("。", 2400),
        )
        val segs = SentenceSegmenter.segment(words)
        assertEquals(2, segs.size)
        assertEquals("你好世界。", segs[0].text)
        assertEquals("今天天气不错。", segs[1].text)
        assertEquals(0L, segs[0].startMs)
        assertEquals(1200L, segs[1].startMs)
    }

    @Test
    fun `英文单词间补空格`() {
        val words = listOf(w("hello", 0), w("world", 400), w(".", 800))
        val segs = SentenceSegmenter.segment(words)
        assertEquals(1, segs.size)
        assertEquals("hello world.", segs[0].text)
    }

    @Test
    fun `中英混排不错误加空格`() {
        val words = listOf(w("我", 0), w("喜欢", 300), w("Kotlin", 600), w("。", 1000))
        val segs = SentenceSegmenter.segment(words)
        assertEquals("我喜欢Kotlin。", segs[0].text)
    }

    @Test
    fun `超30秒强制断句`() {
        // 每个词 10 秒，无标点：应在 30 秒处断开
        val words = (0 until 5).map { i -> w("词$i", i * 10_000L, 10_000L) }
        val segs = SentenceSegmenter.segment(words)
        assertEquals(2, segs.size)
        assertTrue(segs[0].durationMs <= SentenceSegmenter.MAX_SEGMENT_MS)
        assertEquals("词0词1词2", segs[0].text)
        assertEquals("词3词4", segs[1].text)
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(SentenceSegmenter.segment(emptyList()).isEmpty())
    }

    @Test
    fun `timeOffset与idOffset生效`() {
        val words = listOf(w("嗨", 100), w("。", 500))
        val segs = SentenceSegmenter.segment(words, timeOffsetMs = 60_000L, idOffset = 7L)
        assertEquals(7L, segs[0].id)
        assertEquals(60_100L, segs[0].startMs)
    }

    @Test
    fun `无时间戳按字数比例分配`() {
        val segs = SentenceSegmenter.distributeEvenly(
            "你好世界。今天天气不错！",
            startMs = 0L,
            endMs = 10_000L,
        )
        assertEquals(2, segs.size)
        // "你好世界。" 5 字 vs "今天天气不错！" 7 字 → 前段约 5/12
        val firstDur = segs[0].durationMs
        assertTrue(firstDur in 4000..4500)
        // 首尾衔接：总和精确等于区间
        assertEquals(0L, segs[0].startMs)
        assertEquals(10_000L, segs[1].endMs)
        assertEquals(segs[0].endMs, segs[1].startMs)
    }

    @Test
    fun `无标点文本整体一段`() {
        val segs = SentenceSegmenter.distributeEvenly("没有标点的一句话", 1000L, 5000L)
        assertEquals(1, segs.size)
        assertEquals(1000L, segs[0].startMs)
        assertEquals(5000L, segs[0].endMs)
    }

    @Test
    fun `省略号不断成碎片`() {
        val parts = SentenceSegmenter.splitSentences("他在想……然后他说了。")
        assertEquals(2, parts.size)
        assertEquals("他在想……", parts[0])
        assertEquals("然后他说了。", parts[1])
    }

    @Test
    fun `英文省略号不断成碎片`() {
        val parts = SentenceSegmenter.splitSentences("Wait... really?")
        assertEquals(2, parts.size)
        assertEquals("Wait...", parts[0])
        assertEquals("really?", parts[1])
    }
}
