package com.zola.aivideo.di

import com.zola.aivideo.data.asr.AsrModelCatalog
import com.zola.aivideo.data.download.DownloadableModel
import com.zola.aivideo.data.tts.TtsModelCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * TTS/配音能力注入模块（M4）。
 *
 * - ttsHttpClient：云 TTS 专用 OkHttp（音频响应体较大，读超时放宽到 120s）
 * - DownloadableModel 列表：ASR 三档 + Kokoro，供 ModelDownloadManager 聚合管理
 * - TtsPreferences / TtsCredentialStore / 引擎类均为 @Inject 构造，无需在此声明
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    @Named("tts")
    fun provideTtsHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideDownloadableModels(): List<@JvmSuppressWildcards DownloadableModel> =
        AsrModelCatalog.entries + TtsModelCatalog.entry
}
