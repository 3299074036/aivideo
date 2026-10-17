package com.zola.aivideo.data.llm

/**
 * 术语表后处理二次替换（M3，独立实现）：纯 Kotlin，可 JVM 单元测试。
 *
 * 把译文里残留的原文术语换成目标译法（LLM 有时不遵守 prompt 里的术语表）。
 * - 按源术语长度降序替换，避免短词先吞掉长词（如 "learning" 先于 "machine learning"）
 * - 译文已含目标译法时跳过，避免重复替换
 * - 空术语忽略
 */
object GlossaryPostProcessor {
    fun apply(translatedText: String, terms: List<GlossaryTerm>): String {
        var out = translatedText
        terms.sortedByDescending { it.source.length }.forEach { term ->
            if (term.source.isBlank() || term.target.isBlank()) return@forEach
            if (out.contains(term.target)) return@forEach
            out = out.replace(term.source, term.target)
        }
        return out
    }
}
