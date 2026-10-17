package com.zola.aivideo.ui.settings

/**
 * 第三方组件清单（M6）：关于页「开源许可」的数据源。
 *
 * 与 docs/research/aivideo/第三方组件许可证.md 保持一致；
 * 改动任一处时同步另一处。
 *
 * @param name 组件名
 * @param license 许可证（SPDX 风格简称）
 * @param source 源码/主页 URL
 * @param note 备注（用途、合规说明）
 * @param assetPath assets 内许可证全文路径（可空；有则关于页提供"查看全文"）
 */
data class ThirdPartyLicense(
    val name: String,
    val license: String,
    val source: String,
    val note: String,
    val assetPath: String? = null,
)

object ThirdPartyLicenses {
    val all: List<ThirdPartyLicense> = listOf(
        ThirdPartyLicense(
            name = "espeak-ng（经 sherpa-onnx 原生库内置）",
            license = "GPL-3.0-or-later",
            source = "https://github.com/espeak-ng/espeak-ng",
            note = "Kokoro TTS 的音素处理必经 espeak-ng；APK 分发构成 combined work，" +
                "本应用整体以 GPL-3.0 分发。源码获取见本页上方声明。",
            assetPath = "licenses/espeak-ng-COPYING.txt",
        ),
        ThirdPartyLicense(
            name = "espeak-ng-data（随 Kokoro 模型按需下载）",
            license = "GPL-3.0-or-later",
            source = "https://github.com/espeak-ng/espeak-ng",
            note = "语音合成发音数据，存于应用私有目录，随卸载清除。",
        ),
        ThirdPartyLicense(
            name = "FFmpegKit（ffmpegkit-maintained，LGPL 构建）",
            license = "LGPL-3.0",
            source = "https://github.com/ffmpegkit-maintained/ffmpeg",
            note = "视频合成/转码。AAR 动态链接；未引入 -gpl 变体（不含 x264/x265）。",
        ),
        ThirdPartyLicense(
            name = "sherpa-onnx 1.13.8（AAR）",
            license = "Apache-2.0",
            source = "https://github.com/k2-fsa/sherpa-onnx",
            note = "端侧 ASR（Whisper/SenseVoice）与 Kokoro TTS 推理。",
        ),
        ThirdPartyLicense(
            name = "yt-dlp",
            license = "Unlicense",
            source = "https://github.com/yt-dlp/yt-dlp",
            note = "链接批量下载。Unlicense（公有领域式），无传染性；" +
                "仅用于公开可访问内容，遵守平台服务条款。",
        ),
        ThirdPartyLicense(
            name = "Kokoro-82M 模型权重（按需下载）",
            license = "Apache-2.0",
            source = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_1",
            note = "端侧 TTS 语音模型，存于应用私有目录，随卸载清除。",
        ),
        ThirdPartyLicense(
            name = "Whisper 模型权重（按需下载）",
            license = "MIT",
            source = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny",
            note = "端侧 ASR 语音识别模型。",
        ),
        ThirdPartyLicense(
            name = "SenseVoice 模型权重（按需下载）",
            license = "上游许可（FunAudioLLM/SenseVoice）",
            source = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09",
            note = "端侧 ASR 语音识别模型，遵循上游项目许可。",
        ),
        ThirdPartyLicense(
            name = "OkHttp",
            license = "Apache-2.0",
            source = "https://square.github.io/okhttp/",
            note = "网络请求（模型下载、LLM/TTS/素材 API）。",
        ),
        ThirdPartyLicense(
            name = "Gson",
            license = "Apache-2.0",
            source = "https://github.com/google/gson",
            note = "JSON 解析。",
        ),
        ThirdPartyLicense(
            name = "Jetpack Compose / Material3 / Navigation",
            license = "Apache-2.0",
            source = "https://developer.android.com/jetpack/compose",
            note = "UI 框架。",
        ),
        ThirdPartyLicense(
            name = "Hilt",
            license = "Apache-2.0",
            source = "https://dagger.dev/hilt/",
            note = "依赖注入。",
        ),
        ThirdPartyLicense(
            name = "Room",
            license = "Apache-2.0",
            source = "https://developer.android.com/training/data-storage/room",
            note = "本地数据库（术语表、任务）。",
        ),
        ThirdPartyLicense(
            name = "WorkManager",
            license = "Apache-2.0",
            source = "https://developer.android.com/topic/libraries/architecture/workmanager",
            note = "长任务调度（转写/翻译/配音/合成）。",
        ),
        ThirdPartyLicense(
            name = "Security Crypto",
            license = "Apache-2.0",
            source = "https://developer.android.com/topic/security/data",
            note = "API Key 加密存储（EncryptedSharedPreferences）。",
        ),
    )
}
