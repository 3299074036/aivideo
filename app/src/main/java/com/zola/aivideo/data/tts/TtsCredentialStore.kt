package com.zola.aivideo.data.tts

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云 TTS API Key 加密存储（M4）。
 *
 * 与 LLM 的 Key 分开存放（不同文件、不同字段），使用 EncryptedSharedPreferences
 *（AES256），随应用卸载清除。铁律：Key 只能用户自填，代码里绝不硬编码任何密钥。
 */
@Singleton
class TtsCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tts_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "").orEmpty()

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    /** 空字符串表示清除。 */
    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    private companion object {
        const val KEY_API_KEY = "openai_compatible_tts_api_key"
    }
}
