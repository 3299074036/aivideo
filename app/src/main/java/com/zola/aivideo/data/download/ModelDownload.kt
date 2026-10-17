package com.zola.aivideo.data.download

/**
 * 可下载的单个模型文件（M4：从 data.asr 泛化而来，供 ASR/TTS 共用）。
 */
data class ModelFile(
    /** 下载直链。 */
    val url: String,
    /** 落盘相对路径（可含子目录，如 "espeak-ng-data/en_dict"）。 */
    val fileName: String,
)

/**
 * 可下载模型：下载管理器只依赖这个接口，不依赖具体模型类型。
 *
 * 落盘位置：`filesDir/models/<dirName>/`，随应用卸载清除。
 */
interface DownloadableModel {
    /** 模型唯一 ID（同时作为下载状态 key）。 */
    val id: String

    /** 在 filesDir/models/ 下的子目录名，默认 = id。 */
    val dirName: String get() = id

    /** 全部文件（含子目录相对路径）。 */
    val files: List<ModelFile>
}
