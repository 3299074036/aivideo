package com.zola.aivideo.data.task

import com.google.gson.Gson
import com.zola.aivideo.data.llm.TargetLanguage
import com.zola.aivideo.data.llm.TranslateStyle

/**
 * 可持久化的翻译配置（M5）。
 *
 * Worker 运行时从任务实体的 `translateOptionsJson` 读出重建请求。
 * 注意：不含 LLM Key——Key 永远从 EncryptedSharedPreferences 现取，不落盘。
 *
 * TargetLanguage 是 sealed interface，Gson 无法直接反序列化，
 * 因此按 key 字符串存取（"Chinese"/"English"/…/"Custom:<name>"）。
 */
data class PersistedTranslateOptions(
    val langKey: String,
    val style: String,
    val useGlossary: Boolean,
    val bilingual: Boolean,
) {
    /** 还原 TargetLanguage。 */
    fun toTargetLanguage(): TargetLanguage = when {
        langKey.startsWith("Custom:") ->
            TargetLanguage.Custom(langKey.removePrefix("Custom:").ifBlank { "自定义语言" })
        else -> when (langKey) {
            "Chinese" -> TargetLanguage.Chinese
            "English" -> TargetLanguage.English
            "Japanese" -> TargetLanguage.Japanese
            "Korean" -> TargetLanguage.Korean
            "French" -> TargetLanguage.French
            "German" -> TargetLanguage.German
            "Spanish" -> TargetLanguage.Spanish
            else -> TargetLanguage.English
        }
    }

    /** 还原 TranslateStyle（非法值回退默认）。 */
    fun toStyle(): TranslateStyle = runCatching { TranslateStyle.valueOf(style) }
        .getOrDefault(TranslateStyle.COLLOQUIAL)

    fun toJson(gson: Gson): String = gson.toJson(this)

    companion object {
        fun from(
            targetLanguage: TargetLanguage,
            customLangName: String,
            style: TranslateStyle,
            useGlossary: Boolean,
            bilingual: Boolean,
        ): PersistedTranslateOptions {
            val key = when (targetLanguage) {
                is TargetLanguage.Custom ->
                    "Custom:${customLangName.ifBlank { targetLanguage.name }}"
                else -> targetLanguage.javaClass.simpleName
            }
            return PersistedTranslateOptions(key, style.name, useGlossary, bilingual)
        }

        fun fromJson(gson: Gson, json: String): PersistedTranslateOptions? =
            runCatching { gson.fromJson(json, PersistedTranslateOptions::class.java) }.getOrNull()
    }
}
