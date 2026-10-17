package com.zola.aivideo.data.ytdlp

import com.google.gson.Gson
import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import com.zola.aivideo.data.ffmpeg.FFmpegResult
import com.zola.aivideo.data.ffmpeg.FFmpegService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** 单条下载任务的状态。 */
data class YtDlpDownloadTask(
    val url: String,
    val title: String? = null,
    val state: YtDlpDownloadState = YtDlpDownloadState.QUEUED,
    /** 0f~1f。 */
    val progress: Float = 0f,
    val detail: String? = null,
    /** 完成后的文件。 */
    val file: File? = null,
    /** 失败原因（用户可读）。 */
    val error: String? = null,
)

enum class YtDlpDownloadState {
    QUEUED,
    PROBING,
    DOWNLOADING,
    MERGING,
    DONE,
    FAILED,
    CANCELLED,
}

/**
 * yt-dlp 批量下载器（M6）。
 *
 * 两遍策略（规避"yt-dlp 内建合并需要真实 ffmpeg 可执行文件"的问题；
 * 工程的 ffmpeg-kit 是进程内 JNI，没有可执行文件）：
 * 1. `yt-dlp -J` 探测格式 → [selectDownloadPlan]：
 *    - 有 ≤1080p 合流 mp4 → 单文件下载
 *    - 否则下载最佳视频流 + 最佳音频流 → ffmpeg-kit 合并（`-c copy`）
 * 2. 进度解析 `[download] 12.3%`；取消时 destroy 进程
 *
 * 注意：需要解释器已内置且 yt-dlp 归档已下载，否则抛 IllegalStateException
 *（YtDlpEnvironment.processBuilder 的 check 文案可直接展示）。
 */
