package com.zola.aivideo.di

import com.google.gson.Gson
import com.zola.aivideo.data.llm.LlmClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * LLM 能力注入模块（M3）。
 *
 * - llmHttpClient：LLM 专用 OkHttp（连接 30s / 读 180s——长翻译需要 / 写 30s），
 *   与模型下载的 client 分开，避免超时配置互相影响。
 * - Gson：LLM JSON 解析用（与 Retrofit 共用同一实例亦可，这里独立提供更清晰）。
 * - LlmClient / LlmCredentialStore / LlmPreferences / GlossaryRepository /
 *   PromptLoader / SubtitleTranslator 均为 @Inject 构造，无需在此声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    @Named("llm")
    fun provideLlmHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideLlmClient(
        @Named("llm") client: OkHttpClient,
        gson: Gson,
    ): LlmClient = LlmClient(client, gson)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
