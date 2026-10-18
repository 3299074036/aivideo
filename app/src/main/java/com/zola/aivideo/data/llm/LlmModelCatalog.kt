package com.zola.aivideo.data.llm

import com.zola.aivideo.data.download.DownloadableModel
import com.zola.aivideo.data.download.ModelFile

/**
 * 本地语言模型目录（M7）：GGUF 量化模型，走 llama.cpp 推理。
 *
 * 选型：字幕翻译场景中文优先，Qwen 系是唯一合理选择；量化档 Q4_K_M（质量/体积甜点）。
 * 来源：bartowski 在 HuggingFace 发布的 llama.cpp 官方量化（2026-10-17 逐个 curl -I 验证可达）。
 * 下载地址变更时只改此处；文件体积为展示用约数，真实进度以 HTTP Content-Length 为准。
 */
object LlmModelCatalog {

    /** 一个可下载的语言模型条目（实现 DownloadableModel，复用模型下载器）。 */
    data class Entry(
        val displayName: String,
        val description: String,
        /** 展示用体积文案（如 "约 0.4 GB"）。 */
        val sizeLabel: String,
        /** GGUF 文件名（落盘到 filesDir/models/<id>/ 下）。 */
        val fileName: String,
        override val files: List<ModelFile>,
    ) : DownloadableModel {
        override val id: String get() = fileName.removeSuffix(".gguf")
    }

    private const val HF = "https://huggingface.co"

    val entries: List<Entry> = listOf(
        Entry(
            displayName = "Qwen3-0.6B（Q4_K_M）",
            description = "默认推荐。字幕翻译够用，手机上最快，断网可用",
            sizeLabel = "约 0.4 GB",
            fileName = "Qwen_Qwen3-0.6B-Q4_K_M.gguf",
            files = listOf(
                ModelFile(
                    "$HF/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf",
                    "Qwen_Qwen3-0.6B-Q4_K_M.gguf",
                ),
            ),
        ),
        Entry(
            displayName = "Qwen3-1.7B（Q4_K_M）",
            description = "翻译质量更好，速度慢一些；适合插电或对质量要求高的场景",
            sizeLabel = "约 1.1 GB",
            fileName = "Qwen_Qwen3-1.7B-Q4_K_M.gguf",
            files = listOf(
                ModelFile(
                    "$HF/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf",
                    "Qwen_Qwen3-1.7B-Q4_K_M.gguf",
                ),
            ),
        ),
    )

    fun entryOf(id: String): Entry? = entries.firstOrNull { it.id == id }

    /** 默认模型 id（Qwen3-0.6B）。 */
    val DEFAULT_ID: String get() = entries.first().id
}
