package com.zola.aivideo.data.tts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_prefs")

/** 配音引擎：端侧优先，云端备选。 */
enum class TtsEngineId(val id: String) {
    LOCAL("local"),
    CLOUD("cloud"),
    ;

    companion object {
        fun fromId(id: String?): TtsEngineId =
            entries.firstOrNull { it.id == id } ?: LOCAL
    }
}

/** 配音策略：替换原音轨 / 与原音混音。 */
enum class DubStrategy(val id: String) {
    REPLACE("replace"),
    MIX("mix"),
    ;

    companion object {
        fun fromId(id: String?): DubStrategy =
            entries.firstOrNull { it.id == id } ?: REPLACE
    }
}

/**
 * TTS/配音偏好设置（DataStore，M4）。
 *
 * - voiceSid：Kokoro 音色 sid，默认 zf_001（3）
 * - speechSpeed：语速（Kokoro generate 的 speed 参数），默认 1.0
 * - engine：端侧/云端，默认端侧
 * - dubStrategy：替换/混音，默认替换
 * - mixOriginalRatio：混音时原音比例 0~1，默认 0.3
 * - wifiOnlyDownload：TTS 模型仅 Wi-Fi 下载，默认 true
 * - cloudVoice：云 TTS 音色名，默认 alloy
 */
@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val VOICE_SID = intPreferencesKey("voice_sid")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val ENGINE = stringPreferencesKey("engine")
        val DUB_STRATEGY = stringPreferencesKey("dub_strategy")
        val MIX_ORIGINAL_RATIO = floatPreferencesKey("mix_original_ratio")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val CLOUD_VOICE = stringPreferencesKey("cloud_voice")
        val CLOUD_MODEL = stringPreferencesKey("cloud_model")
        val CLOUD_BASE_URL = stringPreferencesKey("cloud_base_url")
        val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
    }

    val voiceSid: Flow<Int> = context.ttsDataStore.data.map {
        it[Keys.VOICE_SID] ?: TtsVoiceCatalog.DEFAULT_SID
    }
    suspend fun setVoiceSid(sid: Int) {
        context.ttsDataStore.edit { it[Keys.VOICE_SID] = sid }
    }

    val speechSpeed: Flow<Float> = context.ttsDataStore.data.map {
        (it[Keys.SPEECH_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f)
    }
    suspend fun setSpeechSpeed(speed: Float) {
        context.ttsDataStore.edit { it[Keys.SPEECH_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    val engine: Flow<TtsEngineId> = context.ttsDataStore.data.map {
        TtsEngineId.fromId(it[Keys.ENGINE])
    }
    suspend fun setEngine(engine: TtsEngineId) {
        context.ttsDataStore.edit { it[Keys.ENGINE] = engine.id }
    }

    val dubStrategy: Flow<DubStrategy> = context.ttsDataStore.data.map {
        DubStrategy.fromId(it[Keys.DUB_STRATEGY])
    }
    suspend fun setDubStrategy(strategy: DubStrategy) {
        context.ttsDataStore.edit { it[Keys.DUB_STRATEGY] = strategy.id }
    }

    val mixOriginalRatio: Flow<Float> = context.ttsDataStore.data.map {
        (it[Keys.MIX_ORIGINAL_RATIO] ?: 0.3f).coerceIn(0f, 1f)
    }
    suspend fun setMixOriginalRatio(ratio: Float) {
        context.ttsDataStore.edit { it[Keys.MIX_ORIGINAL_RATIO] = ratio.coerceIn(0f, 1f) }
    }

    val wifiOnlyDownload: Flow<Boolean> = context.ttsDataStore.data.map {
        it[Keys.WIFI_ONLY_DOWNLOAD] ?: true
    }
    suspend fun setWifiOnlyDownload(enabled: Boolean) {
        context.ttsDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOAD] = enabled }
    }

    suspend fun isWifiOnlyDownload(): Boolean = wifiOnlyDownload.first()

    val cloudVoice: Flow<String> = context.ttsDataStore.data.map {
        it[Keys.CLOUD_VOICE] ?: "alloy"
    }
    suspend fun setCloudVoice(voice: String) {
        context.ttsDataStore.edit { it[Keys.CLOUD_VOICE] = voice }
    }

    val cloudModel: Flow<String> = context.ttsDataStore.data.map {
        it[Keys.CLOUD_MODEL] ?: "tts-1"
    }
    suspend fun setCloudModel(model: String) {
        context.ttsDataStore.edit { it[Keys.CLOUD_MODEL] = model }
    }

    val cloudBaseUrl: Flow<String> = context.ttsDataStore.data.map {
        it[Keys.CLOUD_BASE_URL] ?: "https://api.openai.com/v1"
    }
    suspend fun setCloudBaseUrl(url: String) {
        context.ttsDataStore.edit { it[Keys.CLOUD_BASE_URL] = url.trim().trimEnd('/') }
    }

    /** 云 TTS 服务商预设 id（M7），默认 custom（保持老用户原有手填配置不变）。 */
    val cloudProvider: Flow<String> = context.ttsDataStore.data.map {
        it[Keys.CLOUD_PROVIDER] ?: TtsProviderCatalog.CUSTOM.id
    }
    suspend fun setCloudProvider(providerId: String) {
        context.ttsDataStore.edit { it[Keys.CLOUD_PROVIDER] = providerId }
    }
    suspend fun getCloudProvider(): String = cloudProvider.first()
}
