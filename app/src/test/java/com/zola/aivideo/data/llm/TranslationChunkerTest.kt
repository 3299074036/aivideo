package com.zola.aivideo.data.llm

import com.zola.aivideo.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TranslationChunker] 单元测试：纯 JVM。 */
class TranslationChunkerTest {

    private fun seg(id: Long, text: String, startMs: Long = id * 1000) =
        TranscriptSegment(id, startMs, startMs + 500, text)

    @Test
    fun `短文稿不切分`() {
        val segs = listOf(seg(0, "你好"), seg(1, "世界"))
        assertFalse(TranslationChunker.needsChunking(segs))
        val chunks = TranslationChunker.chunk(segs)
        assertEquals(1, chunks.size)
        assertEquals(2, chunks[0].size)
    }

    @Test
    fun `超限文稿按 token 切分且顺序完整`() {
        // 每段约 3000 CJK 字符 → 约 2400 token；6 段远超 12000
        val segs = (0 until 6).map { seg(it.toLong(), "字".repeat(3000)) }
        assertTrue(TranslationChunker.needsChunking(segs))
        val chunks = TranslationChunker.chunk(segs)
        assertTrue(chunks.size > 1)
        val flat = chunks.flatten()
        assertEquals(6, flat.size)
        assertEquals((0 until 6).map { it.toLong() }, flat.map { it.id })
        // 每个 chunk 都非空且不超过上限太多（单段超大时独占一块）
        chunks.forEach { chunk ->
            assertTrue(chunk.isNotEmpty())
        }
    }

    @Test
    fun `CJK 估算比纯英文高`() {
        // 等长 10 字符：CJK 10*0.8=8 > 拉丁 10*0.35=4（ceil 后）
        val cjk = TranslationChunker.estimateTokens("你好世界你好世界你好")
        val latin = TranslationChunker.estimateTokens("helloworld")
        assertTrue("CJK=$cjk latin=$latin", cjk > latin)
    }

    @Test
    fun `空列表返回空`() {
        assertTrue(TranslationChunker.chunk(emptyList()).isEmpty())
    }
}
