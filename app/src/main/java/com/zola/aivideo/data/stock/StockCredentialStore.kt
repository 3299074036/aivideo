package com.zola.aivideo.data.stock

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 素材库 API Key 加密存储（M6）。
 *
 * Pexels / Pixabay 官方 API Key 全部用户自填（BYOK），与 LLM / 云 TTS 的 Key
 * 分开存放（不同文件）。铁律：代码里绝不硬编码任何密钥。
 *
 * - Pexels Key 申请：https://www.pexels.com/api/（请求头 Authorization 携带）
 * - Pixabay Key 申请：https://pixabay.com/api/docs/（查询参数 key 携带）
 */
@Singleton
class StockCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "stock_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getKey(provider: StockProvider): String = when (provider) {
        StockProvider.PEXELS -> prefs.getString(KEY_PEXELS, "").orEmpty()
        StockProvider.PIXABAY -> prefs.getString(KEY_PIXABAY, "").orEmpty()
    }

    fun hasKey(provider: StockProvider): Boolean = getKey(provider).isNotBlank()

    /** 空字符串表示清除。 */
    fun setKey(provider: StockProvider, apiKey: String) {
        val key = when (provider) {
            StockProvider.PEXELS -> KEY_PEXELS
            StockProvider.PIXABAY -> KEY_PIXABAY
        }
        prefs.edit().putString(key, apiKey.trim()).apply()
    }

    private companion object {
        const val KEY_PEXELS = "pexels_api_key"
        const val KEY_PIXABAY = "pixabay_api_key"
    }
}
