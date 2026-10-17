package com.zola.aivideo.data.task

import com.zola.aivideo.data.local.TaskEntity

/**
 * 任务级状态（M5）。
 *
 * 由各阶段状态推导，规则：
 * - 任一阶段 FAILED → FAILED（且无 RUNNING）
 * - 任一阶段 RUNNING → RUNNING
 * - 全部 DONE → DONE
 * - 任一阶段 NEEDS_INPUT（且无 RUNNING/FAILED）→ NEEDS_INPUT
 * - 否则 RUNNING（有 PENDING 待调度）
 */
enum class TaskStatus(val displayName: String) {
    RUNNING("进行中"),
    NEEDS_INPUT("待确认"),
    DONE("已完成"),
    FAILED("失败"),
}

/**
 * 任务快照：实体 + 派生信息，供 UI 层直接展示。
 */
data class TaskSnapshot(
    val entity: TaskEntity,
    val status: TaskStatus,
    /** 总进度 0f~1f：四阶段进度均值。 */
    val totalProgress: Float,
    /** 当前推进到的阶段（第一个非 DONE 阶段；全 DONE 时为 SYNTHESIZE）。 */
    val currentStage: TaskStage,
    /** 当前阶段文案（如"翻译 · 进行中 42%"）。 */
    val stageLine: String,
) {
    // ---- 实体字段委托（UI 层直接读快照，不用再 .entity） ----
    val id: Long get() = entity.id
    val name: String get() = entity.name
    val updatedAt: Long get() = entity.updatedAt

    val transcribeState: StageState get() = entity.stageState(TaskStage.TRANSCRIBE)
    val translateState: StageState get() = entity.stageState(TaskStage.TRANSLATE)
    val dubState: StageState get() = entity.stageState(TaskStage.DUB)
    val synthesizeState: StageState get() = entity.stageState(TaskStage.SYNTHESIZE)

    val transcribeProgress: Float get() = entity.stageProgress(TaskStage.TRANSCRIBE)
    val translateProgress: Float get() = entity.stageProgress(TaskStage.TRANSLATE)
    val dubProgress: Float get() = entity.stageProgress(TaskStage.DUB)
    val synthesizeProgress: Float get() = entity.stageProgress(TaskStage.SYNTHESIZE)

    val transcribeOutput: String? get() = entity.transcribeOutput
    val translateOutput: String? get() = entity.translateOutput
    val dubOutput: String? get() = entity.dubOutput
    val synthesizeOutput: String? get() = entity.synthesizeOutput

    val transcribeError: String? get() = entity.transcribeError
    val translateError: String? get() = entity.translateError
    val dubError: String? get() = entity.dubError
    val synthesizeError: String? get() = entity.synthesizeError

    companion object {
        private val ORDER = TaskStage.entries.toList()

        fun from(entity: TaskEntity): TaskSnapshot {
            val states = ORDER.associateWith { entity.stageState(it) }
            val status = when {
                states.values.any { it == StageState.RUNNING } -> TaskStatus.RUNNING
                states.values.any { it == StageState.FAILED } -> TaskStatus.FAILED
                states.values.all { it == StageState.DONE } -> TaskStatus.DONE
                states.values.any { it == StageState.NEEDS_INPUT } -> TaskStatus.NEEDS_INPUT
                else -> TaskStatus.RUNNING
            }
            val totalProgress = ORDER.sumOf { entity.stageProgress(it).toDouble() }.toFloat() / ORDER.size
            val currentStage = ORDER.firstOrNull { states[it] != StageState.DONE } ?: TaskStage.SYNTHESIZE
            val currentState = states[currentStage]!!
            val pct = (entity.stageProgress(currentStage) * 100).toInt()
            val stageLine = when (status) {
                TaskStatus.DONE -> "全部完成"
                else -> "${currentStage.displayName} · ${currentState.displayName}" +
                    if (currentState == StageState.RUNNING) " $pct%" else ""
            }
            return TaskSnapshot(entity, status, totalProgress.coerceIn(0f, 1f), currentStage, stageLine)
        }
    }
}
