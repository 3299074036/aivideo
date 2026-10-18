package com.zola.aivideo.data.llm.local

/**
 * llama.cpp JNI 桥（M7）。
 *
 * C++ 实现在 `app/src/main/cpp/`（`aivideo_llama` 库），NDK 构建后 `System.loadLibrary` 加载。
 *
 * 线程模型：所有方法都在调用线程同步执行；[generate] 在采样循环中逐 token 回调
 * [TokenCallback.onToken]，并检查取消标志（[requestCancel] 写入）。
 */
object LlamaBridge {

    init {
        System.loadLibrary("aivideo_llama")
    }

    /** 加载模型，成功返回非 0 句柄，失败返回 0（用 [lastError] 取原因）。 */
    external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Long

    /**
     * 套用模型自带 chat template，把 system/user 拼成完整 prompt。
     * 失败返回 null（此时调用方可降级为手拼）。
     */
    external fun applyChatTemplate(handle: Long, system: String, user: String): String?

    /**
     * 流式生成。prompt 为完整对话文本（含 chat template），
     * 每采样出一个 token 就回调 [callback]，结束返回 0，
     * 失败/取消返回非 0（用 [lastError] 取原因）。
     */
    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback,
    ): Int

    /** 请求取消正在进行的 [generate]（线程安全）。 */
    external fun requestCancel(handle: Long)

    /** 释放模型句柄。 */
    external fun unloadModel(handle: Long)

    /** 取最近一次 native 错误（中文）。 */
    external fun lastError(): String

    /** token 流式回调（native 线程调用）。 */
    interface TokenCallback {
        fun onToken(token: String)
    }
}
