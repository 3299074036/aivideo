package com.zola.aivideo.data.stock

import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import com.zola.aivideo.data.ffmpeg.FFmpegResult
import com.zola.aivideo.data.ffmpeg.FFmpegService
import com.zola.aivideo.data.media.MediaMetadataReader
import com.zola.aivideo.data.synthesize.SynthesizeUseCase
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.data.task.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 素材片段替换用例（M6）。
 *
 * 把任务源视频的 [startSec, endSec) 片段替换为已下载的素材片段：
 * 1. 校验时间范围（不超过源视频时长）与分辨率
 * 2. FFmpeg 三段 concat（编码器走与合成相同的 fallback 链）
 * 3. 产物写 tasks/<id>/replaced.mp4；授权记录追加到 licenses.json
 *
 * 注意：这是画面替换，音频保留源视频完整音轨。
 */
@Singleton
class StockReplaceUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val taskFiles: TaskFiles,
    private val ffmpegService: FFmpegService,
    private val mediaMetadataReader: MediaMetadataReader,
) {
    /**
     * @param clip 下载好的素材文件（任务 stock/ 目录下）
     * @param licenseBase 素材授权信息（片段起止由本次替换填充）
     * @throws IllegalStateException 用户可读错误
     */
    suspend fun replace(
        taskId: Long,
        clip: StockClip,
        clipFile: File,
        startSec: Double,
        endSec: Double,
        onProgress: (Float) -> Unit = {},
    ): File {
        val task = repository.get(taskId)
            ?: throw IllegalStateException("任务不存在")
        require(clipFile.exists()) { "素材文件不存在，请重新下载" }
        require(startSec >= 0 && endSec > startSec) { "时间范围无效" }

        val meta = mediaMetadataReader.read(task.sourceVideoPath)
            ?: throw IllegalStateException("读取源视频信息失败")
        val width = meta.widthPx ?: throw IllegalStateException("源视频分辨率未知")
        val height = meta.heightPx ?: throw IllegalStateException("源视频分辨率未知")
        val durationSec = meta.durationMs / 1000.0
        require(durationSec > 0) { "源视频时长未知" }
        require(endSec <= durationSec) {
            "结束时间超出视频时长（${"%.1f".format(durationSec)}s）"
        }

        val output = taskFiles.replacedMp4(taskId)
        var lastError = ""
        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lastReported = AtomicReference(0f)
        try {
            for (codec in SynthesizeUseCase.CODEC_FALLBACK) {
                coroutineContext.ensureActive()
                val command = FFmpegCommandBuilder.buildReplaceSegment(
                    sourcePath = task.sourceVideoPath,
                    stockPath = clipFile.absolutePath,
                    outputPath = output.absolutePath,
                    startSec = startSec,
                    endSec = endSec,
                    width = width,
                    height = height,
                    videoCodec = codec.ffmpegName,
                )
                when (val r = ffmpegService.execute(command, meta.durationMs) { p ->
                    if (p != null && p - lastReported.get() >= 0.01f || p == 1f) {
                        lastReported.set(p ?: 0f)
                        progressScope.launch { onProgress(p ?: 0f) }
                    }
                }) {
                    is FFmpegResult.Success -> {
                        // 授权存档：片段替换记录
                        taskFiles.appendLicense(
                            taskId,
                            StockLicenseRecord(
                                provider = clip.provider.name.lowercase(),
                                clipId = clip.clipId,
                                clipPageUrl = clip.licensePageUrl,
                                authorName = clip.authorName,
                                authorUrl = clip.authorUrl,
                                licenseName = clip.licenseName,
                                fileName = clipFile.name,
                                replacedSegmentStartSec = startSec,
                                replacedSegmentEndSec = endSec,
                                recordedAt = System.currentTimeMillis(),
                            ),
                        )
                        return output
                    }
                    is FFmpegResult.Cancelled ->
                        throw kotlinx.coroutines.CancellationException("替换已取消")
                    is FFmpegResult.Failure -> {
                        lastError = "编码器 ${codec.ffmpegName} 失败（exit=${r.returnCode}）"
                    }
                }
            }
        } finally {
            progressScope.cancel()
        }
        throw IllegalStateException("素材替换失败：$lastError")
    }
}
