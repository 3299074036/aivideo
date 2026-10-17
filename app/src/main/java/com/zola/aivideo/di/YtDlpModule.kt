package com.zola.aivideo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * yt-dlp（M6）注入模块。
 *
 * - ytdlpHttpClient：更新通道专用 OkHttp（GitHub API + 归档下载；
 *   连接 30s / 读写 120s——归档约 2.9MB 但经代理可能慢）。
 * - YtDlpPreferences / YtDlpEnvironment / YtDlpUpdater / YtDlpDownloader
 *   均为 @Inject 构造，无需在此声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object YtDlpModule {

    @Provides
    @Singleton
    @Named("ytdlp")
    fun provideYtDlpHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
}
