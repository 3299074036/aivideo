package com.zola.aivideo.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LlmClient.extractDelta] 单元测试：纯 JVM。
 *
 * extractDelta 是 internal，同模块测试可见。构造 LlmClient 需要 OkHttp/Gson，
 * 这里只测解析逻辑：用一个轻量子类绕过构造（extractDelta 不依赖成员）。
 */
class LlmClientParseTest {

    // extractDelta 是实例方法但不使用成员；通过反射-free 的方式：
    // 直接new一个最小实例需要 OkHttpClient/Gson——OkHttpClient() 在 JVM 可构造。
    private val client by lazy {
        LlmClient(okhttp3.OkHttpClient(), com.google.gson.Gson())
    }

    @Test
    fun `抽取 delta content`() {
        val payload = """{"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", client.extractDelta(payload))
    }

    @Test
    fun `content 为 null 返回 null`() {
        val payload = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(client.extractDelta(payload))
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertNull(client.extractDelta("not json"))
    }

    @Test
    fun `空 choices 返回 null`() {
        assertNull(client.extractDelta("""{"choices":[]}"""))
    }
}
