package com.zola.aivideo.data.task

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 检查项严重程度。 */
enum class CheckSeverity {
    /** 阻断：不能开始。 */
    BLOCKER,

    /** 警告：用户确认后可继续。 */
    WARNING,
}

/** 单项检查结果。 */
data class PreflightIssue(
    val severity: CheckSeverity,
    /** 用户可读文案。 */
    val message: String,
)

/**
 * 任务开始前检查（M5，技术方案 §2.7）。
 *
 * - 剩余存储空间：估算输出（源视频大小 x 2 + 500MB 缓冲）不足则阻断
 * - 电量：低于 20% 警告（建议插电）
 * - 网络类型：模型下载/云服务在移动数据下二次确认（调用方据 WARNING 弹窗）
 *
 * 纯检查逻辑放这里，弹窗/二次确认由 UI 层做，便于 JVM 单元测试核心规则。
 */
@Singleton
class PreflightCheck @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 估算一次完整流水线所需的磁盘空间。 */
    fun estimateRequiredBytes(sourceVideoPath: String): Long {
        val src = runCatching { File(sourceVideoPath).length() }.getOrDefault(0L)
        return src * 2 + OUTPUT_BUFFER_BYTES
    }

    /** filesDir 可用空间。 */
    fun availableBytes(): Long {
        val dir = context.filesDir
        return runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
    }

    /** 当前电量百分比（0~100），取不到返回 -1。 */
    fun batteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale)
    }

    /** 是否正在充电。 */
    fun isCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** 当前是否为移动数据网络（非 Wi-Fi）。 */
    fun isMobileData(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 执行全部检查，返回问题列表（空表示通过）。
     *
     * @param needsNetwork 转写/翻译/配音任一阶段是否需要网络（云模型下载、LLM、云 TTS）
     */
    fun check(sourceVideoPath: String, needsNetwork: Boolean): List<PreflightIssue> =
        decideIssues(
            required = estimateRequiredBytes(sourceVideoPath),
            available = availableBytes(),
            battery = batteryPercent(),
            charging = isCharging(),
            mobileData = isMobileData(),
            needsNetwork = needsNetwork,
        )

    companion object {
        /** 输出缓冲：源视频大小 x2 + 500MB。 */
        const val OUTPUT_BUFFER_BYTES = 500L * 1024 * 1024

        /** 低电量阈值。 */
        const val LOW_BATTERY_PERCENT = 20

        fun formatBytes(bytes: Long): String {
            val mb = bytes / 1024.0 / 1024.0
            return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
        }

        /**
         * 纯决策逻辑（JVM 可单测）：给定各项输入，返回问题列表。
         * @param battery 电量百分比（0~100）；-1 表示取不到（不判低电量）
         */
        fun decideIssues(
            required: Long,
            available: Long,
            battery: Int,
            charging: Boolean,
            mobileData: Boolean,
            needsNetwork: Boolean,
        ): List<PreflightIssue> {
            val issues = mutableListOf<PreflightIssue>()

            if (available < required) {
                issues += PreflightIssue(
                    CheckSeverity.BLOCKER,
                    "存储空间不足：预计需要 ${formatBytes(required)}，" +
                        "可用 ${formatBytes(available)}。请清理后重试。",
                )
            }

            if (battery in 0 until LOW_BATTERY_PERCENT && !charging) {
                issues += PreflightIssue(
                    CheckSeverity.WARNING,
                    "电量仅 $battery%，转写/合成是重任务，建议连接电源后再开始",
                )
            }

            if (needsNetwork && mobileData) {
                issues += PreflightIssue(
                    CheckSeverity.WARNING,
                    "当前为移动数据网络，模型下载/云服务可能产生流量费用",
                )
            }
            return issues
        }
    }
}
