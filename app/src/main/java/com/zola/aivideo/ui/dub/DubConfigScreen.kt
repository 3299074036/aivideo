package com.zola.aivideo.ui.dub

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zola.aivideo.data.tts.DubStrategy
import com.zola.aivideo.data.tts.TtsEngineId
import com.zola.aivideo.data.tts.TtsVoiceCatalog
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 配音配置页（M4）。
 *
 * - 引擎选择：端侧优先（Kokoro）、云 TTS 备选
 * - 音色选择：端侧从官方核实的音色表选 sid，云端填音色名
 * - 语速滑杆 0.5x~2.0x
 * - 策略：替换原音轨 / 与原音混音（比例滑杆）
 * - Kokoro 模型未下载时提示去下载；云端未配 Key 时提示去设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubConfigScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onManageTtsModel: () -> Unit,
    onTtsSettings: () -> Unit,
    /** 任务模式下确认完成（返回任务详情页）。 */
    onTaskModeDone: () -> Unit = {},
    viewModel: DubConfigViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配音配置", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(tokens.spacing.xl)) {
                uiState.blockingReason?.let { reason ->
                    Text(
                        reason,
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                }
                val taskDone = viewModel.taskModeDone
                LaunchedEffect(taskDone) {
                    if (taskDone) onTaskModeDone()
                }
                Button(
                    onClick = {
                        viewModel.commitOptions()
                        if (!viewModel.taskMode) onStart()
                        // 任务模式下 commitOptions 置 taskModeDone，由 LaunchedEffect 回退
                    },
                    enabled = uiState.blockingReason == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (viewModel.taskMode) "确认并开始配音" else "开始合成",
                        style = tokens.type.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item {
                Text(
                    "共 ${uiState.segmentCount} 个分段 · 约 ${uiState.totalDurationText}",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 引擎选择
            item {
                SectionTitle("引擎")
                EngineOptionRow(
                    selected = uiState.engineId == TtsEngineId.LOCAL,
                    title = "端侧合成（Kokoro）",
                    subtitle = if (uiState.localReady) "模型已就绪 · 离线可用"
                    else "模型未下载，点击管理模型",
                    onClick = { viewModel.setEngine(TtsEngineId.LOCAL) },
                )
                EngineOptionRow(
                    selected = uiState.engineId == TtsEngineId.CLOUD,
                    title = "云 TTS（OpenAI 兼容）",
                    subtitle = if (uiState.cloudReady) "API Key 已配置"
                    else "未配置 API Key，点击前往设置",
                    onClick = { viewModel.setEngine(TtsEngineId.CLOUD) },
                )
                if (uiState.engineId == TtsEngineId.LOCAL && !uiState.localReady) {
                    TextButton("管理 Kokoro 模型", onManageTtsModel)
                }
                if (uiState.engineId == TtsEngineId.CLOUD && !uiState.cloudReady) {
                    TextButton("前往 TTS 设置配置 Key", onTtsSettings)
                }
            }

            // 音色选择
            item {
                if (uiState.engineId == TtsEngineId.LOCAL) {
                    SectionTitle("音色（sid，官方核实）")
                    VoicePicker(
                        selectedSid = uiState.voiceSid,
                        onSelect = viewModel::setVoiceSid,
                    )
                } else {
                    SectionTitle("云端音色名")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            uiState.cloudVoice,
                            style = tokens.type.body,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onTtsSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "TTS 设置")
                        }
                    }
                    Text(
                        "在 TTS 设置页修改音色名与模型",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 语速
            item {
                SectionTitle("语速 ${"%.2f".format(uiState.speed)}x")
                Slider(
                    value = uiState.speed,
                    onValueChange = viewModel::setSpeed,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
            }

            // 策略
            item {
                SectionTitle("配音策略")
                StrategyOptionRow(
                    selected = uiState.strategy == DubStrategy.REPLACE,
                    title = "替换原音轨",
                    subtitle = "配音轨直接作为新音频（无原声）",
                    onClick = { viewModel.setStrategy(DubStrategy.REPLACE) },
                )
                StrategyOptionRow(
                    selected = uiState.strategy == DubStrategy.MIX,
                    title = "与原音混音",
                    subtitle = "保留原音作为背景，按比例混合",
                    onClick = { viewModel.setStrategy(DubStrategy.MIX) },
                )
                if (uiState.strategy == DubStrategy.MIX) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "原音比例",
                            style = tokens.type.body,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(uiState.mixOriginalRatio * 100).toInt()}%",
                            style = tokens.type.body,
                        )
                    }
                    Slider(
                        value = uiState.mixOriginalRatio,
                        onValueChange = viewModel::setMixOriginalRatio,
                        valueRange = 0f..1f,
                    )
                    if (uiState.videoPath == null) {
                        Text(
                            "混音需要原视频文件；当前未携带视频路径，将自动降级为替换模式",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // 对齐说明
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(Modifier.padding(tokens.spacing.md)) {
                        Text("对齐规则", style = tokens.type.label)
                        Spacer(Modifier.height(tokens.spacing.xs))
                        Text(
                            "配音短于槽位 → 补静音；略长 → atempo 变速（≤1.2x）；" +
                                "仍超长 → 截断+淡出并标记。配音只在自己槽位内发声，" +
                                "不挤占前后字幕。",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val tokens = DefaultAiVideoTokens
    Text(text, style = tokens.type.label, modifier = Modifier.padding(vertical = tokens.spacing.sm))
}

@Composable
private fun EngineOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = tokens.type.body)
            Text(
                subtitle,
                style = tokens.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StrategyOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) = EngineOptionRow(selected, title, subtitle, onClick)

@Composable
private fun VoicePicker(
    selectedSid: Int,
    onSelect: (Int) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    var expanded by remember { mutableStateOf(false) }
    val voices = TtsVoiceCatalog.voices
    val selected = TtsVoiceCatalog.voiceOf(selectedSid)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.displayName ?: "音色 #$selectedSid",
                    style = tokens.type.body,
                )
                Text(
                    selected?.name?.let { "sid ${selected.sid} · $it" } ?: "sid $selectedSid",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
    if (expanded) {
        Column {
            for (voice in voices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(voice.sid)
                            expanded = false
                        }
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(voice.displayName, style = tokens.type.body)
                        Text(
                            "sid ${voice.sid} · ${voice.name} · ${voice.lang.label}",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (voice.sid == selectedSid) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    val tokens = DefaultAiVideoTokens
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, style = tokens.type.label)
    }
}
