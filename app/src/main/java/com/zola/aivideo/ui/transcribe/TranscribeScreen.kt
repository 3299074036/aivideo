package com.zola.aivideo.ui.transcribe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 语音转写页。
 *
 * 流程：检查模型 →（未下载）引导模型页 → 二次确认（前台运行提醒）→
 * 阶段式进度 → 成功后进字幕编辑页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscribeScreen(
    videoPath: String,
    viewModel: TranscribeViewModel,
    onBack: () -> Unit,
    onNeedModel: () -> Unit,
    onDone: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val uiState by viewModel.uiState.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(videoPath) { viewModel.start(videoPath) }
    LaunchedEffect(Unit) { viewModel.doneEvent.collect { onDone() } }

    // 二次确认弹窗：状态进入 Confirm 时弹出一次
    LaunchedEffect(uiState) {
        showConfirm = uiState is TranscribeUiState.Confirm
    }

    if (showConfirm) {
        val name = (uiState as? TranscribeUiState.Confirm)?.modelName.orEmpty()
        AlertDialog(
            onDismissRequest = { showConfirm = false; onBack() },
            title = { Text("开始语音转写", style = tokens.type.titleSmall) },
            text = {
                Text(
                    "将使用「$name」在手机上转写，全程不上传云端。\n\n" +
                        "转写在前台运行，请保持本页在前台不要切换出去，" +
                        "否则可能被系统中断。5 分钟视频约需 1~3 分钟。",
                    style = tokens.type.bodySmall,
                )
            },
            confirmButton = {
                Button(onClick = { showConfirm = false; viewModel.confirmStart(videoPath) }) {
                    Text("开始转写", style = tokens.type.label)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false; onBack() }) {
                    Text("取消", style = tokens.type.label)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音转写", style = tokens.type.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancel()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(tokens.spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                TranscribeUiState.Checking -> LoadingBlock("正在检查语音模型…")
                is TranscribeUiState.NeedModel -> NeedModelBlock(
                    modelName = state.modelName,
                    onGoModels = onNeedModel,
                    onBack = onBack,
                )
                is TranscribeUiState.Confirm -> LoadingBlock("准备开始…")
                is TranscribeUiState.Running -> RunningBlock(
                    stageText = state.stageText,
                    progress = state.progress,
                    onCancel = { viewModel.cancel(); onBack() },
                )
                is TranscribeUiState.Error -> ErrorBlock(
                    reason = state.reason,
                    onRetry = { viewModel.retry(videoPath) },
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock(text: String) {
    val tokens = DefaultAiVideoTokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(tokens.spacing.lg))
        Text(text, style = tokens.type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NeedModelBlock(modelName: String, onGoModels: () -> Unit, onBack: () -> Unit) {
    val tokens = DefaultAiVideoTokens
    Card(
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.padding(tokens.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("需要先下载语音模型", style = tokens.type.titleSmall)
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                "当前选择「$modelName」尚未下载，下载后才能在手机上转写。",
                style = tokens.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(tokens.spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                OutlinedButton(onClick = onBack) { Text("返回", style = tokens.type.label) }
                Button(onClick = onGoModels) { Text("去下载模型", style = tokens.type.label) }
            }
        }
    }
}

@Composable
private fun RunningBlock(stageText: String, progress: Float?, onCancel: () -> Unit) {
    val tokens = DefaultAiVideoTokens
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(tokens.spacing.lg))
        Text(stageText, style = tokens.type.bodySmall)
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            "请保持本页在前台",
            style = tokens.type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        OutlinedButton(onClick = onCancel) { Text("取消", style = tokens.type.label) }
    }
}

@Composable
private fun ErrorBlock(reason: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val tokens = DefaultAiVideoTokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        Text("转写失败", style = tokens.type.titleSmall)
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            reason,
            style = tokens.type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            OutlinedButton(onClick = onBack) { Text("返回", style = tokens.type.label) }
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("重试", style = tokens.type.label)
            }
        }
    }
}
