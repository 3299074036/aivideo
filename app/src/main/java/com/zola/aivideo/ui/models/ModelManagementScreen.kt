package com.zola.aivideo.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.data.asr.AsrModelCatalog
import com.zola.aivideo.data.download.ModelDownloadState
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 语音模型管理页。
 *
 * - 三档模型：radio 切换（仅已下载可选）、体积展示
 * - 下载/暂停/继续/重试/删除，进度条 + 已下载/总量
 * - 「仅 Wi-Fi 下载」开关
 * - 底部 FFmpeg 版本诊断行（M1 报告承诺）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val states by viewModel.downloadStates.collectAsState()
    val selected by viewModel.selectedModel.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音模型", style = tokens.type.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(tokens.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            WifiOnlyRow(
                checked = wifiOnly,
                onCheckedChange = viewModel::onWifiOnlyChange,
            )
            viewModel.entries.forEach { entry ->
                ModelCard(
                    entry = entry,
                    state = states[entry.model.id] ?: ModelDownloadState.NotDownloaded,
                    selected = selected == entry.model,
                    onSelect = { viewModel.onSelect(entry.model) },
                    onDownload = { viewModel.onDownload(entry) },
                    onPause = { viewModel.onPause(entry) },
                    onDelete = { viewModel.onDelete(entry) },
                )
            }
            Text(
                text = "模型下载到应用私有目录，不占系统公共空间，随应用卸载自动清除。",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticsCard(ffmpegVersion = viewModel.ffmpegVersion)
        }
    }
}

@Composable
private fun WifiOnlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("仅 Wi-Fi 下载模型", style = tokens.type.bodySmall)
                Text(
                    "模型上百 MB，移动数据下载可能产生流量费用",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ModelCard(
    entry: AsrModelCatalog.Entry,
    state: ModelDownloadState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    enabled = state is ModelDownloadState.Completed,
                )
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = tokens.type.titleSmall)
                    Text(
                        entry.description,
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    entry.sizeLabel,
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            when (state) {
                is ModelDownloadState.NotDownloaded -> {
                    StatusLine("未下载")
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("下载", style = tokens.type.label)
                    }
                }
                is ModelDownloadState.Downloading -> {
                    val total = state.totalBytes
                    StatusLine(
                        if (total != null && total > 0) {
                            "下载中 ${FormatUtils.formatFileSize(state.downloadedBytes)} / " +
                                "${FormatUtils.formatFileSize(total)}"
                        } else {
                            "下载中 ${FormatUtils.formatFileSize(state.downloadedBytes)}"
                        },
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                    }
                    OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Text("暂停", style = tokens.type.label)
                    }
                }
                is ModelDownloadState.Paused -> {
                    val total = state.totalBytes
                    StatusLine(
                        "已暂停" + if (total != null && total > 0) {
                            "（${FormatUtils.formatFileSize(state.downloadedBytes)} / ${FormatUtils.formatFileSize(total)}，可断点续传）"
                        } else {
                            "（可断点续传）"
                        },
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("继续", style = tokens.type.label)
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text("删除", style = tokens.type.label)
                        }
                    }
                }
                is ModelDownloadState.Completed -> {
                    StatusLine(
                        if (selected) "已下载 · 当前使用" else "已下载",
                        positive = true,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("删除模型", style = tokens.type.label)
                    }
                }
                is ModelDownloadState.Failed -> {
                    StatusLine("下载失败：${state.reason}", error = true)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("重试", style = tokens.type.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, positive: Boolean = false, error: Boolean = false) {
    val tokens = DefaultAiVideoTokens
    val color = when {
        error -> MaterialTheme.colorScheme.error
        positive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = tokens.type.bodySmall, color = color)
}

/** 诊断卡：FFmpeg 版本（M1 报告承诺的真机验证入口）。 */
@Composable
private fun DiagnosticsCard(ffmpegVersion: String?) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Text("诊断信息", style = tokens.type.titleSmall)
            Spacer(Modifier.height(tokens.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "FFmpeg 版本",
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ffmpegVersion ?: "未知（真机上查看）",
                    style = tokens.type.bodySmall,
                )
            }
        }
    }
}
