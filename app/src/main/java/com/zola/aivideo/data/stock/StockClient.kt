package com.zola.aivideo.data.stock

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 素材检索客户端（M6）：Pexels / Pixabay 官方 API。
 *
 * - 纯 OkHttp + Gson，无新增第三方库
 * - Key 由调用方从 [StockCredentialStore] 取（BYOK），本类不碰存储
 * - 搜索与下载均为挂起函数，IO 调度
 */
@Singleton
class StockClient @Inject constructor(
    @Named("stock") private val client: OkHttpClient,
    private val gson: Gson,
) {
    companion object {
        const val PEXELS_SEARCH_URL = "https://api.pexels.com/videos/search"
        const val PIXABAY_SEARCH_URL = "https://pixabay.com/api/videos/"
    }

    /**
     * 搜索视频素材。
     *
     * @return Result 成功为片段列表；失败的 exception.message 可直接展示
     */
    suspend fun search(
        provider: StockProvider,
        apiKey: String,
        query: String,
        perPage: Int = 15,
    ): Result<List<StockClip>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(
            IllegalStateException("请先在设置里填写 ${provider.displayName} 的 API Key"),
        )
        if (query.isBlank()) return@withContext Result.failure(
            IllegalStateException("请输入搜索关键词"),
        )
        try {
            val request = when (provider) {
                StockProvider.PEXELS -> {
                    val url = PEXELS_SEARCH_URL.toHttpUrl().newBuilder()
                        .addQueryParameter("query", query)
                        .addQueryParameter("per_page", perPage.coerceIn(1, 80).toString())
                        .build()
                    Request.Builder().url(url)
                        .header("Authorization", apiKey)
                        .get()
                        .build()
                }
                StockProvider.PIXABAY -> {
                    val url = PIXABAY_SEARCH_URL.toHttpUrl().newBuilder()
                        .addQueryParameter("key", apiKey)
                        .addQueryParameter("q", query)
                        .addQueryParameter("per_page", perPage.coerceIn(3, 200).toString())
                        .build()
                    Request.Builder().url(url).get().build()
                }
            }
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(
                            when (resp.code) {
                                401, 403 -> "${provider.displayName} Key 无效或无权限（${resp.code}）"
                                429 -> "请求太频繁，请稍后再试（429）"
                                else -> "搜索失败（HTTP ${resp.code}）"
                            },
                        ),
                    )
                }
                val body = resp.body?.string().orEmpty()
                val clips = when (provider) {
                    StockProvider.PEXELS ->
                        gson.fromJson(body, PexelsSearchResponse::class.java)
                            .videos.mapNotNull { it.toStockClip() }
                    StockProvider.PIXABAY ->
                        gson.fromJson(body, PixabaySearchResponse::class.java)
                            .hits.mapNotNull { it.toStockClip() }
                }
                Result.success(clips)
            }
        } catch (e: Exception) {
            Result.failure(IOException("网络错误：${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * 下载素材直链到目标文件（流式写盘，支持进度）。
     *
     * 素材 CDN 直链无需鉴权，不带 Key。
     */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("下载失败（HTTP ${resp.code}）"),
                    )
                }
                val body = resp.body
                    ?: return@withContext Result.failure(IOException("下载内容为空"))
                val total = body.contentLength()
                var written = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            written += n
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && written < total) {
                    dest.delete()
                    return@withContext Result.failure(IOException("下载不完整，请重试"))
                }
                Result.success(dest)
            }
        } catch (e: Exception) {
            runCatching { dest.delete() }
            Result.failure(IOException("下载失败：${e.message ?: e.javaClass.simpleName}"))
        }
    }
}
