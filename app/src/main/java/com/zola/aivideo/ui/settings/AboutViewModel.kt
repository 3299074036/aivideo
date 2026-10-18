package com.zola.aivideo.ui.settings

import androidx.lifecycle.ViewModel
import com.zola.aivideo.data.ffmpeg.FFmpegService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 关于页 ViewModel（2026-10-17）。
 *
 * 诊断信息（FFmpeg 版本）从模型管理页搬到这里——用户要求诊断别放在模型管理页，
 * 关于页是合适的归宿（版本/环境信息）。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    ffmpegService: FFmpegService,
) : ViewModel() {
    /** FFmpeg 版本（null 表示加载失败，见 ffmpegError）。 */
    val ffmpegVersion: String?

    /** FFmpeg native 加载失败时的原始错误（无则为 null）。 */
    val ffmpegError: String?

    init {
        val (v, e) = ffmpegService.getVersionInfo()
        ffmpegVersion = v
        ffmpegError = e
    }
}
