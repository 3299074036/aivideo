package com.zola.aivideo.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zola.aivideo.R
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import kotlinx.coroutines.CancellationException

/**
 * 长任务 Worker 基类（M5）。
 *
 * 约定：
 * - inputData 只传 `taskId`（[KEY_TASK_ID]），大对象经 Room/TaskFiles 传递，
 *   避免 WorkManager Data 体积限制
 * - [doWork] 为 final 模板方法：参数校验 → 阶段置 RUNNING → 前台服务 →
 *   [doStageWork] → 成功/失败/取消；任何异常都记入任务阶段错误，不抛原生崩溃
 * - 进度经 [reportProgress] 同时写 WorkManager Data 与 Room（UI 观察 Room）
 *
 * 血泪教训（视记）：`setForeground` 必须包在 try-catch 里——API 31+ 后台启动前台服务
 * 被拒（ForegroundServiceStartNotAllowedException）时降级为无通知继续执行，
 * 否则 doWork 直接崩且无诊断信息。
 */
abstract class LongTaskWorker(
    appContext: Context,
    params: WorkerParameters,
    protected val stage: TaskStage,
    protected val repository: TaskRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_PROGRESS = "progress"
        const val NOTIFICATION_ID_BASE = 0xA1_0000
        const val CHANNEL_ID = "aivideo_task_channel"
    }

    /** 任务 id：Worker 传参只传它，大对象走 Room/TaskFiles。 */
    protected val taskId: Long = params.inputData.getLong(KEY_TASK_ID, 0L)

    /** 阶段展示名（通知用），子类可覆盖补充细节。 */
    protected open fun stageDisplayName(): String = stage.displayName

    final override suspend fun doWork(): Result {
        if (taskId <= 0L) {
            return Result.failure(workDataOf(KEY_TASK_ID to taskId))
        }
        repository.updateStageState(taskId, stage, StageState.RUNNING)
        reportProgress(0f)
        safeSetForeground(stageDisplayName(), 0f)
        return try {
            val result = doStageWork()
            // 子类成功时已自行 setStageDone；失败抛异常走下分支
            result
        } catch (e: CancellationException) {
            // 用户取消：保留现场（阶段停在 RUNNING/原进度），由 TaskScheduler 统一处理
            Result.failure(workDataOf(KEY_TASK_ID to taskId))
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() }
                ?: "${e.javaClass.simpleName}（无详细信息）"
            repository.setStageFailed(taskId, stage, msg)
            Result.failure(workDataOf(KEY_TASK_ID to taskId))
        }
    }

    /**
     * 子类实现：执行本阶段工作。
     *
     * 成功时必须调用 [repository.setStageDone]（记产物路径），然后返回 `Result.success()`；
     * 失败直接抛异常（基类统一记错误 + Result.failure）。
     */
    protected abstract suspend fun doStageWork(): Result

    /** 上报进度：WorkManager Data + Room 双写。 */
    protected suspend fun reportProgress(progress: Float, stageText: String? = null) {
        val p = progress.coerceIn(0f, 1f)
        setProgress(workDataOf(KEY_PROGRESS to p))
        repository.updateStageProgress(taskId, stage, p)
        safeSetForeground(stageText ?: stageDisplayName(), p)
    }

    /**
     * 安全地进入前台服务：API 31+ 后台启动被拒时降级为无通知继续执行。
     * （异常发生在系统侧，try-catch 罩得住的是我们自己的调用。）
     */
    private suspend fun safeSetForeground(stageText: String, progress: Float) {
        try {
            setForeground(createForegroundInfo(stageText, progress))
        } catch (_: Exception) {
            // 降级：无通知继续执行，UI 仍可通过 Room 观察进度
        }
    }

    private fun createForegroundInfo(stageText: String, progress: Float): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "任务进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "视频处理长任务的阶段进度" }
            notificationManager.createNotificationChannel(channel)
        }
        val pct = (progress * 100).toInt()
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("影译 · $stageText")
            .setContentText(if (progress > 0f) "已完成 $pct%" else "准备中…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, pct, progress <= 0f)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_BASE + stage.ordinal,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_BASE + stage.ordinal, notification)
        }
    }
}
