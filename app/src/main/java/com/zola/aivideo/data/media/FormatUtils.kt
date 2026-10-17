package com.zola.aivideo.data.media

import java.util.Locale
import kotlin.math.roundToLong

/**
 * 元信息格式化工具：纯 Kotlin，无 Android 依赖，可 JVM 单元测试。
 */
object FormatUtils {

    /** 毫秒 → "mm:ss" / "h:mm:ss"（时长展示）。负数按 0 处理。 */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0) / 1000.0).roundToLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 毫秒 → 字幕时间戳 "mm:ss.mmm"（如 "02:07.250"），超 1 小时为 "h:mm:ss.mmm"。
     * 字幕编辑页时间展示用。负数按 0 处理。
     */
    fun formatTimestampMs(timestampMs: Long): String {
        val ms = timestampMs.coerceAtLeast(0)
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        val millis = ms % 1000
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }

    /** 字节 → "1.2 MB" / "345 KB" 等人类可读文案。 */
    fun formatFileSize(sizeBytes: Long): String {
        val size = sizeBytes.coerceAtLeast(0).toDouble()
        return when {
            size < 1024 -> String.format(Locale.US, "%d B", size.toLong())
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024)
            size < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024 * 1024))
            else -> String.format(Locale.US, "%.2f GB", size / (1024 * 1024 * 1024))
        }
    }
}
