package com.zola.aivideo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 影译 Application：Hilt 依赖注入入口。
 *
 * 2026-10-17：移除 WorkManager（在部分设备上任务入队后不执行），
 * 改为 TaskScheduler + StageRunner 前台协程直连执行。
 */
@HiltAndroidApp
class AiVideoApp : Application()
