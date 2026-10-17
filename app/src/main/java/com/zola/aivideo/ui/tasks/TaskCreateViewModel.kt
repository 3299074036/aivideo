package com.zola.aivideo.ui.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.task.CheckSeverity
import com.zola.aivideo.data.task.PreflightCheck
import com.zola.aivideo.data.task.PreflightIssue
import com.zola.aivideo.data.task.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 待建任务的检查上下文。 */
data class PendingCreate(
    val videoPath: String,
    val name: String,
    val issues: List<PreflightIssue>,
)

/**
 * 任务创建 ViewModel（M5）：导入完成页"创建处理任务"入口。
 *
 * 流程：requestCreate → 行前检查 → UI 弹窗（阻断直接报错 / 警告二次确认）→
 * confirmCreate 建任务 → 回调 taskId 跳任务详情。
 */
@HiltViewModel
class TaskCreateViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val preflight: PreflightCheck,
) : ViewModel() {

    /** 非空时 UI 展示检查结果弹窗。 */
    var pendingCreate by mutableStateOf<PendingCreate?>(null)
        private set

    fun requestCreate(videoPath: String, name: String) {
        pendingCreate = PendingCreate(
            videoPath = videoPath,
            name = name,
            issues = preflight.check(videoPath, needsNetwork = true),
        )
    }

    fun dismiss() {
        pendingCreate = null
    }

    /** 是否有阻断项。 */
    fun hasBlocker(): Boolean =
        pendingCreate?.issues?.any { it.severity == CheckSeverity.BLOCKER } == true

    /** 用户确认后建任务（调用方已确保无阻断项）。 */
    fun confirmCreate(onCreated: (Long) -> Unit) {
        val p = pendingCreate ?: return
        pendingCreate = null
        viewModelScope.launch {
            val id = repository.createTask(p.name, p.videoPath)
            onCreated(id)
        }
    }
}
