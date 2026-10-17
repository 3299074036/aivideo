package com.zola.aivideo.data.task

import com.zola.aivideo.data.local.TaskDao
import com.zola.aivideo.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务仓库（M5）：CRUD + Flow 观察 + 阶段状态流转。
 *
 * 所有状态变更走这里，保证 task 级 status 与 updatedAt 一致。
 * M2/M3/M4 的内存 Holder（TranscriptionResultHolder 等）在任务模式下不再使用，
 * 阶段产物经 TaskFiles 落盘（杀进程不丢）。
 */
@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val files: TaskFiles,
) {
    /** 观察单个任务快照。 */
    fun observeSnapshot(taskId: Long): Flow<TaskSnapshot?> =
        dao.observeById(taskId).map { it?.let(TaskSnapshot::from) }

    /** 观察全部任务快照（按更新时间倒序）。 */
    fun observeAllSnapshots(): Flow<List<TaskSnapshot>> =
        dao.observeAll().map { list -> list.map(TaskSnapshot::from) }

    /** 一次性取任务实体。 */
    suspend fun get(taskId: Long): TaskEntity? = dao.getById(taskId)

    /**
     * 建任务：导入完成即"下载"段 DONE，转写阶段 PENDING。
     * @return 新任务 id
     */
    suspend fun createTask(name: String, sourceVideoPath: String): Long {
        val now = System.currentTimeMillis()
        // 初始：转写 PENDING，其余 PENDING；任务级状态由快照推导，建任务时记 RUNNING
        val entity = TaskEntity(
            name = name,
            sourceVideoPath = sourceVideoPath,
            status = TaskStatus.RUNNING.name,
            createdAt = now,
            updatedAt = now,
        )
        return dao.insert(entity)
    }

    /** 删除任务（含文件目录）。 */
    suspend fun deleteTask(taskId: Long) {
        dao.deleteById(taskId)
        files.deleteTaskDir(taskId)
    }

    /** 更新某阶段状态（附带刷新任务级状态与更新时间）。 */
    suspend fun updateStageState(taskId: Long, stage: TaskStage, state: StageState) {
        mutate(taskId) { e ->
            val next = e.withStageState(stage, state)
            // 进入 RUNNING 时清掉旧错误；进入 PENDING（重试/确认后）清错误并清进度
            val cleared = when (state) {
                StageState.RUNNING -> next.withStageError(stage, null)
                StageState.PENDING -> next.withStageError(stage, null).withStageProgress(stage, 0f)
                else -> next
            }
            cleared
        }
    }

    /** 更新某阶段进度（0f~1f）。 */
    suspend fun updateStageProgress(taskId: Long, stage: TaskStage, progress: Float) {
        mutate(taskId) { it.withStageProgress(stage, progress.coerceIn(0f, 1f)) }
    }

    /** 某阶段完成：记产物路径。 */
    suspend fun setStageDone(taskId: Long, stage: TaskStage, outputPath: String?) {
        mutate(taskId) {
            var e = it.withStageState(stage, StageState.DONE)
                .withStageProgress(stage, 1f)
                .withStageError(stage, null)
            e = when (stage) {
                TaskStage.TRANSCRIBE -> e.copy(transcribeOutput = outputPath)
                TaskStage.TRANSLATE -> e.copy(translateOutput = outputPath)
                TaskStage.DUB -> e.copy(dubOutput = outputPath)
                TaskStage.SYNTHESIZE -> e.copy(synthesizeOutput = outputPath)
            }
            e
        }
    }

    /** 某阶段失败：记错误信息（用户可读）。 */
    suspend fun setStageFailed(taskId: Long, stage: TaskStage, error: String) {
        mutate(taskId) {
            it.withStageState(stage, StageState.FAILED).withStageError(stage, error)
        }
    }

    /** 某阶段待用户确认。 */
    suspend fun setStageNeedsInput(taskId: Long, stage: TaskStage) {
        mutate(taskId) { it.withStageState(stage, StageState.NEEDS_INPUT) }
    }

    /** 保存某阶段的用户配置 JSON。 */
    suspend fun saveStageOptions(taskId: Long, stage: TaskStage, json: String) {
        mutate(taskId) {
            when (stage) {
                TaskStage.TRANSLATE -> it.copy(translateOptionsJson = json)
                TaskStage.DUB -> it.copy(dubOptionsJson = json)
                TaskStage.SYNTHESIZE -> it.copy(synthesizeOptionsJson = json)
                TaskStage.TRANSCRIBE -> it // 转写无用户配置
            }
        }
    }

    /** 原子读-改-写（Room @Update 整行替换）。 */
    private suspend fun mutate(taskId: Long, block: (TaskEntity) -> TaskEntity) {
        val current = dao.getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updated = block(current).copy(updatedAt = now)
        // 任务级状态始终由快照推导，保证一致
        val status = TaskSnapshot.from(updated).status.name
        dao.update(updated.copy(status = status))
    }

    // 注意：withStageState/withStageProgress/withStageError 已是 TaskEntity 的成员函数
    // （M5 构建期补上），不要在这里再定义同名扩展——成员优先，扩展会成为
    // 永远走不到的死代码（M6 已清理）。
}
