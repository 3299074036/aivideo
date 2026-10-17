package com.zola.aivideo.data.asr.engine

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.zola.aivideo.domain.asr.AsrModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** 单词级时间戳（秒 → 毫秒由调用方换算）。 */
data class WordTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** 一个音频块的识别结果。 */
data class ChunkTranscript(
    /** 块内相对时间起点的词级时间戳；SenseVoice 等无时间戳模型返回空列表。 */
    val words: List<WordTiming>,
    /** 整块文本（含标点）。 */
    val text: String,
)

/**
 * sherpa-onnx 端侧 ASR 引擎封装（Apache-2.0）。
 *
 * API 依据：AAR（sherpa-onnx-1.13.8）内 classes.jar 经 javap 核实的
 * `com.k2fsa.sherpa.onnx` 类签名；whisper 空 language 触发自动语种检测的结论
 * 来自上游 `offline-whisper-greedy-search-decoder.cc` 源码
 * （`if (!config_.language.empty()) … else DetectLanguage(…)`）。
 *
 * 约定：
 * - 输入一律 16kHz 单声道 float PCM（由 PcmDecoder 保证）。
 * - 长音频按 [CHUNK_SECONDS] 秒分块，块内时间戳加块偏移即得全局时间戳，
 *   避免一次性喂入大数组导致 OOM。
 * - 同一时间只允许一个转写任务（方法级 synchronized + 调用方串行）。
 */
@Singleton
class SherpaAsrEngine @Inject constructor() {

    companion object {
        /** 转写分块长度（秒）：whisper 内部按 30 秒窗口处理，60 秒块是显存/内存与精度的折中。 */
        const val CHUNK_SECONDS = 60
        const val SAMPLE_RATE = 16000
    }

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var activeModel: AsrModel? = null

    /** 是否已就绪（模型已加载）。 */
    fun isReady(): Boolean = recognizer != null

    /**
     * 初始化（或切换）模型。重复初始化同一模型直接返回。
     *
     * @param model 模型档位
     * @param modelDir 该模型的文件目录（含全部模型文件）
     * @throws IllegalStateException 模型文件缺失时抛出
     */
    @Synchronized
    fun init(model: AsrModel, modelDir: File) {
        if (recognizer != null && activeModel == model) return
        release()

        val numThreads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        val modelConfig = OfflineModelConfig().apply {
            when (model) {
                AsrModel.TINY_INT8, AsrModel.SMALL_INT8 -> {
                    val prefix = if (model == AsrModel.TINY_INT8) "tiny" else "small"
                    val encoder = File(modelDir, "$prefix-encoder.int8.onnx")
                    val decoder = File(modelDir, "$prefix-decoder.int8.onnx")
                    val tokens = File(modelDir, "$prefix-tokens.txt")
                    requireFiles(encoder, decoder, tokens)
                    whisper = OfflineWhisperModelConfig().apply {
                        this.encoder = encoder.absolutePath
                        this.decoder = decoder.absolutePath
                        // 空字符串 → 上游自动语种检测（源码已核实），不写死 "zh"
                        language = ""
                        task = "transcribe"
                        tailPaddings = 1000
                        enableTokenTimestamps = true
                    }
                    this.tokens = tokens.absolutePath
                    modelType = "whisper"
                }
                AsrModel.SENSE_VOICE -> {
                    val modelFile = File(modelDir, "model.int8.onnx")
                    val tokens = File(modelDir, "tokens.txt")
                    requireFiles(modelFile, tokens)
                    senseVoice = OfflineSenseVoiceModelConfig().apply {
                        this.model = modelFile.absolutePath
                        // SenseVoice 官方默认 "auto"：自动判断语种
                        language = "auto"
                        // ITN：数字/符号规范化（如 "一百二十三" → "123"），中文字幕更友好
                        useInverseTextNormalization = true
                    }
                    this.tokens = tokens.absolutePath
                }
            }
            this.numThreads = numThreads
            provider = "cpu"
        }

        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig(SAMPLE_RATE, 80, 0.0f)
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
        }
        // AssetManager 传 null：模型走文件路径，不走 assets
        recognizer = OfflineRecognizer(null, config)
        activeModel = model
    }

    private fun requireFiles(vararg files: File) {
        val missing = files.filterNot { it.exists() && it.length() > 0 }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("模型文件缺失：${missing.joinToString { it.name }}，请重新下载")
        }
    }

    /** 释放原生资源。 */
    @Synchronized
    fun release() {
        runCatching { recognizer?.release() }
        recognizer = null
        activeModel = null
    }

    /**
     * 转写一个 PCM 块（16kHz 单声道 float）。
     *
     * 阻塞调用，必须在后台线程执行。返回块内相对时间的词级时间戳。
     */
    suspend fun transcribeChunk(samples: FloatArray): ChunkTranscript =
        withContext(Dispatchers.Default) {
            val rec = recognizer
                ?: throw IllegalStateException("识别引擎未初始化，请先下载并选择模型")
            // recognizer 非线程安全：方法级串行由调用方保证（use case 单协程驱动）
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)
                val result = rec.getResult(stream)
                ChunkTranscript(
                    words = pairWords(result.tokens, result.timestamps),
                    text = result.text?.trim().orEmpty(),
                )
            } finally {
                runCatching { stream.release() }
            }
        }

    /**
     * 从 PCM 裸文件流式读取并转写全部块。
     *
     * @param pcmPath 16-bit 小端单声道 16kHz PCM 文件
     * @param totalSamples 总采样数
     * @param onChunk 每完成一块回调 (chunkIndex, chunkCount, 块结果)
     */
    suspend fun transcribePcmFile(
        pcmPath: String,
        totalSamples: Long,
        onChunk: suspend (index: Int, total: Int, ChunkTranscript) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val chunkSamples = CHUNK_SECONDS * SAMPLE_RATE
        val chunkCount = ((totalSamples + chunkSamples - 1) / chunkSamples).toInt().coerceAtLeast(1)
        RandomAccessFile(pcmPath, "r").use { raf ->
            val buf = ByteArray(chunkSamples * 2)
            for (index in 0 until chunkCount) {
                ensureActive()
                val read = raf.read(buf)
                if (read <= 0) break
                val shorts = ShortArray(read / 2) { i ->
                    ((buf[i * 2].toInt() and 0xFF) or (buf[i * 2 + 1].toInt() shl 8)).toShort()
                }
                val samples = FloatArray(shorts.size) { i -> shorts[i] / 32768f }
                val chunk = transcribeChunk(samples)
                onChunk(index, chunkCount, chunk)
            }
        }
    }

    /**
     * token[] 与 timestamps[]（秒）配对为词级时间戳。
     * 过滤 whisper 特殊 token（<|…|>）与空 token；防御数组长度不一致。
     */
    internal fun pairWords(tokens: Array<String>?, timestamps: FloatArray?): List<WordTiming> {
        val toks = tokens?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val times = timestamps?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val n = min(toks.size, times.size)
        val out = ArrayList<WordTiming>(n)
        for (i in 0 until n) {
            val t = toks[i].trim()
            if (t.isEmpty() || (t.startsWith("<|") && t.endsWith("|>"))) continue
            val startMs = (times[i] * 1000).toLong().coerceAtLeast(0)
            out.add(WordTiming(text = t, startMs = startMs, endMs = startMs))
        }
        // 用下一个词的开始时间作为当前词的结束时间
        return out.mapIndexed { index, w ->
            val end = out.getOrNull(index + 1)?.startMs ?: w.startMs
            w.copy(endMs = maxOf(end, w.startMs))
        }
    }
}
