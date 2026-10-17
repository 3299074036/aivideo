package com.zola.aivideo.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TtsVoiceCatalog 单元测试（M4）：sid 唯一、官方映射一致性。
 */
class TtsVoiceCatalogTest {

    @Test
    fun `sids are unique`() {
        val sids = TtsVoiceCatalog.voices.map { it.sid }
        assertEquals(sids.size, sids.toSet().size)
    }

    @Test
    fun `names are unique`() {
        val names = TtsVoiceCatalog.voices.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `official sid mapping spot check`() {
        // 官方 kokoro-multi-lang-v1_1 音色表（2026-10-17 核实）
        assertEquals("af_maple", TtsVoiceCatalog.voiceOf(0)?.name)
        assertEquals("af_sol", TtsVoiceCatalog.voiceOf(1)?.name)
        assertEquals("bf_vale", TtsVoiceCatalog.voiceOf(2)?.name)
        assertEquals("zf_001", TtsVoiceCatalog.voiceOf(3)?.name)
        assertEquals("zf_008", TtsVoiceCatalog.voiceOf(10)?.name)
        assertEquals("zm_009", TtsVoiceCatalog.voiceOf(58)?.name)
        assertEquals("zm_015", TtsVoiceCatalog.voiceOf(64)?.name)
        // 中文音色语言标注
        assertEquals(TtsVoiceCatalog.VoiceLang.CHINESE, TtsVoiceCatalog.voiceOf(3)?.lang)
        assertEquals(TtsVoiceCatalog.VoiceLang.ENGLISH, TtsVoiceCatalog.voiceOf(0)?.lang)
    }

    @Test
    fun `default voice exists`() {
        assertNotNull(TtsVoiceCatalog.voiceOf(TtsVoiceCatalog.DEFAULT_SID))
    }

    @Test
    fun `display name fallback`() {
        assertTrue(TtsVoiceCatalog.displayNameOf(999).contains("999"))
    }
}
