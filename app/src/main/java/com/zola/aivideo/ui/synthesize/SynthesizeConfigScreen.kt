package com.zola.aivideo.ui.synthesize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.synthesize.BitrateTier
import com.zola.aivideo.data.synthesize.EncodePreset
import com.zola.aivideo.data.synthesize.SubtitleMode
import com.zola.aivideo.data.synthesize.SynthesizeOptions
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 合成配置页（M5）：字幕模式 × 编码预设 × 码率档。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthesizeConfigScreen(
    onBack: () -> Unit,
    onConfirmDone: () -> Unit,
    viewModel: SynthesizeConfigViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val options = viewModel.options

    val confirmDone = viewModel.confirmDone
    LaunchedEffect(confirmDone) {
        if (confirmDone) onConfirmDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("合成配置", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(tokens.spacing.xl)) {
                viewModel.blockReason?.let { reason ->
                    Text(
                        reason,
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                }
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = viewModel.blockReason == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开始合成", style = tokens.type.label)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
        ) {
            item {
                Spacer(Modifier.height(tokens.spacing.lg))
                OptionGroup(
                    title = "字幕",
                    options = SubtitleMode.entries,
                    selected = options.subtitleMode,
                    label = { it.displayName },
                    onSelect = viewModel::setSubtitleMode,
                )
                Spacer(Modifier.height(tokens.spacing.lg))
                OptionGroup(
                    title = "画质",
                    options = EncodePreset.entries,
                    selected = options.encodePreset,
                    label = { it.displayName },
                    onSelect = viewModel::setEncodePreset,
                )
                if (options.encodePreset == EncodePreset.COMPRESSED) {
                    Spacer(Modifier.height(tokens.spacing.lg))
                    OptionGroup(
                        title = "码率",
                        options = BitrateTier.entries,
                        selected = options.bitrateTier,
                        label = { "${it.displayName} · ${it.videoKbps}k" },
                        onSelect = viewModel::setBitrateTier,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.lg))
                // M6：素材替换产物存在时，允许选用替换版视频作为合成源
                if (viewModel.hasReplacedVideo) {
                    Text(
                        "视频源",
                        style = tokens.type.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    FilterChip(
                        selected = options.useReplacedVideo,
                        onClick = { viewModel.setUseReplacedVideo(!options.useReplacedVideo) },
                        label = { Text("使用素材替换版视频") },
                    )
                    Spacer(Modifier.height(tokens.spacing.lg))
                }
                HintCard(options)
                Spacer(Modifier.height(tokens.spacing.xl))
            }
        }
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Text(title, style = tokens.type.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(tokens.spacing.sm))
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = Modifier.padding(end = tokens.spacing.sm),
            )
        }
    }
}

@Composable
private fun HintCard(options: SynthesizeOptions) {
    val tokens = DefaultAiVideoTokens
    val hint = when {
        options.subtitleMode == SubtitleMode.BURN && options.encodePreset == EncodePreset.ORIGINAL ->
            "烧录模式下字幕烧进画面，视频需重编码；「原画质」档用高码率近似原画质"
        options.subtitleMode == SubtitleMode.BURN ->
            "烧录模式：字幕烧进画面，视频重新编码；编码器按 硬件 → OpenH264 → MPEG-4 降级"
        options.encodePreset == EncodePreset.ORIGINAL ->
            "外挂模式：视频流原样拷贝，只处理音频；字幕以外挂 srt 轨存在"
        else ->
            "压缩模式：H.264 + AAC，编码器按 硬件 → OpenH264 → MPEG-4 降级"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            hint,
            style = tokens.type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(tokens.spacing.lg),
        )
    }
}
