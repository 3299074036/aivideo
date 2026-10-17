package com.zola.aivideo.data.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.zola.aivideo.domain.model.VideoMetadata
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频元信息读取。
 *
 * - 分辨率 / 时长 / MIME：MediaMetadataRetriever
 * - 音轨 / 内嵌字幕轨存在性：MediaExtractor（公开 API，精确可靠）
 *
 * 读取失败（损坏文件 / 不支持的容器）时返回 null，调用方展示"无法读取元信息"空状态，
 * 不抛异常、不崩溃。
 */
@Singleton
class MediaMetadataReader @Inject constructor() {

    fun read(filePath: String): VideoMetadata? {
        val file = File(filePath)
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val (hasAudio, hasSubtitle) = detectTracks(filePath)

            VideoMetadata(
                filePath = filePath,
                displayName = file.name,
                widthPx = width,
                heightPx = height,
                durationMs = durationMs,
                sizeBytes = file.length(),
                hasAudioTrack = hasAudio,
                hasSubtitleTrack = hasSubtitle,
                mimeType = mime,
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // 忽略 release 异常
            }
        }
    }

    /** 用 MediaExtractor 枚举轨道：mime 以 audio/ 开头为音频轨，text/ 或含 subtitle 为字幕轨。 */
    private fun detectTracks(filePath: String): Pair<Boolean, Boolean> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(filePath)
            var audio = false
            var subtitle = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("audio/") -> audio = true
                    mime.startsWith("text/") || mime.contains("subtitle", ignoreCase = true) -> subtitle = true
                }
            }
            audio to subtitle
        } catch (_: Exception) {
            false to false
        } finally {
            extractor.release()
        }
    }
}
