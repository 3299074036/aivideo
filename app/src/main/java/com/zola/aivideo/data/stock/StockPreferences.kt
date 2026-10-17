package com.zola.aivideo.data.stock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 素材库非敏感偏好（M6）：默认提供方选择。
 *
 * Key 本身敏感 → [StockCredentialStore]；这里只存普通 SharedPreferences。
 */
@Singleton
class StockPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("stock_prefs", Context.MODE_PRIVATE)

    var provider: StockProvider
        get() = runCatching { StockProvider.valueOf(prefs.getString(KEY_PROVIDER, null) ?: "") }
            .getOrDefault(StockProvider.PEXELS)
        set(value) {
            prefs.edit().putString(KEY_PROVIDER, value.name).apply()
        }

    private companion object {
        const val KEY_PROVIDER = "provider"
    }
}
