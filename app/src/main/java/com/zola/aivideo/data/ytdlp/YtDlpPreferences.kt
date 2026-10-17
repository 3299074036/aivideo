package com.zola.aivideo.data.ytdlp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * yt-dlp 偏好与更新通道状态（M6）。
 *
 * - 更新通道：stable（默认）/ nightly（yt-dlp/yt-dlp-nightly-builds，
 *   官方称 extractor 坏得快，nightly 对普通用户更友好）
 * - 已安装版本：GitHub release tag（如 "2026.08.19"），SHA-256 校验通过后才更新
 * - 独立更新通道：只管 yt-dlp 归档（约 2.9MB），与 APK 发版解耦
 */
@Singleton
class YtDlpPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ytdlp_prefs", Context.MODE_PRIVATE)

    /** 更新通道。 */
    var channel: YtDlpChannel
        get() = runCatching { YtDlpChannel.valueOf(prefs.getString(KEY_CHANNEL, null) ?: "") }
            .getOrDefault(YtDlpChannel.STABLE)
        set(value) {
            prefs.edit().putString(KEY_CHANNEL, value.name).apply()
        }

    /** 已安装的 yt-dlp 版本（GitHub tag），未安装为空。 */
    var installedTag: String
        get() = prefs.getString(KEY_INSTALLED_TAG, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_INSTALLED_TAG, value).apply()
        }

    /** 上次检查更新的时间戳（毫秒），用于"每日一次"节流。 */
    var lastCheckTime: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECK, value).apply()
        }

    /** 自动检查更新（默认开；检查本身只调 GitHub API，不下载）。 */
    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()
        }

    /** 仅 Wi-Fi 下载更新（默认开；2.9MB 虽小，仍尊重用户流量）。 */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    fun hasInstalled(): Boolean = installedTag.isNotBlank()

    private companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_INSTALLED_TAG = "installed_tag"
        const val KEY_LAST_CHECK = "last_check_time"
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}

/** yt-dlp 更新通道。 */
enum class YtDlpChannel(
    val displayName: String,
    /** GitHub releases API。 */
    val apiUrl: String,
) {
    STABLE(
        "稳定版",
        "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
    ),
    NIGHTLY(
        "每日构建版",
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest",
    ),
}
