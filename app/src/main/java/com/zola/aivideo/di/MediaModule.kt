package com.zola.aivideo.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 媒体能力注入模块。
 *
 * MediaMetadataReader 与 FFmpegService 均为 @Singleton + @Inject 构造，
 * Hilt 可直接构造，无需显式 @Provides。此模块为后续里程碑预留
 * （如 DataStore、OkHttp、代理配置等需要 @Provides 的依赖）。
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule
