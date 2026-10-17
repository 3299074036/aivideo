package com.zola.aivideo.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AudioResample 单元测试（M4）。
 */
class AudioResampleTest {

    @Test
    fun `linear 16k to 24k scales sample count`() {
        val input = FloatArray(16000) { 0.5f } // 1s @16k
        val out = AudioResample.linear(input, 16000, 24000)
        assertEquals(24000, out.size)
        assertTrue(out.all { it == 0.5f })
    }

    @Test
    fun `linear identity rate copies`() {
        val input = FloatArray(100) { it / 100f }
        val out = AudioResample.linear(input, 24000, 24000)
        assertTrue(input.contentEquals(out))
    }

    @Test
    fun `linear interpolates ramp`() {
        // 斜坡 [0, 0.5, 1] @3Hz（t=0, 1/3, 2/3s）→ @6Hz：输出 6 采样，
        // 按时间映射线性插值；超出输入末尾的采样钳制为末值
        val input = floatArrayOf(0f, 0.5f, 1f)
        val out = AudioResample.linear(input, 3, 6)
        assertEquals(6, out.size)
        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0.25f, out[1], 0.0001f)
        assertEquals(0.5f, out[2], 0.0001f)
        assertEquals(0.75f, out[3], 0.0001f)
        assertEquals(1.0f, out[4], 0.0001f)
        assertEquals(1.0f, out[5], 0.0001f)
    }

    @Test
    fun `linear empty input yields empty`() {
        assertEquals(0, AudioResample.linear(FloatArray(0), 16000, 24000).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear rejects zero rate`() {
        AudioResample.linear(FloatArray(10), 0, 24000)
    }
}
