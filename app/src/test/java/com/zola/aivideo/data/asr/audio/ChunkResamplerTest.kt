package com.zola.aivideo.data.asr.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** 流式重采样器单元测试：纯 Kotlin 逻辑。 */
class ChunkResamplerTest {

    /** 1kHz 正弦波，便于验证频率成分保留。 */
    private fun sine(freqHz: Double, rate: Int, seconds: Double): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n) { i -> sin(2 * PI * freqHz * i / rate).toFloat() }
    }

    @Test
    fun `48k转16k输出长度约为三分之一`() {
        val input = sine(1000.0, 48000, 1.0)
        val out = ChunkResampler(48000).resampleAll(input)
        // 48000/16000 = 3，允许 ±2 个采样点误差
        assertTrue(out.size in 15998..16002)
    }

    @Test
    fun `同采样率近似恒等变换`() {
        val input = sine(440.0, 16000, 0.5)
        val out = ChunkResampler(16000).resampleAll(input)
        assertEquals(input.size, out.size)
        // 线性插值在整数步长下应精确还原（允许量化误差 2）
        for (i in input.indices) {
            val expected = (input[i] * 32767).toInt()
            assertTrue(kotlin.math.abs(out[i].toInt() - expected) <= 2)
        }
    }

    @Test
    fun `分块流式等价于一次性重采样`() {
        val input = sine(1000.0, 44100, 2.0)
        val oneShot = ChunkResampler(44100).resampleAll(input)

        val streaming = ChunkResampler(44100)
        val chunks = input.toList().chunked(5000).map { it.toFloatArray() }
        val streamed = chunks.flatMapIndexed { index, chunk ->
            // 最后一块用 isLast=true；等价写法是中途全用 isLast=false + 最后 finish()
            val bytes = streaming.process(chunk, isLast = index == chunks.lastIndex)
            val bb = java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            List(bb.remaining()) { bb.get(it) }
        }
        assertEquals(oneShot.size, streamed.size)
        assertEquals(oneShot.toList(), streamed)
    }

    @Test
    fun `分块加finish与一次性重采样一致`() {
        val input = sine(1000.0, 48000, 1.5)
        val oneShot = ChunkResampler(48000).resampleAll(input)

        val streaming = ChunkResampler(48000)
        val chunks = input.toList().chunked(7000).map { it.toFloatArray() }
        val streamed = chunks.flatMap { chunk ->
            val bytes = streaming.process(chunk)
            val bb = java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            List(bb.remaining()) { bb.get(it) }
        }.toMutableList()
        // 收尾：发射遗留尾部点
        val tailBytes = streaming.finish()
        val tailBb = java.nio.ByteBuffer.wrap(tailBytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        repeat(tailBb.remaining()) { streamed.add(tailBb.get(it)) }

        assertEquals(oneShot.size, streamed.size)
        assertEquals(oneShot.toList(), streamed)
    }

    @Test
    fun `多声道混单声道取平均`() {
        // 立体声：左声道 1.0，右声道 -1.0 → 混音后应为 0
        val stereo = shortArrayOf(32767, -32767, 32767, -32767)
        val mono = PcmDecoder.toMonoFloat(stereo, 2)
        assertEquals(2, mono.size)
        assertTrue(kotlin.math.abs(mono[0]) < 0.001f)
        assertTrue(kotlin.math.abs(mono[1]) < 0.001f)
    }

    @Test
    fun `单声道直通仅做归一化`() {
        val pcm = shortArrayOf(0, 16384, -16384)
        val mono = PcmDecoder.toMonoFloat(pcm, 1)
        assertEquals(3, mono.size)
        assertEquals(0f, mono[0], 0.0001f)
        assertEquals(0.5f, mono[1], 0.0001f)
        assertEquals(-0.5f, mono[2], 0.0001f)
    }
}
