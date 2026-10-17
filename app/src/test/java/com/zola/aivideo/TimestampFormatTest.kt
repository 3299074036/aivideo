package com.zola.aivideo

import com.zola.aivideo.data.media.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** 字幕时间戳格式化单元测试。 */
class TimestampFormatTest {

    @Test
    fun `毫秒转mm_ss_mmm`() {
        assertEquals("00:07.250", FormatUtils.formatTimestampMs(7250))
        assertEquals("02:07.250", FormatUtils.formatTimestampMs(127250))
        assertEquals("00:00.000", FormatUtils.formatTimestampMs(0))
    }

    @Test
    fun `超一小时带小时位`() {
        assertEquals("1:02:03.456", FormatUtils.formatTimestampMs(3_723_456))
    }

    @Test
    fun `负数按零处理`() {
        assertEquals("00:00.000", FormatUtils.formatTimestampMs(-500))
    }
}