@Singleton
class YtDlpDownloader @Inject constructor(
    private val environment: YtDlpEnvironment,
    private val ffmpegService: FFmpegService,
    private val gson: Gson,
) {
    /**
     * 下载单个 URL（挂起；支持协程取消）。
     *
     * @param onState 状态回调（UI 刷新用）
     */
    suspend fun download(
        url: String,
        maxHeight: Int = 1080,
        onState: (YtDlpDownloadTask) -> Unit = {},
    ): YtDlpDownloadTask = withContext(Dispatchers.IO) {
        var task = YtDlpDownloadTask(url = url, state = YtDlpDownloadState.PROBING)
        onState(task)
        try {
            // 1. 探测
            coroutineContext.ensureActive()
            val info = probe(url)
            task = task.copy(title = info.title)
            onState(task)
            val plan = selectDownloadPlan(info.formats, maxHeight)
                ?: throw IllegalStateException("没有可用的下载格式")
            val safeId = sanitizeId(info.id.ifBlank { "video" })
            val dir = environment.downloadsDir()

            // 2. 下载
            task = task.copy(state = YtDlpDownloadState.DOWNLOADING)
            onState(task)
            val downloaded: File = when (plan) {
                is DownloadPlan.SingleFile -> {
                    val out = File(dir, "$safeId.%(ext)s")
                    runYtDlp(
                        listOf("-f", plan.format.formatId, "-o", out.absolutePath, url),
                    ) { p -> onState(task.copy(progress = p)) }
                    findDownloaded(dir, safeId)
                        ?: throw IllegalStateException("下载完成但找不到产物文件")
                }
                is DownloadPlan.SplitStreams -> {
                    val vOut = File(dir, "${safeId}_v.%(ext)s")
                    val aOut = File(dir, "${safeId}_a.%(ext)s")
                    runYtDlp(
                        listOf("-f", plan.video.formatId, "-o", vOut.absolutePath, url),
                    ) { p -> onState(task.copy(progress = p * 0.5f, detail = "下载视频流")) }
                    coroutineContext.ensureActive()
                    runYtDlp(
                        listOf("-f", plan.audio.formatId, "-o", aOut.absolutePath, url),
                    ) { p ->
                        onState(task.copy(progress = 0.5f + p * 0.4f, detail = "下载音频流"))
                    }
                    val vFile = findDownloaded(dir, "${safeId}_v")
                        ?: throw IllegalStateException("视频流下载完成但找不到文件")
                    val aFile = findDownloaded(dir, "${safeId}_a")
                        ?: throw IllegalStateException("音频流下载完成但找不到文件")
                    // 3. ffmpeg-kit 合并（-c copy，不重编码）
                    task = task.copy(state = YtDlpDownloadState.MERGING, progress = 0.9f)
                    onState(task)
                    val merged = File(dir, "$safeId.mp4")
                    val cmd = FFmpegCommandBuilder.buildMergeStreams(
                        vFile.absolutePath,
                        aFile.absolutePath,
                        merged.absolutePath,
                    )
                    when (val r = ffmpegService.execute(cmd)) {
                        is FFmpegResult.Success -> {
                            vFile.delete()
                            aFile.delete()
                            merged
                        }
                        is FFmpegResult.Cancelled ->
                            throw kotlinx.coroutines.CancellationException("合并已取消")
                        is FFmpegResult.Failure ->
                            throw IllegalStateException(
                                "音视频合并失败（exit=${r.returnCode}）",
                            )
                    }
                }
            }
            task = task.copy(
                state = YtDlpDownloadState.DONE,
                progress = 1f,
                file = downloaded,
                detail = null,
            )
            onState(task)
            task
        } catch (e: kotlinx.coroutines.CancellationException) {
            task = task.copy(state = YtDlpDownloadState.CANCELLED, detail = null)
            onState(task)
            task
        } catch (e: Exception) {
            task = task.copy(
                state = YtDlpDownloadState.FAILED,
                error = e.message ?: e.javaClass.simpleName,
                detail = null,
            )
            onState(task)
            task
        }
    }

    /** 探测：`yt-dlp -J --no-playlist <url>` 取标题/id/格式列表。 */
    private fun probe(url: String): YtDlpInfoJson {
        val pb = environment.processBuilder(
            listOf("-J", "--no-playlist", "--no-warnings", url),
        )
        val proc = pb.start()
        // 必须消费 stderr，否则缓冲区满会卡死
        val errGobbler = Thread { proc.errorStream.bufferedReader().readText() }
        errGobbler.isDaemon = true
        errGobbler.start()
        val out = proc.inputStream.bufferedReader().readText()
        val finished = proc.waitFor(PROBE_TIMEOUT_SEC, TimeUnit.SECONDS)
        errGobbler.join(2000)
        if (!finished) {
            proc.destroyForcibly()
            throw IllegalStateException("探测超时（${PROBE_TIMEOUT_SEC}s），请检查链接或网络")
        }
        if (proc.exitValue() != 0) {
            throw IllegalStateException("无法解析该链接（yt-dlp 退出码 ${proc.exitValue()}）")
        }
        return runCatching { gson.fromJson(out, YtDlpInfoJson::class.java) }.getOrNull()
            ?.takeIf { it.formats.isNotEmpty() }
            ?: throw IllegalStateException("该链接没有可下载的格式")
    }

    /**
     * 执行一次 yt-dlp 下载（阻塞到进程结束；调用方在 IO 调度）。
     *
     * 进度从 stdout 的 `[download] 12.3%` 解析；取消由外层协程中断触发
     * （上层 download() 的 ensureActive + 这里的进程 destroy）。
     */
    private suspend fun runYtDlp(args: List<String>, onProgress: (Float) -> Unit) {
        val proc = environment.processBuilder(args).start()
        // stderr 消费线程（yt-dlp 进度默认打 stderr）
        val errLines = mutableListOf<String>()
        val gobbler = Thread {
            proc.errorStream.bufferedReader().forEachLine { line ->
                synchronized(errLines) {
                    if (errLines.size < 50) errLines.add(line)
                }
                parseProgress(line)?.let(onProgress)
            }
        }
        gobbler.isDaemon = true
        gobbler.start()
        // stdout 也消费（避免缓冲卡死）
        val outGobbler = Thread { proc.inputStream.bufferedReader().readText() }
        outGobbler.isDaemon = true
        outGobbler.start()
        try {
            while (proc.isAlive) {
                // 协作式取消：外层协程取消时中断等待并杀进程
                if (!coroutineContext.isActive) {
                    proc.destroyForcibly()
                    throw kotlinx.coroutines.CancellationException("下载已取消")
                }
                proc.waitFor(200, TimeUnit.MILLISECONDS)
            }
        } finally {
            gobbler.join(3000)
            outGobbler.join(3000)
        }
        if (proc.exitValue() != 0) {
            val tail = synchronized(errLines) { errLines.takeLast(3).joinToString("\n") }
            throw IllegalStateException(
                "yt-dlp 下载失败（退出码 ${proc.exitValue()}）" +
                    (tail.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
            )
        }
    }

    private fun findDownloaded(dir: File, prefix: String): File? =
        dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }
            ?.maxByOrNull { it.lastModified() }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "video" }

    companion object {
        /** 探测超时（秒）。 */
        const val PROBE_TIMEOUT_SEC = 60L

        /**
         * 解析 yt-dlp 进度行 `[download]  12.3% of 10.00MiB ...`。
         * 纯函数，可单元测试。
         */
        fun parseProgress(line: String): Float? {
            val m = PROGRESS_REGEX.find(line) ?: return null
            return m.groupValues[1].toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
        }

        private val PROGRESS_REGEX = Regex("""\[download\]\s+([0-9.]+)%""")
    }
}
