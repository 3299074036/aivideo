package com.zola.aivideo

import com.zola.aivideo.data.media.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** 元信息格式化单元测试：纯 JVM。 */
class FormatUtilsTest {

    @Test
    fun `时长格式化_分秒`() {
        assertEquals("05:00", FormatUtils.formatDuration(300_000L))
        assertEquals("00:07", FormatUtils.formatDuration(7_000L))
    }

    @Test
    fun `时长格式化_含小时`() {
        assertEquals("1:02:03", FormatUtils.formatDuration(3_723_000L))
    }

    @Test
    fun `时长格式化_负数按零处理`() {
        assertEquals("00:00", FormatUtils.formatDuration(-100L))
    }

    @Test
    fun `文件大小格式化`() {
        assertEquals("512 B", FormatUtils.formatFileSize(512L))
        assertEquals("1.5 KB", FormatUtils.formatFileSize(1536L))
        assertEquals("97.0 MB", FormatUtils.formatFileSize(97L * 1024 * 1024))
        assertEquals("1.50 GB", FormatUtils.formatFileSize(1610612736L))
    }
}
