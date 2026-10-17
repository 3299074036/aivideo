package com.zola.aivideo.data.tts

import java.io.File

/**
 * 配音轨组装（M4）：纯 Kotlin，可 JVM 单元测试。
 *
 * 把各分段的对齐后音频按字幕起始时间铺到一条时间轴上，空隙填静音，
 * 输出单声道 WAV。时间轴采样率固定为 [TIMELINE_SAMPLE_RATE]（Kokoro 原生 24kHz，
 * 云 TTS 分段先由调用方重采样到此时钟，见 [AudioResample]）。
 */
object DubTrackAssembler {

    /** 配音时间轴采样率（Hz）。 */
    const val TIMELINE_SAMPLE_RATE = 24000

    /** 时间轴上的一个片段。 */
    data class TimelineClip(
        /** 起始偏移（毫秒，≥0）。 */
        val startMs: Long,
        /** 音频采样（已对齐到槽位，目标采样率见 [TIMELINE_SAMPLE_RATE]）。 */
        val samples: FloatArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TimelineClip) return false
            return startMs == other.startMs && samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int = 31 * startMs.hashCode() + samples.contentHashCode()
    }

    /**
     * 组装时间轴。
     *
     * @param clips 片段列表（按 startMs 排序为佳；重叠时后者覆盖前者尾部）
     * @param totalMs 时间轴总时长（毫秒，通常取最后一段的 endMs；0 则自动推导）
     * @return 组装后的 float 采样数组
     */
    fun assemble(
        clips: List<TimelineClip>,
        totalMs: Long,
        sampleRate: Int = TIMELINE_SAMPLE_RATE,
    ): FloatArray {
        val resolvedTotal = if (totalMs > 0) totalMs else {
            clips.maxOfOrNull { it.startMs + it.samples.size * 1000L / sampleRate } ?: 0L
        }
        val totalSize = DubAligner.msToSamples(resolvedTotal, sampleRate)
        val out = FloatArray(totalSize) // 默认 0 = 静音
        for (clip in clips.sortedBy { it.startMs }) {
            val offset = DubAligner.msToSamples(clip.startMs.coerceAtLeast(0), sampleRate)
            for (i in clip.samples.indices) {
                val idx = offset + i
                if (idx >= totalSize) break
                // 重叠区：后者覆盖（分段本不应重叠，防御性处理）
                out[idx] = clip.samples[i]
            }
        }
        return out
    }

    /** 组装并写 WAV 文件，返回输出文件。 */
    fun assembleToWav(
        clips: List<TimelineClip>,
        totalMs: Long,
        outputFile: File,
        sampleRate: Int = TIMELINE_SAMPLE_RATE,
    ): File {
        val samples = assemble(clips, totalMs, sampleRate)
        outputFile.parentFile?.mkdirs()
        WavWriter.write(outputFile, samples, sampleRate, channels = 1)
        return outputFile
    }
}
