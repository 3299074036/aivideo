package com.zola.aivideo.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.coroutines.coroutineContext
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型下载状态（按 modelId 索引）。
 */
sealed interface ModelDownloadState {
    /** 未下载。 */
    data object NotDownloaded : ModelDownloadState

    /** 下载中：downloadedBytes/totalBytes（totalBytes 可能未知为 null）。 */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ModelDownloadState

    /** 已暂停：保留已下载部分，可断点续传。 */
    data class Paused(val downloadedBytes: Long, val totalBytes: Long?) : ModelDownloadState

    /** 已下载完整。 */
    data object Completed : ModelDownloadState

    /** 失败：reason 为用户可读文案。 */
    data class Failed(val reason: String) : ModelDownloadState
}

/**
 * 模型下载管理器：OkHttp + HTTP Range 断点续传（M4：从 data.asr 泛化为通用实现，
 * ASR 与 TTS 模型共用同一套下载/暂停/续传/删除逻辑）。
 *
 * 设计要点：
 * - 每个模型文件先写 `<name>.part` 临时文件，全部完成后原子改名；暂停/杀进程后
 *   下次从 .part 大小处续传（服务端返回 206）；若服务端不支持 Range（返回 200），
 *   则删掉 .part 从头下载。
 * - 多文件模型的进度跨文件聚合（见 [aggregateProgress]，纯函数可单元测试）。
 * - 模型存 `filesDir/models/<dirName>/`，随应用卸载清除。
 * - 网络类型判断（仅 Wi-Fi 开关用）走 ConnectivityManager，普通权限无需动态申请。
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val catalogs: List<@JvmSuppressWildcards DownloadableModel>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    /** key = DownloadableModel.id */
    val states: StateFlow<Map<String, ModelDownloadState>> = _states.asStateFlow()

    /** 当前正在执行的下载任务（modelId → job），暂停/取消用。 */
    private val jobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init {
        refreshAll()
    }

    /** 模型根目录。 */
    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    /** 单个模型的目录。 */
    fun modelDir(model: DownloadableModel): File = File(modelsDir(), model.dirName)

    /** 是否已完整下载（全部文件存在且非空）。 */
    fun isInstalled(model: DownloadableModel): Boolean {
        val dir = modelDir(model)
        return model.files.all { f ->
            val file = File(dir, f.fileName)
            file.exists() && file.length() > 0
        }
    }

    /** 刷新全部模型状态（启动/删除后调用）。 */
    fun refreshAll() {
        val map = catalogs.associate { entry ->
            entry.id to if (isInstalled(entry)) {
                ModelDownloadState.Completed as ModelDownloadState
            } else {
                // 有残留 .part 则视为可续传的暂停态
                val downloaded = downloadedBytes(entry)
                if (downloaded > 0) ModelDownloadState.Paused(downloaded, null)
                else ModelDownloadState.NotDownloaded
            }
        }
        _states.value = map
    }

    /** 开始（或继续）下载。 */
    fun startDownload(model: DownloadableModel) {
        if (jobs.containsKey(model.id)) return
        updateState(model, ModelDownloadState.Downloading(downloadedBytes(model), null))
        jobs[model.id] = scope.launch {
            try {
                downloadEntry(model)
                updateState(model, ModelDownloadState.Completed)
            } catch (e: DownloadPaused) {
                updateState(
                    model,
                    ModelDownloadState.Paused(downloadedBytes(model), lastTotals[model.id]),
                )
            } catch (e: Exception) {
                updateState(model, ModelDownloadState.Failed(friendlyReason(e)))
            } finally {
                jobs.remove(model.id)
            }
        }
    }

    /** 暂停下载（保留已下载部分）。 */
    fun pauseDownload(model: DownloadableModel) {
        jobs.remove(model.id)?.cancel(DownloadPaused())
    }

    /** 删除已下载的模型（含残留临时文件）。 */
    fun deleteModel(model: DownloadableModel) {
        pauseDownload(model)
        scope.launch {
            modelDir(model).deleteRecursively()
            lastTotals.remove(model.id)
            updateState(model, ModelDownloadState.NotDownloaded)
        }
    }

    // ---------- 网络状态 ----------

    /** 是否有可用网络。 */
    fun isOnline(): Boolean = activeNetworkCapabilities() != null

    /** 当前是否为 Wi-Fi 连接。 */
    fun isWifiConnected(): Boolean {
        val caps = activeNetworkCapabilities() ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun activeNetworkCapabilities(): NetworkCapabilities? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(network)
    }

    // ---------- 内部实现 ----------

    /** 最近一次发布的聚合总大小（modelId → totalBytes），用于暂停态展示。 */
    private val lastTotals = mutableMapOf<String, Long?>()

    private class DownloadPaused : kotlinx.coroutines.CancellationException("paused by user")

    private fun updateState(model: DownloadableModel, state: ModelDownloadState) {
        _states.value = _states.value + (model.id to state)
    }

    /** 已下载字节数（含 .part 残留），用于续传起点与暂停态展示。 */
    private fun downloadedBytes(entry: DownloadableModel): Long {
        val dir = modelDir(entry)
        return entry.files.sumOf { f ->
            val final = File(dir, f.fileName)
            val part = File(dir, f.fileName + PART_SUFFIX)
            maxOf(if (final.exists()) final.length() else 0, if (part.exists()) part.length() else 0)
        }
    }

    private suspend fun downloadEntry(entry: DownloadableModel) {
        val dir = modelDir(entry).apply { mkdirs() }
        // 各文件已知总大小（下载过程中逐步探明），用于跨文件聚合进度
        val fileTotals = mutableMapOf<String, Long?>()
        val fileDownloaded = mutableMapOf<String, Long>()

        for (file in entry.files) {
            val dest = File(dir, file.fileName)
            if (dest.exists() && dest.length() > 0) {
                fileDownloaded[file.fileName] = dest.length()
                fileTotals[file.fileName] = dest.length()
                publishProgress(entry, fileDownloaded, fileTotals)
                continue
            }
            // 子目录文件：确保父目录存在
            dest.parentFile?.mkdirs()
            val part = File(dir, file.fileName + PART_SUFFIX)
            part.parentFile?.mkdirs()
            var offset = if (part.exists()) part.length() else 0L

            val request = Request.Builder()
                .url(file.url)
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val append = response.code == 206
                if (!append) {
                    // 服务端不支持 Range，从头下载
                    part.delete()
                    offset = 0L
                }
                val total = (response.body?.contentLength() ?: -1L).let { len ->
                    if (len > 0) len + offset else null
                }
                fileTotals[file.fileName] = total

                RandomAccessFile(part, "rw").use { raf ->
                    if (append) raf.seek(offset) else raf.setLength(0)
                    val body = response.body ?: throw IllegalStateException("空响应体")
                    val buf = ByteArray(256 * 1024)
                    var read: Int
                    var lastPublish = 0L
                    val input = body.byteStream()
                    while (true) {
                        // 协作式取消：暂停时抛 DownloadPaused
                        coroutineContext[Job]?.ensureActive()
                        read = input.read(buf)
                        if (read == -1) break
                        raf.write(buf, 0, read)
                        offset += read
                        fileDownloaded[file.fileName] = offset
                        if (offset - lastPublish >= 256 * 1024) {
                            lastPublish = offset
                            publishProgress(entry, fileDownloaded, fileTotals)
                        }
                    }
                }
                fileDownloaded[file.fileName] = part.length()
                publishProgress(entry, fileDownloaded, fileTotals)
            }

            // 原子改名：.part → 正式文件
            val final = File(dir, file.fileName)
            if (!part.renameTo(final)) {
                part.copyTo(final, overwrite = true)
                part.delete()
            }
            fileDownloaded[file.fileName] = final.length()
            publishProgress(entry, fileDownloaded, fileTotals)
        }
    }

    private fun publishProgress(
        entry: DownloadableModel,
        downloaded: Map<String, Long>,
        totals: Map<String, Long?>,
    ) {
        val perFile = entry.files.map { f ->
            (downloaded[f.fileName] ?: 0L) to totals[f.fileName]
        }
        val (dl, total) = aggregateProgress(perFile)
        lastTotals[entry.id] = total
        updateState(entry, ModelDownloadState.Downloading(dl, total))
    }

    private fun friendlyReason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "网络不可用，请检查网络连接后重试"
        is java.net.SocketTimeoutException -> "下载超时，请检查网络后重试"
        is IllegalStateException -> "下载失败：${e.message}"
        else -> "下载失败：${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        const val PART_SUFFIX = ".part"

        /**
         * 跨文件聚合进度：纯函数，可单元测试。
         *
         * @param perFile 每个文件的 (已下载字节, 总字节|null)
         * @return (总已下载字节, 总字节；任一文件未知则为 null)
         */
        fun aggregateProgress(perFile: List<Pair<Long, Long?>>): Pair<Long, Long?> {
            val downloaded = perFile.sumOf { it.first }
            val total = if (perFile.all { it.second != null }) {
                perFile.sumOf { it.second!! }
            } else {
                null
            }
            return downloaded to total
        }
    }
}
