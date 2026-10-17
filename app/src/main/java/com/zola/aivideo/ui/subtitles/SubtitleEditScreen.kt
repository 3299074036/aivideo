package com.zola.aivideo.ui.subtitles

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.domain.model.TranscriptSegment
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens
import kotlinx.coroutines.launch

/**
 * 字幕预览与编辑页。
 *
 * - 分段列表预览；每段文本可直接编辑
 * - 点时间行弹出校准对话框（手动改开始/结束秒数）
 * - 删除分段
 * - 底部「下一步：翻译」（M3 已上线）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleEditScreen(
    viewModel: SubtitleEditViewModel,
    onBack: () -> Unit,
    onNextStep: () -> Unit = {},
) {
    val tokens = DefaultAiVideoTokens
    val segments = viewModel.segments
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingTimeOf by remember { mutableStateOf<TranscriptSegment?>(null) }

    editingTimeOf?.let { seg ->
        TimeAdjustDialog(
            segment = seg,
            onDismiss = { editingTimeOf = null },
            onConfirm = { startMs, endMs ->
                val err = viewModel.updateTime(seg.id, startMs, endMs)
                if (err != null) {
                    scope.launch { snackbar.showSnackbar(err) }
                } else {
                    editingTimeOf = null
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("字幕预览与编辑", style = tokens.type.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.spacing.xl),
            ) {
                Text(
                    "${segments.size} 段 · 共 ${viewModel.totalChars} 字",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(tokens.spacing.sm))
                Button(
                    onClick = {
                        if (viewModel.taskMode) {
                            // 任务模式：保存到任务目录并返回（任务详情页"校对字幕"入口）
                            val error = viewModel.saveToTask()
                            if (error == null) {
                                onBack()
                            } else {
                                scope.launch { snackbar.showSnackbar(error) }
                            }
                        } else {
                            viewModel.commitToTranslation()
                            onNextStep()
                        }
                    },
                    enabled = segments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (viewModel.taskMode) "保存" else "下一步：翻译",
                        style = tokens.type.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (segments.isEmpty()) {
            EmptyBlock(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                items(segments, key = { it.id }) { seg ->
                    SegmentCard(
                        segment = seg,
                        onTextChange = { viewModel.updateText(seg.id, it) },
                        onAdjustTime = { editingTimeOf = seg },
                        onDelete = { viewModel.delete(seg.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBlock(modifier: Modifier = Modifier) {
    val tokens = DefaultAiVideoTokens
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "暂无字幕分段",
            style = tokens.type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SegmentCard(
    segment: TranscriptSegment,
    onTextChange: (String) -> Unit,
    onAdjustTime: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onAdjustTime) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "校准时间",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${FormatUtils.formatTimestampMs(segment.startMs)} → " +
                            FormatUtils.formatTimestampMs(segment.endMs),
                        style = tokens.type.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除本段",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            OutlinedTextField(
                value = segment.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = tokens.type.body,
                placeholder = { Text("（空）", style = tokens.type.body) },
                maxLines = 6,
            )
        }
    }
}

/** 时间校准对话框：输入开始/结束秒数（支持小数，如 12.5）。 */
@Composable
private fun TimeAdjustDialog(
    segment: TranscriptSegment,
    onDismiss: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    var startText by remember { mutableStateOf("%.3f".format(segment.startMs / 1000.0)) }
    var endText by remember { mutableStateOf("%.3f".format(segment.endMs / 1000.0)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准时间轴", style = tokens.type.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                Text(
                    "单位：秒，支持小数（如 12.5）。时间戳精度决定 M4 配音对齐效果。",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; error = null },
                    label = { Text("开始（秒）", style = tokens.type.caption) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; error = null },
                    label = { Text("结束（秒）", style = tokens.type.caption) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = tokens.type.caption, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = startText.toDoubleOrNull()
                val e = endText.toDoubleOrNull()
                when {
                    s == null || e == null -> error = "请输入有效的数字"
                    s < 0 -> error = "开始时间不能为负数"
                    e <= s -> error = "结束时间必须晚于开始时间"
                    else -> onConfirm((s * 1000).toLong(), (e * 1000).toLong())
                }
            }) {
                Text("确定", style = tokens.type.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = tokens.type.label) }
        },
    )
}
