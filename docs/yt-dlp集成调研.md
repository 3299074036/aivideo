调研完成。所有关键问题都已找到有来源的答案。以下是结构化调研结论。

---

# yt-dlp Android 集成调研结论（影译 App，com.zola.aivideo）

## 核心先验结论（一句话）

**官方 yt-dlp 没有可直接在 Android 上运行的原生二进制**（`yt-dlp_linux_aarch64` 是 glibc 构建，Android 的 bionic 跑不了）。业界唯一被验证可行的路线（Seal / youtubedl-android，数万 star、F-Droid 在架）是：**APK 内置 bionic 版 CPython 解释器 + 运行时下载官方 `yt-dlp` 纯 Python 归档（zipimport，需解释器）+ ProcessBuilder 调用**。这条路线同时满足「零新增 build.gradle 第三方库」和「独立更新通道」两个硬约束。

---

## 1. 可信的二进制/归档来源

### 1.1 官方 releases 里真正能用的东西：`yt-dlp`（zipimport 归档），不是 `yt-dlp_linux_aarch64`
- 官方 releases 的资产清单（以 `2026.08.19` 版、GitHub API 实测为准）：
  - `yt-dlp` —— **平台无关的 zipimport Python 归档，需要解释器**，`size: 3072469`（约 **2.9 MB**），`sha256: 1fa6733c…`（API 直接给出每资产 digest）
  - `yt-dlp_linux_aarch64` —— Linux **glibc 2.17+** standalone aarch64 二进制，约 **38.3 MB**（`size: 40167448`），**在 Android 上无法运行**（见 1.3）
  - `SHA2-256SUMS` / `SHA2-256SUMS.sig`（GPG 签名）随每版发布，可做校验
  - 下载 URL 模板（实测有效）：`https://github.com/yt-dlp/yt-dlp/releases/download/<TAG>/yt-dlp`；latest 简写：`https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp`
- 来源：
  - https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest （资产名/大小/digest/browser_download_url 实测）
  - https://pypi.org/project/yt-dlp/2024.3.9.232714.dev0/ （Release Files 表：`yt-dlp` = "Platform-independent zipimport binary. Needs Python"；`yt-dlp_linux_aarch64` = "Linux standalone aarch64"）

### 1.2 Seal 是怎么做的
Seal（JunkFood02/Seal，~30k star）**没有自己下载原生二进制**，它依赖 `youtubedl-android` 库（现由 JunkFood02 维护），机制如下（已读源码）：
- 更新检查：`GET https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest`（stable 通道；另有 `yt-dlp/yt-dlp-nightly-builds` 和 `yt-dlp/yt-dlp-master-builds` 两个通道），取 `tag_name` 与本地 SharedPrefs 存的版本比对
- 下载：在 release assets 里找 `name` 恰好等于 `"yt-dlp"` 的资产（即上面的 zipimport 归档），用其 `browser_download_url` 下载，安装到 `noBackupFilesDir` 下的 yt-dlp 目录；失败自动回滚到内置默认版本
- 执行：`ProcessBuilder(<python解释器>, <yt-dlp归档>, args…)` —— 解释器是随 AAR 发版的 bionic 版 Python（见第 2 节）
- 来源：
  - https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDLUpdater.kt
  - https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt
  - https://github.com/JunkFood02/Seal （README Credits: "based on youtubedl-android"）

### 1.3 为什么不能用 `yt-dlp_linux_aarch64`
- 它是 PyInstaller 打包的 **glibc 动态链接**二进制；Android 用 bionic，无 `/lib/ld-linux-aarch64.so.1`，exec 直接失败。这是 Linux/glibc vs Android/bionic 的根本 ABI 差异。
- 独立第三方调研同样认定：该资产 "must not be treated as an Android runtime"，Termux 的包体系也因 prefix 硬编码无法搬到普通 App 内复用。
- 来源：
  - https://copyprogramming.com/howto/android-static-linking-vs-dynamic-linking-against-glibc （"Android exclusively uses Bionic… Glibc is not available in the Android NDK"）
  - https://github.com/zhaojiaxiang97-crypto/reclipmac/blob/HEAD/docs/tickets/android/04-android-runtime-tools.md （同上独立结论）
  - https://github.com/yausername/youtubedl-android （README Docs 节：官方 Android 安装指引指向 Termux；其 prebuilt 包 "android5+/android7+" 均不可直接复用）

