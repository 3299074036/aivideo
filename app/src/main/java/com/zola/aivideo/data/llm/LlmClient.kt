package com.zola.aivideo.data.llm

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OpenAI 兼容 chat completions 的消息。 */
data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
    }
}

/** 翻译测试结果（M7：真实短翻译替代 /v1/models 探测）。 */
sealed interface TranslationTestResult {
    /** 成功：附带译文与耗时毫秒。 */
    data class Success(val translated: String, val elapsedMs: Long) : TranslationTestResult
    data class Failed(val reason: String) : TranslationTestResult
}

/**
 * OpenAI 兼容 LLM 客户端（M3，独立实现）。
 *
 * - `/v1/chat/completions`：SSE 流式、可取消（协程取消即中断底层 Call）
 * - `/v1/models`：测试连接
 * - 超时/重试：网络 IO 异常最多重试 2 次（退避 1s/2s）；401 直接报 Key 无效，不重试
 *
 * 注意：这是纯网络层，不读任何本地配置；调用方负责组装 [LlmConfig]。
 */
@Singleton
class LlmClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val gson: Gson,
) {
    /**
     * 流式对话。每个 delta 文本块经 [onDelta] 回调；协程取消时立即中断请求。
     * @throws LlmException 失败时抛带中文原因的异常
     */
    suspend fun streamChat(
        config: LlmConfig,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ) {
        require(config.isComplete()) { "LLM 配置不完整" }
        val body = gson.toJson(
            mapOf(
                "model" to config.model,
                "messages" to messages,
                "stream" to true,
                "temperature" to 0.3,
            ),
        )
        val request = Request.Builder()
            .url(config.chatCompletionsUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeWithRetry {
            suspendCancellableCoroutine { cont ->
                val call = okHttp.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                try {
                    val response = call.execute()
                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        throw LlmException(httpErrorMessage(code))
                    }
                    val bodyStream = response.body
                    if (bodyStream == null) {
                        response.close()
                        throw LlmException("服务端返回空响应")
                    }
                    try {
                        val parser = SseParser()
                        var finished = false
                        bodyStream.source().use { source ->
                            while (!finished && cont.isActive) {
                                val line = try {
                                    source.readUtf8Line()
                                } catch (e: IOException) {
                                    if (cont.isActive) throw LlmException("读取流失败：${e.message}")
                                    else break // 取消导致的读异常，直接退出
                                } ?: break
                                when (val event = parser.feedLine(line)) {
                                    is SseParser.SseEvent.Data -> {
                                        val delta = extractDelta(event.payload)
                                        if (delta != null) onDelta(delta)
                                    }
                                    SseParser.SseEvent.Done -> finished = true
                                    null -> Unit
                                }
                            }
                        }
                        parser.flush()?.let { event ->
                            if (event is SseParser.SseEvent.Data) {
                                extractDelta(event.payload)?.let(onDelta)
                            }
                        }
                    } finally {
                        response.close()
                    }
                    cont.resume(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LlmException) {
                    cont.resumeWithException(e)
                } catch (e: IOException) {
                    // 包装后由外层重试逻辑识别
                    cont.resumeWithException(e)
                } catch (e: Exception) {
                    cont.resumeWithException(LlmException("请求失败：${e.message}"))
                }
            }
        }
    }

    /**
     * 测试连接（M7）：发一句真实短翻译（英→中），验证整条链路可用。
     * 成功返回译文与耗时；失败返回中文原因。
     */
    suspend fun testTranslation(config: LlmConfig): TranslationTestResult {
        if (!config.isComplete()) {
            return TranslationTestResult.Failed("请先填写完整地址、Key 和模型名")
        }
        val start = System.currentTimeMillis()
        val sb = StringBuilder()
        return try {
            streamChat(
                config,
                messages = listOf(
                    ChatMessage.system("把用户输入翻译成中文，只输出译文，不要解释。"),
                    ChatMessage.user("Hello, how are you?"),
                ),
                onDelta = { sb.append(it) },
            )
            val text = sb.toString().trim()
            if (text.isEmpty()) {
                TranslationTestResult.Failed("服务端返回空译文，请检查模型名是否正确")
            } else {
                TranslationTestResult.Success(text, System.currentTimeMillis() - start)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            TranslationTestResult.Failed(e.message ?: "翻译测试失败")
        } catch (e: Exception) {
            TranslationTestResult.Failed("测试失败：${e.message}")
        }
    }

    /** 从一个 SSE data payload 里抽 delta.content；解析失败返回 null（跳过该块）。 */
    internal fun extractDelta(payload: String): String? {
        return try {
            val chunk = gson.fromJson(payload, ChatCompletionChunk::class.java)
            chunk?.choices?.firstOrNull()?.delta?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "API Key 无效（401），请检查密钥"
        403 -> "无权访问（403），请检查 Key 权限或地址"
        404 -> "接口不存在（404），请检查 Base URL 是否正确"
        429 -> "请求被限流（429），稍后重试"
        in 500..599 -> "服务端错误（$code），稍后重试"
        else -> "请求失败（HTTP $code）"
    }

    /** 网络 IO 异常重试 2 次；LlmException（业务错误）不重试；取消不重试。 */
    private suspend fun executeWithRetry(block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                throw e
            } catch (e: SocketTimeoutException) {
                // 读超时也值得重试一次（长翻译场景）
                if (++attempt > MAX_RETRIES) throw LlmException("请求超时，请重试")
                delay(RETRY_DELAY_MS * attempt)
            } catch (e: IOException) {
                if (++attempt > MAX_RETRIES) throw LlmException("网络错误：${e.message}，请检查网络后重试")
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private data class ChatCompletionChunk(
        val choices: List<ChunkChoice>?,
    )
    private data class ChunkChoice(
        val delta: DeltaContent?,
    )
    private data class DeltaContent(
        val content: String?,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 1000L
    }
}

/** LLM 调用失败：message 即为展示给用户的中文原因。 */
class LlmException(message: String, cause: Throwable? = null) : IOException(message, cause)
