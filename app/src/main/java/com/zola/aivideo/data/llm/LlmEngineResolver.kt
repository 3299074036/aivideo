package com.zola.aivideo.data.llm

import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM 引擎选择器（M7）：按用户在设置页选择的引擎返回对应实现。
 *
 * 调用方（[SubtitleTranslator]）每次翻译时取一次，切换引擎即时生效，无需重启。
 */
@Singleton
class LlmEngineResolver @Inject constructor(
    private val apiEngine: ApiLlmEngine,
    private val localEngine: LocalLlmEngine,
    private val prefs: LlmPreferences,
) {
    suspend fun current(): LlmEngine =
        when (prefs.getLlmEngine()) {
            LlmEngineId.LOCAL -> localEngine
            LlmEngineId.API -> apiEngine
        }
}
