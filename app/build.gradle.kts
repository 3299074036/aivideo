plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zola.aivideo"
    // compileSdk 36（与视记同组合，保证 Compose BOM 2026.06.01 可用）；targetSdk 35
    compileSdk = 36
    // 本地 LLM（llama.cpp）用 NDK 30（与视记一致）
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.zola.aivideo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // arm64-v8a only：真机优先；x86_64 仅模拟器调试需要时再加。
        // 注意：ffmpeg-kit AAR 自带 arm64-v8a + x86_64，本工程只取 arm64-v8a。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // 调试包使用项目固定 debug keystore（codes/keystores/aivideo-debug.keystore，
    // 密码 android/android，仅开发调试用，禁止用于发布）。keystore 不存在时回退默认调试签名。
    // 注意：rootProject 目录即 codes/aivideo，因此用 ../keystores 定位到 codes/keystores。
    val debugKeystore = rootProject.file("../keystores/aivideo-debug.keystore")
    signingConfigs {
        if (debugKeystore.exists()) {
            create("aivideoDebug") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "aivideo-debug"
                keyPassword = "android"
            }
        }
        // 发布签名模板（默认禁用）：不要把 keystore 与密码提交到仓库。
        // 构建时通过环境变量传入密码（不要写在 gradle 文件里）：
        //      RELEASE_STORE_PASSWORD=... RELEASE_KEY_PASSWORD=... ./gradlew assembleRelease
        // create("aivideoRelease") {
        //     storeFile = rootProject.file("../keystores/aivideo-release.keystore")
        //     storePassword = System.getenv("RELEASE_STORE_PASSWORD")
        //     keyAlias = "aivideo"
        //     keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        // }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("aivideoDebug")?.let { signingConfig = it }
        }
        release {
            // M5 起按需启用代码与资源压缩；proguard-rules.pro 已为 FFmpegKit 准备 keep 规则。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 本地 LLM：llama.cpp JNI（M7），cmake 配置见 app/src/main/cpp/CMakeLists.txt
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM 统一管理版本（含 material3 1.4.0，即 M3 Expressive 稳定版）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room（M1 仅接入依赖 + 极简 Task 实体占位，完整任务中心在 M5 落地）
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // JVM 单元测试
    testImplementation(libs.junit)

    // 网络：Retrofit + OkHttp（M1 接依赖，下载/LLM 在后续里程碑落地）
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // FFmpeg（M1 核心）：ffmpegkit-maintained 社区维护分支 LGPL 构建。
    // 严禁 -gpl 变体。AAR 内含 arm64-v8a + x86_64 .so，abiFilters 只取 arm64-v8a。
    // 注意：maintained 分支 POM 漏声明 smart-exception-java 传递依赖，显式补上，
    // 否则 native 加载失败时异常处理分支报 NoClassDefFoundError（2026-10-17 真机实锤）。
    implementation(libs.ffmpeg.kit.full)
    implementation(libs.smart.exception.java)

    // 端侧 ASR（M2）：sherpa-onnx 官方 AAR（Apache-2.0），已内置 onnxruntime，
    // 禁止再引入 onnxruntime-android。AAR 解析顺序：workspace 级 dependencies/sherpa-onnx/
    //（本地开发）→ 仓库内 libs/（远端克隆，用 bash libs/fetch-sherpa-onnx.sh 拉取，带 SHA-256 校验）。
    val sherpaAar = rootProject.file("../../dependencies/sherpa-onnx/sherpa-onnx-1.13.8.aar")
        .takeIf { it.exists() }
        ?: rootProject.file("libs/sherpa-onnx-1.13.8.aar")
            .takeIf { it.exists() }
        ?: error("未找到 sherpa-onnx AAR：请在仓库根目录执行 bash libs/fetch-sherpa-onnx.sh")
    implementation(files(sherpaAar))

    // DataStore：偏好设置（M1 接依赖）
    implementation(libs.androidx.datastore.preferences)

    // EncryptedSharedPreferences：后续里程碑 LLM / 云服务 Key 加密存储
    implementation(libs.androidx.security.crypto)

    // 2026-10-17：WorkManager 已移除（改用 TaskScheduler + StageRunner 前台协程直连）。
}
