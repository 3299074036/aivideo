package com.zola.aivideo.data

import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import com.zola.aivideo.data.synthesize.SynthesizeOptions
import com.zola.aivideo.data.work.SynthesizeStageRunner
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M6 合成链路测试：
 * 1. 素材片段替换命令拼装（纯 Kotlin）
 * 2. 合成源选择 resolveVideoSource
 * 3. SynthesizeOptions 新增字段的老 JSON 兼容
 */
class ReplaceSegmentCommandTest {

    @Test
    fun `buildReplaceSegment 三段 concat 结构正确`() {
        val cmd = FFmpegCommandBuilder.buildReplaceSegment(
            sourcePath = "/a/src.mp4",
            stockPath = "/a/stock.mp4",
            outputPath = "/a/out.mp4",
            startSec = 5.0,
            endSec = 15.0,
            width = 1920,
            height = 1080,
            videoCodec = "h264_mediacodec",
        )
        // 输入
        assertTrue(cmd.contains("-i \"/a/src.mp4\""))
        assertTrue(cmd.contains("-i \"/a/stock.mp4\""))
        // 三段：前段 / 素材段（scale+crop 到源分辨率，时长 10s）/ 后段
        assertTrue(cmd.contains("trim=start=0:end=5.0"))
        assertTrue(cmd.contains("scale=1920:1080:force_original_aspect_ratio=increase"))
        assertTrue(cmd.contains("crop=1920:1080"))
        assertTrue(cmd.contains("trim=start=0:end=10.0"))
        assertTrue(cmd.contains("trim=start=15.0"))
        assertTrue(cmd.contains("concat=n=3:v=1:a=0"))
        // 音频保留源音轨（可选映射，无音频轨的源也能跑）
        assertTrue(cmd.contains("-map 0:a?"))
        assertTrue(cmd.contains("-c:v h264_mediacodec"))
        assertTrue(cmd.contains("\"/a/out.mp4\""))
    }

    @Test
    fun `buildReplaceSegment 非法参数抛异常`() {
        // end <= start
        try {
            FFmpegCommandBuilder.buildReplaceSegment("/a.mp4", "/b.mp4", "/c.mp4", 10.0, 5.0, 1920, 1080, "mpeg4")
            throw AssertionError("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 预期
        }
        // start < 0
        try {
            FFmpegCommandBuilder.buildReplaceSegment("/a.mp4", "/b.mp4", "/c.mp4", -1.0, 5.0, 1920, 1080, "mpeg4")
            throw AssertionError("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 预期
        }
    }

    @Test
    fun `resolveVideoSource 选用替换版需文件存在`() {
        val dir = Files.createTempDirectory("aivideo-test").toFile()
        try {
            val replaced = File(dir, "replaced.mp4").apply { writeText("fake") }
            val src = "/orig/src.mp4"
            // 选用 + 文件存在 → 替换版
            assertEquals(
                replaced.absolutePath,
                SynthesizeStageRunner.resolveVideoSource(true, src, replaced),
            )
            // 未选用 → 源视频
            assertEquals(src, SynthesizeStageRunner.resolveVideoSource(false, src, replaced))
            // 选用但文件被删 → 回落源视频（不阻断合成）
            replaced.delete()
            assertEquals(src, SynthesizeStageRunner.resolveVideoSource(true, src, replaced))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `SynthesizeOptions 老 JSON 缺字段时回退默认`() {
        val gson = Gson()
        // M5 时代的 JSON（无 useReplacedVideo 字段）
        val oldJson = """{"subtitleMode":"BURN","encodePreset":"ORIGINAL","bitrateTier":"MEDIUM"}"""
        val options = gson.fromJson(oldJson, SynthesizeOptions::class.java)
        assertFalse(options.useReplacedVideo)
        assertEquals("BURN", options.subtitleMode.name)
        // 新 JSON 往返
        val json = gson.toJson(SynthesizeOptions(useReplacedVideo = true))
        val back = gson.fromJson(json, SynthesizeOptions::class.java)
        assertTrue(back.useReplacedVideo)
    }
}