### 1.4 yausername / termux 相关（任务点名项）
- **yausername** = `yausername/youtubedl-android`（现属 JunkFood02 名下，Maven：`io.github.junkfood02.youtubedl-android`）。**结论：不能直接用**——双重出局：(1) 它是 GPL-3.0 许可证的库（仓库 License 页明示），违反本任务 GPL 禁令；(2) 它是 build.gradle 第三方依赖，违反零新增依赖约束。但它的**架构是本调研推荐方案的事实标准参照**。(3) 它内置的 Python 是 **3.8，已过时**——官方要求 Python 3.9+（见 5.2），新版 yt-dlp 在 3.8 上跑不起来。
  - 来源：https://github.com/yausername/youtubedl-android （License: GPL-3.0；README: "yt-dlp executable and python 3.8 are bundled"）
  - https://pypi.org/project/yt-dlp/2024.12.6.161513.dev0/ （"Python versions 3.9+ (CPython)… are supported"）
- **termux**：官方把 Android CLI 用户指引到 Termux（`pkg install yt-dlp`），但其 Python/包体系的 prefix 硬编码在 Termux 应用私有目录下，**不能作为普通 App 的运行时复用**。
- 另：`xibr/ytdlp-lazy`（曾为 youtubedl-android 构建 lazy-extractors 版 yt-dlp）已声明自 2022.08.14 起不再使用，因为**官方 releases 已默认启用 lazy extractors**（启动更快、内存更小——我们直接用官方归档即享受该优化）。
  - 来源：https://github.com/xibr/ytdlp-lazy

---

## 2. 在 Android 上执行的方式（API 26~35）

### 2.1 关键限制：API 29+ 不能从应用可写私有目录执行 ELF
多方来源一致结论：自 Android 10（API 29）起，**不能执行写入应用私有数据目录（filesDir/cacheDir 等）的二进制文件**（W^X / SELinux 拦截，典型报错 `avc: denied { execute } … tcontext=u:object_r:app_data_file:s0`）。可执行文件唯一合法位置是 **`nativeLibraryDir`**——即随 APK 以 native library 形式安装（文件名形如 `libXXX.so`，`android:extractNativeLibs="true"`）。
- 这正是 youtubedl-android 把 Python 解释器伪装成 `libpython.so` 打进 APK 的原因；Seal/它 ainsi 在 Android 14 上正常工作。
- 对本任务的直接推论：**Python 解释器必须随 APK 内置（jniLibs → nativeLibraryDir），不能运行时下载到 filesDir 再 chmod +x**（后者在 API 29+ 上会被 SELinux 拒绝；且 nativeLibraryDir 下的文件安装器已自带可执行权限，无需 chmod）。
- 来源：
  - https://copyprogramming.com/howto/running-shell-commands-on-android-using-runtime-getruntime （"Android 10 (API 29) and Higher: Apps cannot execute files from the app's writable home directory—a W^X violation… Executable binaries must be embedded in your APK"）
  - http://dev.to/pocketcode/how-i-got-postgresql-18-running-inside-an-android-app-5chj （"since Android 10 you can't execute anything you write into your own data folder. SELinux cuts you off… the only place the system lets you run binaries is nativeLibraryDir"）
  - https://forum.godotengine.org/t/os-execute-not-working-on-android/45038 （同结论，引用 StackOverflow 64786837）
  - https://www.iopenv.com/3A5A8U78F/Adding-Selinux-permissionscommon-Selinux-permission-issues （真实 `avc: denied { execute }` on `app_data_file` 案例）
  - https://github.com/yausername/youtubedl-android （README 要求 `android:extractNativeLibs="true"`；源码 `binDir = applicationInfo.nativeLibraryDir`）

