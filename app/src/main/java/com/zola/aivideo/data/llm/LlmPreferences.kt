package com.zola.aivideo.data.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_prefs")

/**
 * LLM 非敏感配置（DataStore）。
 *
 * - baseUrl：OpenAI 兼容接口地址，默认 https://api.openai.com
 * - model：模型名，默认 gpt-4o-mini
 * API Key 不在这里，走 [LlmCredentialStore] 加密存储。
 */
@Singleton
class LlmPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
    }

    val baseUrl: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL
    }

    val model: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.MODEL] ?: DEFAULT_MODEL
    }

    suspend fun setBaseUrl(baseUrl: String) {
        context.llmDataStore.edit { it[Keys.BASE_URL] = baseUrl.trim().trimEnd('/') }
    }

    suspend fun setModel(model: String) {
        context.llmDataStore.edit { it[Keys.MODEL] = model.trim() }
    }

    suspend fun getBaseUrl(): String = baseUrl.first()
    suspend fun getModel(): String = model.first()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
