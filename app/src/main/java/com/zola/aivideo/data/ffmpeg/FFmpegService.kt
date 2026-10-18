package com.zola.aivideo.data.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * FFmpeg 执行服务：FFmpegKit（ffmpegkit-maintained 社区维护分支，LGPL-3.0 构建）封装。
 *
 * - 命令拼装一律走 [FFmpegCommandBuilder]（纯 Kotlin，可单元测试）
 * - 本类只负责执行、进度回调、取消
 * - 严禁引入 `-gpl` 变体依赖（见 libs.versions.toml 注释与许可证文档）
 */
@Singleton
class FFmpegService @Inject constructor() {

    /**
     * FFmpeg 版本与加载错误（诊断用）：成功返回版本，失败第二项为原始错误信息。
     *
     * 注意：必须 catch Throwable——native 库加载失败时抛的是 Error
     *（如 UnsatisfiedLinkError；FFmpegKit 还会把它再包一层 Error 重抛），
     *只抓 Exception 会直接崩掉调用方页面（2026-10-17 模型管理页闪退的根因）。
     */
    fun getVersionInfo(): Pair<String?, String?> = try {
        FFmpegKitConfig.getFFmpegVersion() to null
    } catch (t: Throwable) {
        null to (t.message ?: t.toString()).take(300)
    }

    /** FFmpeg 版本字符串（如 "n8.1.2"），用于诊断页与问题排查；取不到返回 null。 */
    fun getVersion(): String? = getVersionInfo().first

    /** 当前是否有正在执行的会话。 */
    fun hasActiveSession(): Boolean = activeSessionId.get() != 0L

    private val activeSessionId = AtomicLong(0L)

    /**
     * 异步执行 FFmpeg 命令。
     *
     * @param command FFmpegKit 命令字符串（由 [FFmpegCommandBuilder] 生成）
     * @param onProgress 进度回调（0f~1f，基于已处理时长 / 总时长；总时长未知时回调 null）
     * @param totalDurationMs 媒体总时长（毫秒），用于计算进度；未知传 null
     * @return [FFmpegResult]
     */
    suspend fun execute(
        command: String,
        totalDurationMs: Long? = null,
        onProgress: ((Float?) -> Unit)? = null,
    ): FFmpegResult = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeAsync(
            command,
            { completed ->
                activeSessionId.set(0L)
                val rc = completed.returnCode
                val result = if (ReturnCode.isSuccess(rc)) {
                    FFmpegResult.Success(completed.output.orEmpty())
                } else if (ReturnCode.isCancel(rc)) {
                    FFmpegResult.Cancelled
                } else {
                    FFmpegResult.Failure(
                        returnCode = rc?.value ?: -1,
                        output = completed.output.orEmpty(),
                    )
                }
                if (cont.isActive) cont.resume(result)
            },
            { log -> /* 按需接入诊断日志（M5） */ },
            { statistics: Statistics ->
                if (totalDurationMs != null && totalDurationMs > 0) {
                    val progress = (statistics.time.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    onProgress?.invoke(progress)
                } else {
                    onProgress?.invoke(null)
                }
            },
        )
        activeSessionId.set(session.sessionId)

        cont.invokeOnCancellation {
            runCatching { FFmpegKit.cancel(session.sessionId) }
        }
    }

    /** 取消当前所有 FFmpeg 会话。 */
    fun cancelAll() {
        runCatching { FFmpegKit.cancel() }
        activeSessionId.set(0L)
    }
}

/** FFmpeg 执行结果。 */
sealed interface FFmpegResult {
    /** 成功：output 为完整执行日志。 */
    data class Success(val output: String) : FFmpegResult

    /** 被取消。 */
    data object Cancelled : FFmpegResult

    /** 失败：returnCode 为 FFmpeg 退出码，output 为执行日志（排查用）。 */
    data class Failure(val returnCode: Int, val output: String) : FFmpegResult
}