### 2.2 执行范式（照抄 youtubedl-android，经生产验证）
```text
ProcessBuilder(<nativeLibraryDir>/libpython.so, <noBackupFilesDir>/yt-dlp, args…)
```
- yt-dlp 归档本身**不被直接执行**（它是传给解释器的脚本参数），所以不需要可执行权限、不触碰 W^X 红线。
- 必须设置的环境变量（源码实测清单）：`LD_LIBRARY_PATH`（python 的 usr/lib）、`SSL_CERT_FILE`（自带 cert.pem，Python 在 Android 上无系统 CA 概念）、`PATH`（追加 nativeLibraryDir）、`PYTHONHOME`、`HOME`（指向受控目录，避免 yt-dlp 读写不可写位置）、`TMPDIR`（cacheDir）。
- 默认追加 `--no-cache-dir`（除非用户显式指定）；stdout/stderr 双线程消费、支持按 processId 取消（`destroy()` + 子进程清理）。
- 来源：https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt （`executeImpl` 全函数）

### 2.3 Google Play 政策（本 App 目标上架 Play，必须看）
- Play《设备和网络滥用》政策原文：*"An app distributed via Google Play may not … download executable code (such as dex, JAR, .so files) from a source other than Google Play."*
- **但有明确例外**：*"This restriction does not apply to code that runs in a virtual machine or an interpreter … (such as JavaScript in a webview or browser)"*，且点名 *"interpreted languages (JavaScript, Python, Lua, etc.) loaded at run time"*。
- 推论（利好本方案）：**解释器随 APK 内置 + 运行时只下载 yt-dlp 纯 Python 归档（解释型代码）落在政策例外内**；反之，若运行时下载 ELF 原生二进制则直接撞政策红线。这也是推荐架构的又一决定性理由。
- 残余风险（需用户知晓）：政策另有 "apps that access or use a service or API in a manner that violates its terms of service" 条款；下载器类应用与 YouTube ToS 的关系是灰色地带——Seal 选择不上架 Play（仅 GitHub/F-Droid）即是例证。
- 来源：https://support.google.com/googleplay/android-developer/answer/14906471?hl=en&ref=hackernoon.com&visit_id=639253765701663664-120612902&rd=1

---

## 3. 体积影响

| 组件 | 体积 | 是否进 APK | 来源 |
|---|---|---|---|
| `yt-dlp` 归档（官方） | **约 2.9 MB**（2026.08.19 实测 3,072,469 B） | 否，运行时按需下载/更新 | api.github.com 实测 JSON |
| CPython 解释器 + 标准库（arm64，需自建，见 6） | 约 **11~14 MB**（youtubedl-android AAR 56.5 MB / 4 ABI ≈ 14.8 MB/ABI，含约 3 MB 的 yt-dlp 及 quickjs，取整估算） | **是**（jniLibs → nativeLibraryDir） | Maven Central 实测：`library-0.18.1.aar` = 59,213,110 B（第三方合规文档记录） |
| quickjs（JS 挑战求解，见 5.4） | 约 1 MB（估计值） | 是 | — |
| ffmpeg | **0**（不另行内置，用工程现有 ffmpeg-kit 见 5.3） | — | — |

- **结论**：APK 增量约 **12~16 MB**（arm64 only；注意 `extractNativeLibs=true` 会使安装占用接近双倍，即约 +25 MB 已安装体积）；运行时更新下载仅 **2.9 MB/次**，对更新通道毫无压力。**你的理解是对的：yt-dlp 本体不进 APK，运行时按需下载。**
- 来源：
  - https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest
  - https://github.com/sysadmindoc/aura/blob/HEAD/docs/legal/native-compliance.md （AAR 字节数实测表）
  - https://github.com/debzroot/debz-ai-agent/blob/HEAD/skills/software-development/android-apk-ci-build/references/ytdl-android-embed.md （"release APK ≈ 160 MB" for 3 ABIs → 印证量级）

