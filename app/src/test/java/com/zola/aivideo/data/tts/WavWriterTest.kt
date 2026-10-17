package com.zola.aivideo.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * WavWriter 单元测试（M4）：写→读回采样一致性。
 */
class WavWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write and read roundtrip preserves samples`() {
        val file = tmp.newFile("test.wav")
        val samples = FloatArray(4800) { i -> (i % 100) / 100f * 0.8f - 0.4f }
        WavWriter.write(file, samples, sampleRate = 24000)
        val (read, rate) = WavWriter.readSamples(file)
        assertEquals(24000, rate)
        assertEquals(samples.size, read.size)
        // 16-bit 量化误差容忍
        for (i in samples.indices) {
            assertTrue(kotlin.math.abs(samples[i] - read[i]) < 0.001f)
        }
    }

    @Test
    fun `write clamps out of range samples`() {
        val file = tmp.newFile("clamp.wav")
        WavWriter.write(file, floatArrayOf(2.0f, -2.0f, 0f), sampleRate = 16000)
        val (read, _) = WavWriter.readSamples(file)
        assertTrue(read[0] > 0.99f)
        assertTrue(read[1] < -0.99f)
        assertEquals(0f, read[2], 0.001f)
    }

    @Test
    fun `write produces valid riff header`() {
        val file = tmp.newFile("hdr.wav")
        WavWriter.write(file, FloatArray(100), sampleRate = 44100)
        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(44 + 200, bytes.size)
    }
}
