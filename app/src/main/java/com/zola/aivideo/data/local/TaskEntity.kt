package com.zola.aivideo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskStage

/**
 * 任务表实体（M5 完整版，Room v3）。
 *
 * 流水线：识别 → 翻译 → 配音 → 合成（导入在建任务时即完成）。
 * 每阶段独立的状态 / 进度 / 产物路径 / 错误信息 / 用户配置 JSON，
 * 失败重试时已完成阶段的产物直接复用（断点续跑）。
 *
 * 枚举存 name() 字符串，避免序数迁移问题。
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 任务名（默认取视频文件名）。 */
    val name: String,
    /** M1 导入的本地视频绝对路径（filesDir/imports/ 下）。 */
    val sourceVideoPath: String,
    /** 任务级状态：见 [com.zola.aivideo.data.task.TaskSnapshot] 的推导规则。 */
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,

    // ---- 各阶段状态（StageState.name） ----
    val transcribeState: String = StageState.PENDING.name,
    val translateState: String = StageState.PENDING.name,
    val dubState: String = StageState.PENDING.name,
    val synthesizeState: String = StageState.PENDING.name,

    // ---- 各阶段进度（0f~1f） ----
    val transcribeProgress: Float = 0f,
    val translateProgress: Float = 0f,
    val dubProgress: Float = 0f,
    val synthesizeProgress: Float = 0f,

    // ---- 各阶段产物路径 ----
    /** transcript.json（字幕分段）。 */
    val transcribeOutput: String? = null,
    /** translated.json（译文分段）。 */
    val translateOutput: String? = null,
    /** 配音轨 WAV。 */
    val dubOutput: String? = null,
    /** 最终成品视频（MediaStore 导出后的路径/URI 字符串）。 */
    val synthesizeOutput: String? = null,

    // ---- 各阶段错误信息（用户可读） ----
    val transcribeError: String? = null,
    val translateError: String? = null,
    val dubError: String? = null,
    val synthesizeError: String? = null,

    // ---- 各阶段用户配置（JSON；Worker 运行时读取，不含密钥） ----
    /** PersistedTranslateOptions 的 JSON。 */
    val translateOptionsJson: String? = null,
    /** DubOptions 的 JSON。 */
    val dubOptionsJson: String? = null,
    /** SynthesizeOptions 的 JSON。 */
    val synthesizeOptionsJson: String? = null,
) {
    /** 取某阶段的状态。 */
    fun stageState(stage: TaskStage): StageState = when (stage) {
        TaskStage.TRANSCRIBE -> StageState.valueOf(transcribeState)
        TaskStage.TRANSLATE -> StageState.valueOf(translateState)
        TaskStage.DUB -> StageState.valueOf(dubState)
        TaskStage.SYNTHESIZE -> StageState.valueOf(synthesizeState)
    }

    /** 取某阶段的进度。 */
    fun stageProgress(stage: TaskStage): Float = when (stage) {
        TaskStage.TRANSCRIBE -> transcribeProgress
        TaskStage.TRANSLATE -> translateProgress
        TaskStage.DUB -> dubProgress
        TaskStage.SYNTHESIZE -> synthesizeProgress
    }

    /** 取某阶段的产物路径。 */
    fun stageOutput(stage: TaskStage): String? = when (stage) {
        TaskStage.TRANSCRIBE -> transcribeOutput
        TaskStage.TRANSLATE -> translateOutput
        TaskStage.DUB -> dubOutput
        TaskStage.SYNTHESIZE -> synthesizeOutput
    }

    /** 取某阶段的错误信息。 */
    fun stageError(stage: TaskStage): String? = when (stage) {
        TaskStage.TRANSCRIBE -> transcribeError
        TaskStage.TRANSLATE -> translateError
        TaskStage.DUB -> dubError
        TaskStage.SYNTHESIZE -> synthesizeError
    }

    /** 返回某阶段状态被替换的新实体（不可变更新）。 */
    fun withStageState(stage: TaskStage, state: StageState): TaskEntity = when (stage) {
        TaskStage.TRANSCRIBE -> copy(transcribeState = state.name)
        TaskStage.TRANSLATE -> copy(translateState = state.name)
        TaskStage.DUB -> copy(dubState = state.name)
        TaskStage.SYNTHESIZE -> copy(synthesizeState = state.name)
    }

    /** 返回某阶段进度被替换的新实体。 */
    fun withStageProgress(stage: TaskStage, progress: Float): TaskEntity = when (stage) {
        TaskStage.TRANSCRIBE -> copy(transcribeProgress = progress.coerceIn(0f, 1f))
        TaskStage.TRANSLATE -> copy(translateProgress = progress.coerceIn(0f, 1f))
        TaskStage.DUB -> copy(dubProgress = progress.coerceIn(0f, 1f))
        TaskStage.SYNTHESIZE -> copy(synthesizeProgress = progress.coerceIn(0f, 1f))
    }

    /** 返回某阶段错误信息被替换的新实体。 */
    fun withStageError(stage: TaskStage, error: String?): TaskEntity = when (stage) {
        TaskStage.TRANSCRIBE -> copy(transcribeError = error)
        TaskStage.TRANSLATE -> copy(translateError = error)
        TaskStage.DUB -> copy(dubError = error)
        TaskStage.SYNTHESIZE -> copy(synthesizeError = error)
    }
}
