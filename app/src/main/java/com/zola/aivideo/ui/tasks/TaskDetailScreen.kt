package com.zola.aivideo.ui.tasks

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.task.StageState
import com.zola.aivideo.data.task.TaskSnapshot
import com.zola.aivideo.data.task.TaskStage
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 任务详情页（M5）。
 *
 * 步骤条：导入 → 识别 → 翻译 → 配音 → 合成。
 * 每步的状态（等待/进行中/完成/失败/待确认）一目了然；
 * 失败段显示原因 + 重试按钮；NEEDS_INPUT 段显示"继续"按钮；
 * 合成完成后可打开/分享成品。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onContinueStage: (taskId: Long, stage: TaskStage) -> Unit,
    onProofread: (taskId: Long) -> Unit,
    onOpenStock: (taskId: Long) -> Unit = {},
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val snapshot by viewModel.snapshot.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除任务") },
            text = { Text("将删除该任务及其全部中间产物，不可恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTask(onBack) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        snapshot?.name ?: "任务详情",
                        style = tokens.type.title,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除任务")
                    }
                },
            )
        },
    ) { innerPadding ->
        val s = snapshot
        if (s == null) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .padding(tokens.spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("任务不存在或已被删除", style = tokens.type.body)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item { Spacer(Modifier.height(tokens.spacing.sm)) }

            // 步骤条
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(tokens.spacing.lg)) {
                        Text("处理进度", style = tokens.type.label)
                        Spacer(Modifier.height(tokens.spacing.sm))
                        StageRow("导入", StageState.DONE, 1f, null)
                        StageRow("识别", s.transcribeState, s.transcribeProgress, s.transcribeError)
                        StageRow("翻译", s.translateState, s.translateProgress, s.translateError)
                        StageRow("配音", s.dubState, s.dubProgress, s.dubError)
                        StageRow("合成", s.synthesizeState, s.synthesizeProgress, s.synthesizeError)
                    }
                }
            }

            // 新任务：开始识别（TRANSCRIBE PENDING 时的主动作）
            if (s.transcribeState == StageState.PENDING) {
                item(key = "start-transcribe") {
                    Button(
                        onClick = { viewModel.retryStage(TaskStage.TRANSCRIBE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("开始识别") }
                }
            }

            // 阶段操作区（失败重试 / NEEDS_INPUT 继续 / 校对字幕）
            for (stage in TaskStage.entries) {
                val state = s.stageStateOf(stage)
                val error = s.stageErrorOf(stage)
                if (state == StageState.FAILED || state == StageState.NEEDS_INPUT) {
                    item(key = "action-$stage") {
                        StageActionCard(
                            stage = stage,
                            state = state,
                            error = error,
                            onRetry = { viewModel.retryStage(stage) },
                            onContinue = { onContinueStage(s.id, stage) },
                            onCancel = { viewModel.cancelStage(stage) },
                        )
                    }
                }
            }

            // 转写 DONE 后的次要动作：校对字幕
            if (s.transcribeState == StageState.DONE) {
                item(key = "proofread") {
                    OutlinedButton(
                        onClick = { onProofread(s.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("校对字幕") }
                }
            }

            // M6：素材替换（Pexels/Pixabay 片段替换源视频画面）
            item(key = "stock") {
                OutlinedButton(
                    onClick = { onOpenStock(s.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("素材库：替换视频片段") }
            }

            // 合成 DONE：打开 / 分享成品
            if (s.synthesizeState == StageState.DONE && s.synthesizeOutput != null) {
                item(key = "output") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(tokens.spacing.lg)) {
                            Text("合成完成", style = tokens.type.label)
                            Spacer(Modifier.height(tokens.spacing.sm))
                            Row {
                                Button(
                                    onClick = {
                                        viewModel.viewOutput()?.let {
                                            context.startActivity(
                                                Intent.createChooser(it, "打开视频"),
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("打开")
                                }
                                Spacer(Modifier.width(tokens.spacing.sm))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareOutput()?.let {
                                            context.startActivity(
                                                Intent.createChooser(it, "分享视频"),
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("分享")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}

/** 单步行：图标 + 名称 + 进度/错误。 */
@Composable
private fun StageRow(
    name: String,
    state: StageState,
    progress: Float,
    error: String?,
) {
    val tokens = DefaultAiVideoTokens
    val (icon, tint) = when (state) {
        StageState.DONE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        StageState.RUNNING -> null to MaterialTheme.colorScheme.primary
        StageState.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        StageState.NEEDS_INPUT -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.tertiary
        StageState.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (state == StageState.RUNNING) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            icon?.let {
                Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(tokens.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(name, style = tokens.type.body)
            if (state == StageState.RUNNING && progress > 0f) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state == StageState.FAILED && !error.isNullOrBlank()) {
                Text(
                    error,
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
        Text(
            stageStateText(state),
            style = tokens.type.caption,
            color = tint,
        )
    }
}

private fun stageStateText(state: StageState): String = when (state) {
    StageState.PENDING -> "等待"
    StageState.RUNNING -> "进行中"
    StageState.DONE -> "完成"
    StageState.FAILED -> "失败"
    StageState.NEEDS_INPUT -> "待确认"
}

/** 失败/待确认段的操作卡片。 */
@Composable
private fun StageActionCard(
    stage: TaskStage,
    state: StageState,
    error: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val container = if (state == StageState.FAILED) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Text(
                "${stageName(stage)}${if (state == StageState.FAILED) "失败" else "待确认"}",
                style = tokens.type.label,
            )
            if (state == StageState.FAILED && !error.isNullOrBlank()) {
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(error, style = tokens.type.caption)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            Row {
                if (state == StageState.FAILED) {
                    Button(onClick = onRetry) { Text("重试") }
                    Spacer(Modifier.width(tokens.spacing.sm))
                    TextButton(onClick = onCancel) { Text("取消") }
                } else {
                    Button(onClick = onContinue) { Text("继续") }
                }
            }
        }
    }
}

private fun stageName(stage: TaskStage): String = when (stage) {
    TaskStage.TRANSCRIBE -> "识别"
    TaskStage.TRANSLATE -> "翻译"
    TaskStage.DUB -> "配音"
    TaskStage.SYNTHESIZE -> "合成"
}

private fun TaskSnapshot.stageStateOf(stage: TaskStage): StageState = when (stage) {
    TaskStage.TRANSCRIBE -> transcribeState
    TaskStage.TRANSLATE -> translateState
    TaskStage.DUB -> dubState
    TaskStage.SYNTHESIZE -> synthesizeState
}

private fun TaskSnapshot.stageErrorOf(stage: TaskStage): String? = when (stage) {
    TaskStage.TRANSCRIBE -> transcribeError
    TaskStage.TRANSLATE -> translateError
    TaskStage.DUB -> dubError
    TaskStage.SYNTHESIZE -> synthesizeError
}
