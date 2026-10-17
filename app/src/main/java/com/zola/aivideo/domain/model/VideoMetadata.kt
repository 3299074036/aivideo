package com.zola.aivideo.domain.model

/**
 * 视频元信息（领域模型，与数据源解耦）。
 *
 * @param filePath App 私有目录下导入文件的绝对路径
 * @param displayName 展示用文件名
 * @param widthPx 视频宽度（px），取不到为 null
 * @param heightPx 视频高度（px），取不到为 null
 * @param durationMs 时长（毫秒），取不到为 0
 * @param sizeBytes 文件大小（字节）
 * @param hasAudioTrack 是否含音频轨
 * @param hasSubtitleTrack 是否含内嵌字幕轨
 * @param mimeType 容器/编码 MIME（如 video/mp4），取不到为 null
 */
data class VideoMetadata(
    val filePath: String,
    val displayName: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val hasAudioTrack: Boolean,
    val hasSubtitleTrack: Boolean,
    val mimeType: String?,
) {
    /** 分辨率展示文案，如 "1920×1080"，未知时为 null。 */
    val resolutionLabel: String?
        get() = if (widthPx != null && heightPx != null) "${widthPx}×${heightPx}" else null
}
