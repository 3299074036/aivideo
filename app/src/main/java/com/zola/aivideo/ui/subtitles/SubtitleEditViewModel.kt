package com.zola.aivideo.ui.subtitles

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.zola.aivideo.data.asr.TranscriptionResultHolder
import com.zola.aivideo.data.task.TaskFiles
import com.zola.aivideo.domain.model.TranscriptSegment
import com.zola.aivideo.ui.translate.TranslationFlowHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 字幕编辑 ViewModel。
 *
 * 分段列表常驻内存（mutableStateList，Compose 可观察）；编辑操作直接改列表项。
 * 任务模式（taskId > 0，M5）：分段从任务目录的 transcript.json 读取，
 * "保存"写回任务目录（任务详情页的"校对字幕"入口）。
 */
@HiltViewModel
class SubtitleEditViewModel @Inject constructor(
    holder: TranscriptionResultHolder,
    private val translationHolder: TranslationFlowHolder,
    savedStateHandle: SavedStateHandle,
    private val taskFiles: TaskFiles,
) : ViewModel() {

    /** 任务模式的任务 id（0 表示普通前台流程）。 */
    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L

    /** 任务模式。 */
    val taskMode: Boolean get() = taskId > 0L

    private val _segments = mutableStateListOf<TranscriptSegment>()
    /** 当前分段（UI 直接观察）。 */
    val segments: List<TranscriptSegment> get() = _segments

    init {
        val source = if (taskMode) taskFiles.loadTranscript(taskId) else holder.segments
        _segments.addAll(source.sortedBy { it.startMs })
    }

    /**
     * 任务模式：把编辑结果写回任务目录。
     * @return null 表示成功；非 null 为错误文案
     */
    fun saveToTask(): String? {
        if (!taskMode) return "非任务模式"
        return runCatching {
            taskFiles.saveTranscript(taskId, _segments.sortedBy { it.startMs })
        }.fold(
            onSuccess = { null },
            onFailure = { it.message ?: "保存失败" },
        )
    }

    /** 分段总数。 */
    val count: Int get() = _segments.size

    /** 总字数（去空格）。 */
    val totalChars: Int get() = _segments.sumOf { it.text.filterNot(Char::isWhitespace).length }

    /** 编辑某段文本。 */
    fun updateText(id: Long, text: String) {
        val i = _segments.indexOfFirst { it.id == id }
        if (i >= 0) _segments[i] = _segments[i].copy(text = text)
    }

    /**
     * 校准某段时间轴。
     * @return null 表示成功；非 null 为用户可读的错误文案
     */
    fun updateTime(id: Long, startMs: Long, endMs: Long): String? {
        if (startMs < 0) return "开始时间不能为负数"
        if (endMs <= startMs) return "结束时间必须晚于开始时间"
        val i = _segments.indexOfFirst { it.id == id }
        if (i < 0) return "分段不存在"
        _segments[i] = _segments[i].copy(startMs = startMs, endMs = endMs)
        return null
    }

    /** 删除某段。 */
    fun delete(id: Long) {
        _segments.removeAll { it.id == id }
    }

    /** 把当前编辑好的分段提交给翻译流程（M3）。 */
    fun commitToTranslation() {
        translationHolder.putSource(
            _segments.sortedBy { it.startMs }.mapIndexed { i, s -> s.copy(id = i.toLong()) },
            "转写结果",
        )
    }
}
