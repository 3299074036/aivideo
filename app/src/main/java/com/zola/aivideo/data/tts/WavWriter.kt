package com.zola.aivideo.data.tts

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 纯 Kotlin WAV 写入器（M4）：16-bit PCM 小端单声道/立体声。
 *
 * 配音轨组装、试听、FFmpeg 混音的输入都走标准 WAV，避免裸 PCM 的格式歧义。
 * 可 JVM 单元测试（写后校验头 + 采样回读）。
 */
object WavWriter {

    /**
     * 写 WAV 文件。
     *
     * @param file 输出文件（父目录需存在）
     * @param samples float 采样（-1f..1f，超范围钳制）
     * @param sampleRate 采样率
     * @param channels 声道数（默认 1）
     */
    fun write(file: File, samples: FloatArray, sampleRate: Int, channels: Int = 1) {
        require(sampleRate > 0 && channels in 1..2) { "非法采样率/声道数" }
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // fmt chunk size
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2) // byte rate
        header.putShort((channels * 2).toShort()) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val v = (s.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
                buf.putShort(v.toShort())
            }
            out.write(buf.array())
        }
    }

    /**
     * 读回 WAV 的采样（仅支持本写入器写的 16-bit PCM 格式，测试/校验用）。
     */
    fun readSamples(file: File): Pair<FloatArray, Int> {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "文件过小，不是合法 WAV" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "非 RIFF 文件" }
        val channels = buf.getShort(22).toInt()
        val sampleRate = buf.getInt(24)
        val dataSize = buf.getInt(40)
        val count = dataSize / 2
        val samples = FloatArray(count) { i ->
            buf.getShort(44 + i * 2) / 32768f
        }
        require(channels == 1) { "测试读取仅支持单声道" }
        return samples to sampleRate
    }
}
