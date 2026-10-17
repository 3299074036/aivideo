package com.zola.aivideo.data.ffmpeg

import java.io.File

/**
 * FFmpeg 命令拼装：纯 Kotlin，无 Android / FFmpegKit 依赖，可 JVM 单元测试。
 *
 * 所有路径先做合法性校验（非空、非目录穿越由调用方保证，此处只做存在性断言由执行层负责）。
 * 返回值为可直接传给 FFmpegKit.executeAsync 的命令字符串。
 */
object FFmpegCommandBuilder {

    /**
     * 抽取音频为 16kHz 单声道 PCM（wav），供 ASR 使用。
     * M2 转写链路使用。
     */
    fun buildExtractPcmWav(inputPath: String, outputPath: String): String =
        "-i ${quote(inputPath)} -vn -ac 1 -ar 16000 -f wav ${quote(outputPath)}"

    /**
     * 烧录字幕（内嵌）：ass/srt 字幕烧进画面。
     * 使用 libass 滤镜（ffmpeg-kit-full LGPL 构建包含 libass）。
     */
    fun buildBurnSubtitles(inputPath: String, subtitlePath: String, outputPath: String): String =
        "-i ${quote(inputPath)} -vf ${quote("subtitles=" + escapeFilterPath(subtitlePath))} " +
            "-c:a copy ${quote(outputPath)}"

    /**
     * 外挂字幕：视频流 copy，仅混入字幕轨为 mov_text（mp4 兼容）。
     */
    fun buildMuxExternalSubtitle(inputPath: String, subtitlePath: String, outputPath: String): String =
        "-i ${quote(inputPath)} -i ${quote(subtitlePath)} -c copy -c:s mov_text ${quote(outputPath)}"

    /**
     * 替换音轨：保留原视频流，用新音频替换（-shortest 以较短者为准）。
     * M4 配音合成使用。
     */
    fun buildReplaceAudio(inputPath: String, audioPath: String, outputPath: String): String =
        "-i ${quote(inputPath)} -i ${quote(audioPath)} -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 " +
            "-shortest ${quote(outputPath)}"

    /**
     * 混音：原音频与新配音按权重混合（输出含视频的 mp4，M5 合成用）。
     *
     * M4 修正：两路输入先 aresample 到 44100 立体声（amix 要求采样率/声道一致；
     * 配音轨为 24kHz 单声道，原视频音频多为 44.1k/48k，不加会失败）。
     *
     * @param originalWeight 原音权重（0.0~1.0），配音权重为 1 - originalWeight
     */
    fun buildMixAudio(
        inputPath: String,
        dubAudioPath: String,
        outputPath: String,
        originalWeight: Float,
    ): String {
        val w = originalWeight.coerceIn(0f, 1f)
        return "-i ${quote(inputPath)} -i ${quote(dubAudioPath)} " +
            "-filter_complex ${quote("[0:a]aresample=44100,aformat=channel_layouts=stereo,volume=$w[a0];[1:a]aresample=44100,aformat=channel_layouts=stereo,volume=${1 - w}[a1];[a0][a1]amix=inputs=2:duration=first:dropout_transition=0[a]")} " +
            "-map 0:v:0 -map \"[a]\" -c:v copy -c:a aac -shortest ${quote(outputPath)}"
    }

    /**
     * 配音混音（M4）：原视频音频 + 配音轨按权重混合，输出纯音频 WAV。
     *
     * 用于 M4 试听与 M5 合成前的中间产物；最终视频混流在 M5。
     *
     * @param originalWeight 原音权重（0.0~1.0），配音权重为 1 - originalWeight
     */
    fun buildMixDubAudio(
        videoPath: String,
        dubAudioPath: String,
        outputWavPath: String,
        originalWeight: Float,
    ): String {
        val w = originalWeight.coerceIn(0f, 1f)
        return "-i ${quote(videoPath)} -i ${quote(dubAudioPath)} " +
            "-filter_complex ${quote("[0:a]aresample=44100,aformat=channel_layouts=stereo,volume=$w[a0];[1:a]aresample=44100,aformat=channel_layouts=stereo,volume=${1 - w}[a1];[a0][a1]amix=inputs=2:duration=first:dropout_transition=0[a]")} " +
            "-map \"[a]\" -c:a pcm_s16le ${quote(outputWavPath)}"
    }

    /**
     * 变速：超长配音句子的 atempo 适配（0.5~2.0 安全拼接，单级建议 0.8~1.2）。
     */
    fun buildChangeTempo(inputPath: String, outputPath: String, tempo: Float): String {
        val t = tempo.coerceIn(0.5f, 2.0f)
        return "-i ${quote(inputPath)} -filter:a ${quote("atempo=$t")} ${quote(outputPath)}"
    }

    // ---------- M5：最终合成 ----------

    /** 视频编码器：按优先顺序 fallback（调用方循环尝试）。 */
    enum class VideoCodec(val ffmpegName: String) {
        /** 硬件编码（优先）。 */
        H264_MEDIACODEC("h264_mediacodec"),

        /** OpenH264（BSD 许可，LGPL 构建自带）。 */
        LIBOPENH264("libopenh264"),

        /** FFmpeg 内置 MPEG-4（保底，体积大但一定可用）。 */
        MPEG4("mpeg4"),
    }

