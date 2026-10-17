package com.zola.aivideo.data.task

/**
 * 单阶段状态（M5）。
 *
 * 流转：
 * - PENDING → RUNNING → DONE / FAILED
 * - 任意阶段 → NEEDS_INPUT（等待用户在对应 UI 页确认/配置），用户确认后回到 PENDING
 * - FAILED →（用户点重试）→ PENDING（已完成阶段的产物直接复用，即断点续跑）
 *
 * Room 里存 name() 字符串，避免枚举序数迁移问题。
 */
enum class StageState(val displayName: String) {
    /** 等待调度。 */
    PENDING("等待"),

    /** Worker 运行中。 */
    RUNNING("进行中"),

    /** 完成（产物已落盘）。 */
    DONE("完成"),

    /** 失败（error 字段有原因，可重试）。 */
    FAILED("失败"),

    /** 待用户确认/配置（不走 Worker，任务详情页"继续"按钮路由到对应 UI 页）。 */
    NEEDS_INPUT("待确认"),
}
