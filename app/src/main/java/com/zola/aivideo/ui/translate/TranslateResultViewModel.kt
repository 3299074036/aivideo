package com.zola.aivideo.ui.translate

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.zola.aivideo.data.asr.TranscriptionResultHolder
import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.data.subtitle.SrtWriter
import com.zola.aivideo.ui.dub.DubFlowHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 翻译结果 ViewModel（M3）。
 *
 * - 预览译文分段；未通过门禁的段标注「时间点待核实」
 * - SAF CreateDocument 导出 .srt（文件名过滤非法字符）
 */
@HiltViewModel
class TranslateResultViewModel @Inject constructor(
    private val holder: TranslationFlowHolder,
    private val dubHolder: DubFlowHolder,
    private val transcriptionHolder: TranscriptionResultHolder,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var segments by mutableStateOf<List<TranslatedSegment>>(emptyList())
        private set

    var sourceLabel by mutableStateOf("")
        private set

    var bilingual by mutableStateOf(false)
        private set

    var exportMessage by mutableStateOf<String?>(null)
        private set

    init {
        segments = holder.lastOutcome
        sourceLabel = holder.sourceLabel
        bilingual = holder.lastRequest?.bilingual == true
    }

    /**
     * 进入配音流程（M4）：把翻译分段与视频路径交到 DubFlowHolder。
     *
     * 纯字幕导入流程没有视频路径 → 混音模式在配置页自动降级为替换模式。
     */
    fun startDub() {
        val videoPath = transcriptionHolder.videoPath.ifBlank { null }
        dubHolder.putSource(segments, videoPath)
    }

    /** 导出 .srt 到用户选择的 Uri。 */
    suspend fun exportTo(uri: Uri): Boolean {
        return try {
            val srt = SrtWriter.toSrt(segments, bilingual)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(srt.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法打开输出流")
            }
            exportMessage = "已导出 ${segments.size} 段字幕"
            true
        } catch (e: Exception) {
            exportMessage = "导出失败：${e.message}"
            false
        }
    }

    /** 建议的文件名（已过滤非法字符）。 */
    fun suggestedFileName(): String {
        val base = sourceLabel.ifBlank { "translated" }
        return SrtWriter.sanitizeFileName("${base}_译文") + ".srt"
    }

    fun clearMessage() {
        exportMessage = null
    }

    companion object {
        /**
         * SAF 创建文档 contract，Screen 层使用。
         *
         * 修复（2026-10-17）：MIME 必须用 application/x-subrip（SubRip 标准类型）。
         * 之前用 text/plain，OPPO 文件管理器会自动把扩展名存成 .txt，
         * 用户被迫手动改名才能导入。
         */
        val CreateSrtDocument = ActivityResultContracts.CreateDocument("application/x-subrip")
    }
}
