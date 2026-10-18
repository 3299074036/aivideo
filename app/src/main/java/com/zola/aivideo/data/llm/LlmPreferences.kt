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
        val PROVIDER = stringPreferencesKey("provider")
        val ENGINE = stringPreferencesKey("engine")
        val LOCAL_MODEL = stringPreferencesKey("local_model")
    }

    val baseUrl: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL
    }

    val model: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.MODEL] ?: DEFAULT_MODEL
    }

    /** 当前选择的服务商预设 id（M7），默认 custom（保持老用户原有手填配置不变）。 */
    val provider: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.PROVIDER] ?: LlmProviderCatalog.CUSTOM.id
    }

    suspend fun setBaseUrl(baseUrl: String) {
        context.llmDataStore.edit { it[Keys.BASE_URL] = baseUrl.trim().trimEnd('/') }
    }

    suspend fun setModel(model: String) {
        context.llmDataStore.edit { it[Keys.MODEL] = model.trim() }
    }

    suspend fun getBaseUrl(): String = baseUrl.first()
    suspend fun getModel(): String = model.first()

    suspend fun setProvider(providerId: String) {
        context.llmDataStore.edit { it[Keys.PROVIDER] = providerId }
    }

    suspend fun getProvider(): String = provider.first()

    /** LLM 引擎选择（M7）：API / 本地，默认 API。 */
    val llmEngine: Flow<LlmEngineId> = context.llmDataStore.data.map { prefs ->
        LlmEngineId.fromId(prefs[Keys.ENGINE])
    }

    suspend fun setLlmEngine(engine: LlmEngineId) {
        context.llmDataStore.edit { it[Keys.ENGINE] = engine.id }
    }

    suspend fun getLlmEngine(): LlmEngineId = llmEngine.first()

    /** 本地语言模型选择（M7）：LlmModelCatalog 的 entry id，默认 Qwen3-0.6B。 */
    val localModel: Flow<String> = context.llmDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_MODEL] ?: LlmModelCatalog.DEFAULT_ID
    }

    suspend fun setLocalModel(modelId: String) {
        context.llmDataStore.edit { it[Keys.LOCAL_MODEL] = modelId }
    }

    suspend fun getLocalModel(): String = localModel.first()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
