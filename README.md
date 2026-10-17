# 影译（AiVideo）

本机运行的 AI 视频处理 Android App：**导入 → 识别 → 翻译 → 配音 → 合成**。
App 名「影译」为暂定，包名 `com.zola.aivideo`。

- 技术方案：`docs/技术方案.md`
- 里程碑报告：`docs/M1-报告.md` ~ `docs/M6-报告.md`
- 第三方组件许可证：`docs/第三方组件许可证.md`
- 构建说明：`docs/构建说明.md`

## 许可证

本仓库代码以 **GPL-3.0** 授权（见 `LICENSE`），因 sherpa-onnx 内置的 espeak-ng（GPL-3.0）
随 APK 分发。源码分发清单见 `docs/源码分发清单.md`。

## 构建

### 环境要求

- JDK 17、Android SDK（含 platform-35/36）
- minSdk 26 / targetSdk 35 / compileSdk 36，`arm64-v8a` only

### 步骤

```bash
# 1. 恢复 gradle-wrapper.jar（GitHub 连接器无法推送二进制，此文件未进仓库）
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  "https://muse.ai/files/1411770905342312/2108789576675133/w5vb9njohswdvtp6tde5i5tf/gradle-wrapper-8.14.6.jar"

# 2. 拉取 sherpa-onnx AAR（~47MB，带 SHA-256 校验）
bash libs/fetch-sherpa-onnx.sh

# 3. 构建（需按 docs/构建说明.md 配置代理环境变量）
./gradlew assembleDebug --no-daemon

# 4. 测试
./gradlew testDebugUnitTest --no-daemon
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`

## 下载构建包

- [aivideo-debug-0.1.0.apk](https://muse.ai/files/1411770905342312/989273186802866/gxnc93f5bjygwkkm64f3841m/aivideo-debug-0.1.0.apk)（82.6MB，同 debug 签名，可直接覆盖安装；链接有效期至 2026-10-19）
- 建议将 APK 挂到 GitHub Release 附件做长期分发（本仓库连接器暂不支持上传 Release 二进制）

### 依赖说明

- FFmpeg：`dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`（LGPL-3.0，**严禁 `-gpl` 变体**）
- 端侧 ASR/TTS：sherpa-onnx 官方 AAR 1.13.8（Apache-2.0；其内置 espeak-ng 为 GPL-3.0）
- 全部 Key（LLM / 云 TTS / 素材库）均为 BYOK，存于 EncryptedSharedPreferences，绝不硬编码

### 调试签名

`app/build.gradle.kts` 优先使用 `../keystores/aivideo-debug.keystore`（仅开发机有，
storepass/keypass 均为 `android`）；不存在时自动回退 Android 默认调试签名。
