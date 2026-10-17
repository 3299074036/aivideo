package com.zola.aivideo.ui.translate

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 流式翻译进度页（M3）。
 *
 * - 阶段文案 + chunk 进度 + 已接收字符数，禁止白屏干等
 * - 可取消；失败显示原因 + 重试
 * - 成功后自动跳转结果页（由 NavGraph 观察 doneResult）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateProgressScreen(
    viewModel: TranslateProgressViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在翻译", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (s.running) viewModel.cancel()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
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
            when {
                s.error != null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        shape = tokens.corners.md,
                    ) {
                        Column(Modifier.padding(tokens.spacing.lg)) {
                            Text("翻译失败", style = tokens.type.titleSmall)
                            Spacer(Modifier.height(tokens.spacing.sm))
                            Text(
                                s.error.orEmpty(),
                                style = tokens.type.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(tokens.spacing.lg))
                    Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                        Text("重试", style = tokens.type.label)
                    }
                    Spacer(Modifier.height(tokens.spacing.sm))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回", style = tokens.type.label)
                    }
                }
                s.cancelled -> {
                    Text("已取消翻译", style = tokens.type.titleSmall)
                    Spacer(Modifier.height(tokens.spacing.lg))
                    Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                        Text("重新翻译", style = tokens.type.label)
                    }
                }
                else -> {
                    if (s.running) CircularProgressIndicator()
                    Spacer(Modifier.height(tokens.spacing.lg))
                    Text(s.stageText, style = tokens.type.body)
                    Spacer(Modifier.height(tokens.spacing.md))
                    if (s.chunkTotal > 1) {
                        LinearProgressIndicator(
                            progress = { s.chunkDone.toFloat() / s.chunkTotal },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Text(
                            "第 ${s.chunkDone + 1}/${s.chunkTotal} 组 · 已接收 ${s.charsReceived} 字符",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "已接收 ${s.charsReceived} 字符",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(tokens.spacing.xl))
                    OutlinedButton(
                        onClick = viewModel::cancel,
                        enabled = s.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消翻译", style = tokens.type.label)
                    }
                }
            }
        }
    }
}
