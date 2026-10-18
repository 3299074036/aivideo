package com.zola.aivideo.ui.dub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 合成进度页（M4）：按分段流式合成，可取消。
 *
 * - 逐段合成进度条 + 当前阶段文案
 * - 取消按钮：取消协程（FFmpeg 命令也会被中断）
 * - 完成 → 跳转试听页；失败 → 显示错误与重试/返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubProgressScreen(
    onBack: () -> Unit,
    onPreview: () -> Unit,
    viewModel: DubProgressViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.finished) {
        LaunchedEffect(Unit) { onPreview() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("合成配音", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.running) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(tokens.spacing.xl)) {
                if (uiState.running) {
                    OutlinedButton(
                        onClick = viewModel::cancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消", style = tokens.type.label)
                    }
                } else if (uiState.error != null) {
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重试", style = tokens.type.label)
                    }
                    Spacer(Modifier.height(tokens.spacing.sm))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("返回配置", style = tokens.type.label)
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(tokens.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (uiState.running) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(tokens.spacing.md))
                Text(uiState.phase, style = tokens.type.body)
                Spacer(Modifier.height(tokens.spacing.sm))
                Text(
                    "${uiState.done}/${uiState.total} 段",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.error != null) {
                Text(
                    "合成失败",
                    style = tokens.type.title,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(tokens.spacing.sm))
                Text(
                    uiState.error!!,
                    style = tokens.type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.cancelled) {
                Text("已取消", style = tokens.type.title)
                Spacer(Modifier.height(tokens.spacing.sm))
                Text(
                    "已合成的分段保留在临时目录，未生成完整配音轨",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 兜底（2026-10-17）：三态都不满足时不空白，显示准备中
                CircularProgressIndicator()
                Spacer(Modifier.height(tokens.spacing.md))
                Text("准备中…", style = tokens.type.body)
            }
        }
    }
}
