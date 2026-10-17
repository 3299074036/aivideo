package com.zola.aivideo.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.zola.aivideo.data.media.FormatUtils
import com.zola.aivideo.domain.model.VideoMetadata
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 导入结果页：展示视频元信息（分辨率、时长、大小、音轨/字幕轨）。
 *
 * 状态机：复制中 → 读取中 → 成功 / 失败（原因 + 重试）。
 * 耗时操作一律有进度反馈，禁止白屏干等。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportResultScreen(
    uriString: String,
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onTranscribe: (filePath: String) -> Unit = {},
    /** M5：创建处理任务（任务名、源视频路径）。 */
    onCreateTask: (name: String, filePath: String) -> Unit = { _, _ -> },
) {
    val tokens = DefaultAiVideoTokens
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uriString) {
        viewModel.importFromUri(uriString)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频信息", style = tokens.type.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                is ImportUiState.Copying -> LoadingBlock("正在导入视频…")
                is ImportUiState.Reading -> LoadingBlock("正在读取视频信息…")
                is ImportUiState.Success -> MetadataBlock(
                    metadata = state.metadata,
                    onTranscribe = onTranscribe,
                    onCreateTask = onCreateTask,
                )
                is ImportUiState.Error -> ErrorBlock(
                    reason = state.reason,
                    onRetry = {
                        viewModel.reset()
                        viewModel.importFromUri(uriString)
                    },
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
private fun ErrorBlock(reason: String, onRetry: () -> Unit) {
    val tokens = DefaultAiVideoTokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        Text("导入失败", style = tokens.type.titleSmall)
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            text = reason,
            style = tokens.type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.padding(tokens.spacing.xs))
            Text("重试", style = tokens.type.label)
        }
    }
}

@Composable
private fun MetadataBlock(
    metadata: VideoMetadata,
    onTranscribe: (filePath: String) -> Unit,
    onCreateTask: (name: String, filePath: String) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.padding(tokens.spacing.xs))
            Text(
                text = metadata.displayName,
                style = tokens.type.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MetadataCard(
            rows = listOf(
                "分辨率" to (metadata.resolutionLabel ?: "未知"),
                "时长" to FormatUtils.formatDuration(metadata.durationMs),
                "大小" to FormatUtils.formatFileSize(metadata.sizeBytes),
                "格式" to (metadata.mimeType ?: "未知"),
                "音频轨" to if (metadata.hasAudioTrack) "有" else "无",
                "内嵌字幕" to if (metadata.hasSubtitleTrack) "有" else "无",
            ),
        )
        if (!metadata.hasAudioTrack) {
            Text(
                text = "该视频没有音频轨，语音识别与配音功能将不可用",
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // M2：语音转写入口（有音频轨才可用）
        Button(
            onClick = { onTranscribe(metadata.filePath) },
            enabled = metadata.hasAudioTrack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("语音转写", style = tokens.type.label)
        }
        // M5：创建处理任务入口（任务中心流水线）
        OutlinedButton(
            onClick = { onCreateTask(metadata.displayName, metadata.filePath) },
            enabled = metadata.hasAudioTrack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("创建处理任务", style = tokens.type.label)
        }
        Text(
            text = "创建任务后，在任务中心按 识别 → 翻译 → 配音 → 合成 逐步推进",
            style = tokens.type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetadataCard(rows: List<Pair<String, String>>) {
    val tokens = DefaultAiVideoTokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.corners.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = tokens.type.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = value, style = tokens.type.bodySmall)
                }
            }
        }
    }
}
