package com.zola.aivideo.ui.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.work.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 任务详情 ViewModel（M5）。
 *
 * 步骤条 5 步：导入（建任务时即完成）→ 识别 → 翻译 → 配音 → 合成。
 * - 失败段：显示错误信息 + 重试（从失败阶段续跑，已完成产物复用）
 * - NEEDS_INPUT 段："继续"路由到对应配置页（NavGraph 按 stage → 路由）
 * - 合成 DONE：打开/分享成品（FileProvider 或 MediaStore Uri）
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val scheduler: TaskScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L

    val snapshot = repository.observeSnapshot(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 重试失败阶段（已完成产物复用）。 */
    fun retryStage(stage: TaskStage) {
        viewModelScope.launch { scheduler.scheduleStage(taskId, stage) }
    }

    /** 取消运行中的阶段。 */
    fun cancelStage(stage: TaskStage) {
        viewModelScope.launch {
            scheduler.cancelStage(taskId, stage)
            val current = repository.get(taskId) ?: return@launch
            if (current.stageState(stage) == StageState.RUNNING) {
                repository.updateStageState(taskId, stage, StageState.PENDING)
            }
        }
    }

    /** 删除任务（含产物）。 */
    fun deleteTask(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            scheduler.cancelTask(taskId)
            repository.deleteTask(taskId)
            onDone()
        }
    }

    /** 成品 Uri：MediaStore 的 content Uri，或经 FileProvider 包装的私有文件。 */
    fun outputUri(): Uri? {
        val ref = snapshot.value?.synthesizeOutput ?: return null
        return when {
            ref.startsWith("content://") -> Uri.parse(ref)
            else -> {
                val f = File(ref)
                if (!f.exists()) null
                else FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f,
                )
            }
        }
    }

    /** 分享成品（ACTION_SEND，video/mp4）。 */
    fun shareOutput(): Intent? {
        val uri = outputUri() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 打开成品（ACTION_VIEW）。 */
    fun viewOutput(): Intent? {
        val uri = outputUri() ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
