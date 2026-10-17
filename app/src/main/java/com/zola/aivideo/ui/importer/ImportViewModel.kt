package com.zola.aivideo.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.media.MediaMetadataReader
import com.zola.aivideo.domain.model.VideoMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 导入页 UI 状态。 */
sealed interface ImportUiState {
    /** 复制文件中。 */
    data object Copying : ImportUiState

    /** 读取元信息中。 */
    data class Reading(val filePath: String) : ImportUiState

    /** 成功：展示元信息。 */
    data class Success(val metadata: VideoMetadata) : ImportUiState

    /** 失败：展示原因 + 重试（原因文案，不抛原生 Toast）。 */
    data class Error(val reason: String) : ImportUiState
}

/**
 * 导入 ViewModel：系统选择器 Uri → 复制到 `filesDir/imports/` → 读元信息。
 *
 * 复制到私有目录的原因：后续流水线（ASR/FFmpeg）需要稳定可读的文件路径，
 * content:// Uri 在进程重启后可能失效。
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataReader: MediaMetadataReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Copying)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** 开始导入（幂等：重复调用同一 uri 会复用已复制的文件）。 */
    fun importFromUri(uriString: String) {
        if (_uiState.value !is ImportUiState.Copying) return
        viewModelScope.launch {
            _uiState.value = try {
                val filePath = withContext(Dispatchers.IO) { copyToImports(uriString) }
                _uiState.value = ImportUiState.Reading(filePath)
                val metadata = withContext(Dispatchers.IO) { metadataReader.read(filePath) }
                if (metadata != null) {
                    ImportUiState.Success(metadata)
                } else {
                    ImportUiState.Error("无法读取该视频的元信息，文件可能已损坏或格式不受支持")
                }
            } catch (e: SecurityException) {
                ImportUiState.Error("没有该文件的读取权限，请重新选择")
            } catch (e: Exception) {
                ImportUiState.Error("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 重试：回到初始态，由调用方再次触发 importFromUri。 */
    fun reset() {
        _uiState.value = ImportUiState.Copying
    }

    /**
     * 复制到 filesDir/imports/，返回目标绝对路径。
     * 文件名：原文件名（非法字符过滤）+ 时间戳后缀，避免冲突。
     */
    private fun copyToImports(uriString: String): String {
        val uri = Uri.parse(uriString)
        val originalName = queryDisplayName(uri) ?: "video"
        val safeName = originalName.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            .takeIf { it.isNotBlank() } ?: "video"
        val ext = safeName.substringAfterLast('.', "")
        val base = if (ext.isNotEmpty()) safeName.substringBeforeLast('.') else safeName
        val fileName = "${base}_${System.currentTimeMillis()}${if (ext.isNotEmpty()) ".$ext" else ""}"

        val importsDir = File(context.filesDir, "imports").apply { mkdirs() }
        val dest = File(importsDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法打开所选文件")
        return dest.absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }
}
