package com.zola.aivideo.data.synthesize

/**
 * 合成配置（M5）。
 *
 * - 字幕二选一：内嵌烧录（subtitles 滤镜，需重编码视频）/ 外挂字幕（mov_text 字幕轨）
 * - 编码预设二选一：原画质（视频流 copy，只处理音频；烧录模式下被迫重编码时用高码率近似）
 *   / 压缩版（H.264 + AAC，码率三档）
 */
enum class SubtitleMode(val displayName: String) {
    BURN("内嵌烧录"),
    EXTERNAL("外挂字幕"),
}

enum class EncodePreset(val displayName: String) {
    ORIGINAL("原画质"),
    COMPRESSED("压缩版"),
}

enum class BitrateTier(val displayName: String, val videoKbps: Int) {
    LOW("流畅", 1500),
    MEDIUM("标清", 3000),
    HIGH("高清", 6000),
}

data class SynthesizeOptions(
    val subtitleMode: SubtitleMode = SubtitleMode.BURN,
    val encodePreset: EncodePreset = EncodePreset.ORIGINAL,
    val bitrateTier: BitrateTier = BitrateTier.MEDIUM,
    /**
     * M6：使用素材替换后的视频（tasks/<id>/replaced.mp4）作为合成源。
     * 默认 false；Gson 反序列化缺字段时回退默认，老配置 JSON 兼容。
     */
    val useReplacedVideo: Boolean = false,
)