---

## 4. 独立更新通道设计建议

照搬 youtubedl-android/Seal 的生产验证设计（源码级），适配本工程现有设施：

1. **版本检查**：`GET https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest` 取 `tag_name`（如 `2026.08.19`），与 EncryptedSharedPreferences（复用现有凭据存储模式）中存的已安装 tag 比对。备选：`https://pypi.org/pypi/yt-dlp/json` 取 `info.version`（无严格限流）。注意 GitHub API 未认证限流 60 次/小时/IP——检查频率设为**每日一次 + 手动触发**即足够（yt-dlp 发版节奏约每周）。
   - 通道选择：默认 **stable**；建议给高级用户加 **nightly** 开关（`yt-dlp/yt-dlp-nightly-builds`，官方称 nightly 为 regular users 推荐通道，因 extractor 坏得快）。master 通道不建议。
2. **更新提示时机**：App 启动/设置页「yt-dlp 版本」行触发检查；有新版弹对话框（显示新旧版本号 + 约 2.9 MB 大小）；**仅 Wi-Fi 下载**（复用 ModelDownloadManager 已有开关模式）；下载前二次确认。
3. **下载与校验**：复用 `data/download/ModelDownloadManager.kt` 的 OkHttp Range 断点续传模式；下载到临时文件 → **SHA-256 校验**（对照 GitHub API 该资产的 `digest` 字段，或下载 `SHA2-256SUMS` 比对；官方每版附 `SHA2-256SUMS` + GPG 签名，但设备端无 GPG 工具链，SHA-256 + HTTPS 传输是务实方案）→ 校验通过后原子 rename 替换 → 更新 SharedPrefs 中的 tag。失败则保留旧版（参考 YoutubeDLUpdater 的回滚逻辑）。
4. **版本真相来源**：以 GitHub `tag_name` 为准；`yt-dlp --version` 可作本地二次核验（跑一次解释器，开销可接受）。
- 来源：
  - https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDLUpdater.kt
  - https://pypi.org/project/yt-dlp/2025.1.28.232803.dev0/ （stable/nightly/master 三通道说明，`--update-to` 语义）

---

## 5. Android 上运行的已知限制

1. **必须用 bionic 版 Python + 官方 zipimport 归档**；官方 standalone 二进制（glibc）在 Android 上无法运行（第 1.3 节）。
2. **Python ≥ 3.9**（官方要求；3.9 已 EOL，建议直接构建 **3.12**，EOL 到 2028 年）。youtubedl-android 内置的 3.8 对新版 yt-dlp 已不够用——这是"照抄 Seal"时必须修正的点。
3. **API 29+ 可执行文件必须随 APK**（第 2.1 节）；`extractNativeLibs="true"`；NDK r27+ 构建保证 16KB 页对齐（Android 15+ 上架要求；工程 ffmpeg-kit 的 .so 已满足同标准）。
4. **ffmpeg 依赖**：yt-dlp 合并分流音视频（DASH 的 bv+ba）**必须**要一个真实可执行的 ffmpeg 文件（`--ffmpeg-location` 指向文件路径）。**工程现有的 ffmpeg-kit 是进程内 JNI 调用，没有可执行文件，不能被 `--ffmpeg-location` 复用。** 务实方案：**yt-dlp 只负责提取直链/分别下载视频流与音频流（两次调用或 `-f bv` / `-f ba`），合并/转码走现有 ffmpeg-kit**——彻底避开"再下载一个 ffmpeg 二进制"（那会同时撞上 2.1 的执行限制和 2.3 的 Play 政策）。
---

## 6. 集成状态（M6，2026-10-17）

### 6.1 已实现（框架代码，单元测试覆盖）

