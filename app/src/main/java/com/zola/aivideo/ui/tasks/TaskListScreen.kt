package com.zola.aivideo.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.task.TaskSnapshot
import com.zola.aivideo.data.task.TaskStatus
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务中心列表（M5）。
 *
 * 状态徽标 + 总进度 + 当前阶段文案；空状态精心设计；删除二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val snapshots by viewModel.snapshots.collectAsState()
    var deleting by remember { mutableStateOf<TaskSnapshot?>(null) }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除任务") },
            text = { Text("将删除「${target.name}」及其全部中间产物，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(target.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务中心", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (snapshots.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(tokens.spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(tokens.spacing.lg))
                    Text("还没有处理任务", style = tokens.type.title)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        "导入视频后点击「创建处理任务」，\n转写 → 翻译 → 配音 → 合成，一步步走完流水线。",
                        style = tokens.type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = tokens.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                item { Spacer(Modifier.height(tokens.spacing.sm)) }
                items(snapshots, key = { it.id }) { snapshot ->
                    TaskCard(
                        snapshot = snapshot,
                        onOpen = { onOpenTask(snapshot.id) },
                        onDelete = { deleting = snapshot },
                    )
                }
                item { Spacer(Modifier.height(tokens.spacing.xl)) }
            }
        }
    }
}

@Composable
private fun TaskCard(
    snapshot: TaskSnapshot,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot.name,
                    style = tokens.type.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除任务",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                "${snapshot.stageLine} · ${formatTime(snapshot.updatedAt)}",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(tokens.spacing.sm))
            LinearProgressIndicator(
                progress = { snapshot.totalProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(tokens.spacing.sm))
            AssistChip(
                onClick = onOpen,
                label = { Text(statusText(snapshot.status)) },
            )
        }
    }
}

private fun statusText(status: TaskStatus): String = when (status) {
    TaskStatus.RUNNING -> "进行中"
    TaskStatus.NEEDS_INPUT -> "待确认"
    TaskStatus.DONE -> "已完成"
    TaskStatus.FAILED -> "失败"
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
