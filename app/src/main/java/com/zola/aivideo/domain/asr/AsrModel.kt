package com.zola.aivideo.domain.asr

/**
 * 端侧 ASR 模型三档（M2）。
 *
 * - TINY_INT8：默认推荐，whisper tiny int8，多语言（含中文），体积最小、速度最快
 * - SENSE_VOICE：中日韩粤英多语言，中文场景准确率更高，带逆文本正则化（ITN）
 * - SMALL_INT8：whisper small int8，多语言，准确率最高但体积大、速度慢
 *
 * 全部为 sherpa-onnx 官方发布的 int8 量化 ONNX 模型（Apache-2.0 / MIT 权重），
 * 走 HuggingFace 直链下载到应用私有目录。
 */
enum class AsrModel(val id: String) {
    TINY_INT8("tiny-int8"),
    SENSE_VOICE("sense-voice"),
    SMALL_INT8("small-int8"),
    ;

    companion object {
        /** 默认模型：tiny-int8。 */
        val DEFAULT = TINY_INT8

        fun fromId(id: String?): AsrModel =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
