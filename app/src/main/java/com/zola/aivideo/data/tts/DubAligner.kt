package com.zola.aivideo.data.tts

/**
 * 配音时间轴对齐（M4）：纯 Kotlin，可 JVM 单元测试。
 *
 * 每段合成音频时长 vs 字幕槽位时长的三种处置：
 * - [AlignAction.PadSilence]：音频短于等于槽位 → 尾部补静音对齐槽位
 * - [AlignAction.SpeedUp]：音频略长（tempo = audioMs/slotMs ≤ [MAX_TEMPO]）→
 *   FFmpeg `atempo` 变速压缩（调用方执行，本类只做决策）
 * - [AlignAction.Truncate]：音频远超槽位 → 截断到槽位长度 + 尾部淡出，并标记该段
 *
 * 验收口径（技术方案 §2.4）：抽查 10 处，配音与字幕时间轴误差 ≤ 1 秒。
 * 本算法保证每段配音**起始**与字幕槽位起始对齐、**结束**不超过槽位结束，
 * 因此单段误差上确界为 0（截断段）或静音段（观众感知为停顿）。
 */
object DubAligner {

    /** atempo 安全区上限：超过则截断（技术方案 §2.4：0.8~1.2）。 */
    const val MAX_TEMPO = 1.2f

    /** 截断段尾部淡出时长（毫秒），避免爆音。 */
    const val TRUNCATE_FADE_MS = 200L

    /** 对齐动作（决策结果）。 */
    sealed interface AlignAction {
        /** 补静音：silenceMs 为需补的时长。 */
        data class PadSilence(val silenceMs: Long) : AlignAction

        /** 变速：tempo 为 atempo 系数（>1 加速，∈ (1, MAX_TEMPO]）。 */
        data class SpeedUp(val tempo: Float) : AlignAction

        /** 截断：超长部分丢弃，尾部淡出 fadeMs。 */
        data class Truncate(val fadeMs: Long = TRUNCATE_FADE_MS) : AlignAction
    }

    /**
     * 决策单段的对齐动作：纯函数。
     *
     * @param slotMs 字幕槽位时长（毫秒，>0）
     * @param audioMs 合成音频时长（毫秒，≥0）
     */
    fun plan(slotMs: Long, audioMs: Long): AlignAction {
        require(slotMs > 0) { "槽位时长必须为正数" }
        val audio = audioMs.coerceAtLeast(0)
        if (audio <= slotMs) return AlignAction.PadSilence(slotMs - audio)
        val tempo = audio.toFloat() / slotMs
        return if (tempo <= MAX_TEMPO) AlignAction.SpeedUp(tempo)
        else AlignAction.Truncate()
    }

    /**
     * 补静音：在 samples 尾部追加静音至目标时长。
     *
     * @return 新数组（长度 = targetMs 对应的采样数）
     */
    fun padSilence(samples: FloatArray, targetMs: Long, sampleRate: Int): FloatArray {
        val targetSize = msToSamples(targetMs, sampleRate)
        if (samples.size >= targetSize) return samples.copyOf(targetSize)
        return samples.copyOf(targetSize) // copyOf 尾部自动补 0（静音）
    }

    /**
     * 截断 + 尾部线性淡出。
     *
     * @return 长度为 targetMs 对应采样数的数组，尾部 fadeMs 内线性衰减到 0
     */
    fun truncateWithFade(
        samples: FloatArray,
        targetMs: Long,
        sampleRate: Int,
        fadeMs: Long = TRUNCATE_FADE_MS,
    ): FloatArray {
        val targetSize = msToSamples(targetMs, sampleRate)
        val out = samples.copyOf(minOf(targetSize, samples.size))
        if (out.size < targetSize) {
            // 音频本身比槽位短（plan 不会走到这里，防御性补齐）
            return out.copyOf(targetSize)
        }
        val fadeSize = msToSamples(fadeMs, sampleRate).coerceAtMost(out.size)
        // 淡出：起点增益 1.0（保持原振幅），线性衰减到 ~0
        for (i in 0 until fadeSize) {
            val idx = out.size - fadeSize + i
            val gain = (fadeSize - i).toFloat() / fadeSize
            out[idx] = out[idx] * gain
        }
        return out
    }

    /** 毫秒 → 采样数（向下取整，至少 0）。 */
    fun msToSamples(ms: Long, sampleRate: Int): Int =
        ((ms.coerceAtLeast(0) * sampleRate) / 1000).toInt()
}
