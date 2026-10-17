package com.zola.aivideo.domain.model

/**
 * 任务领域模型（M1 占位，完整状态机在 M5 任务中心落地）。
 *
 * 流水线阶段：IMPORT → DOWNLOAD → ASR → TRANSLATE → DUB → MUX → DONE / FAILED。
 * M1 仅定义模型与基础状态，供 Room 实体映射参考。
 */
data class Task(
    val id: Long,
    val name: String,
    val status: TaskStatus,
    val createdAt: Long,
)

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    RECOGNIZING,
    TRANSLATING,
    DUBBING,
    MUXING,
    DONE,
    FAILED,
}
