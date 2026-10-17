package com.zola.aivideo.ui.dub

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.tts.DubAligner
import com.zola.aivideo.data.tts.DubSegmentOutcome
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 配音试听页（M4）。
 *
 * - MediaPlayer 播放对齐后的配音轨（WAV）；播完/失败提示
 * - 分段状态徽标：补静音（静音 n 秒）/变速（tempo x）/截断（被截断，标记核对）
 * - 底部：M5 合成占位入口（导出配音轨为文件，视频混流在 M5）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubPreviewScreen(
    onBack: () -> Unit,
    viewModel: DubPreviewViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val uiState = viewModel.uiState
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = DubPreviewViewModel.CreateWavDocument,
        onResult = { uri ->
            if (uri != null) viewModel.exportTo(uri) else viewModel.exportCancelled()
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配音试听", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(tokens.spacing.xl)) {
                Button(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = uiState.result != null && !uiState.exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (uiState.exporting) "导出中…" else "导出配音轨（M5 视频合成）",
                        style = tokens.type.label,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.sm))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回", style = tokens.type.label)
                }
                uiState.exportMessage?.let { msg ->
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        msg,
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.result == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(tokens.spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "暂无配音结果，请先合成",
                    style = tokens.type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        val result = uiState.result!!
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(tokens.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.togglePlay() }) {
                            Icon(
                                if (uiState.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.playing) "暂停" else "播放",
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (uiState.playing) "播放中…" else "配音轨",
                                style = tokens.type.body,
                            )
                            Text(
                                "时长 ${formatMs(result.totalMs)} · " +
                                    "${result.outcomes.size} 段 · " +
                                    (if (result.strategy == com.zola.aivideo.data.tts.DubStrategy.MIX) "混音" else "替换") +
                                    "模式",
                                style = tokens.type.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                val truncated = result.outcomes.count { it.action is DubAligner.AlignAction.Truncate }
                val speedUp = result.outcomes.count { it.action is DubAligner.AlignAction.SpeedUp }
                if (truncated > 0 || speedUp > 0) {
                    Text(
                        buildString {
                            if (truncated > 0) append("$truncated 段被截断（超长，已淡出），请检查译文是否过长\n")
                            if (speedUp > 0) append("$speedUp 段轻微变速（≤1.2x）以对齐槽位")
                        }.trim(),
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(result.outcomes, key = { it.index }) { outcome ->
                SegmentOutcomeCard(outcome)
            }
            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}

@Composable
private fun SegmentOutcomeCard(outcome: DubSegmentOutcome) {
    val tokens = DefaultAiVideoTokens
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "分段 ${outcome.index + 1}",
                    style = tokens.type.label,
                    modifier = Modifier.weight(1f),
                )
                OutcomeBadge(outcome.action)
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                "槽位 ${outcome.slotMs}ms · 合成 ${outcome.synthMs}ms",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutcomeBadge(action: DubAligner.AlignAction) {
    val (label, isWarn) = when (action) {
        is DubAligner.AlignAction.PadSilence ->
            "补静音 ${action.silenceMs}ms" to false
        is DubAligner.AlignAction.SpeedUp ->
            "变速 ${"%.2f".format(action.tempo)}x" to false
        is DubAligner.AlignAction.Truncate ->
            "截断+淡出（请核对）" to true
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = DefaultAiVideoTokens.type.caption) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isWarn) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

private fun formatMs(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
