package com.zola.aivideo.data.tts.engine

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.zola.aivideo.data.tts.SynthesizedSpeech
import com.zola.aivideo.data.tts.TtsEngine
import com.zola.aivideo.data.tts.TtsEngineId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * sherpa-onnx 端侧 Kokoro TTS 引擎封装（M4，Apache-2.0）。
 *
 * API 依据：AAR（sherpa-onnx-1.13.8）内 classes.jar 经 javap 核实的
 * `com.k2fsa.sherpa.onnx` 类签名（OfflineTtsKokoroModelConfig 的
 * model/voices/tokens/dataDir/lexicon/lang/dictDir/lengthScale 字段；
 * OfflineTts.generate(text, sid, speed)）。
 *
 * 模型：kokoro-int8-multi-lang-v1_1（int8，103 音色，中英双语，原生 24kHz），
 * 文件布局见 [TtsModelCatalog]。注意 dataDir 指向 espeak-ng-data（音素器数据，
 * GPL-3.0），合规结论见 docs/research/aivideo/第三方组件许可证.md。
 *
 * 约定：
 * - 同一时间只允许一个合成任务（方法级 synchronized + 调用方串行）。
 * - 单段文本过长时调用方负责截断；此处对超长文本做防御性截断（按字符）。
 */
@Singleton
class OfflineKokoroTtsEngine @Inject constructor() : TtsEngine {

    override val engineId: TtsEngineId = TtsEngineId.LOCAL

    companion object {
        /** Kokoro 原生采样率（Hz），官方文档确认 24000。 */
        const val SAMPLE_RATE = 24000

        /** 单段文本防御性截断长度（字符）：Kokoro 对超长输入会变慢/截断。 */
        const val MAX_TEXT_CHARS = 500
    }

    @Volatile
    private var tts: OfflineTts? = null

    override suspend fun isAvailable(): Boolean = tts != null

    /**
     * 初始化模型。重复初始化直接返回。
     *
     * @param modelDir 模型目录（含 model.int8.onnx / voices.bin / tokens.txt /
     *   espeak-ng-data/ / lexicon-*.txt / dict/ / *-zh.fst）
     * @throws IllegalStateException 模型文件缺失时抛出
     */
    @Synchronized
    fun init(modelDir: File) {
        if (tts != null) return
        val modelFile = File(modelDir, "model.int8.onnx")
        val voicesFile = File(modelDir, "voices.bin")
        val tokensFile = File(modelDir, "tokens.txt")
        val dataDirFile = File(modelDir, "espeak-ng-data")
        val missing = listOf(modelFile, voicesFile, tokensFile)
            .filterNot { it.exists() && it.length() > 0 }
        require(missing.isEmpty()) {
            "Kokoro 模型文件缺失：${missing.joinToString { it.name }}，请重新下载"
        }
        require(dataDirFile.exists() && dataDirFile.isDirectory) {
            "espeak-ng-data 目录缺失，Kokoro 音素器无法工作，请重新下载模型"
        }

        val kokoroConfig = OfflineTtsKokoroModelConfig().apply {
            model = modelFile.absolutePath
            voices = voicesFile.absolutePath
            tokens = tokensFile.absolutePath
            dataDir = dataDirFile.absolutePath
            // 美音 + 中文词典（与官方 kokoro 文档示例一致）
            val usEn = File(modelDir, "lexicon-us-en.txt")
            val zh = File(modelDir, "lexicon-zh.txt")
            val lexicons = listOf(usEn, zh).filter { it.exists() }.map { it.absolutePath }
            if (lexicons.isNotEmpty()) lexicon = lexicons.joinToString(",")
            val dict = File(modelDir, "dict")
            if (dict.exists() && dict.isDirectory) dictDir = dict.absolutePath
        }
        val modelConfig = OfflineTtsModelConfig().apply {
            kokoro = kokoroConfig
            numThreads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            debug = false
            provider = "cpu"
        }
        val config = OfflineTtsConfig().apply {
            model = modelConfig
            // 中文数字/日期/电话规范化（bundle 内 *-zh.fst，存在才传）
            val fsts = listOf("date-zh.fst", "number-zh.fst", "phone-zh.fst")
                .map { File(modelDir, it) }
                .filter { it.exists() }
                .map { it.absolutePath }
            if (fsts.isNotEmpty()) ruleFsts = fsts.joinToString(",")
            maxNumSentences = 20
        }
        // AssetManager 传 null：模型走文件路径，不走 assets（与 ASR 引擎一致）
        tts = OfflineTts(null, config)
    }

    /** 释放原生资源。 */
    @Synchronized
    fun release() {
        runCatching { tts?.release() }
        tts = null
    }

    /**
     * 合成单段文本。
     *
     * 阻塞调用，必须在后台线程执行。Kokoro 为整句一次性合成（非流式），
     * 进度由调用方按分段计数。
     *
     * @param voiceId 音色 sid 的字符串形式（如 "3"）；非法时回退默认音色
     */
    override suspend fun synthesize(
        text: String,
        voiceId: String,
        speed: Float,
    ): SynthesizedSpeech = withContext(Dispatchers.Default) {
        val engine = tts
            ?: throw IllegalStateException("TTS 引擎未初始化，请先下载 Kokoro 模型")
        val clean = text.trim().take(MAX_TEXT_CHARS)
        require(clean.isNotEmpty()) { "合成文本为空" }
        val sid = voiceId.toIntOrNull()?.takeIf { it in 0 until engine.numSpeakers() }
            ?: com.zola.aivideo.data.tts.TtsVoiceCatalog.DEFAULT_SID
        // generate 非线程安全：调用方串行（DubSynthesizer 单协程驱动）
        val audio = engine.generate(clean, sid, speed.coerceIn(0.5f, 2.0f))
        val samples = audio.samples ?: FloatArray(0)
        val sampleRate = audio.sampleRate.takeIf { it > 0 } ?: SAMPLE_RATE
        SynthesizedSpeech(samples, sampleRate)
    }
}
