package com.zola.aivideo.data.ytdlp

import com.google.gson.annotations.SerializedName

/**
 * yt-dlp 更新通道与下载的数据模型（M6）。
 *
 * 架构（经调研确定，参照 youtubedl-android/Seal 的生产验证设计）：
 * - Python 解释器（bionic 版 CPython）必须随 APK 内置
 *   （`app/src/main/jniLibs/arm64-v8a/libpyinterpreter.so` →
 *   `applicationInfo.nativeLibraryDir`；API 29+ 不允许从可写私有目录执行 ELF，
 *   也不能运行时下载解释器）
 * - yt-dlp 本体是官方 release 的 `yt-dlp` **zipimport 纯 Python 归档**（约 2.9MB，
 *   资产名恰好为 "yt-dlp"），运行时按需下载到应用私有目录，
 *   以脚本参数传给解释器（不直接执行，不触碰 W^X 红线）
 * - Google Play 政策例外：解释器随 APK + 运行时只下载解释型代码（Python）
 *   属于明示例外；运行时下载 ELF 原生二进制则撞红线
 */

// ---------- GitHub Release DTO ----------

internal data class GithubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("assets") val assets: List<GithubAsset> = emptyList(),
)

internal data class GithubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    /** 形如 "sha256:abc..."（GitHub API 提供）。 */
    @SerializedName("digest") val digest: String? = null,
)

/** 可用的 yt-dlp 更新（解析后的结果）。 */
data class YtDlpUpdate(
    /** GitHub tag，如 "2026.08.19"。 */
    val tag: String,
    /** zipimport 归档下载地址。 */
    val downloadUrl: String,
    /** 归档大小（字节），展示用。 */
    val sizeBytes: Long,
    /** 期望 SHA-256（小写 hex，无前缀）。 */
    val sha256: String,
)

/**
 * 从 GitHub release JSON 解析 yt-dlp 更新信息。
 *
 * 约定（youtubedl-android 同款逻辑）：在 assets 里找 `name` 恰好等于
 * `"yt-dlp"` 的资产——即官方的平台无关 zipimport 归档；
 * `yt-dlp_linux_aarch64` 等 glibc standalone 二进制在 Android 上跑不了，
 * 绝不能选。
 */
internal fun GithubRelease.toYtDlpUpdate(): YtDlpUpdate? {
    if (tagName.isBlank()) return null
    val asset = assets.firstOrNull { it.name == "yt-dlp" } ?: return null
    if (asset.browserDownloadUrl.isBlank()) return null
    val sha256 = asset.digest
        ?.removePrefix("sha256:")
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        ?: return null
    return YtDlpUpdate(
        tag = tagName,
        downloadUrl = asset.browserDownloadUrl,
        sizeBytes = asset.size,
        sha256 = sha256.lowercase(),
    )
}

/**
 * 版本比较：tag 形如 "2026.08.19"（ nightly 为 "2026.08.19.232710"）。
 * 按数字段逐段比较；格式异常时回退字符串比较。
 */
fun isNewerVersion(latest: String, installed: String): Boolean {
    if (installed.isBlank()) return latest.isNotBlank()
    if (latest.isBlank()) return false
    val lp = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val ip = installed.split(".").map { it.toIntOrNull() ?: 0 }
    val n = maxOf(lp.size, ip.size)
    for (i in 0 until n) {
        val l = lp.getOrElse(i) { 0 }
        val r = ip.getOrElse(i) { 0 }
        if (l != r) return l > r
    }
    return false
}

// ---------- yt-dlp -J 格式探测 DTO ----------

internal data class YtDlpInfoJson(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("formats") val formats: List<YtDlpFormat> = emptyList(),
)

internal data class YtDlpFormat(
    @SerializedName("format_id") val formatId: String = "",
    @SerializedName("ext") val ext: String = "",
    @SerializedName("vcodec") val vcodec: String? = null,
    @SerializedName("acodec") val acodec: String? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("filesize") val filesize: Long? = null,
) {
    /** 是否为仅视频流。 */
    val videoOnly: Boolean
        get() = vcodec != null && vcodec != "none" && (acodec == null || acodec == "none")

    /** 是否为仅音频流。 */
    val audioOnly: Boolean
        get() = (vcodec == null || vcodec == "none") && acodec != null && acodec != "none"

    /** 是否为音视频合流（可直接下载单个文件，无需合并）。 */
    val progressive: Boolean get() = !videoOnly && !audioOnly
}

/** 下载计划：单文件 或 分流下载后合并。 */
internal sealed interface DownloadPlan {
    /** 直接下载单个合流格式。 */
    data class SingleFile(val format: YtDlpFormat) : DownloadPlan

    /** 分别下载视频流与音频流，再用 ffmpeg-kit 合并（yt-dlp 自身无可用 ffmpeg 时的路线）。 */
    data class SplitStreams(val video: YtDlpFormat, val audio: YtDlpFormat) : DownloadPlan
}

/**
 * 选择下载计划（纯函数，可测试）。
 *
 * 策略：优先 ≤maxHeight 的合流 mp4（单文件，最省事）；没有合流时选
 * 最佳视频流（≤maxHeight）+ 最佳音频流，下载后用 ffmpeg-kit 合并。
 * （不用 yt-dlp 内建合并：它需要一个真实可执行的 ffmpeg 文件，
 * 而工程的 ffmpeg-kit 是进程内 JNI，没有可执行文件。）
 */
internal fun selectDownloadPlan(
    formats: List<YtDlpFormat>,
    maxHeight: Int = 1080,
): DownloadPlan? {
    val usable = formats.filter { it.formatId.isNotBlank() }
    // 1. 合流：mp4 优先，高度 ≤ 上限中取最高
    val progressive = usable
        .filter { it.progressive && (it.height ?: Int.MAX_VALUE) <= maxHeight }
        .sortedWith(
            compareByDescending<YtDlpFormat> { it.ext == "mp4" }
                .thenByDescending { it.height ?: 0 },
        )
        .firstOrNull()
    if (progressive != null) return DownloadPlan.SingleFile(progressive)
    // 2. 分流：视频取 ≤上限最高，音频取 filesize 最大（通常音质最好）
    val video = usable
        .filter { it.videoOnly && (it.height ?: Int.MAX_VALUE) <= maxHeight }
        .maxByOrNull { it.height ?: 0 }
        ?: return null
    val audio = usable
        .filter { it.audioOnly }
        .maxByOrNull { it.filesize ?: 0L }
        ?: usable.filter { it.audioOnly }.firstOrNull()
        ?: return null
    return DownloadPlan.SplitStreams(video, audio)
}
