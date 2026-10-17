package com.zola.aivideo.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务调度器（M5）。
 *
 * - 每阶段独立调度（不硬链式）：阶段之间可能需要用户介入（NEEDS_INPUT），
 *   用户在对应 UI 页确认后再调度下一阶段
 * - 唯一任务名：`aivideo-task-<taskId>-<STAGE>`，REPLACE 语义避免重复入队
 * - 失败重试 = 重新调度该失败阶段；已完成阶段的产物在磁盘上，直接复用（断点续跑）
 * - 取消：取消该任务的全部阶段任务
 */
@Singleton
class TaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
) {
    private fun workManager(): WorkManager = WorkManager.getInstance(context)

    private fun uniqueName(taskId: Long, stage: TaskStage): String =
        "aivideo-task-$taskId-${stage.name}"

    private fun workerClass(stage: TaskStage): Class<out LongTaskWorker> = when (stage) {
        TaskStage.TRANSCRIBE -> TranscribeWorker::class.java
        TaskStage.TRANSLATE -> TranslateWorker::class.java
        TaskStage.DUB -> DubWorker::class.java
        TaskStage.SYNTHESIZE -> SynthesizeWorker::class.java
    }

    /**
     * 调度某阶段（幂等）。
     * 已是 RUNNING 的阶段不重复调度；其余状态先置 PENDING 再入队。
     */
    suspend fun scheduleStage(taskId: Long, stage: TaskStage) {
        val task = repository.get(taskId) ?: return
        if (task.stageState(stage) == StageState.RUNNING) return
        repository.updateStageState(taskId, stage, StageState.PENDING)
        @Suppress("UNCHECKED_CAST")
        val request = androidx.work.OneTimeWorkRequest.Builder(
            workerClass(stage) as Class<out androidx.work.ListenableWorker>,
        )
            .setInputData(workDataOf(LongTaskWorker.KEY_TASK_ID to taskId))
            .build()
        workManager().enqueueUniqueWork(
            uniqueName(taskId, stage),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** 取消任务的全部阶段（用户主动取消）。 */
    fun cancelTask(taskId: Long) {
        for (stage in TaskStage.entries) {
            workManager().cancelUniqueWork(uniqueName(taskId, stage))
        }
    }

    /** 取消单个阶段。 */
    fun cancelStage(taskId: Long, stage: TaskStage) {
        workManager().cancelUniqueWork(uniqueName(taskId, stage))
    }
}
