package com.zola.aivideo.ui.stock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zola.aivideo.data.stock.StockClip
import com.zola.aivideo.data.stock.StockProvider
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 素材搜索页（M6，任务模式 ?taskId=）。
 *
 * 上：关键词搜索（当前提供方）；中：搜索结果（下载）；
 * 下：已下载素材（替换片段 / 删除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSearchScreen(
    viewModel: StockSearchViewModel,
    onBack: () -> Unit,
    onReplaceClip: (fileName: String) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // 本地视频选择器（2026-10-17 本地化：无 Key 时的本地替代方案）
    val localPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val fileName = viewModel.importLocalVideo(uri)
                    if (fileName != null) onReplaceClip(fileName)
                }
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("素材库", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            // 本地视频入口（2026-10-17）：无需 Key，直接从本机选视频作素材
            item {
                Spacer(Modifier.height(tokens.spacing.sm))
                OutlinedButton(
                    onClick = { localPicker.launch("video/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Text("从本地选择视频", style = tokens.type.label)
                }
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(
                    "无 API Key 时用本地视频代替在线素材",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Spacer(Modifier.height(tokens.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StockProvider.entries.forEach { p ->
                        OutlinedButton(
                            onClick = { viewModel.setProvider(p) },
                            enabled = s.provider != p,
                        ) { Text(p.displayName, style = tokens.type.label) }
                        Spacer(Modifier.width(tokens.spacing.sm))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = s.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("搜索关键词（英文效果更好）") },
                        placeholder = { Text("city night") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Button(
                        onClick = viewModel::search,
                        enabled = !s.searching && s.query.isNotBlank(),
                    ) { Text("搜索", style = tokens.type.label) }
                }
                if (s.searching) {
                    Spacer(Modifier.height(tokens.spacing.sm))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                s.searchError?.let { err ->
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(err, style = tokens.type.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (s.results.isNotEmpty()) {
                item { Text("搜索结果", style = tokens.type.titleSmall) }
                items(s.results, key = { it.clipId }) { clip ->
                    val progress = s.downloading[clip.clipId]
                    ClipCard(
                        clip = clip,
                        downloading = progress != null,
                        progress = progress ?: 0f,
                        actionLabel = "下载",
                        onAction = { viewModel.download(clip) },
                    )
                }
            }

            if (s.downloaded.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text("已下载（任务内）", style = tokens.type.titleSmall)
                }
                items(s.downloaded, key = { it.file.name }) { item ->
                    val clip = item.clip
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = tokens.corners.md,
                    ) {
                        Column(Modifier.padding(tokens.spacing.lg)) {
                            Text(
                                clip?.let {
                                    "${it.provider.displayName} · ${it.authorName} · ${it.width}×${it.height}"
                                } ?: item.file.name,
                                style = tokens.type.bodySmall,
                            )
                            clip?.let {
                                Text(
                                    it.licenseName,
                                    style = tokens.type.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(tokens.spacing.sm))
                            Row {
                                Button(onClick = { onReplaceClip(item.file.name) }) {
                                    Text("替换片段", style = tokens.type.label)
                                }
                                Spacer(Modifier.width(tokens.spacing.sm))
                                OutlinedButton(onClick = {
                                    item.file.delete()
                                    viewModel.refreshDownloaded()
                                }) {
                                    Text("删除", style = tokens.type.label)
                                }
                            }
                        }
                    }
                }
            }
            s.downloadError?.let { err ->
                item {
                    Text(err, style = tokens.type.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}

@Composable
private fun ClipCard(
    clip: StockClip,
    downloading: Boolean,
    progress: Float,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Text(
                "${clip.provider.displayName} · ${clip.authorName}",
                style = tokens.type.bodySmall,
            )
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                "${clip.width}×${clip.height}" +
                    (clip.durationSec?.let { " · ${it.toInt()}s" } ?: "") +
                    " · ${clip.licenseName}",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(tokens.spacing.sm))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(onClick = onAction) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(actionLabel, style = tokens.type.label)
                }
            }
        }
    }
}
