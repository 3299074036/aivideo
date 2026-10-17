package com.zola.aivideo.data

import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合成命令拼装单元测试（M5）：纯 JVM，验证字幕模式 × 编码预设矩阵。
 */
class SynthesizeCommandTest {

    @Test
    fun `外挂加原画质_视频流 copy_字幕走 mov_text`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = "/v/subs.srt",
            outputPath = "/v/out.mp4",
            subtitleMode = "external",
            videoCodec = "copy",
            videoBitrateKbps = 0,
        )
        assertTrue(cmd.contains("-c:v copy"))
        assertFalse(cmd.contains("-b:v"))
        assertTrue(cmd.contains("-c:s mov_text"))
        assertTrue(cmd.contains("-map 2:s:0"))
        assertTrue(cmd.contains("-c:a aac"))
        // 输出时长以视频为准：不加 -shortest
        assertFalse(cmd.contains("-shortest"))
    }

    @Test
    fun `烧录模式_字幕过 subtitles 滤镜_必须重编码`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = "/v/subs.srt",
            outputPath = "/v/out.mp4",
            subtitleMode = "burn",
            videoCodec = "h264_mediacodec",
            videoBitrateKbps = 8000,
        )
        assertTrue(cmd.contains("subtitles="))
        assertTrue(cmd.contains("-filter_complex"))
        assertTrue(cmd.contains("-c:v h264_mediacodec"))
        assertTrue(cmd.contains("-b:v 8000k"))
        // 烧录不走字幕轨
        assertFalse(cmd.contains("-c:s mov_text"))
    }

    @Test
    fun `压缩版走 fallback 编码器_libopenh264`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = "/v/subs.srt",
            outputPath = "/v/out.mp4",
            subtitleMode = "external",
            videoCodec = "libopenh264",
            videoBitrateKbps = 3000,
        )
        assertTrue(cmd.contains("-c:v libopenh264"))
        assertTrue(cmd.contains("-b:v 3000k"))
        assertTrue(cmd.contains("-c:s mov_text"))
    }

    @Test
    fun `压缩版走 fallback 编码器_mpeg4`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = "/v/subs.srt",
            outputPath = "/v/out.mp4",
            subtitleMode = "burn",
            videoCodec = "mpeg4",
            videoBitrateKbps = 1500,
        )
        assertTrue(cmd.contains("-c:v mpeg4"))
        assertTrue(cmd.contains("-b:v 1500k"))
    }

    @Test
    fun `无字幕时不拼字幕输入与映射`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = null,
            outputPath = "/v/out.mp4",
            subtitleMode = "external",
            videoCodec = "copy",
            videoBitrateKbps = 0,
        )
        assertFalse(cmd.contains("subs.srt"))
        assertFalse(cmd.contains("-map 2:s:0"))
        assertFalse(cmd.contains("-c:s mov_text"))
        // 配音轨映射仍在
        assertTrue(cmd.contains("-map 1:a:0"))
    }

    @Test
    fun `路径含空格与冒号被正确引用转义`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/my video.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = "/v/sub title.srt",
            outputPath = "/v/out.mp4",
            subtitleMode = "burn",
            videoCodec = "h264_mediacodec",
            videoBitrateKbps = 6000,
        )
        assertTrue(cmd.contains("\"/v/my video.mp4\""))
        // 滤镜路径中的空格保留（单引号包裹），冒号会被转义
        assertTrue(cmd.contains("subtitles="))
    }

    @Test
    fun `音频统一转 AAC_128k_44k1_立体声`() {
        val cmd = FFmpegCommandBuilder.buildSynthesize(
            videoPath = "/v/in.mp4",
            dubAudioPath = "/v/dub.wav",
            subtitlePath = null,
            outputPath = "/v/out.mp4",
            subtitleMode = "external",
            videoCodec = "copy",
            videoBitrateKbps = 0,
        )
        assertTrue(cmd.contains("-c:a aac"))
        assertTrue(cmd.contains("-b:a 128k"))
        assertTrue(cmd.contains("-ar 44100"))
        assertTrue(cmd.contains("-ac 2"))
    }
}
