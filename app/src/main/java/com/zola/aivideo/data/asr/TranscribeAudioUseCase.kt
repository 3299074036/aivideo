package com.zola.aivideo.data.asr

import com.zola.aivideo.data.asr.audio.PcmDecoder
import com.zola.aivideo.data.asr.engine.SentenceSegmenter
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.asr.engine.SherpaAsrEngine
import com.zola.aivideo.domain.asr.AsrModel
import com.zola.aivideo.domain.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** 转写阶段（UI 进度展示用）。 */
sealed interface TranscribeStage {
    /** 解码中：progress 0f~1f。 */
    data class Decoding(val progress: Float) : TranscribeStage

    /** 转写中：第 index/n 块。 */
    data class Transcribing(val index: Int, val total: Int) : TranscribeStage

    /** 分句中。 */
    data object Segmenting : TranscribeStage
}

/**
 * 端侧转写用例：本地视频 → 16kHz PCM → sherpa-onnx 分块转写 → 分句。
 *
 * 流程：
 * 1. PcmDecoder 把视频音频轨解码为 `filesDir/asr_tmp/<ts>.pcm`（16kHz 单声道 16-bit）
 * 2. SherpaAsrEngine 按 60 秒块流式转写（防 OOM），块时间戳加块偏移
 * 3. whisper（有词级时间戳）→ SentenceSegmenter.segment；
 *    SenseVoice（无词级时间戳）→ SentenceSegmenter.distributeEvenly 按字数比例分配
 * 4. 成功后删除 PCM 临时文件；失败/取消也尽力清理
 *
 * 取消：调用方取消协程即可（ViewModelScope），各阶段检查 ensureActive。
 */
class TranscribeAudioUseCase @Inject constructor(
    private val pcmDecoder: PcmDecoder,
    private val engine: SherpaAsrEngine,
    private val downloadManager: ModelDownloadManager,
    private val prefs: AsrPreferences,
) {

    /**
     * 执行转写。
     *
     * @param videoPath M1 导入的本地视频绝对路径
     * @param onStage 阶段回调（主线程调用方自行切线程）
     * @return 字幕分段列表（时间递增）
     * @throws IllegalStateException 模型未下载等可预期错误（文案可直接展示）
     */
    suspend fun transcribe(
        videoPath: String,
        onStage: (TranscribeStage) -> Unit,
    ): List<TranscriptSegment> {
        val model = prefs.getSelectedModel()
        val entry = AsrModelCatalog.entryOf(model)
        if (!downloadManager.isInstalled(entry)) {
            throw IllegalStateException("语音模型尚未下载，请先在模型管理页下载")
        }
        val modelDir = downloadManager.modelDir(entry)

        val tmpDir = File(downloadManager.modelsDir().parentFile, "asr_tmp").apply { mkdirs() }
        val pcmFile = File(tmpDir, "transcribe_${System.currentTimeMillis()}.pcm")
        try {
            // 1. 解码（阶段权重 0~15%）
            onStage(TranscribeStage.Decoding(0f))
            val pcmInfo = pcmDecoder.decodeToPcmFile(videoPath, pcmFile.absolutePath) { p ->
                onStage(TranscribeStage.Decoding(p))
            }
            coroutineContext[Job]?.ensureActive()
            if (pcmInfo.totalSamples <= 0) {
                throw IllegalStateException("未能从视频中解出音频数据")
            }

            // 2. 初始化引擎 + 分块转写（15%~90%）
            engine.init(model, modelDir)
            val segments = mutableListOf<TranscriptSegment>()
            var idOffset = 0L
            engine.transcribePcmFile(pcmFile.absolutePath, pcmInfo.totalSamples) { index, total, chunk ->
                coroutineContext[Job]?.ensureActive()
                onStage(TranscribeStage.Transcribing(index + 1, total))
                val chunkStartMs = index * SherpaAsrEngine.CHUNK_SECONDS * 1000L
                // 块时长按实际采样数算（最后一块不足 60 秒）
                val chunkEndMs = minOf(
                    chunkStartMs + SherpaAsrEngine.CHUNK_SECONDS * 1000L,
                    pcmInfo.totalSamples * 1000L / SherpaAsrEngine.SAMPLE_RATE,
                )
                val seg = if (chunk.words.isNotEmpty()) {
                    SentenceSegmenter.segment(
                        chunk.words,
                        timeOffsetMs = chunkStartMs,
                        idOffset = idOffset,
                    )
                } else if (chunk.text.isNotBlank()) {
                    // 无词级时间戳（SenseVoice）：按字数比例分配本块时长
                    SentenceSegmenter.distributeEvenly(
                        chunk.text, chunkStartMs, chunkEndMs, idOffset,
                    )
                } else {
                    emptyList()
                }
                segments.addAll(seg)
                idOffset += seg.size
            }

            // 3. 收尾：按时间排序（防御），返回
            coroutineContext[Job]?.ensureActive()
            onStage(TranscribeStage.Segmenting)
            return segments.sortedBy { it.startMs }
        } catch (e: CancellationException) {
            throw e
        } finally {
            runCatching { pcmFile.delete() }
            // 引擎保留（下次转写复用），不 release
        }
    }
}

/**
 * 转写结果暂存器：转写页 → 字幕编辑页之间传递分段列表。
 *
 * M2 用内存暂存（进程被杀则丢失，属已知限制）；M5 任务中心落地后持久化到 Room。
 */
@Singleton
class TranscriptionResultHolder @Inject constructor() {
    @Volatile
    var segments: List<TranscriptSegment> = emptyList()
        private set

    @Volatile
    var videoPath: String = ""
        private set

    fun put(videoPath: String, segments: List<TranscriptSegment>) {
        this.videoPath = videoPath
        this.segments = segments
    }

    fun clear() {
        segments = emptyList()
        videoPath = ""
    }
}
