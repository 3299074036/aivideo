package com.zola.aivideo.data.llm

/**
 * LLM 服务商预设（M7）。
 *
 * 全部为 OpenAI 兼容接口（/v1/chat/completions），点选后一键填入 Base URL 与模型候选；
 * Key 仍由用户自备（BYOK），此处只给官网链接与计费提示，不存任何密钥。
 * 地址/模型名变更时只改此处。
 */
data class LlmProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    /** 模型名候选（第一个为推荐，点选预设时自动填入）。 */
    val models: List<String>,
    /** 获取 Key 的官网链接。 */
    val keyUrl: String,
    /** 计费/额度一句话提示。 */
    val keyHint: String,
)

object LlmProviderCatalog {

    val SILICONFLOW = LlmProvider(
        id = "siliconflow",
        displayName = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        models = listOf(
            "deepseek-ai/DeepSeek-V3",
            "Qwen/Qwen2.5-7B-Instruct",
            "deepseek-ai/DeepSeek-R1",
        ),
        keyUrl = "https://cloud.siliconflow.cn/",
        keyHint = "新用户有免费额度；LLM + 云 TTS 一站式，一个 Key 全包",
    )

    val DEEPSEEK = LlmProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        keyUrl = "https://platform.deepseek.com/",
        keyHint = "价格便宜，翻译质量好；无 TTS 服务",
    )

    val QWEN = LlmProvider(
        id = "qwen",
        displayName = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        keyUrl = "https://bailian.console.aliyun.com/",
        keyHint = "阿里百炼，国内访问稳定",
    )

    val ZHIPU = LlmProvider(
        id = "zhipu",
        displayName = "智谱",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf("glm-4-flash", "glm-4"),
        keyUrl = "https://open.bigmodel.cn/",
        keyHint = "glm-4-flash 有免费额度，可先体验",
    )

    val KIMI = LlmProvider(
        id = "kimi",
        displayName = "Kimi",
        baseUrl = "https://api.moonshot.cn/v1",
        models = listOf("kimi-k2", "moonshot-v1-8k"),
        keyUrl = "https://platform.moonshot.cn/",
        keyHint = "长文本能力强",
    )

    /** 自定义：任何 OpenAI 兼容接口，走原有手填流程。 */
    val CUSTOM = LlmProvider(
        id = "custom",
        displayName = "自定义",
        baseUrl = "",
        models = emptyList(),
        keyUrl = "",
        keyHint = "任何 OpenAI 兼容的 /v1/chat/completions 接口",
    )

    val all: List<LlmProvider> =
        listOf(SILICONFLOW, DEEPSEEK, QWEN, ZHIPU, KIMI, CUSTOM)

    fun byId(id: String?): LlmProvider =
        all.firstOrNull { it.id == id } ?: CUSTOM
}
