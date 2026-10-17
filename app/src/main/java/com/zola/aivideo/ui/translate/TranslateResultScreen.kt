package com.zola.aivideo.ui.translate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens
import kotlinx.coroutines.launch

/**
 * 翻译结果预览页（M3）。
 *
 * - 分段预览译文；门禁未通过的段显示「时间点待核实」徽标
 * - 导出 .srt（SAF）；下一步进入 M4 配音配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateResultScreen(
    viewModel: TranslateResultViewModel,
    onBack: () -> Unit,
    onStartDub: () -> Unit = {},
) {
    val tokens = DefaultAiVideoTokens
    val segments = viewModel.segments
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = TranslateResultViewModel.CreateSrtDocument,
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    viewModel.exportTo(uri)
                }
            }
        },
    )

    viewModel.exportMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("翻译结果", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.padding(tokens.spacing.xl)) {
                val unverified = segments.count { !it.verified }
                if (unverified > 0) {
                    Text(
                        "$unverified 段未通过时间戳校验，已标注，请核对后手动修正",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                }
                Button(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = segments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导出字幕 .srt", style = tokens.type.label)
                }
                Spacer(Modifier.height(tokens.spacing.sm))
                Button(
                    onClick = {
                        viewModel.startDub()
                        onStartDub()
                    },
                    enabled = segments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("下一步：配音", style = tokens.type.label)
                }
            }
        },
    ) { innerPadding ->
        if (segments.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(tokens.spacing.xl),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "暂无翻译结果",
                    style = tokens.type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                item {
                    Text(
                        "来源：${viewModel.sourceLabel}（${segments.size} 段）",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = tokens.spacing.xl,
                            vertical = tokens.spacing.sm,
                        ),
                    )
                }
                items(segments, key = { it.original.id }) { seg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.xl),
                        shape = tokens.corners.md,
                    ) {
                        Column(Modifier.padding(tokens.spacing.lg)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    FormatUtils.formatTimestampMs(seg.original.startMs) +
                                        " → " +
                                        FormatUtils.formatTimestampMs(seg.original.endMs),
                                    style = tokens.type.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!seg.verified) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("时间点待核实") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                        ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(tokens.spacing.xs))
                            Text(seg.displayText, style = tokens.type.body)
                        }
                    }
                }
                item { Spacer(Modifier.height(tokens.spacing.sm)) }
            }
        }
    }
}
