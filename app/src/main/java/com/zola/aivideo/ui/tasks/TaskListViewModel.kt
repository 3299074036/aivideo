package com.zola.aivideo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.task.TaskRepository
import com.zola.aivideo.data.work.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务中心列表 ViewModel（M5）。
 *
 * 任务列表倒序（最近更新在前）；删除时先取消 WorkManager 的全部阶段任务，
 * 再删 Room 记录与任务目录。
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: TaskScheduler,
) : ViewModel() {

    val snapshots = repository.observeAllSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 删除任务（含产物目录）。 */
    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            scheduler.cancelTask(taskId)
            repository.deleteTask(taskId)
        }
    }
}
