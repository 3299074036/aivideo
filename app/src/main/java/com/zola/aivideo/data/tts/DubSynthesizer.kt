package com.zola.aivideo.data.tts

import android.content.Context
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.ffmpeg.FFmpegCommandBuilder
import com.zola.aivideo.data.ffmpeg.FFmpegResult
import com.zola.aivideo.data.ffmpeg.FFmpegService
import com.zola.aivideo.data.tts.engine.OfflineKokoroTtsEngine
import com.zola.aivideo.data.tts.engine.OpenAiTtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 配音输入分段（由翻译结果转换而来）。 */
data class DubSegmentInput(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    /** 用于配音的文本（纯译文，不含原文）。 */
    val text: String,
) {
    val slotMs: Long get() = (endMs - startMs).coerceAtLeast(1)
}

/** 单段配音结果（含对齐动作，供 UI 展示与抽查）。 */
data class DubSegmentOutcome(
    val index: Int,
    val slotMs: Long,
    /** 合成原始时长（毫秒，对齐前）。 */
    val synthMs: Long,
    val action: DubAligner.AlignAction,
)

/** 配音产物。 */
data class DubResult(
    /** 最终配音轨 WAV（24kHz 单声道；混音模式为混合后音轨）。 */
    val trackFile: File,
    val totalMs: Long,
    val outcomes: List<DubSegmentOutcome>,
    val strategy: DubStrategy,
)

/**
 * 配音合成编排（M4）。
 *
 * 流程：逐段合成 → 对齐（补静音 / FFmpeg atempo / 截断淡出）→
 * 组装时间轴 WAV →（混音模式）FFmpeg amix 与原音混合。
 *
 * - 合成在前台协程串行执行（Kokoro generate 非线程安全），支持取消
 * - 每段产物先落盘（workDir），失败可定位到具体分段
 * - 最终视频混流是 M5 的事；M4 交付对齐后的配音轨 WAV
 */
