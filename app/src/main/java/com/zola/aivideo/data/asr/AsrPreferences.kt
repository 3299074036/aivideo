package com.zola.aivideo.data.asr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zola.aivideo.domain.asr.AsrModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.asrDataStore: DataStore<Preferences> by preferencesDataStore(name = "asr_prefs")

/**
 * ASR 偏好设置（DataStore）。
 *
 * - selectedModel：当前选用的模型 id，默认 tiny-int8
 * - wifiOnlyDownload：仅在 Wi-Fi 下下载模型，默认 true（模型上百 MB，防跑流量）
 */
@Singleton
class AsrPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
    }

    val selectedModel: Flow<AsrModel> =
        context.asrDataStore.data.map { prefs ->
            AsrModel.fromId(prefs[Keys.SELECTED_MODEL])
        }

    suspend fun setSelectedModel(model: AsrModel) {
        context.asrDataStore.edit { it[Keys.SELECTED_MODEL] = model.id }
    }

    suspend fun getSelectedModel(): AsrModel = selectedModel.first()

    val wifiOnlyDownload: Flow<Boolean> =
        context.asrDataStore.data.map { prefs ->
            prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: true
        }

    suspend fun setWifiOnlyDownload(enabled: Boolean) {
        context.asrDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOAD] = enabled }
    }

    suspend fun isWifiOnlyDownload(): Boolean = wifiOnlyDownload.first()
}
