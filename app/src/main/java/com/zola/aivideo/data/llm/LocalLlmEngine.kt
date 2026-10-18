package com.zola.aivideo.data.llm

import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.llm.local.LlamaBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 本地语言模型引擎（M7）：llama.cpp 本机推理（GGUF）。
 *
 * - 模型文件走 [LlmModelCatalog] + [ModelDownloadManager]（filesDir/models/<id>/xxx.gguf）
 * - 句柄按 modelId 缓存复用：一次翻译任务内多次 streamChat 不重复加载
 * - 取消：协程取消 → requestCancel 中断 native 采样循环
 * - 未下载模型时抛中文错，引导用户去模型管理页下载
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    private val prefs: LlmPreferences,
    private val downloadManager: ModelDownloadManager,
) : LlmEngine {

    override val requiresApiConfig: Boolean = false

    private val mutex = Mutex()
    private var handle: Long = 0L
    private var loadedModelId: String? = null
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llm-local-gen").apply { isDaemon = true }
    }

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ) {
        val h = ensureLoaded()
        val prompt = buildPrompt(h, messages)
        val maxTokens = estimateMaxTokens(messages)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { LlamaBridge.requestCancel(h) }
            ioExecutor.execute {
                try {
                    val rc = LlamaBridge.generate(
                        h, prompt, maxTokens, TEMPERATURE,
                        object : LlamaBridge.TokenCallback {
                            override fun onToken(token: String) = onDelta(token)
                        },
                    )
                    when (rc) {
                        0 -> cont.resume(Unit)
                        6 -> cont.resumeWithException(CancellationException("本地推理已取消"))
                        else -> cont.resumeWithException(
                            LlmException("本地推理失败：${LlamaBridge.lastError()}"),
                        )
                    }
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }
        }
    }

    /** 确保模型已加载，返回 native 句柄（切换模型时自动卸载旧的）。 */
    private suspend fun ensureLoaded(): Long = mutex.withLock {
        val modelId = prefs.getLocalModel()
        val entry = LlmModelCatalog.entryOf(modelId)
            ?: throw LlmException("未知的本地模型：$modelId")
        val file = File(downloadManager.modelDir(entry), entry.fileName)
        if (!file.exists()) {
            throw LlmException(
                "本地语言模型「${entry.displayName}」尚未下载，" +
                    "请先在「模型管理」页下载后再用本地引擎",
            )
        }
        if (handle == 0L || loadedModelId != modelId) {
            if (handle != 0L) {
                LlamaBridge.unloadModel(handle)
                handle = 0L
            }
            val h = withContext(Dispatchers.IO) {
                LlamaBridge.loadModel(file.absolutePath, N_CTX, N_THREADS)
            }
            if (h == 0L) throw LlmException("本地模型加载失败：${LlamaBridge.lastError()}")
            handle = h
            loadedModelId = modelId
        }
        handle
    }

    /**
     * system/user 拼成完整 prompt（优先模型自带 chat template）。
     *
     * 修复（2026-10-17）：Qwen3 默认开启思考模式，会输出大量推理 token
     * 导致字幕解析失败。加 `/no_think` 关闭（videonotes 已验证有效）。
     */
    private fun buildPrompt(h: Long, messages: List<ChatMessage>): String {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val user = messages.filter { it.role != "system" }.joinToString("\n") { it.content }
        // Qwen3：/no_think 关闭思考模式，直接输出译文
        val systemNoThink = if (system.contains("/no_think")) system else "/no_think\n$system"
        return LlamaBridge.applyChatTemplate(h, systemNoThink, user)
            ?: "$systemNoThink\n\n$user\n" // 降级：手拼（极少走到）
    }

    /** 按输入长度估算最大生成 token 数。 */
    private fun estimateMaxTokens(messages: List<ChatMessage>): Int {
        val chars = messages.sumOf { it.content.length }
        return (chars * 2 + 256).coerceIn(256, 4096)
    }

    private companion object {
        /** 上下文长度：字幕分块都很小，4K 足够且省内存。 */
        const val N_CTX = 4096

        /** 推理线程数：大小核混合调度，4 线程是功耗/速度甜点。 */
        const val N_THREADS = 4

        /** 翻译用低温采样（与 API 链路的 temperature=0.3 对齐）。 */
        const val TEMPERATURE = 0.3f
    }
}
