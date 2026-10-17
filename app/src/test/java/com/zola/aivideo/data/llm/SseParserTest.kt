package com.zola.aivideo.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SseParser] 单元测试：纯 JVM。 */
class SseParserTest {

    @Test
    fun `单行 data 事件在空行终结时产出`() {
        val p = SseParser()
        assertNull(p.feedLine("data: {\"a\":1}"))
        val event = p.feedLine("")
        assertTrue(event is SseParser.SseEvent.Data)
        assertEquals("{\"a\":1}", (event as SseParser.SseEvent.Data).payload)
    }

    @Test
    fun `data 冒号后空格被去掉`() {
        val p = SseParser()
        p.feedLine("data:hello")
        val event = p.feedLine("") as SseParser.SseEvent.Data
        assertEquals("hello", event.payload)
    }

    @Test
    fun `多行 data 拼接为一个事件`() {
        val p = SseParser()
        p.feedLine("data: line1")
        p.feedLine("data: line2")
        val event = p.feedLine("") as SseParser.SseEvent.Data
        assertEquals("line1\nline2", event.payload)
    }

    @Test
    fun `冒号开头为注释心跳被忽略`() {
        val p = SseParser()
        assertNull(p.feedLine(": keep-alive"))
        assertNull(p.feedLine(""))
    }

    @Test
    fun `DONE 结束流`() {
        val p = SseParser()
        p.feedLine("data: [DONE]")
        val event = p.feedLine("")
        assertTrue(event is SseParser.SseEvent.Done)
    }

    @Test
    fun `flush 吐出残留未终结事件`() {
        val p = SseParser()
        p.feedLine("data: tail")
        val event = p.flush() as SseParser.SseEvent.Data
        assertEquals("tail", event.payload)
        assertNull(p.flush())
    }

    @Test
    fun `连续两个事件互不干扰`() {
        val p = SseParser()
        p.feedLine("data: first")
        val e1 = p.feedLine("") as SseParser.SseEvent.Data
        p.feedLine("data: second")
        val e2 = p.feedLine("") as SseParser.SseEvent.Data
        assertEquals("first", e1.payload)
        assertEquals("second", e2.payload)
    }
}
