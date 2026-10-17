package com.zola.aivideo.data.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DubTrackAssembler 单元测试（M4）：时间轴铺设、间隙静音、重叠覆盖。
 */
class DubTrackAssemblerTest {

    private val sr = DubTrackAssembler.TIMELINE_SAMPLE_RATE // 24000

    @Test
    fun `assemble places clips at offsets with silence gaps`() {
        val clip1 = DubTrackAssembler.TimelineClip(
            startMs = 0,
            samples = FloatArray(24000) { 0.5f }, // 1s
        )
        val clip2 = DubTrackAssembler.TimelineClip(
            startMs = 2000,
            samples = FloatArray(24000) { 0.25f }, // 1s
        )
        val out = DubTrackAssembler.assemble(listOf(clip1, clip2), totalMs = 3000, sr)
        assertEquals(72000, out.size)
        // 片段 1
        for (i in 0 until 24000) assertEquals(0.5f, out[i], 0.0001f)
        // 间隙（1s~2s）为静音
        for (i in 24000 until 48000) assertEquals(0f, out[i], 0.0001f)
        // 片段 2
        for (i in 48000 until 72000) assertEquals(0.25f, out[i], 0.0001f)
    }

    @Test
    fun `assemble total zero derives from clips`() {
        val clip = DubTrackAssembler.TimelineClip(
            startMs = 500,
            samples = FloatArray(24000) { 0.5f }, // 500ms + 1000ms = 1500ms
        )
        val out = DubTrackAssembler.assemble(listOf(clip), totalMs = 0, sr)
        assertEquals(36000, out.size)
        for (i in 0 until 12000) assertEquals(0f, out[i], 0.0001f)
        for (i in 12000 until 36000) assertEquals(0.5f, out[i], 0.0001f)
    }

    @Test
    fun `assemble sorts by start time`() {
        val late = DubTrackAssembler.TimelineClip(2000, FloatArray(2400) { 0.9f })
        val early = DubTrackAssembler.TimelineClip(0, FloatArray(2400) { 0.1f })
        val out = DubTrackAssembler.assemble(listOf(late, early), totalMs = 2100, sr)
        assertEquals(0.1f, out[0], 0.0001f)
        assertEquals(0.9f, out[48000], 0.0001f)
    }

    @Test
    fun `assemble clamps overflow beyond total`() {
        val clip = DubTrackAssembler.TimelineClip(0, FloatArray(48000) { 0.5f })
        val out = DubTrackAssembler.assemble(listOf(clip), totalMs = 1000, sr)
        assertEquals(24000, out.size)
    }

    @Test
    fun `assemble empty clips yields silence`() {
        val out = DubTrackAssembler.assemble(emptyList(), totalMs = 1000, sr)
        assertEquals(24000, out.size)
        assertTrue(out.all { it == 0f })
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
