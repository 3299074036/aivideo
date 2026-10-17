package com.zola.aivideo.data.stock

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 注意：中文 backtick 测试名 + 函数内匿名 object 会让 Kotlin 生成含中文的
 * class 文件名（如 `StockClientParseTest$授权记录 JSON 往返$back$1.class`），
 * 在本机构建文件系统上写文件会报 InvalidPathException。
 * 因此 TypeToken 必须提到文件顶层（匿名类名保持 ASCII）。
 */
private val licenseListType: java.lang.reflect.Type =
    object : TypeToken<List<StockLicenseRecord>>() {}.type

/**
 * 素材 API 响应解析测试（M6）。
 *
 * 用接近真实的最小 JSON 验证 Pexels / Pixabay → StockClip 的映射，
 * 包括"选 ≤1080p 最大分辨率"规则与无可用文件时的 null。
 */
class StockClientParseTest {

    private val gson = Gson()

    private val pexelsJson = """
        {
          "videos": [
            {
              "id": 3121459,
              "url": "https://www.pexels.com/video/3121459/",
              "image": "https://images.pexels.com/videos/3121459/preview.jpg",
              "duration": 30,
              "user": {"name": "Pixabay", "url": "https://www.pexels.com/@pixabay/"},
              "video_files": [
                {"quality": "uhd", "file_type": "video/mp4", "width": 3840, "height": 2160,
                 "link": "https://example.com/uhd.mp4"},
                {"quality": "hd", "file_type": "video/mp4", "width": 1920, "height": 1080,
                 "link": "https://example.com/hd.mp4"},
                {"quality": "sd", "file_type": "video/mp4", "width": 640, "height": 360,
                 "link": "https://example.com/sd.mp4"}
              ]
            },
            {
              "id": 999,
              "url": "https://www.pexels.com/video/999/",
              "user": {"name": "Nobody"},
              "video_files": [
                {"quality": "hd", "file_type": "video/webm", "width": 1920, "height": 1080,
                 "link": "https://example.com/hd.webm"}
              ]
            }
          ]
        }
    """.trimIndent()

    private val pixabayJson = """
        {
          "hits": [
            {
              "id": 12345,
              "pageURL": "https://pixabay.com/videos/city-12345/",
              "previewURL": "https://i.vimeocdn.com/video/preview.jpg",
              "duration": 20,
              "user": "JohnDoe",
              "videos": {
                "large": {"url": "https://cdn.pixabay.com/video/large.mp4",
                          "width": 1920, "height": 1080, "size": 5000000},
                "medium": {"url": "https://cdn.pixabay.com/video/medium.mp4",
                           "width": 1280, "height": 720, "size": 2000000},
                "small": {"url": "https://cdn.pixabay.com/video/small.mp4",
                          "width": 640, "height": 360, "size": 500000}
              }
            },
            {
              "id": 54321,
              "pageURL": "https://pixabay.com/videos/none-54321/",
              "user": "Jane",
              "videos": {
                "tiny": {"url": "", "width": 0, "height": 0}
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `pexels 选 1080p mp4 且映射作者与授权页`() {
        val clips = gson.fromJson(pexelsJson, PexelsSearchResponse::class.java)
            .videos.mapNotNull { it.toStockClip() }
        assertEquals(1, clips.size)
        val clip = clips[0]
        assertEquals(StockProvider.PEXELS, clip.provider)
        assertEquals("3121459", clip.clipId)
        assertEquals("https://example.com/hd.mp4", clip.videoUrl)
        assertEquals(1920, clip.width)
        assertEquals(1080, clip.height)
        assertEquals(30.0, clip.durationSec!!, 0.001)
        assertEquals("Pixabay", clip.authorName)
        assertEquals("https://www.pexels.com/video/3121459/", clip.licensePageUrl)
        assertEquals("Pexels License", clip.licenseName)
    }

    @Test
    fun `pexels 无 mp4 文件时返回 null（被过滤）`() {
        // 第二个 video 只有 webm → mapNotNull 过滤，只剩 1 条（上一个测试已断言）
        val videos = gson.fromJson(pexelsJson, PexelsSearchResponse::class.java).videos
        assertNull(videos[1].toStockClip())
    }

    @Test
    fun `pixabay 选最大可用分辨率且映射作者`() {
        val clips = gson.fromJson(pixabayJson, PixabaySearchResponse::class.java)
            .hits.mapNotNull { it.toStockClip() }
        assertEquals(1, clips.size)
        val clip = clips[0]
        assertEquals(StockProvider.PIXABAY, clip.provider)
        assertEquals("12345", clip.clipId)
        assertEquals("https://cdn.pixabay.com/video/large.mp4", clip.videoUrl)
        assertEquals(1920, clip.width)
        assertEquals("JohnDoe", clip.authorName)
        assertEquals("https://pixabay.com/videos/city-12345/", clip.licensePageUrl)
        assertEquals("Pixabay Content License", clip.licenseName)
    }

    @Test
    fun `pixabay 无可用变体时返回 null`() {
        val hits = gson.fromJson(pixabayJson, PixabaySearchResponse::class.java).hits
        assertNull(hits[1].toStockClip())
    }

    @Test
    fun `授权记录 JSON 往返`() {
        val record = StockLicenseRecord(
            provider = "pexels",
            clipId = "3121459",
            clipPageUrl = "https://www.pexels.com/video/3121459/",
            authorName = "Pixabay",
            authorUrl = "https://www.pexels.com/@pixabay/",
            licenseName = "Pexels License",
            fileName = "pexels_3121459.mp4",
            replacedSegmentStartSec = 5.0,
            replacedSegmentEndSec = 15.0,
            recordedAt = 1729000000000L,
        )
        val json = gson.toJson(listOf(record))
        assertTrue(json.contains("Pexels License"))
        val back = gson.fromJson(json, licenseListType) as List<StockLicenseRecord>
        assertEquals(1, back.size)
        assertEquals(record, back[0])
        assertNotNull(back[0].replacedSegmentStartSec)
    }
}
