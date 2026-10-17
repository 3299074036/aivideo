package com.zola.aivideo.data.llm

/**
 * SSE（Server-Sent Events）行解析器：纯 Kotlin，无 Android/OkHttp 依赖，可 JVM 单元测试。
 *
 * 按 SSE 规范处理：
 * - 以 "data:" 开头的行为数据行；连续多个 data 行用 "\n" 拼接为一个事件
 * - 空行表示一个事件结束，产出拼接后的 payload
 * - 以 ":" 开头的行为注释/心跳，忽略
 * - payload 为 "[DONE]" 表示流结束（OpenAI 约定）
 */
class SseParser {
    private val dataBuffer = StringBuilder()
    private var hasData = false

    /**
     * 喂入一行（不含换行符）。
     * @return 该行终结的事件 payload；若行未终结事件则返回 null。
     *   返回 [SseEvent.Done] 表示收到 "[DONE]"。
     */
    fun feedLine(line: String): SseEvent? {
        if (line.isEmpty()) {
            // 空行：事件结束
            if (!hasData) return null
            val payload = dataBuffer.toString()
            dataBuffer.clear()
            hasData = false
            return if (payload == "[DONE]") SseEvent.Done else SseEvent.Data(payload)
        }
        if (line.startsWith(":")) return null // 注释/心跳
        if (line.startsWith("data:")) {
            var value = line.removePrefix("data:")
            if (value.startsWith(" ")) value = value.substring(1)
            if (hasData) dataBuffer.append('\n')
            dataBuffer.append(value)
            hasData = true
        }
        // event:/id:/retry: 等字段本场景不需要，忽略
        return null
    }

    /** 流结束时调用：把残留未被空行终结的数据吐出来（容错）。 */
    fun flush(): SseEvent? {
        if (!hasData) return null
        val payload = dataBuffer.toString()
        dataBuffer.clear()
        hasData = false
        return if (payload == "[DONE]") SseEvent.Done else SseEvent.Data(payload)
    }

    sealed interface SseEvent {
        data class Data(val payload: String) : SseEvent
        data object Done : SseEvent
    }
}