    /**
     * 最终合成：源视频 + 配音轨 + 字幕 → 成品 mp4（M5）。
     *
     * 矛盾处理：
     * - 外挂字幕 + 原画质：视频流 copy（`-c:v copy`），只处理音频
     * - 内嵌烧录：字幕烧进画面**必须**重编码视频，"原画质"档用 8000k 高码率近似
     * - 输出时长以视频为准（不加 -shortest，避免配音轨短时截断画面）
     *
     * @param subtitleMode "burn" 烧录 / "external" 外挂字幕轨
     * @param videoCodec "copy" 或 [VideoCodec.ffmpegName]
     * @param videoBitrateKbps 重编码时的视频码率（copy 时忽略）
     */
    fun buildSynthesize(
        videoPath: String,
        dubAudioPath: String,
        subtitlePath: String?,
        outputPath: String,
        subtitleMode: String,
        videoCodec: String,
        videoBitrateKbps: Int,
    ): String {
        val audioArgs = "-c:a aac -b:a 128k -ar 44100 -ac 2"
        return if (subtitleMode == "burn" && subtitlePath != null) {
            // 烧录：[0:v] 过 subtitles 滤镜 → 必须重编码
            val filter = "[0:v]subtitles='${escapeFilterPath(subtitlePath)}'[v]"
            "-i ${quote(videoPath)} -i ${quote(dubAudioPath)} " +
                "-filter_complex ${quote(filter)} " +
                "-map \"[v]\" -map 1:a:0 " +
                "-c:v $videoCodec -b:v ${videoBitrateKbps}k $audioArgs " +
                quote(outputPath)
        } else {
            // 外挂 / 无字幕
            val inputs = if (subtitleMode == "external" && subtitlePath != null) {
                "-i ${quote(videoPath)} -i ${quote(dubAudioPath)} -i ${quote(subtitlePath)}"
            } else {
                "-i ${quote(videoPath)} -i ${quote(dubAudioPath)}"
            }
            val maps = if (subtitleMode == "external" && subtitlePath != null) {
                "-map 0:v:0 -map 1:a:0 -map 2:s:0"
            } else {
                "-map 0:v:0 -map 1:a:0"
            }
            val videoArgs = if (videoCodec == "copy") {
                "-c:v copy"
            } else {
                "-c:v $videoCodec -b:v ${videoBitrateKbps}k"
            }
            val subArgs = if (subtitleMode == "external" && subtitlePath != null) "-c:s mov_text" else ""
            "$inputs $maps $videoArgs $audioArgs $subArgs ${quote(outputPath)}".trim()
        }
    }

    // ---------- M6：素材片段替换 ----------

    /**
     * 素材片段替换：把源视频 [startSec, endSec) 的画面替换为素材片段。
     *
     * 做法：源视频切三段（前/被替换/后），素材片段 scale+crop 到源分辨率并
     * trim 到片段时长，三段 concat；音频保留源视频完整音轨（`-map 0:a?`，
     * 无音频轨的源视频也能跑）。
     *
     * @param startSec 被替换片段起点（秒），必须 ≥ 0
     * @param endSec 被替换片段终点（秒），必须 > startSec
     * @param width 源视频宽度（素材缩放目标）
     * @param height 源视频高度（素材缩放目标）
     * @param videoCodec 视频编码器（调用方按 fallback 链重试）
     */
    fun buildReplaceSegment(
        sourcePath: String,
        stockPath: String,
        outputPath: String,
        startSec: Double,
        endSec: Double,
        width: Int,
        height: Int,
        videoCodec: String,
    ): String {
        require(startSec >= 0) { "startSec 必须 ≥ 0" }
        require(endSec > startSec) { "endSec 必须 > startSec" }
        require(width > 0 && height > 0) { "分辨率必须 > 0" }
        val dur = endSec - startSec
        val filter = "[0:v]split=2[v0a][v0b];" +
            "[v0a]trim=start=0:end=$startSec,setpts=PTS-STARTPTS[p1];" +
            "[1:v]scale=${width}:${height}:force_original_aspect_ratio=increase," +
            "crop=${width}:${height}," +
            "trim=start=0:end=$dur,setpts=PTS-STARTPTS[p2];" +
            "[v0b]trim=start=$endSec,setpts=PTS-STARTPTS[p3];" +
            "[p1][p2][p3]concat=n=3:v=1:a=0[v]"
        return "-i ${quote(sourcePath)} -i ${quote(stockPath)} " +
            "-filter_complex ${quote(filter)} " +
            "-map \"[v]\" -map 0:a? " +
            "-c:v $videoCodec -c:a aac " +
            quote(outputPath)
    }

    // ---------- M6：yt-dlp 分流下载后合并 ----------

    /**
     * 合并分流：视频流文件 + 音频流文件 → 单个 mp4（流 copy，不重编码）。
     *
     * 用于 yt-dlp 分流下载后的合并（yt-dlp 内建合并需要真实可执行的 ffmpeg，
     * 而本工程的 ffmpeg-kit 是进程内 JNI，只能走这条路）。
     */
    fun buildMergeStreams(videoPath: String, audioPath: String, outputPath: String): String =
        "-i ${quote(videoPath)} -i ${quote(audioPath)} " +
            "-map 0:v:0 -map 1:a:0 -c copy -shortest ${quote(outputPath)}"

    /** 路径加双引号（处理空格与特殊字符）。 */
    internal fun quote(path: String): String = "\"${path.replace("\"", "\\\"")}\""

    /**
     * subtitles 滤镜内的路径转义：Windows 盘符冒号与单引号需要转义；
     * Android 上主要是单引号与冒号。
     */
    internal fun escapeFilterPath(path: String): String =
        path.replace("\\", "/")
            .replace(":", "\\:")
            .replace("'", "\\'")

    /** 输出目录确保存在（幂等）。 */
    fun ensureParentDir(outputPath: String): Boolean {
        val parent = File(outputPath).parentFile ?: return false
        return if (parent.exists()) true else parent.mkdirs()
    }
}
