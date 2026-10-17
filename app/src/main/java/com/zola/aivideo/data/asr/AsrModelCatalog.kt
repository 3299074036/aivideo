package com.zola.aivideo.data.asr

import com.zola.aivideo.data.download.DownloadableModel
import com.zola.aivideo.data.download.ModelFile
import com.zola.aivideo.domain.asr.AsrModel

/**
 * ASR 模型目录：三档模型的元信息与下载地址。
 *
 * 模型来源：sherpa-onnx 官方维护者（csukuangfj）在 HuggingFace 发布的
 * 官方 int8 量化模型仓库（2026-10-17 逐个 curl -I 验证可达）：
 * - whisper tiny：https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny
 * - whisper small：https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small
 * - SenseVoice：https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09
 *
 * 下载地址变更时只改此处；文件体积为展示用约数，真实进度以 HTTP Content-Length 为准。
 */
object AsrModelCatalog {

    /** 一个可下载的模型条目（M4 起实现 DownloadableModel，与 TTS 模型共用下载器）。 */
    data class Entry(
        val model: AsrModel,
        val displayName: String,
        val description: String,
        /** 展示用体积文案（如 "约 117 MB"）。 */
        val sizeLabel: String,
        override val files: List<ModelFile>,
    ) : DownloadableModel {
        override val id: String get() = model.id
    }

    private const val HF = "https://huggingface.co/csukuangfj"

    val entries: List<Entry> = listOf(
        Entry(
            model = AsrModel.TINY_INT8,
            displayName = "Whisper Tiny（int8）",
            description = "默认推荐。多语言（含中文），体积最小、手机上转写最快",
            sizeLabel = "约 117 MB",
            files = listOf(
                ModelFile(
                    "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx",
                    "tiny-encoder.int8.onnx",
                ),
                ModelFile(
                    "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx",
                    "tiny-decoder.int8.onnx",
                ),
                ModelFile(
                    "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt",
                    "tiny-tokens.txt",
                ),
            ),
        ),
        Entry(
            model = AsrModel.SENSE_VOICE,
            displayName = "SenseVoice（int8）",
            description = "中日韩粤英多语言，中文场景准确率更高，自动加标点与数字规范化",
            sizeLabel = "约 244 MB",
            files = listOf(
                ModelFile(
                    "$HF/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main/model.int8.onnx",
                    "model.int8.onnx",
                ),
                ModelFile(
                    "$HF/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main/tokens.txt",
                    "tokens.txt",
                ),
            ),
        ),
        Entry(
            model = AsrModel.SMALL_INT8,
            displayName = "Whisper Small（int8）",
            description = "多语言（含中文），准确率最高；体积大、转写慢，建议插电或长视频用",
            sizeLabel = "约 375 MB",
            files = listOf(
                ModelFile(
                    "$HF/sherpa-onnx-whisper-small/resolve/main/small-encoder.int8.onnx",
                    "small-encoder.int8.onnx",
                ),
                ModelFile(
                    "$HF/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx",
                    "small-decoder.int8.onnx",
                ),
                ModelFile(
                    "$HF/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt",
                    "small-tokens.txt",
                ),
            ),
        ),
    )

    fun entryOf(model: AsrModel): Entry =
        entries.first { it.model == model }
}