- `data/ytdlp/`：`YtDlpPreferences`（stable/nightly 双通道、已安装 tag、
  自动检查、仅 Wi-Fi）、`YtDlpEnvironment`（解释器路径解析、
  ProcessBuilder + 生产验证环境变量）、`YtDlpUpdater`（GitHub API 版本检查、
  SHA-256 校验、原子替换、失败回滚）、`YtDlpDownloader`
  （两遍格式策略：合流优先 / 分流下载后 ffmpeg-kit 合并、`[download] %` 解析、
  取消）、`YtDlpModels`（release 解析只选官方 zipimport 归档）。
- `di/YtDlpModule`（@Named("ytdlp") OkHttpClient）；`ui/ytdlp/`
  （环境状态卡、更新通道 UI、批量下载队列、完成后经 FileProvider 导入任务）；
  导航 `Routes.YTDLP`；设置页入口。
- 零新增 build.gradle 第三方库；M5 任务模型 DOWNLOAD 段即"导入"步，
  下载完成经导入页创建任务进入流水线。

### 6.2 待办：Python 解释器二进制（构建配方）

现状：框架已按"解释器随 APK 内置"实现（`YtDlpEnvironment.hasInterpreter()`），
但本机构建**尚未包含**解释器（交叉编译 CPython + OpenSSL 是独立大任务，
且沙箱 x86_64 无法冒烟测试 arm64 二进制——带病上线风险高于收益）。
界面在缺失时明确提示，不静默失败。

构建配方（后续跟进时执行）：

1. **OpenSSL**（yt-dlp 需要 HTTPS）：NDK 30 toolchain，
   `./Configure android-arm64 -D__ANDROID_API__=26 --prefix=$OUT/openssl`，
   `make && make install`。
2. **CPython 3.12**（官方要求 3.9+；3.12 EOL 到 2028）：
   NDK clang（`aarch64-linux-android26-clang`），
   `./configure --host=aarch64-linux-android --build=x86_64-linux-gnu
   --disable-test-modules --without-ensurepip
   --with-openssl=$OUT/openssl`；
   按需 `--disable-` 不需要的模块（tkinter/curses 必然关；保留 ssl、
   hashlib、zlib、_socket）。
   参考：termux-packages 的 `packages/python/build.sh`（bionic 补丁集），
   但**不要**其 prefix 硬编码——安装到可重定位目录。
3. **标准库**：`python3.12.zip`（Lib 打包）进 `assets/` 或随解释器包；
   运行时解压到 `filesDir/ytdlp/`，`PYTHONHOME`/`PYTHONPATH` 指向它。
4. **APK 内置**：解释器改名 `app/src/main/jniLibs/arm64-v8a/libpyinterpreter.so`
   （youtubedl-android 同款伪装；nativeLibraryDir 下安装器自带可执行权限）。
5. **manifest**：`android:extractNativeLibs="true"`（注意：这会让**所有**
   .so 解压到文件系统，ffmpeg-kit 的 .so 也会翻倍计安装体积——打包时评估）。
6. **16KB 页对齐**：NDK r27+ 默认 `-Wl,-z,max-page-size=16384`，
   NDK 30 满足 Play 要求；打包后按 M1 方法抽查 `p_align`。
7. **cacert.pem**：随解释器包提供一份 CA  bundle（`ytdlp/cacert.pem`，
   `YtDlpEnvironment` 已预留 `SSL_CERT_FILE` 逻辑），来源如 certifi。
8. 验证：真机跑 `yt-dlp --version`（`YtDlpUpdater.localVersion()` 已实现），
   再跑一次真实小视频下载。

### 6.3 真机验证清单（解释器就绪后）

- 更新通道：检查更新 → 下载 2.9MB 归档 → SHA-256 校验 → 版本号变化
- 批量下载：合流视频（单文件）与分流视频（下载后 ffmpeg-kit 合并）各一次
- 导入到任务：下载完成 → 导入页 → 创建任务 → 识别阶段可跑
- 移动数据二次确认、仅 Wi-Fi 开关、取消下载
