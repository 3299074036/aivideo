package com.zola.aivideo.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 手写 WorkerFactory（M5）。
 *
 * **不要用 `@HiltWorker`**：Hilt 2.57.2 + KSP 下 @HiltWorker 的绑定代码根本不生成，
 * WorkManager 建不出 Worker（doWork 一次都不进，直接标 FAILED）——这是视记项目用
 * 血泪换来的教训。本工厂直调各 Worker 构造函数的 Factory，依赖经 Hilt 注入。
 *
 * 在 [com.zola.aivideo.AiVideoApp.getWorkManagerConfiguration] 中接入。
 */
@Singleton
class AiVideoWorkerFactory @Inject constructor(
    private val transcribeFactory: TranscribeWorker.Factory,
    private val translateFactory: TranslateWorker.Factory,
    private val dubFactory: DubWorker.Factory,
    private val synthesizeFactory: SynthesizeWorker.Factory,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        TranscribeWorker::class.java.name -> transcribeFactory.create(appContext, workerParameters)
        TranslateWorker::class.java.name -> translateFactory.create(appContext, workerParameters)
        DubWorker::class.java.name -> dubFactory.create(appContext, workerParameters)
        SynthesizeWorker::class.java.name -> synthesizeFactory.create(appContext, workerParameters)
        else -> null
    }
}
