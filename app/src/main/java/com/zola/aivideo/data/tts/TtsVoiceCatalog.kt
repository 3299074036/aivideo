package com.zola.aivideo.data.tts

/**
 * Kokoro 音色目录（M4）。
 *
 * sid↔音色名映射来自 sherpa-onnx 官方文档
 * （https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html，
 * kokoro-multi-lang-v1_1，103 音色，24kHz，2026-10-17 核实）。
 *
 * v1_1 的英文音色只有 3 个（af_maple/af_sol/bf_vale），其余 100 个为中文音色
 * （zf_* 中文女声 55 个，zm_* 中文男声 45 个）。此处收录 curated 子集
 * （中英各若干，覆盖男女声）；全部 103 个 sid 均可用，如需完整列表
 * 可按官方映射表扩展。
 */
object TtsVoiceCatalog {

    /** 音色语言。 */
    enum class VoiceLang(val label: String) {
        CHINESE("中文"),
        ENGLISH("英文"),
    }

    /** 单个音色。 */
    data class Voice(
        /** sherpa-onnx sid（generate 的 speaker id 参数）。 */
        val sid: Int,
        /** 官方音色名。 */
        val name: String,
        val lang: VoiceLang,
        /** 展示用中文标签。 */
        val displayName: String,
    )

    val voices: List<Voice> = listOf(
        // 英文（官方仅 3 个）
        Voice(0, "af_maple", VoiceLang.ENGLISH, "英文女声 · Maple"),
        Voice(1, "af_sol", VoiceLang.ENGLISH, "英文女声 · Sol"),
        Voice(2, "bf_vale", VoiceLang.ENGLISH, "英式女声 · Vale"),
        // 中文女声（官方 zf_001..zf_099，sid 3..57）
        Voice(3, "zf_001", VoiceLang.CHINESE, "中文女声 · 001"),
        Voice(10, "zf_008", VoiceLang.CHINESE, "中文女声 · 008"),
        Voice(20, "zf_028", VoiceLang.CHINESE, "中文女声 · 028"),
        Voice(28, "zf_044", VoiceLang.CHINESE, "中文女声 · 044"),
        Voice(40, "zf_073", VoiceLang.CHINESE, "中文女声 · 073"),
        // 中文男声（官方 zm_009..zm_100，sid 58..102）
        Voice(58, "zm_009", VoiceLang.CHINESE, "中文男声 · 009"),
        Voice(64, "zm_015", VoiceLang.CHINESE, "中文男声 · 015"),
        Voice(72, "zm_034", VoiceLang.CHINESE, "中文男声 · 034"),
        Voice(76, "zm_045", VoiceLang.CHINESE, "中文男声 · 045"),
        Voice(84, "zm_058", VoiceLang.CHINESE, "中文男声 · 058"),
    )

    /** 默认音色：中文女声 zf_001（sid 3）。 */
    val DEFAULT_SID = 3

    fun voiceOf(sid: Int): Voice? = voices.firstOrNull { it.sid == sid }

    fun displayNameOf(sid: Int): String =
        voiceOf(sid)?.displayName ?: "音色 #$sid"
}
