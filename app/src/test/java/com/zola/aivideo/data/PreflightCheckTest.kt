package com.zola.aivideo.data

import com.zola.aivideo.data.task.CheckSeverity
import com.zola.aivideo.data.task.PreflightCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务开始前检查规则单元测试（M5）：纯 JVM，测决策逻辑。
 */
class PreflightCheckTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `一切正常时无问题`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = 80,
            charging = false,
            mobileData = false,
            needsNetwork = true,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `存储不足时阻断`() {
        val issues = PreflightCheck.decideIssues(
            required = 5 * gb,
            available = 1 * gb,
            battery = 80,
            charging = false,
            mobileData = false,
            needsNetwork = false,
        )
        assertEquals(1, issues.size)
        assertEquals(CheckSeverity.BLOCKER, issues[0].severity)
        assertTrue(issues[0].message.contains("存储空间不足"))
    }

    @Test
    fun `低电量未充电时警告`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = 15,
            charging = false,
            mobileData = false,
            needsNetwork = false,
        )
        assertEquals(1, issues.size)
        assertEquals(CheckSeverity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("15%"))
    }

    @Test
    fun `低电量但充电中不警告`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = 10,
            charging = true,
            mobileData = false,
            needsNetwork = false,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `电量取不到时不判低电量`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = -1,
            charging = false,
            mobileData = false,
            needsNetwork = false,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `移动数据加需要网络时警告`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = 80,
            charging = false,
            mobileData = true,
            needsNetwork = true,
        )
        assertEquals(1, issues.size)
        assertEquals(CheckSeverity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("移动数据"))
    }

    @Test
    fun `不需要网络时移动数据不警告`() {
        val issues = PreflightCheck.decideIssues(
            required = gb,
            available = 10 * gb,
            battery = 80,
            charging = false,
            mobileData = true,
            needsNetwork = false,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `多项问题叠加_阻断与警告并存`() {
        val issues = PreflightCheck.decideIssues(
            required = 5 * gb,
            available = 1 * gb,
            battery = 10,
            charging = false,
            mobileData = true,
            needsNetwork = true,
        )
        assertEquals(3, issues.size)
        assertEquals(CheckSeverity.BLOCKER, issues[0].severity)
        assertEquals(CheckSeverity.WARNING, issues[1].severity)
        assertEquals(CheckSeverity.WARNING, issues[2].severity)
    }

    @Test
    fun `formatBytes 格式化`() {
        assertEquals("500 MB", PreflightCheck.formatBytes(500L * 1024 * 1024))
        assertEquals("1.5 GB", PreflightCheck.formatBytes((1.5 * gb).toLong()))
    }
}
