package com.zola.aivideo.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 下载进度聚合（纯函数）单元测试。 */
class ModelDownloadManagerTest {

    @Test
    fun `多文件进度聚合`() {
        val (dl, total) = ModelDownloadManager.aggregateProgress(
            listOf(100L to 200L, 50L to 100L),
        )
        assertEquals(150L, dl)
        assertEquals(300L, total)
    }

    @Test
    fun `任一文件总量未知则总量为null`() {
        val (dl, total) = ModelDownloadManager.aggregateProgress(
            listOf(100L to 200L, 50L to null),
        )
        assertEquals(150L, dl)
        assertNull(total)
    }

    @Test
    fun `空列表聚合为零`() {
        val (dl, total) = ModelDownloadManager.aggregateProgress(emptyList())
        assertEquals(0L, dl)
        assertEquals(0L, total)
    }
}
