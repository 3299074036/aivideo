package com.zola.aivideo.data.tts.engine

import com.google.gson.Gson
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAiTtsEngine 请求构造单元测试（M4）：纯函数 buildSpeechRequest。
 *
 * 网络执行与音频解码需要真机/模拟器（MediaCodec + 密钥），此处只验证
 * 请求的 URL / 鉴权头 / JSON 体构造。
 */
class OpenAiTtsEngineTest {

    private val gson = Gson()

    private fun requestBody(request: okhttp3.Request): String {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `buildSpeechRequest hits audio speech endpoint`() {
        val request = OpenAiTtsEngine.buildSpeechRequest(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test",
            model = "tts-1",
            voice = "alloy",
            text = "你好",
            speed = 1.0f,
            gson = gson,
        )
        assertEquals("https://api.openai.com/v1/audio/speech", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `buildSpeechRequest body contains model voice format`() {
        val request = OpenAiTtsEngine.buildSpeechRequest(
            baseUrl = "https://api.openai.com/v1/",
            apiKey = "k",
            model = "tts-1-hd",
            voice = "nova",
            text = "测试文本",
            speed = 1.25f,
            gson = gson,
        )
        // baseUrl 尾斜杠不应导致双斜杠
        assertEquals("https://api.openai.com/v1/audio/speech", request.url.toString())
        val body = requestBody(request)
        assertTrue(body.contains("\"model\":\"tts-1-hd\""))
        assertTrue(body.contains("\"voice\":\"nova\""))
        assertTrue(body.contains("\"input\":\"测试文本\""))
        assertTrue(body.contains("\"response_format\":\"wav\""))
        assertTrue(body.contains("\"speed\":1.25"))
    }

    @Test
    fun `buildSpeechRequest clamps speed to api range`() {
        val fast = requestBody(
            OpenAiTtsEngine.buildSpeechRequest(
                "https://x/v1", "k", "tts-1", "alloy", "a", 9.0f, gson,
            ),
        )
        assertTrue(fast.contains("\"speed\":4.0"))
        val slow = requestBody(
            OpenAiTtsEngine.buildSpeechRequest(
                "https://x/v1", "k", "tts-1", "alloy", "a", 0.01f, gson,
            ),
        )
        assertTrue(slow.contains("\"speed\":0.25"))
    }
}
