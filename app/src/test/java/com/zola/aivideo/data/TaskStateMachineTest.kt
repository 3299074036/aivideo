package com.zola.aivideo.data

import com.zola.aivideo.data.local.TaskEntity
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskSnapshot
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.data.task.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 任务状态机单元测试（M5）：纯 JVM，不依赖 Room/WorkManager。
 *
 * 覆盖：PENDING→RUNNING→DONE/FAILED 流转、失败→重试、断点续跑、
 * NEEDS_INPUT 推导、总进度计算。
 */
class TaskStateMachineTest {

    private fun freshTask(): TaskEntity = TaskEntity(
        name = "测试任务",
        sourceVideoPath = "/tmp/in.mp4",
        status = "RUNNING",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `新任务全部 PENDING_任务级推导为 RUNNING_当前阶段为识别`() {
        val snap = TaskSnapshot.from(freshTask())
        assertEquals(TaskStatus.RUNNING, snap.status)
        assertEquals(TaskStage.TRANSCRIBE, snap.currentStage)
        assertEquals(0f, snap.totalProgress, 0.001f)
    }

    @Test
    fun `PENDING 到 RUNNING 到 DONE 流转`() {
        var e = freshTask()
            .withStageState(TaskStage.TRANSCRIBE, StageState.RUNNING)
            .withStageProgress(TaskStage.TRANSCRIBE, 0.5f)
        var snap = TaskSnapshot.from(e)
        assertEquals(TaskStatus.RUNNING, snap.status)
        assertEquals("识别 · 进行中 50%", snap.stageLine)

        e = e.withStageState(TaskStage.TRANSCRIBE, StageState.DONE)
            .withStageProgress(TaskStage.TRANSCRIBE, 1f)
        snap = TaskSnapshot.from(e)
        // 转写 DONE 后下一阶段为翻译（Worker 会置 NEEDS_INPUT）
        assertEquals(TaskStage.TRANSLATE, snap.currentStage)
        assertEquals(0.25f, snap.totalProgress, 0.001f)
    }

    @Test
    fun `失败后重试_错误被清除_可重新进入 RUNNING`() {
        var e = freshTask()
            .withStageState(TaskStage.TRANSCRIBE, StageState.RUNNING)
        // 模拟 Repository.setStageFailed
        e = e.withStageState(TaskStage.TRANSCRIBE, StageState.FAILED)
            .withStageError(TaskStage.TRANSCRIBE, "模型文件缺失")
        var snap = TaskSnapshot.from(e)
        assertEquals(TaskStatus.FAILED, snap.status)
        assertEquals("模型文件缺失", snap.transcribeError)

        // 模拟 Repository.updateStageState(PENDING)：清错误与进度
        e = e.withStageState(TaskStage.TRANSCRIBE, StageState.PENDING)
            .withStageError(TaskStage.TRANSCRIBE, null)
            .withStageProgress(TaskStage.TRANSCRIBE, 0f)
        snap = TaskSnapshot.from(e)
        assertEquals(TaskStatus.RUNNING, snap.status)
        assertEquals(null, snap.transcribeError)

        e = e.withStageState(TaskStage.TRANSCRIBE, StageState.RUNNING)
        assertEquals(TaskStatus.RUNNING, TaskSnapshot.from(e).status)
    }

    @Test
    fun `断点续跑_已完成阶段保持 DONE_当前阶段指向失败处`() {
        val e = freshTask()
            .withStageState(TaskStage.TRANSCRIBE, StageState.DONE)
            .withStageProgress(TaskStage.TRANSCRIBE, 1f)
            .withStageOutputPath(TaskStage.TRANSCRIBE, "/tmp/transcript.json")
            .withStageState(TaskStage.TRANSLATE, StageState.FAILED)
            .withStageError(TaskStage.TRANSLATE, "网络超时")
        val snap = TaskSnapshot.from(e)
        // 已完成产物保留（续跑时复用）
        assertEquals(StageState.DONE, snap.transcribeState)
        assertEquals("/tmp/transcript.json", snap.transcribeOutput)
        // 当前阶段指向翻译（失败处）
        assertEquals(TaskStage.TRANSLATE, snap.currentStage)
        assertEquals(TaskStatus.FAILED, snap.status)
        assertEquals(0.25f, snap.totalProgress, 0.001f)
    }

    @Test
    fun `下一阶段 NEEDS_INPUT 时任务级为待确认`() {
        val e = freshTask()
            .withStageState(TaskStage.TRANSCRIBE, StageState.DONE)
            .withStageProgress(TaskStage.TRANSCRIBE, 1f)
            .withStageState(TaskStage.TRANSLATE, StageState.NEEDS_INPUT)
        val snap = TaskSnapshot.from(e)
        assertEquals(TaskStatus.NEEDS_INPUT, snap.status)
        assertEquals(TaskStage.TRANSLATE, snap.currentStage)
    }

    @Test
    fun `全部 DONE 时任务级为 DONE`() {
        var e = freshTask()
        for (stage in TaskStage.entries) {
            e = e.withStageState(stage, StageState.DONE).withStageProgress(stage, 1f)
        }
        val snap = TaskSnapshot.from(e)
        assertEquals(TaskStatus.DONE, snap.status)
        assertEquals("全部完成", snap.stageLine)
        assertEquals(1f, snap.totalProgress, 0.001f)
    }

    @Test
    fun `withStageState 不可变_原实体不受影响`() {
        val original = freshTask()
        val next = original.withStageState(TaskStage.DUB, StageState.RUNNING)
        assertEquals(StageState.PENDING, original.stageState(TaskStage.DUB))
        assertEquals(StageState.RUNNING, next.stageState(TaskStage.DUB))
    }

    /** 测试辅助：设置某阶段产物路径（TaskEntity 无 withStageOutput，手动 copy）。 */
    private fun TaskEntity.withStageOutputPath(stage: TaskStage, path: String): TaskEntity =
        when (stage) {
            TaskStage.TRANSCRIBE -> copy(transcribeOutput = path)
            TaskStage.TRANSLATE -> copy(translateOutput = path)
            TaskStage.DUB -> copy(dubOutput = path)
            TaskStage.SYNTHESIZE -> copy(synthesizeOutput = path)
        }
}
