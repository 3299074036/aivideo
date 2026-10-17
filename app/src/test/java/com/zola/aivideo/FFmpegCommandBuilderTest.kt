package com.zola.aivideo

import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * FFmpeg 命令拼装单元测试：纯 JVM，不依赖 Android 框架与原生 .so。
 */
class FFmpegCommandBuilderTest {

    @Test
    fun `extract pcm wav 命令包含 16k 单声道参数`() {
        val cmd = FFmpegCommandBuilder.buildExtractPcmWav("/sdcard/in.mp4", "/sdcard/out.wav")
        assertTrue(cmd.contains("-ac 1"))
        assertTrue(cmd.contains("-ar 16000"))
        assertTrue(cmd.contains("-vn"))
        assertTrue(cmd.contains("-f wav"))
    }

    @Test
    fun `路径含空格时被双引号包裹`() {
        val cmd = FFmpegCommandBuilder.buildExtractPcmWav("/sdcard/my video.mp4", "/sdcard/out.wav")
        assertTrue(cmd.contains("\"/sdcard/my video.mp4\""))
    }

    @Test
    fun `烧录字幕使用 subtitles 滤镜且音频 copy`() {
        val cmd = FFmpegCommandBuilder.buildBurnSubtitles("in.mp4", "sub.srt", "out.mp4")
        assertTrue(cmd.contains("subtitles="))
        assertTrue(cmd.contains("-c:a copy"))
    }

    @Test
    fun `外挂字幕混入 mov_text 字幕轨`() {
        val cmd = FFmpegCommandBuilder.buildMuxExternalSubtitle("in.mp4", "sub.srt", "out.mp4")
        assertTrue(cmd.contains("-c:s mov_text"))
        assertTrue(cmd.contains("-c copy"))
    }

    @Test
    fun `替换音轨映射视频流与新音频流`() {
        val cmd = FFmpegCommandBuilder.buildReplaceAudio("in.mp4", "dub.aac", "out.mp4")
        assertTrue(cmd.contains("-map 0:v:0"))
        assertTrue(cmd.contains("-map 1:a:0"))
        assertTrue(cmd.contains("-shortest"))
    }

    @Test
    fun `混音权重钳制在 0 到 1 之间`() {
        val cmd = FFmpegCommandBuilder.buildMixAudio("in.mp4", "dub.aac", "out.mp4", 1.5f)
        assertTrue(cmd.contains("volume=1.0"))
        assertTrue(cmd.contains("volume=0.0"))
        val cmd2 = FFmpegCommandBuilder.buildMixAudio("in.mp4", "dub.aac", "out.mp4", -0.5f)
        assertTrue(cmd2.contains("volume=0.0"))
    }

    @Test
    fun `混音命令先重采样保证 amix 输入一致`() {
        // M4 修正：配音轨 24kHz 单声道 vs 原音 44.1k/48k，不加 aresample 会失败
        val cmd = FFmpegCommandBuilder.buildMixAudio("in.mp4", "dub.wav", "out.mp4", 0.3f)
        assertTrue(cmd.contains("aresample=44100"))
        assertTrue(cmd.contains("aformat=channel_layouts=stereo"))
        assertTrue(cmd.contains("amix=inputs=2"))
    }

    @Test
    fun `配音混音命令输出纯音频 wav`() {
        val cmd = FFmpegCommandBuilder.buildMixDubAudio("in.mp4", "dub.wav", "out.wav", 0.3f)
        assertTrue(cmd.contains("aresample=44100"))
        assertTrue(cmd.contains("amix=inputs=2"))
        assertTrue(cmd.contains("-map \"[a]\""))
        assertTrue(cmd.contains("pcm_s16le"))
        assertTrue(cmd.contains("\"out.wav\""))
        // 权重钳制
        val cmd2 = FFmpegCommandBuilder.buildMixDubAudio("in.mp4", "dub.wav", "out.wav", 1.5f)
        assertTrue(cmd2.contains("volume=1.0"))
        assertTrue(cmd2.contains("volume=0.0"))
    }

    @Test
    fun `变速钳制在 0点5 到 2点0 之间`() {
        val cmd = FFmpegCommandBuilder.buildChangeTempo("in.aac", "out.aac", 3.0f)
        assertTrue(cmd.contains("atempo=2.0"))
        val cmd2 = FFmpegCommandBuilder.buildChangeTempo("in.aac", "out.aac", 0.1f)
        assertTrue(cmd2.contains("atempo=0.5"))
    }

    @Test
    fun `滤镜路径转义冒号与单引号`() {
        val escaped = FFmpegCommandBuilder.escapeFilterPath("/sdcard/a'b:c.srt")
        assertEquals("/sdcard/a\\'b\\:c.srt", escaped)
    }

    @Test
    fun `ensureParentDir 创建不存在的父目录`() {
        val tmp = Files.createTempDirectory("ffmpeg-test").toFile()
        val target = File(tmp, "nested/deep/out.mp4").absolutePath
        assertTrue(FFmpegCommandBuilder.ensureParentDir(target))
        assertTrue(File(tmp, "nested/deep").isDirectory)
        tmp.deleteRecursively()
    }
}
