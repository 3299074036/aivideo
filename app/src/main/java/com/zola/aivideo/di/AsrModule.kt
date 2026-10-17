package com.zola.aivideo.di

import android.content.Context
import com.zola.aivideo.data.asr.AsrPreferences
import com.zola.aivideo.data.download.DownloadableModel
import com.zola.aivideo.data.download.ModelDownloadManager
import com.zola.aivideo.data.asr.audio.PcmDecoder
import com.zola.aivideo.data.asr.engine.SherpaAsrEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * ASR 能力注入模块（M2）。
 *
 * - OkHttpClient：模型下载专用（30 秒读写超时，跟随重定向，支持 Range）。
 *   与后续 Retrofit 用的 client 分开，避免超时配置互相影响。
 * - ModelDownloadManager / AsrPreferences / PcmDecoder / SherpaAsrEngine：
 *   @Singleton，应用生命周期内复用（引擎初始化耗时，只做一次）。
 */
@Module
@InstallIn(SingletonComponent::class)
object AsrModule {

    @Provides
    @Singleton
    fun provideDownloadHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        catalogs: List<@JvmSuppressWildcards DownloadableModel>,
    ): ModelDownloadManager = ModelDownloadManager(context, client, catalogs)

    @Provides
    @Singleton
    fun provideAsrPreferences(
        @ApplicationContext context: Context,
    ): AsrPreferences = AsrPreferences(context)

    @Provides
    @Singleton
    fun providePcmDecoder(): PcmDecoder = PcmDecoder()

    @Provides
    @Singleton
    fun provideSherpaAsrEngine(): SherpaAsrEngine = SherpaAsrEngine()
}
