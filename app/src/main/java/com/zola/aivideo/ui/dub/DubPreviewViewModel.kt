package com.zola.aivideo.ui.dub

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.tts.DubResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 试听页 UI 状态。 */
data class DubPreviewUiState(
    val result: DubResult? = null,
    val playing: Boolean = false,
    val exporting: Boolean = false,
    val exportMessage: String? = null,
)

/**
 * 配音试听 ViewModel（M4）。
 *
 * - MediaPlayer 播放合成后的配音轨 WAV（系统解码，无需 FFmpeg）
 * - 导出配音轨：SAF CreateDocument 写 .wav（M5 视频合成用）
 */
@HiltViewModel
class DubPreviewViewModel @Inject constructor(
    private val holder: DubFlowHolder,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        val CreateWavDocument = ActivityResultContracts.CreateDocument("audio/wav")
    }

    var uiState by mutableStateOf(DubPreviewUiState(result = holder.lastResult))
        private set

    private var player: MediaPlayer? = null

    fun togglePlay() {
        val track = uiState.result?.trackFile ?: return
        val current = player
        if (current?.isPlaying == true) {
            current.pause()
            uiState = uiState.copy(playing = false)
            return
        }
        if (current != null) {
            current.start()
            uiState = uiState.copy(playing = true)
            return
        }
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(track))
                setOnCompletionListener {
                    uiState = uiState.copy(playing = false)
                    runCatching { it.reset(); it.release() }
                    player = null
                }
                setOnErrorListener { _, _, _ ->
                    uiState = uiState.copy(
                        playing = false,
                        exportMessage = "播放失败，文件可能已损坏",
                    )
                    player = null
                    true
                }
                prepare()
                start()
            }
            player = mp
            uiState = uiState.copy(playing = true, exportMessage = null)
        } catch (e: Exception) {
            uiState = uiState.copy(exportMessage = "播放失败：${e.message}")
        }
    }

    fun suggestedFileName(): String = "dub_track_${System.currentTimeMillis()}.wav"

    /** SAF 导出配音轨 WAV。 */
    fun exportTo(uri: Uri) {
        val track = uiState.result?.trackFile ?: return
        viewModelScope.launch {
            uiState = uiState.copy(exporting = true, exportMessage = null)
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    track.inputStream().use { it.copyTo(out) }
                }
                uiState = uiState.copy(
                    exporting = false,
                    exportMessage = "配音轨已导出（${track.length() / 1024}KB）",
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    exporting = false,
                    exportMessage = "导出失败：${e.message}",
                )
            }
        }
    }

    fun exportCancelled() {
        // 用户取消文件选择器：无事可做
    }

    override fun onCleared() {
        runCatching { player?.release() }
        player = null
        super.onCleared()
    }
}
