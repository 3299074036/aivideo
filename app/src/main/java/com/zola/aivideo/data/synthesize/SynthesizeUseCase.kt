package com.zola.aivideo.data.synthesize

import android.content.Context
import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import com.zola.aivideo.data.ffmpeg.FFmpegResult
import com.zola.aivideo.data.ffmpeg.FFmpegService
import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.data.media.MediaMetadataReader
import com.zola.aivideo.data.subtitle.SrtWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** 合成结果。 */
data class SynthesizeResult(
    /** 任务目录内的 output.mp4。 */
    val outputFile: File,
    /** MediaStore 导出 Uri 字符串（导出失败为 null，任务仍算成功）。 */
    val exportedUri: String?,
    /** 实际使用的视频编码器（fallback 链路可追溯）。 */
    val videoCodecUsed: String,
)

/**
 * 最终合成用例（M5）：源视频 + 配音轨 + 字幕 → 成品 mp4 → MediaStore。
 *
 * 流程：
 * 1. 译文分段 → subs.srt（双语按翻译配置）
 * 2. 按字幕模式/编码预设拼 FFmpeg 命令
 * 3. 视频编码器 fallback 链：h264_mediacodec → libopenh264 → mpeg4
 *    （某一级失败自动降级重试；原画质+外挂字幕直接 `-c:v copy` 不走链路）
 * 4. 成功后经 MediaStore 导出到 Movies/AiVideo
 *
 * 取消：FFmpegService.execute 本就支持协程取消（cancel 会话）。
 */
@Singleton
class SynthesizeUseCase @Inject constructor(
    private val ffmpegService: FFmpegService,
    private val mediaMetadataReader: MediaMetadataReader,
    private val mediaStoreExporter: MediaStoreExporter,
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** 烧录模式下"原画质"档的高码率近似（烧录必须重编码）。 */
        const val BURN_ORIGINAL_BITRATE_KBPS = 8000

        /** 编码器 fallback 链（按优先顺序）。 */
        val CODEC_FALLBACK = listOf(
            FFmpegCommandBuilder.VideoCodec.H264_MEDIACODEC,
            FFmpegCommandBuilder.VideoCodec.LIBOPENH264,
            FFmpegCommandBuilder.VideoCodec.MPEG4,
        )
    }

    /**
     * @param onProgress 0f~1f（FFmpeg statistics 回调换算）
     * @throws IllegalStateException 合成失败（文案可直接展示，含最后一次 FFmpeg 日志摘要）
     */
    suspend fun synthesize(
        videoPath: String,
        dubTrackWav: String,
        segments: List<TranslatedSegment>,
        bilingual: Boolean,
        options: SynthesizeOptions,
        outputMp4: File,
        srtFile: File,
        displayName: String,
        onProgress: (Float) -> Unit,
    ): SynthesizeResult {
        require(File(videoPath).exists()) { "源视频不存在" }
        require(File(dubTrackWav).exists()) { "配音轨不存在，请重试配音阶段" }
        require(segments.isNotEmpty()) { "没有可合成的字幕分段" }

        // 1. 字幕文件
        srtFile.writeText(SrtWriter.toSrt(segments, bilingual))
        coroutineContext.ensureActive()

        // 2. 总时长（进度换算用）
        val totalMs = runCatching { mediaMetadataReader.read(videoPath)?.durationMs }
            .getOrNull()?.takeIf { it > 0 }

        // 3. 编码参数
        val burn = options.subtitleMode == SubtitleMode.BURN
        val (codecsToTry, bitrateKbps) = when {
            // 外挂 + 原画质：视频流 copy，不走 fallback
            !burn && options.encodePreset == EncodePreset.ORIGINAL ->
                listOf(null) to 0
            // 烧录 + 原画质：必须重编码，高码率近似
            burn && options.encodePreset == EncodePreset.ORIGINAL ->
                CODEC_FALLBACK to BURN_ORIGINAL_BITRATE_KBPS
            // 压缩版：按档位码率走 fallback
            else -> CODEC_FALLBACK to options.bitrateTier.videoKbps
        }

        // 4. 执行（fallback 链）
        var lastError = ""
        for (codec in codecsToTry) {
            coroutineContext.ensureActive()
            val videoCodecArg = codec?.ffmpegName ?: "copy"
            val command = FFmpegCommandBuilder.buildSynthesize(
                videoPath = videoPath,
                dubAudioPath = dubTrackWav,
                subtitlePath = srtFile.absolutePath,
                outputPath = outputMp4.absolutePath,
                subtitleMode = if (burn) "burn" else "external",
                videoCodec = videoCodecArg,
                videoBitrateKbps = bitrateKbps,
            )
            when (val r = ffmpegService.execute(command, totalMs) { p ->
                if (p != null) onProgress(p)
            }) {
                is FFmpegResult.Success -> {
                    val exported = mediaStoreExporter.exportVideo(outputMp4, displayName)
                    return SynthesizeResult(outputMp4, exported, videoCodecArg)
                }
                is FFmpegResult.Cancelled ->
                    throw kotlinx.coroutines.CancellationException("合成已取消")
                is FFmpegResult.Failure -> {
                    lastError = "编码器 $videoCodecArg 失败（exit=${r.returnCode}）"
                    // 继续降级；日志摘要保留到最终异常
                    continue
                }
            }
        }
        throw IllegalStateException("合成失败：$lastError")
    }
}
