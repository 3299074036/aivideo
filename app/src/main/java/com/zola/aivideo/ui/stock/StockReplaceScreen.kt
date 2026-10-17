package com.zola.aivideo.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 素材替换页（M6）：选择源视频的时间范围，替换为已下载素材。
 *
 * 画面替换、音频保留原音轨；产物 replaced.mp4 在合成配置页可选用；
 * 授权记录自动追加到任务 licenses.json。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockReplaceScreen(
    viewModel: StockReplaceViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(s.done) {
        if (s.done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("替换视频片段", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            s.clip?.let { clip ->
                Card(shape = tokens.corners.md) {
                    Column(Modifier.padding(tokens.spacing.lg)) {
                        Text(
                            "${clip.provider.displayName} · ${clip.authorName}",
                            style = tokens.type.bodySmall,
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Text(
                            "${clip.width}×${clip.height} · ${clip.licenseName}",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (s.sourceDurationSec > 0) {
                Text(
                    "源视频时长：${"%.1f".format(s.sourceDurationSec)}s",
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = s.startSecInput,
                    onValueChange = viewModel::setStart,
                    label = { Text("起点（秒）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = !s.replacing,
                )
                OutlinedTextField(
                    value = s.endSecInput,
                    onValueChange = viewModel::setEnd,
                    label = { Text("终点（秒）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = !s.replacing,
                )
            }
            s.error?.let { err ->
                Text(err, style = tokens.type.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::confirm,
                enabled = !s.replacing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (s.replacing) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(tokens.spacing.sm))
                }
                Text(if (s.replacing) "替换中…" else "开始替换", style = tokens.type.label)
            }
            if (s.replacing) {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = tokens.corners.md,
            ) {
                Column(Modifier.padding(tokens.spacing.lg)) {
                    Text("说明", style = tokens.type.titleSmall)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        "· 只替换画面，音频保留源视频完整音轨\n" +
                            "· 素材会自动缩放到源视频分辨率（居中裁剪）\n" +
                            "· 替换结果存为任务的 replaced.mp4，在合成配置页可选用\n" +
                            "· 素材来源、作者、授权页已记入任务 licenses.json",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
