package com.zola.aivideo.data.tts

/**
 * 纯 Kotlin 音频重采样（M4）：线性插值。
 *
 * 用于云 TTS（解码后 16kHz）→ 配音时间轴（24kHz）的采样率统一。
 * 与 M2 ChunkResampler 的流式实现不同，此处处理的是已在内存中的短分段
 * （单字幕分段通常 < 30 秒），简单线性插值精度足够，可 JVM 单元测试。
 */
object AudioResample {

    /**
     * 线性插值重采样。
     *
     * @param input 输入 float 采样（-1f..1f）
     * @param fromRate 输入采样率
     * @param toRate 目标采样率
     * @return 重采样后的采样数组
     */
    fun linear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "采样率必须为正数" }
        if (input.isEmpty()) return FloatArray(0)
        if (fromRate == toRate) return input.copyOf()
        if (input.size == 1) {
            // 单采样：按比例复制
            val n = ((1L * toRate) / fromRate).toInt().coerceAtLeast(1)
            return FloatArray(n) { input[0] }
        }
        // 时长守恒：outSize = input.size * toRate / fromRate（整数运算，无浮点 off-by-one）
        val outSize = ((input.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val step = fromRate.toDouble() / toRate
        return FloatArray(outSize) { i ->
            val pos = i * step
            val idx = pos.toInt().coerceIn(0, input.size - 2)
            val frac = (pos - idx).toFloat().coerceIn(0f, 1f)
            input[idx] * (1 - frac) + input[idx + 1] * frac
        }
    }
}
