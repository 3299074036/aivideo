package com.zola.aivideo.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * DubAligner 单元测试（M4）：对齐决策阈值、补静音、截断淡出。
 */
class DubAlignerTest {

    @Test
    fun `plan short audio pads silence`() {
        val action = DubAligner.plan(slotMs = 2000, audioMs = 1500)
        assertTrue(action is DubAligner.AlignAction.PadSilence)
        assertEquals(500L, (action as DubAligner.AlignAction.PadSilence).silenceMs)
    }

    @Test
    fun `plan exact fit pads zero`() {
        val action = DubAligner.plan(slotMs = 2000, audioMs = 2000)
        assertTrue(action is DubAligner.AlignAction.PadSilence)
        assertEquals(0L, (action as DubAligner.AlignAction.PadSilence).silenceMs)
    }

    @Test
    fun `plan slightly long audio speeds up within limit`() {
        // 2200/2000 = 1.1 <= 1.2 → 变速
        val action = DubAligner.plan(slotMs = 2000, audioMs = 2200)
        assertTrue(action is DubAligner.AlignAction.SpeedUp)
        val tempo = (action as DubAligner.AlignAction.SpeedUp).tempo
        assertTrue(abs(tempo - 1.1f) < 0.001f)
    }

    @Test
    fun `plan at tempo boundary uses speedup`() {
        // 2400/2000 = 1.2 → 边界，仍走变速
        val action = DubAligner.plan(slotMs = 2000, audioMs = 2400)
        assertTrue(action is DubAligner.AlignAction.SpeedUp)
    }

    @Test
    fun `plan overlong audio truncates`() {
        // 2600/2000 = 1.3 > 1.2 → 截断
        val action = DubAligner.plan(slotMs = 2000, audioMs = 2600)
        assertTrue(action is DubAligner.AlignAction.Truncate)
        assertEquals(
            DubAligner.TRUNCATE_FADE_MS,
            (action as DubAligner.AlignAction.Truncate).fadeMs,
        )
    }

    @Test
    fun `padSilence extends to slot with zeros`() {
        val sr = 24000
        val samples = FloatArray(24000) { 0.5f } // 1000ms
        val out = DubAligner.padSilence(samples, targetMs = 1500, sampleRate = sr)
        assertEquals(36000, out.size)
        // 前半保持
        for (i in 0 until 24000) assertEquals(0.5f, out[i], 0.0001f)
        // 后半为静音
        for (i in 24000 until 36000) assertEquals(0f, out[i], 0.0001f)
    }

    @Test
    fun `truncateWithFade cuts and fades tail`() {
        val sr = 24000
        val samples = FloatArray(48000) { 1.0f } // 2000ms
        val out = DubAligner.truncateWithFade(samples, targetMs = 1000, sampleRate = sr)
        assertEquals(24000, out.size)
        // 尾部淡出：最后一个采样应衰减到 ~0
        assertTrue(abs(out.last()) < 0.01f)
        // 中间保持
        assertEquals(1.0f, out[12000], 0.0001f)
        // 淡出区单调递减（采样点检查）
        val fadeStart = out.size - DubAligner.msToSamples(DubAligner.TRUNCATE_FADE_MS, sr)
        var prev = out[fadeStart]
        for (i in fadeStart + 480 until out.size step 480) {
            assertTrue(out[i] <= prev + 0.001f)
            prev = out[i]
        }
    }

    @Test
    fun `truncateWithFade fades from full amplitude to zero`() {
        val sr = 24000
        val samples = FloatArray(48000) { 0.8f }
        val out = DubAligner.truncateWithFade(samples, targetMs = 1000, sampleRate = sr)
        val fadeSize = DubAligner.msToSamples(DubAligner.TRUNCATE_FADE_MS, sr)
        val fadeStart = out.size - fadeSize
        // 淡出起点保持原振幅（增益 1.0）
        assertTrue(abs(out[fadeStart] - 0.8f) < 0.01f)
        // 淡出中点增益 ≈ 0.5
        assertTrue(abs(out[fadeStart + fadeSize / 2] - 0.4f) < 0.02f)
        // 淡出终点 ≈ 0
        assertTrue(abs(out.last()) < 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plan rejects non-positive slot`() {
        DubAligner.plan(slotMs = 0, audioMs = 100)
    }
}
