package com.zola.aivideo.data.ytdlp

import com.google.gson.Gson
import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * yt-dlp 集成单元测试（M6）。
 *
 * 覆盖：更新通道解析（只选官方 zipimport 归档）、版本比较、
 * 进度行解析、下载计划选择（合流优先 / 分流+合并）、合并命令拼装。
 * 进程执行与网络走真实环境，真机验证（沙箱 x86_64 跑不了 arm 解释器）。
 */
class YtDlpUnitTest {

    private val gson = Gson()

    // ---------- 更新通道解析 ----------

    private val releaseJson = """
        {
          "tag_name": "2026.08.19",
          "assets": [
            {"name": "yt-dlp", "size": 3072469,
             "browser_download_url": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp",
             "digest": "sha256:1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"},
            {"name": "yt-dlp_linux_aarch64", "size": 40167448,
             "browser_download_url": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp_linux_aarch64",
             "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
            {"name": "SHA2-256SUMS", "size": 2024,
             "browser_download_url": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/SHA2-256SUMS",
             "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
          ]
        }
    """.trimIndent()

    @Test
    fun `只选中官方 zipimport 归档不选 glibc 二进制`() {
        val update = gson.fromJson(releaseJson, GithubRelease::class.java).toYtDlpUpdate()
        assertNotNull(update)
        assertEquals("2026.08.19", update!!.tag)
        // 必须是 "yt-dlp" 资产的 URL，而不是 yt-dlp_linux_aarch64
        assertTrue(update.downloadUrl.endsWith("/yt-dlp"))
        assertFalse(update.downloadUrl.contains("aarch64"))
        assertEquals(3072469L, update.sizeBytes)
        assertEquals(
            "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6",
            update.sha256,
        )
    }

    @Test
    fun `无 yt-dlp 资产或 digest 非法时返回 null`() {
        val noArchive = GithubRelease("2026.08.19", emptyList())
        assertNull(noArchive.toYtDlpUpdate())
        val badDigest = GithubRelease(
            "2026.08.19",
            listOf(
                GithubAsset(
                    "yt-dlp", 100, "https://example.com/yt-dlp",
                    "sha256:nothex",
                ),
            ),
        )
        assertNull(badDigest.toYtDlpUpdate())
    }

    @Test
    fun `版本比较按数字段`() {
        assertTrue(isNewerVersion("2026.08.19", "2026.08.01"))
        assertFalse(isNewerVersion("2026.08.01", "2026.08.19"))
        assertFalse(isNewerVersion("2026.08.19", "2026.08.19"))
        // nightly 长 tag
        assertTrue(isNewerVersion("2026.08.19.232710", "2026.08.19"))
        // 未安装时视为有更新
        assertTrue(isNewerVersion("2026.08.19", ""))
        assertFalse(isNewerVersion("", "2026.08.19"))
    }

    // ---------- 进度解析 ----------

    @Test
    fun `解析 download 百分比`() {
        assertEquals(0.123f, YtDlpDownloader.parseProgress("[download]  12.3% of 10.00MiB at 1.00MiB/s ETA 00:09")!!, 0.001f)
        assertEquals(1f, YtDlpDownloader.parseProgress("[download] 100% of 5.00MiB in 00:01")!!, 0.001f)
        assertNull(YtDlpDownloader.parseProgress("[info] Downloading 1 videos"))
        assertNull(YtDlpDownloader.parseProgress(""))
    }

    // ---------- 下载计划选择 ----------

    private fun fmt(
        id: String,
        ext: String = "mp4",
        vcodec: String? = "avc1",
        acodec: String? = "mp4a",
        height: Int? = 720,
    ) = YtDlpFormat(
        formatId = id, ext = ext, vcodec = vcodec, acodec = acodec,
        height = height, width = 1280, filesize = 1000L,
    )

    @Test
    fun `有合流 mp4 时选单文件`() {
        val formats = listOf(
            fmt("247", "webm", "vp9", "none", 720), // 视频流
            fmt("140", "m4a", "none", "mp4a", null), // 音频流
            fmt("22", "mp4", "avc1", "mp4a", 720), // 合流
        )
        val plan = selectDownloadPlan(formats)
        assertTrue(plan is DownloadPlan.SingleFile)
        assertEquals("22", (plan as DownloadPlan.SingleFile).format.formatId)
    }

    @Test
    fun `无合流时选分流视频加音频`() {
        val formats = listOf(
            fmt("247", "webm", "vp9", "none", 720),
            fmt("248", "webm", "vp9", "none", 1080),
            fmt("251", "webm", "none", "opus", null),
            fmt("140", "m4a", "none", "mp4a", null),
        )
        val plan = selectDownloadPlan(formats)
        assertTrue(plan is DownloadPlan.SplitStreams)
        plan as DownloadPlan.SplitStreams
        assertEquals("248", plan.video.formatId) // ≤1080 最高
        assertNotNull(plan.audio.formatId)
    }

    @Test
    fun `超过高度上限的格式被过滤`() {
        val formats = listOf(
            fmt("271", "webm", "vp9", "none", 1440),
            fmt("140", "m4a", "none", "mp4a", null),
        )
        // 1440p 视频流超限 → 无可用视频 → null
        assertNull(selectDownloadPlan(formats, maxHeight = 1080))
    }

    @Test
    fun `空格式列表返回 null`() {
        assertNull(selectDownloadPlan(emptyList()))
    }

    // ---------- 合并命令 ----------

    @Test
    fun `buildMergeStreams 流 copy 不重编码`() {
        val cmd = FFmpegCommandBuilder.buildMergeStreams("/a/v.mp4", "/a/a.m4a", "/a/out.mp4")
        assertTrue(cmd.contains("-i \"/a/v.mp4\""))
        assertTrue(cmd.contains("-i \"/a/a.m4a\""))
        assertTrue(cmd.contains("-map 0:v:0"))
        assertTrue(cmd.contains("-map 1:a:0"))
        assertTrue(cmd.contains("-c copy"))
        assertTrue(cmd.contains("\"/a/out.mp4\""))
    }
}
