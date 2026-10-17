package com.zola.aivideo.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/** [GlossaryPostProcessor] 单元测试：纯 JVM。 */
class GlossaryPostProcessorTest {

    @Test
    fun `残留原文术语被替换`() {
        val terms = listOf(GlossaryTerm(source = "machine learning", target = "机器学习", targetLang = "中文"))
        val out = GlossaryPostProcessor.apply("I love machine learning", terms)
        assertEquals("I love 机器学习", out)
    }

    @Test
    fun `已含目标译法不重复替换`() {
        val terms = listOf(GlossaryTerm(source = "AI", target = "人工智能", targetLang = "中文"))
        val out = GlossaryPostProcessor.apply("人工智能很有趣", terms)
        assertEquals("人工智能很有趣", out)
    }

    @Test
    fun `长词优先避免短词吞掉长词`() {
        val terms = listOf(
            GlossaryTerm(source = "learning", target = "学习", targetLang = "中文"),
            GlossaryTerm(source = "machine learning", target = "机器学习", targetLang = "中文"),
        )
        val out = GlossaryPostProcessor.apply("machine learning", terms)
        assertEquals("机器学习", out)
    }

    @Test
    fun `空术语被忽略`() {
        val terms = listOf(GlossaryTerm(source = "", target = "x", targetLang = "中文"))
        val out = GlossaryPostProcessor.apply("hello", terms)
        assertEquals("hello", out)
    }

    @Test
    fun `无术语原文不动`() {
        val out = GlossaryPostProcessor.apply("hello world", emptyList())
        assertEquals("hello world", out)
    }
}
