package com.zola.aivideo.data.ytdlp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * yt-dlp 运行环境（M6）。
 *
 * 架构约束（调研结论，API 29+）：
 * - Python 解释器必须随 APK 内置：`app/src/main/jniLibs/arm64-v8a/libpyinterpreter.so`
 *   → 安装后位于 `applicationInfo.nativeLibraryDir`（安装器自带可执行权限，
 *   无需 chmod）。**绝不能**运行时下载解释器到 filesDir 再执行（SELinux 拒绝）。
 * - yt-dlp 归档本身是传给解释器的**脚本参数**（不直接执行），可放 filesDir。
 * - 随 APK 内置解释器时，manifest 需 `android:extractNativeLibs="true"`
 *  （注意：这会让所有 .so 解压到文件系统，安装体积接近翻倍——打包解释器时评估）。
 *
 * 环境变量清单照抄 youtubedl-android 的生产验证配置。
 */
@Singleton
class YtDlpEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /**
         * APK 内置解释器的库名（jniLibs/arm64-v8a/ 下的文件名）。
         * 构建配方见 docs/research/aivideo/yt-dlp集成调研.md。
         */
        const val INTERPRETER_LIB_NAME = "libpyinterpreter.so"

        /** yt-dlp 归档在私有目录的位置。 */
        const val ARCHIVE_NAME = "yt-dlp"

        /** 归档目录。 */
        const val YTDLP_DIR = "ytdlp"
    }

    /** 解释器文件（nativeLibraryDir 下）。 */
    fun interpreterFile(): File =
        File(context.applicationInfo.nativeLibraryDir, INTERPRETER_LIB_NAME)

    /** 解释器是否已随 APK 内置。 */
    fun hasInterpreter(): Boolean = interpreterFile().exists()

    /** yt-dlp 工作目录（归档 + 下载产物都在此树下）。 */
    fun workDir(): File = File(context.filesDir, YTDLP_DIR).apply { mkdirs() }

    /** yt-dlp zipimport 归档（运行时经独立更新通道下载）。 */
    fun archiveFile(): File = File(workDir(), ARCHIVE_NAME)

    /** yt-dlp 归档是否已就绪（已下载且非空）。 */
    fun hasArchive(): Boolean = archiveFile().let { it.exists() && it.length() > 0 }

    /** 下载产物目录（批量下载落点，导入任务时经 FileProvider 分享）。 */
    fun downloadsDir(): File = File(workDir(), "downloads").apply { mkdirs() }

    /**
     * 构造 yt-dlp 执行命令。
     *
     * @throws IllegalStateException 解释器或归档缺失（调用方转用户可读文案）
     */
    fun processBuilder(args: List<String>): ProcessBuilder {
        val python = interpreterFile()
        check(python.exists()) {
            "Python 运行环境未内置：本功能需要随 APK 内置解释器" +
                "（构建配方见 docs/research/aivideo/yt-dlp集成调研.md），当前构建未包含"
        }
        val archive = archiveFile()
        check(archive.exists()) {
            "yt-dlp 尚未下载：请先在「链接下载」页检查更新并下载 yt-dlp"
        }
        val cmd = mutableListOf(python.absolutePath, archive.absolutePath, "--no-cache-dir")
        cmd.addAll(args)
        return ProcessBuilder(cmd).apply {
            environment()["LD_LIBRARY_PATH"] = buildLibraryPath()
            environment()["PATH"] = buildPath()
            environment()["PYTHONHOME"] = workDir().absolutePath
            environment()["HOME"] = workDir().absolutePath
            environment()["TMPDIR"] = context.cacheDir.absolutePath
            // Python 在 Android 上无系统 CA 概念；随解释器包提供 cacert.pem 时才设置
            caCertFile()?.let { environment()["SSL_CERT_FILE"] = it.absolutePath }
            directory(workDir())
            redirectErrorStream(false)
        }
    }

    private fun buildLibraryPath(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
        return listOf(nativeDir, workDir().absolutePath, existing)
            .filter { it.isNotBlank() }
            .joinToString(":")
    }

    private fun buildPath(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val existing = System.getenv("PATH").orEmpty()
        return listOf(nativeDir, existing).filter { it.isNotBlank() }.joinToString(":")
    }

    /**
     * CA 证书（随解释器包提供，`ytdlp/cacert.pem`）。
     * 缺失时返回 null（调用方不设置 SSL_CERT_FILE；此时 HTTPS 可能失败，
     * 由下载错误透出，而非静默）。
     */
    private fun caCertFile(): File? =
        File(workDir(), "cacert.pem").takeIf { it.exists() }

    // ---------- 网络状态（更新通道的"仅 Wi-Fi"开关用） ----------

    /** 当前是否为 Wi-Fi 连接（与 ModelDownloadManager 同口径）。 */
    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }
}
