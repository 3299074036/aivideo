package com.zola.aivideo.data.work

import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务调度器（2026-10-17 重构：去 WorkManager 化）。
 *
 * 背景：WorkManager 在部分设备（OPPO ColorOS）上入队后不执行，
 * 用户点"开始识别"后无反应。改为 Singleton 作用域的协程直接执行
 * StageRunner——用户本来就在前台看着进度，不需要后台调度。
 *
 * - 每阶段独立调度（不硬链式）：阶段之间可能需要用户介入（NEEDS_INPUT），
 *   用户在对应 UI 页确认后再调度下一阶段
 * - 同一 taskId+stage 同时只跑一个 Job，重复调度时先取消旧的
 * - 失败重试 = 重新调度该失败阶段；已完成阶段的产物在磁盘上，直接复用（断点续跑）
 * - 进度经 Room 双写（TaskRepository.updateStageProgress），UI 观察 Room
 */
@Singleton
class TaskScheduler @Inject constructor(
    private val repository: TaskRepository,
    private val transcribeRunner: TranscribeStageRunner,
    private val translateRunner: TranslateStageRunner,
    private val dubRunner: DubStageRunner,
    private val synthesizeRunner: SynthesizeStageRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** key = "$taskId-${stage.name}" → 运行中的 Job（用于取消）。 */
    private val jobs = ConcurrentHashMap<String, Job>()

    private fun runnerFor(stage: TaskStage): StageRunner = when (stage) {
        TaskStage.TRANSCRIBE -> transcribeRunner
        TaskStage.TRANSLATE -> translateRunner
        TaskStage.DUB -> dubRunner
        TaskStage.SYNTHESIZE -> synthesizeRunner
    }

    private fun jobKey(taskId: Long, stage: TaskStage) = "$taskId-${stage.name}"

    /**
     * 调度某阶段（幂等）。
     * 已是 RUNNING 的阶段不重复调度；其余状态先置 PENDING 再在后台协程执行。
     */
    suspend fun scheduleStage(taskId: Long, stage: TaskStage) {
        val task = repository.get(taskId) ?: return
        if (task.stageState(stage) == StageState.RUNNING) return

        val key = jobKey(taskId, stage)
        jobs[key]?.cancel()
        jobs.remove(key)

        repository.updateStageState(taskId, stage, StageState.PENDING)

        val job = scope.launch {
            try {
                repository.updateStageState(taskId, stage, StageState.RUNNING)
                repository.updateStageProgress(taskId, stage, 0f)
                val runner = runnerFor(stage)
                // onProgress 非挂起、可能在任意线程回调；起子协程写 Room
                runner.run(taskId) { p, _ ->
                    launch { repository.updateStageProgress(taskId, stage, p.coerceIn(0f, 1f)) }
                }
                // Runner 成功时已自行 setStageDone
            } catch (e: CancellationException) {
                // 用户取消：保留现场，由调用方决定状态
                throw e
            } catch (e: Exception) {
                val msg = e.message?.takeIf { it.isNotBlank() }
                    ?: "${e.javaClass.simpleName}（无详细信息）"
                repository.setStageFailed(taskId, stage, msg)
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
    }

    /** 取消任务的全部阶段（用户主动取消）。 */
    fun cancelTask(taskId: Long) {
        for (stage in TaskStage.entries) cancelStage(taskId, stage)
    }

    /** 取消单个阶段。 */
    fun cancelStage(taskId: Long, stage: TaskStage) {
        jobs[jobKey(taskId, stage)]?.cancel()
        jobs.remove(jobKey(taskId, stage))
    }
}
