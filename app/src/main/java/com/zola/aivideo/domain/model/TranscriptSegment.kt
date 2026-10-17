package com.zola.aivideo.domain.model

/**
 * 转写字幕分段（领域模型）。
 *
 * 时间以毫秒为单位；文本为该时间区间内的识别内容（可编辑）。
 * M3 翻译、M4 配音都以它为输入，因此时间戳精度是全链路的基准。
 *
 * @param id 分段序号（同一转写结果内唯一，0 起）
 * @param startMs 开始时间（毫秒，含）
 * @param endMs 结束时间（毫秒，不含）
 * @param text 字幕文本
 */
data class TranscriptSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    /** 时长（毫秒），防御性取非负。 */
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
