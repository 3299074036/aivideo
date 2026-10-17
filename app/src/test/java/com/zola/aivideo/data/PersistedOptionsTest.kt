package com.zola.aivideo.data

import com.google.gson.Gson
import com.zola.aivideo.data.synthesize.BitrateTier
import com.zola.aivideo.data.synthesize.EncodePreset
import com.zola.aivideo.data.synthesize.SubtitleMode
import com.zola.aivideo.data.synthesize.SynthesizeOptions
import com.zola.aivideo.data.llm.TargetLanguage
import com.zola.aivideo.data.llm.TranslateStyle
import com.zola.aivideo.data.task.PersistedTranslateOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务配置持久化编解码单元测试（M5）：纯 JVM。
 *
 * 翻译配置走 key 字符串（TargetLanguage 是 sealed interface，Gson 无法直接处理）；
 * 配音/合成配置是普通 data class/enum，直接 Gson。
 */
class PersistedOptionsTest {

    private val gson = Gson()

    @Test
    fun `翻译配置_中英互译往返编解码`() {
        val original = PersistedTranslateOptions.from(
            targetLanguage = TargetLanguage.English,
            customLangName = "",
            style = TranslateStyle.COLLOQUIAL,
            useGlossary = true,
            bilingual = true,
        )
        val restored = PersistedTranslateOptions.fromJson(gson, original.toJson(gson))!!
        assertEquals(TargetLanguage.English, restored.toTargetLanguage())
        assertEquals(TranslateStyle.COLLOQUIAL, restored.toStyle())
        assertTrue(restored.useGlossary)
        assertTrue(restored.bilingual)
    }

    @Test
    fun `翻译配置_自定义语言名往返`() {
        val original = PersistedTranslateOptions.from(
            targetLanguage = TargetLanguage.Custom("泰语"),
            customLangName = "泰语",
            style = TranslateStyle.LITERAL,
            useGlossary = false,
            bilingual = false,
        )
        assertEquals("Custom:泰语", original.langKey)
        val restored = PersistedTranslateOptions.fromJson(gson, original.toJson(gson))!!
        assertEquals(TargetLanguage.Custom("泰语"), restored.toTargetLanguage())
    }

    @Test
    fun `翻译配置_损坏 JSON 返回 null 不崩`() {
        assertNull(PersistedTranslateOptions.fromJson(gson, "{not json"))
        assertNull(PersistedTranslateOptions.fromJson(gson, ""))
    }

    @Test
    fun `翻译配置_非法风格回退默认`() {
        val p = PersistedTranslateOptions("English", "NO_SUCH_STYLE", false, false)
        assertEquals(TranslateStyle.COLLOQUIAL, p.toStyle())
    }

    @Test
    fun `合成配置_Gson 往返编解码`() {
        val original = SynthesizeOptions(
            subtitleMode = SubtitleMode.EXTERNAL,
            encodePreset = EncodePreset.COMPRESSED,
            bitrateTier = BitrateTier.HIGH,
        )
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, SynthesizeOptions::class.java)
        assertEquals(original, restored)
    }

    @Test
    fun `合成配置_损坏 JSON 时调用方可回退默认`() {
        val restored = runCatching {
            gson.fromJson("{bad", SynthesizeOptions::class.java)
        }.getOrNull()
        // Gson 对坏 JSON 可能抛异常或返回部分对象；调用方统一 runCatching 回退默认
        val safe = restored ?: SynthesizeOptions()
        assertEquals(SubtitleMode.BURN, safe.subtitleMode)
    }
}
