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
 * 素材库（M6）注入模块。
 *
 * - stockHttpClient：素材 API 专用 OkHttp（连接 30s / 读写 120s——视频文件下载需要），
 *   与 LLM / 模型下载的 client 分开，避免超时配置互相影响。
 * - StockClient / StockCredentialStore / StockPreferences / StockReplaceUseCase
 *   均为 @Inject 构造，无需在此声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object StockModule {

    @Provides
    @Singleton
    @Named("stock")
    fun provideStockHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
}
