package com.zola.aivideo.data.stock

import com.google.gson.annotations.SerializedName

/**
 * 素材库数据模型（M6 素材替换 P1）。
 *
 * 素材来源：Pexels / Pixabay 官方 API（Key 用户自填 BYOK）。
 * 授权信息随任务存档：tasks/<taskId>/licenses.json。
 */

/** 素材提供方。 */
enum class StockProvider(val displayName: String) {
    PEXELS("Pexels"),
    PIXABAY("Pixabay"),
}

/**
 * 统一后的素材片段。
 *
 * @param videoUrl 直链（mp4，≤1080p 优先），下载后落盘到任务目录
 * @param licensePageUrl 素材来源/授权页（Pexels 为视频页，Pixabay 为 pageURL）
 */
data class StockClip(
    val provider: StockProvider,
    val clipId: String,
    val videoUrl: String,
    val width: Int,
    val height: Int,
    val durationSec: Double?,
    val previewImageUrl: String?,
    val authorName: String,
    val authorUrl: String?,
    val licensePageUrl: String,
    val licenseName: String,
)

/**
 * 授权存档记录（licenses.json 的数组元素）。
 *
 * 每次"下载素材"或"替换片段"成功后追加一条，保证每个任务用到的第三方素材
 * 来源可追溯（Pexels License / Pixabay Content License 均要求保留来源信息，
 * 且署名作者是推荐做法）。
 */
data class StockLicenseRecord(
    val provider: String,
    val clipId: String,
    val clipPageUrl: String,
    val authorName: String,
    val authorUrl: String?,
    val licenseName: String,
    /** 落盘文件名（任务 stock/ 目录下）。 */
    val fileName: String,
    /** 被替换的源视频片段（秒）；仅下载未替换时为 null。 */
    val replacedSegmentStartSec: Double?,
    val replacedSegmentEndSec: Double?,
    val recordedAt: Long,
)

// ---------- Pexels API DTO ----------

internal data class PexelsSearchResponse(
    @SerializedName("videos") val videos: List<PexelsVideo> = emptyList(),
)

internal data class PexelsVideo(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("url") val url: String = "",
    @SerializedName("image") val image: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("user") val user: PexelsUser? = null,
    @SerializedName("video_files") val videoFiles: List<PexelsVideoFile> = emptyList(),
)

internal data class PexelsUser(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String? = null,
)

internal data class PexelsVideoFile(
    @SerializedName("quality") val quality: String = "",
    @SerializedName("file_type") val fileType: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
    @SerializedName("link") val link: String = "",
)

/** Pexels → 统一模型：选 mp4、高度≤1080 的最大分辨率文件。 */
internal fun PexelsVideo.toStockClip(): StockClip? {
    val file = videoFiles
        .filter { it.fileType.contains("mp4", ignoreCase = true) && it.link.isNotBlank() }
        .filter { it.height in 1..1080 }
        .maxByOrNull { it.height }
        ?: return null
    return StockClip(
        provider = StockProvider.PEXELS,
        clipId = id.toString(),
        videoUrl = file.link,
        width = file.width,
        height = file.height,
        durationSec = duration?.toDouble(),
        previewImageUrl = image,
        authorName = user?.name.orEmpty().ifBlank { "Pexels 作者" },
        authorUrl = user?.url,
        licensePageUrl = url,
        licenseName = "Pexels License",
    )
}

// ---------- Pixabay API DTO ----------

internal data class PixabaySearchResponse(
    @SerializedName("hits") val hits: List<PixabayHit> = emptyList(),
)

internal data class PixabayHit(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("pageURL") val pageURL: String = "",
    @SerializedName("previewURL") val previewURL: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("user") val user: String = "",
    @SerializedName("videos") val videos: Map<String, PixabayVideoVariant> = emptyMap(),
)

internal data class PixabayVideoVariant(
    @SerializedName("url") val url: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)

/** Pixabay → 统一模型：选高度≤1080 的最大分辨率变体。 */
internal fun PixabayHit.toStockClip(): StockClip? {
    val variant = videos.values
        .filter { it.url.isNotBlank() && it.height in 1..1080 }
        .maxByOrNull { it.height }
        ?: return null
    return StockClip(
        provider = StockProvider.PIXABAY,
        clipId = id.toString(),
        videoUrl = variant.url,
        width = variant.width,
        height = variant.height,
        durationSec = duration?.toDouble(),
        previewImageUrl = previewURL,
        authorName = user.ifBlank { "Pixabay 作者" },
        authorUrl = null,
        licensePageUrl = pageURL,
        licenseName = "Pixabay Content License",
    )
}
