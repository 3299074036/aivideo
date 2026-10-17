package com.zola.aivideo

import android.app.Application
import androidx.work.Configuration
import com.zola.aivideo.data.work.AiVideoWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 影译 Application：Hilt 依赖注入入口 + WorkManager 手动初始化。
 *
 * M5：实现 [Configuration.Provider]，注入手写的 [AiVideoWorkerFactory]
 *（**不用 `@HiltWorker`**：Hilt 2.57.2 + KSP 下绑定代码不生成，血泪教训）。
 * manifest 里已用 tools:node="remove" 禁掉 WorkManager 默认初始化。
 */
@HiltAndroidApp
class AiVideoApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: AiVideoWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            // 最小日志，避免 release 包吵闹；debug 可按需调高
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
