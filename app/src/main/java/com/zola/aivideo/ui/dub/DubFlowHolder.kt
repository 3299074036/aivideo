package com.zola.aivideo.ui.dub

import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.data.tts.DubOptions
import com.zola.aivideo.data.tts.DubResult
import com.zola.aivideo.data.tts.DubSegmentInput
import com.zola.aivideo.data.tts.DubStrategy
import com.zola.aivideo.data.tts.TtsEngineId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配音流程内存传递器（M4）：翻译结果页 → 配音配置页 → 配音进度页 → 试听页。
 *
 * 与 M2/M3 的 Holder 同模式：@Singleton 注入，界面间传大对象避免导航参数
 * 体积限制。M5 任务中心落地后持久化到 Room。
 */
@Singleton
class DubFlowHolder @Inject constructor() {
    @Volatile
    var sourceSegments: List<TranslatedSegment> = emptyList()
        private set

    /** 原视频文件路径（混音模式需要；替换模式可为 null）。 */
    @Volatile
    var videoPath: String? = null
        private set

    @Volatile
    var options: DubOptions = DubOptions()
        private set

    @Volatile
    var lastResult: DubResult? = null
        private set

    fun putSource(segments: List<TranslatedSegment>, videoPath: String?) {
        sourceSegments = segments
        this.videoPath = videoPath
        lastResult = null
    }

    fun putOptions(options: DubOptions) {
        this.options = options
    }

    fun putResult(result: DubResult) {
        lastResult = result
    }

    /** 转换为配音输入分段（取纯译文）。 */
    fun toDubInputs(): List<DubSegmentInput> =
        sourceSegments.mapIndexed { index, seg ->
            DubSegmentInput(
                index = index,
                startMs = seg.original.startMs,
                endMs = seg.original.endMs,
                text = seg.translatedText,
            )
        }
}
