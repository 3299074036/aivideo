package com.zola.aivideo.ui.dub

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.download.ModelDownloadState
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.data.tts.TtsModelCatalog
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * TTS 模型管理页（M4）：Kokoro 模型下载/删除。
 *
 * - kokoro-int8-multi-lang-v1_1（约 205MB，中英双语，103 音色，24kHz）
 * - 下载/暂停/继续/重试/删除，进度条 + 已下载/总量（totalBytes 未知时只显示已下载）
 * - 模型走应用私有目录按需下载，不打包进 APK（铁律）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsModelScreen(
    onBack: () -> Unit,
    viewModel: TtsModelViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val states by viewModel.downloadStates.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配音模型", style = tokens.type.title) },
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
            TtsModelCard(
                state = states[TtsModelCatalog.entry.id]
                    ?: ModelDownloadState.NotDownloaded,
                onDownload = viewModel::onDownload,
                onPause = viewModel::onPause,
                onDelete = viewModel::onDelete,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(tokens.spacing.md)) {
                    Text("音色说明", style = tokens.type.label)
                    Spacer(Modifier.height(tokens.spacing.xs))
                    Text(
                        "含 103 个音色（3 个英文、100 个中文），24kHz。" +
                            "配音配置页可按语言/性别挑选；全部 sid 映射见官方音色表。",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "模型下载到应用私有目录，不占系统公共空间，随应用卸载自动清除。",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TtsModelCard(
    state: ModelDownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val entry = TtsModelCatalog.entry
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = tokens.type.label)
                    Text(
                        entry.description,
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (state) {
                    is ModelDownloadState.Completed -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除模型",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                if (state is ModelDownloadState.Paused) Icons.Default.PlayArrow
                                else Icons.Default.Download,
                                contentDescription = "下载",
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            when (state) {
                is ModelDownloadState.NotDownloaded -> {
                    Text("未下载", style = tokens.type.caption)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("下载（${entry.sizeLabel}）", style = tokens.type.label)
                    }
                }
                is ModelDownloadState.Downloading -> {
                    val total = state.totalBytes
                    Text(
                        if (total != null && total > 0) {
                            "下载中 ${FormatUtils.formatFileSize(state.downloadedBytes)} / " +
                                FormatUtils.formatFileSize(total)
                        } else {
                            "下载中 ${FormatUtils.formatFileSize(state.downloadedBytes)}"
                        },
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(tokens.spacing.xs))
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is ModelDownloadState.Paused -> {
                    val total = state.totalBytes
                    Text(
                        "已暂停（${FormatUtils.formatFileSize(state.downloadedBytes)}" +
                            (if (total != null && total > 0) " / ${FormatUtils.formatFileSize(total)}" else "") +
                            "，可断点续传）",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(tokens.spacing.xs))
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is ModelDownloadState.Failed -> {
                    Text(
                        "下载失败：${state.reason}，点击重试",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("重试", style = tokens.type.label)
                    }
                }
                is ModelDownloadState.Completed -> {
                    Text(
                        "已下载（${entry.sizeLabel}），可离线使用",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("删除模型释放空间", style = tokens.type.label)
                    }
                }
            }
        }
    }
}
