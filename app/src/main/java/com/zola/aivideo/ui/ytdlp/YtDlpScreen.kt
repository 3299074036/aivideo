package com.zola.aivideo.ui.ytdlp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.data.ytdlp.YtDlpChannel
import com.zola.aivideo.data.ytdlp.YtDlpDownloadState
import com.zola.aivideo.data.ytdlp.YtDlpDownloadTask
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens
import java.io.File

/**
 * 链接下载页（M6）：yt-dlp 独立更新通道 + 批量下载。
 *
 * 上：环境状态（Python 解释器是否内置 / yt-dlp 版本 / 更新通道 / 检查更新）；
 * 下：批量下载（每行一个链接，顺序执行，完成后可导入到任务）。
 *
 * 前置要求说明：API 29+ 不允许从可写私有目录执行 ELF，因此 Python 解释器
 * 必须随 APK 内置（构建配方见 docs/research/aivideo/yt-dlp集成调研.md）；
 * 当前构建若未包含，界面会明确提示而非静默失败。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtDlpScreen(
    viewModel: YtDlpViewModel,
    environment: com.zola.aivideo.data.ytdlp.YtDlpEnvironment,
    onBack: () -> Unit,
    onImportFile: (File) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s by viewModel.uiState.collectAsState()
    var showWifiConfirm by remember { mutableStateOf(false) }

    if (showWifiConfirm) {
        AlertDialog(
            onDismissRequest = { showWifiConfirm = false },
            title = { Text("当前不在 Wi-Fi 下") },
            text = { Text("你开启了「仅 Wi-Fi 下载更新」，确定用移动数据下载约 2.9MB 的 yt-dlp 更新吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showWifiConfirm = false
                    viewModel.downloadUpdate()
                }) { Text("仍要下载") }
            },
            dismissButton = {
                TextButton(onClick = { showWifiConfirm = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("链接下载", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item {
                Spacer(Modifier.height(tokens.spacing.sm))
                EnvCard(
                    hasInterpreter = s.hasInterpreter,
                    hasArchive = s.hasArchive,
                    installedTag = s.installedTag,
                    localVersion = s.localVersion,
                )
            }

            // 更新通道
            item {
                Card(shape = tokens.corners.md) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(tokens.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    ) {
                        Text("yt-dlp 更新通道", style = tokens.type.titleSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            YtDlpChannel.entries.forEach { ch ->
                                OutlinedButton(
                                    onClick = { viewModel.setChannel(ch) },
                                    enabled = s.channel != ch && !s.checking,
                                ) { Text(ch.displayName, style = tokens.type.label) }
                            }
                            Spacer(Modifier.weight(1f))
                            if (s.checking) CircularProgressIndicator()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "仅 Wi-Fi 下载更新",
                                style = tokens.type.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = viewModel.isWifiOnly(),
                                onCheckedChange = { viewModel.setWifiOnly(it) },
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { viewModel.checkForUpdate(force = true) },
                                enabled = !s.checking && !s.downloadingUpdate,
                            ) { Text("检查更新", style = tokens.type.label) }
                        }
                        s.checkError?.let { err ->
                            Text(
                                err,
                                style = tokens.type.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        s.checkMessage?.let { msg ->
                            Text(
                                msg,
                                style = tokens.type.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        s.availableUpdate?.let { update ->
                            Text(
                                "发现新版本 ${update.tag}（约 ${update.sizeBytes / 1024}KB）",
                                style = tokens.type.bodySmall,
                            )
                            if (s.downloadingUpdate) {
                                LinearProgressIndicator(
                                    progress = { s.updateProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Button(onClick = {
                                    // 仅 Wi-Fi 开关：移动数据下二次确认
                                    if (viewModel.isWifiOnly() && !environment.isWifiConnected()) {
                                        showWifiConfirm = true
                                    } else {
                                        viewModel.downloadUpdate()
                                    }
                                }) {
                                    Text("下载并安装", style = tokens.type.label)
                                }
                            }
                        }
                    }
                }
            }

            // 批量下载
            item {
                Text("批量下载", style = tokens.type.titleSmall)
                Spacer(Modifier.height(tokens.spacing.sm))
                OutlinedTextField(
                    value = s.urlsInput,
                    onValueChange = viewModel::setUrlsInput,
                    label = { Text("视频链接（每行一个）") },
                    placeholder = { Text("https://…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !s.batchRunning,
                )
                Spacer(Modifier.height(tokens.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    Button(
                        onClick = viewModel::startBatch,
                        enabled = !s.batchRunning && s.hasInterpreter && s.hasArchive,
                    ) { Text("开始下载", style = tokens.type.label) }
                    if (s.batchRunning) {
                        OutlinedButton(onClick = viewModel::cancelBatch) {
                            Text("取消", style = tokens.type.label)
                        }
                    }
                }
                if (!s.hasInterpreter || !s.hasArchive) {
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        "下载功能不可用：" +
                            if (!s.hasInterpreter) "Python 运行环境未随 APK 内置。"
                            else "yt-dlp 尚未下载，请先在上方检查更新并安装。",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // key 用 index：用户可能粘贴重复 URL，url 作 key 会冲突
            itemsIndexed(s.tasks, key = { index, _ -> index }) { index, task ->
                DownloadTaskCard(
                    task = task,
                    onImport = { viewModel.doneFile(index)?.let(onImportFile) },
                )
            }
            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}

@Composable
private fun EnvCard(
    hasInterpreter: Boolean,
    hasArchive: Boolean,
    installedTag: String,
    localVersion: String?,
) {
    val tokens = DefaultAiVideoTokens
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = tokens.corners.md,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text("运行环境", style = tokens.type.titleSmall)
            EnvRow("Python 解释器（APK 内置）", hasInterpreter)
            EnvRow(
                "yt-dlp " + if (installedTag.isNotBlank()) installedTag else "",
                hasArchive,
            )
            localVersion?.let {
                Text(
                    "本地核验版本：$it",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                "说明：yt-dlp 为 Unlicense（公有领域式），无传染性；" +
                    "请仅下载公开可访问的内容，遵守平台服务条款。",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EnvRow(label: String, ok: Boolean) {
    val tokens = DefaultAiVideoTokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "✗",
            style = tokens.type.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(tokens.spacing.sm))
        Text(label.trim(), style = tokens.type.bodySmall)
    }
}

@Composable
private fun DownloadTaskCard(
    task: YtDlpDownloadTask,
    onImport: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Card(shape = tokens.corners.md) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(
                task.title ?: task.url,
                style = tokens.type.bodySmall,
                maxLines = 2,
            )
            Text(
                stateLabel(task),
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (task.state) {
                YtDlpDownloadState.DOWNLOADING, YtDlpDownloadState.PROBING,
                YtDlpDownloadState.MERGING,
                    -> LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                YtDlpDownloadState.DONE -> {
                    Button(onClick = onImport) {
                        Text("导入到任务", style = tokens.type.label)
                    }
                }
                YtDlpDownloadState.FAILED -> {
                    task.error?.let {
                        Text(
                            it,
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun stateLabel(task: YtDlpDownloadTask): String = when (task.state) {
    YtDlpDownloadState.QUEUED -> "排队中"
    YtDlpDownloadState.PROBING -> "解析链接…"
    YtDlpDownloadState.DOWNLOADING ->
        (task.detail ?: "下载中") + " ${(task.progress * 100).toInt()}%"
    YtDlpDownloadState.MERGING -> "合并音视频…"
    YtDlpDownloadState.DONE -> "完成"
    YtDlpDownloadState.FAILED -> "失败"
    YtDlpDownloadState.CANCELLED -> "已取消"
}
