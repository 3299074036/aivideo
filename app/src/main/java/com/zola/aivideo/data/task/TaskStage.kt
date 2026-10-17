package com.zola.aivideo.data.task

/**
 * 流水线阶段（M5）。
 *
 * 顺序固定：识别 → 翻译 → 配音 → 合成。"导入/下载"在建任务时即完成，
 * 在步骤条上作为首个已完成步骤展示（虚拟阶段，不调度 Worker）。
 */
enum class TaskStage(val displayName: String) {
    /** 语音识别：端侧 ASR（Worker，无需用户介入）。 */
    TRANSCRIBE("识别"),

    /** 字幕翻译：LLM（Worker；跑之前需要用户在配置页确认参数）。 */
    TRANSLATE("翻译"),

    /** AI 配音：端侧/云端 TTS（Worker；跑之前需要用户在配置页确认参数）。 */
    DUB("配音"),

    /** 合成导出：FFmpeg（Worker；跑之前需要用户选择字幕模式与编码预设）。 */
    SYNTHESIZE("合成"),
}

/** 步骤条展示用的全部步骤（含虚拟的导入首步）。 */
enum class PipelineStep(val displayName: String) {
    IMPORT("下载"),
    TRANSCRIBE("识别"),
    TRANSLATE("翻译"),
    DUB("配音"),
    SYNTHESIZE("合成"),
}
