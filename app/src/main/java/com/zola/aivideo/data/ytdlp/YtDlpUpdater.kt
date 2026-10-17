package com.zola.aivideo.data.ytdlp

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * yt-dlp 独立更新通道（M6）。
 *
 * - 版本真相来源：GitHub releases API 的 `tag_name`（stable / nightly 双通道）
 * - 更新包：release assets 中 `name` 恰好为 `"yt-dlp"` 的官方 zipimport 归档
 *   （约 2.9MB；`yt-dlp_linux_aarch64` 等 glibc 二进制绝不能选）
 * - 校验：SHA-256（对照 API 返回的 digest；官方每版另附 SHA2-256SUMS，
 *   设备端无 GPG 工具链，SHA-256 + HTTPS 是务实方案）
 * - 原子替换：下载到临时文件 → 校验通过 → rename；失败保留旧版（回滚）
 * - 节流：GitHub API 未认证限流 60 次/小时/IP，自动检查每日一次 + 手动触发
 */
@Singleton
class YtDlpUpdater @Inject constructor(
    @Named("ytdlp") private val client: OkHttpClient,
    private val gson: Gson,
    private val prefs: YtDlpPreferences,
    private val environment: YtDlpEnvironment,
) {
    /**
     * 检查更新：有新版返回 [YtDlpUpdate]，已是最新返回 null。
     *
     * @param force 忽略"每日一次"节流（用户手动触发）
     */
    suspend fun checkForUpdate(force: Boolean = false): Result<YtDlpUpdate?> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if (!force && now - prefs.lastCheckTime < CHECK_INTERVAL_MS) {
                    return@withContext Result.success(null)
                }
                val request = Request.Builder()
                    .url(prefs.channel.apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("检查更新失败（HTTP ${resp.code}）"),
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    val release = gson.fromJson(body, GithubRelease::class.java)
                    val update = release.toYtDlpUpdate()
                        ?: return@withContext Result.failure(
                            IOException("release 解析失败（未找到 yt-dlp 归档资产）"),
                        )
                    prefs.lastCheckTime = now
                    if (isNewerVersion(update.tag, prefs.installedTag)) {
                        Result.success(update)
                    } else {
                        Result.success(null)
                    }
                }
            } catch (e: Exception) {
                Result.failure(IOException("检查更新失败：${e.message ?: e.javaClass.simpleName}"))
            }
        }

    /**
     * 下载并安装更新：流式下载 → SHA-256 校验 → 原子替换。
     *
     * 校验失败/下载中断时旧归档原样保留（回滚）。
     */
    suspend fun downloadUpdate(
        update: YtDlpUpdate,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tmp = File(environment.workDir(), "yt-dlp.tmp")
            val request = Request.Builder().url(update.downloadUrl).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("下载失败（HTTP ${resp.code}）"),
                    )
                }
                val body = resp.body
                    ?: return@withContext Result.failure(IOException("下载内容为空"))
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            written += n
                            if (total > 0) {
                                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (written == 0L) {
                    tmp.delete()
                    return@withContext Result.failure(IOException("下载内容为空"))
                }
                // SHA-256 校验
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != update.sha256) {
                    tmp.delete()
                    return@withContext Result.failure(
                        IOException("校验失败（SHA-256 不匹配），已保留旧版本"),
                    )
                }
                // 原子替换
                val target = environment.archiveFile()
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@withContext Result.failure(IOException("安装失败（文件替换失败）"))
                }
                prefs.installedTag = update.tag
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(IOException("下载失败：${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * 本地二次核验：跑一次 `yt-dlp --version`（需要解释器已内置）。
     * 失败返回 null（不阻断更新流程，只做参考）。
     */
    suspend fun localVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val pb = environment.processBuilder(listOf("--version"))
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private companion object {
        /** 自动检查节流：24 小时。 */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
