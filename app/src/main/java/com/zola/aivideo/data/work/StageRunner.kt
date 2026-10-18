package com.zola.aivideo.data.work

import com.zola.aivideo.data.task.TaskStage

/**
 * 阶段执行器（2026-10-17 重构：替代 WorkManager）。
 *
 * 背景：WorkManager 在用户设备（OPPO Find X8, ColorOS）上任务入队后不执行，
 * 导致"开始识别"点后无反应。改为前台协程直连执行——用户本来就在 App 内
 * 看着进度，不需要后台调度；简单可靠。
 *
 * 约定：
 * - [run] 成功时必须调用 `repository.setStageDone(taskId, stage, 产物路径)`；
 * - 失败直接抛异常（TaskScheduler 统一记错误）；
 * - 通过 [onProgress] 上报进度（0f~1f）与阶段文本；onProgress 非挂起、线程安全，
 *   可在任意回调线程调用，TaskScheduler 内部负责切到 IO 写 Room。
 */
interface StageRunner {
    val stage: TaskStage

    suspend fun run(
        taskId: Long,
        onProgress: (progress: Float, stageText: String) -> Unit,
    )
}