@Singleton
class DubSynthesizer @Inject constructor(
    private val kokoroEngine: OfflineKokoroTtsEngine,
    private val cloudEngine: OpenAiTtsEngine,
    private val downloadManager: ModelDownloadManager,
    private val ffmpegService: FFmpegService,
    @ApplicationContext private val context: Context,
) {

    /**
     * 执行配音。
     *
     * @param onProgress (已完成段数, 总段数, 阶段文案)
     */
    suspend fun synthesizeDub(
        segments: List<DubSegmentInput>,
        voiceSid: Int,
        cloudVoice: String,
        speed: Float,
        engineId: TtsEngineId,
        strategy: DubStrategy,
        mixOriginalRatio: Float,
        videoPath: String?,
        onProgress: (done: Int, total: Int, phase: String) -> Unit,
    ): DubResult = withContext(Dispatchers.IO) {
        require(segments.isNotEmpty()) { "配音分段为空" }
        val engine: TtsEngine = when (engineId) {
            TtsEngineId.LOCAL -> {
                ensureKokoroReady()
                kokoroEngine
            }
            TtsEngineId.CLOUD -> {
                require(cloudEngine.isAvailable()) { "云 TTS 未配置 API Key" }
                cloudEngine
            }
        }
        val voiceId = if (engineId == TtsEngineId.LOCAL) voiceSid.toString() else cloudVoice

        val workDir = File(context.filesDir, "dub_tmp/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val clips = ArrayList<DubTrackAssembler.TimelineClip>(segments.size)
            val outcomes = ArrayList<DubSegmentOutcome>(segments.size)

            segments.forEachIndexed { i, seg ->
                ensureActive()
                onProgress(i, segments.size, "合成配音 ${i + 1}/${segments.size}")
                val speech = engine.synthesize(seg.text, voiceId, speed)
                val action = DubAligner.plan(seg.slotMs, speech.durationMs)
                val aligned: FloatArray = when (action) {
                    is DubAligner.AlignAction.PadSilence ->
                        DubAligner.padSilence(speech.samples, seg.slotMs, speech.sampleRate)
                    is DubAligner.AlignAction.SpeedUp ->
                        applyTempo(speech, action.tempo, workDir, i)
                    is DubAligner.AlignAction.Truncate ->
                        DubAligner.truncateWithFade(speech.samples, seg.slotMs, speech.sampleRate)
                }
                // 统一到 24kHz 时间轴
                val at24k = if (speech.sampleRate == DubTrackAssembler.TIMELINE_SAMPLE_RATE) {
                    aligned
                } else {
                    AudioResample.linear(aligned, speech.sampleRate, DubTrackAssembler.TIMELINE_SAMPLE_RATE)
                }
                clips.add(DubTrackAssembler.TimelineClip(seg.startMs, at24k))
                outcomes.add(DubSegmentOutcome(i, seg.slotMs, speech.durationMs, action))
            }

            ensureActive()
            onProgress(segments.size, segments.size, "对齐组装中")
            val totalMs = segments.maxOf { it.endMs }
            val dubTrack = File(workDir, "dub_track.wav")
            DubTrackAssembler.assembleToWav(clips, totalMs, dubTrack)

            val finalTrack = if (strategy == DubStrategy.MIX && videoPath != null) {
                ensureActive()
                onProgress(segments.size, segments.size, "与原音混音中")
                mixWithOriginal(videoPath, dubTrack.absolutePath, workDir, mixOriginalRatio)
            } else {
                dubTrack
            }
            DubResult(finalTrack, totalMs, outcomes, strategy)
        } catch (e: Exception) {
            // 保留 workDir 供排查（M5 任务中心会统一清理策略）
            throw e
        }
    }

    /** 确保 Kokoro 引擎就绪（模型已下载则初始化）。 */
    private suspend fun ensureKokoroReady() {
        if (kokoroEngine.isAvailable()) return
        val entry = TtsModelCatalog.entry
        require(downloadManager.isInstalled(entry)) { "Kokoro 模型尚未下载，请先下载" }
        withContext(Dispatchers.Default) {
            kokoroEngine.init(downloadManager.modelDir(entry))
        }
    }

    /**
     * 对单段音频做 atempo 变速（FFmpeg），返回变速后的 24kHz 采样。
     *
     * 经 FFmpeg 变速后解码回采样：变速保持采样率（24kHz），用 PcmDecoder
     * 解码为 16kHz 单声道再线性重采样回 24kHz。
     */
    private suspend fun applyTempo(
        speech: SynthesizedSpeech,
        tempo: Float,
        workDir: File,
        index: Int,
    ): FloatArray {
        val inWav = File(workDir, "seg_${index}_raw.wav")
        WavWriter.write(inWav, speech.samples, speech.sampleRate)
        val outWav = File(workDir, "seg_${index}_tempo.wav")
        val cmd = FFmpegCommandBuilder.buildChangeTempo(
            inWav.absolutePath, outWav.absolutePath, tempo,
        )
        when (val r = ffmpegService.execute(cmd)) {
            is FFmpegResult.Success -> Unit
            is FFmpegResult.Cancelled -> throw kotlinx.coroutines.CancellationException("变速被取消")
            is FFmpegResult.Failure ->
                throw IllegalStateException("atempo 变速失败（分段 $index）：${r.output.take(300)}")
        }
        // 解码回采样：PcmDecoder 输出 16kHz 单声道 PCM
        val pcmFile = File(workDir, "seg_${index}_tempo.pcm")
        try {
            // PcmDecoder 是 aivideo 自己的解码器（M2），此处复用解码任意音频文件
            val decoder = com.zola.aivideo.data.asr.audio.PcmDecoder()
            decoder.decodeToPcmFile(outWav.absolutePath, pcmFile.absolutePath)
            val bytes = pcmFile.readBytes()
            val shorts = ShortArray(bytes.size / 2) { i ->
                ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            val mono16k = FloatArray(shorts.size) { i -> shorts[i] / 32768f }
            return AudioResample.linear(mono16k, 16000, DubTrackAssembler.TIMELINE_SAMPLE_RATE)
        } finally {
            runCatching { pcmFile.delete() }
        }
    }

    /** 混音模式：配音轨与原视频音轨按比例混合，输出 WAV。 */
    private suspend fun mixWithOriginal(
        videoPath: String,
        dubTrackPath: String,
        workDir: File,
        originalRatio: Float,
    ): File {
        val outWav = File(workDir, "dub_mixed.wav")
        val cmd = FFmpegCommandBuilder.buildMixDubAudio(
            videoPath, dubTrackPath, outWav.absolutePath, originalRatio,
        )
        return when (val r = ffmpegService.execute(cmd)) {
            is FFmpegResult.Success -> outWav
            is FFmpegResult.Cancelled ->
                throw kotlinx.coroutines.CancellationException("混音被取消")
            is FFmpegResult.Failure ->
                throw IllegalStateException("混音失败：${r.output.take(300)}")
        }
    }
}
