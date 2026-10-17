package com.zola.aivideo.data.llm

import android.content.Context
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.domain.model.TranscriptSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 版本化 prompt 模板加载与组装（M3，独立实现）。
 *
 * 模板放在 `app/src/main/assets/prompts/`，纯文本，带版本号：
 * - system_prompt_v1.txt：system prompt，内置质量门禁指令
 * - user_prompt_v1.txt：用户消息模板，占位符 {target_language} {style} {glossary_section} {segment_count} {chunk_info} {segments}
 * - repair_prompt_v1.txt：修正重试模板，占位符 {issues} {segments}
 *
 * 分段行协议（LLM 必须遵守，解析器 [TranslationLineParser] 与之对应）：
 *   [序号] (开始时间-->结束时间) 文本
 * 时间格式与 [FormatUtils.formatTimestampMs] 一致（mm:ss.mmm）。
 */
@Singleton
class PromptLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 当前模板版本；升级模板时同步改这里并新增 _vN 文件。 */
    val version: String = PROMPT_VERSION

    fun loadSystemPrompt(): String = readAsset("prompts/system_prompt_v1.txt")

    fun loadUserTemplate(): String = readAsset("prompts/user_prompt_v1.txt")

    fun loadRepairTemplate(): String = readAsset("prompts/repair_prompt_v1.txt")

    /**
     * 组装一次翻译调用的 messages。
     * @param chunk 全局分段的子集（chunk 内分段保持原顺序）
     * @param globalOffset chunk 第一段在全局列表中的起始下标（用于序号连续）
     */
    fun buildMessages(
        chunk: List<TranscriptSegment>,
        globalOffset: Int,
        targetLanguage: TargetLanguage,
        style: TranslateStyle,
        glossarySection: String,
        chunkInfo: String,
    ): List<ChatMessage> {
        val segmentLines = chunk.mapIndexed { i, seg ->
            "[${globalOffset + i + 1}] " +
                "(${FormatUtils.formatTimestampMs(seg.startMs)}-->${FormatUtils.formatTimestampMs(seg.endMs)}) " +
                seg.text
        }.joinToString("\n")
        val user = loadUserTemplate()
            .replace("{target_language}", targetLanguage.displayName)
            .replace("{style}", style.instruction)
            .replace("{glossary_section}", glossarySection.ifBlank { "（无术语表）" })
            .replace("{segment_count}", chunk.size.toString())
            .replace("{chunk_info}", chunkInfo)
            .replace("{segments}", segmentLines)
        return listOf(ChatMessage.system(loadSystemPrompt()), ChatMessage.user(user))
    }

    /** 组装修正重试的 messages。 */
    fun buildRepairMessages(
        issues: String,
        segments: List<TranscriptSegment>,
        globalOffset: Int,
    ): List<ChatMessage> {
        val segmentLines = segments.mapIndexed { i, seg ->
            "[${globalOffset + i + 1}] " +
                "(${FormatUtils.formatTimestampMs(seg.startMs)}-->${FormatUtils.formatTimestampMs(seg.endMs)}) " +
                seg.text
        }.joinToString("\n")
        val user = loadRepairTemplate()
            .replace("{issues}", issues)
            .replace("{segments}", segmentLines)
        return listOf(ChatMessage.system(loadSystemPrompt()), ChatMessage.user(user))
    }

    /** 术语表注入片段：按目标语言分组后的 "原文→译文" 列表。 */
    fun buildGlossarySection(terms: List<GlossaryTerm>): String {
        if (terms.isEmpty()) return ""
        val lines = terms.joinToString("\n") { "  - ${it.source} → ${it.target}" }
        return "术语表（以下术语必须按给定译法翻译）：\n$lines"
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val PROMPT_VERSION = "v1"
    }
}

/** 术语表条目（领域模型，与 Room 实体解耦）。 */
data class GlossaryTerm(
    val id: Long = 0,
    val source: String,
    val target: String,
    /** 目标语言分组，如"英语"。 */
    val targetLang: String,
)
