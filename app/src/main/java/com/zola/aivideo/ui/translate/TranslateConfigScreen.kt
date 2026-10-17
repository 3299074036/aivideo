package com.zola.aivideo.ui.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.zola.aivideo.data.llm.TargetLanguage
import com.zola.aivideo.data.llm.TranslateStyle
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 翻译配置页（M3）。
 *
 * - 目标语言：7 种预设 + 自定义输入
 * - 翻译风格：直译 / 意译 / 口语化
 * - 术语表开关（显示当前语言分组条目数）、双语字幕开关
 * - 开始前检查 LLM 配置；缺失则引导去 LLM 设置页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateConfigScreen(
    viewModel: TranslateConfigViewModel,
    onBack: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenGlossary: () -> Unit,
    onStartTranslate: () -> Unit,
    /** 任务模式下确认完成（返回任务详情页）。 */
    onTaskModeDone: () -> Unit = {},
) {
    val tokens = DefaultAiVideoTokens
    val options = viewModel.options
    var glossaryCount by remember { mutableStateOf<Int?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(options.targetLanguage) {
        glossaryCount = viewModel.glossaryCountFor(options.targetLanguage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("翻译配置", style = tokens.type.title) },
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
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        ) {
            Text(
                "来源：${viewModel.sourceLabel}（${viewModel.segmentCount} 段）",
                style = tokens.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("目标语言", style = tokens.type.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                TargetLanguage.presets.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = options.targetLanguage == lang,
                                role = Role.RadioButton,
                            ) { viewModel.onTargetLanguageChange(lang) }
                            .padding(vertical = tokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = options.targetLanguage == lang,
                            onClick = { viewModel.onTargetLanguageChange(lang) },
                        )
                        Text(lang.displayName, style = tokens.type.body)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = options.targetLanguage is TargetLanguage.Custom,
                        onClick = {
                            viewModel.onTargetLanguageChange(
                                TargetLanguage.Custom(viewModel.customLangName),
                            )
                        },
                    )
                    OutlinedTextField(
                        value = viewModel.customLangName,
                        onValueChange = {
                            viewModel.onCustomLangNameChange(it)
                            viewModel.onTargetLanguageChange(TargetLanguage.Custom(it))
                        },
                        label = { Text("自定义语言") },
                        placeholder = { Text("如：粤语") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text("翻译风格", style = tokens.type.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                TranslateStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = options.style == style,
                                role = Role.RadioButton,
                            ) { viewModel.onStyleChange(style) }
                            .padding(vertical = tokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = options.style == style,
                            onClick = { viewModel.onStyleChange(style) },
                        )
                        Column {
                            Text(style.displayName, style = tokens.type.body)
                            Text(
                                style.instruction,
                                style = tokens.type.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("术语表", style = tokens.type.body)
                    Text(
                        "当前语言分组共 ${glossaryCount ?: "…"} 条；翻译时注入 prompt 并二次替换",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = options.useGlossary,
                    onCheckedChange = viewModel::onUseGlossaryChange,
                )
            }
            OutlinedButton(onClick = onOpenGlossary, modifier = Modifier.fillMaxWidth()) {
                Text("管理术语表", style = tokens.type.label)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("双语字幕", style = tokens.type.body)
                    Text(
                        "输出原文 + 译文对照",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = options.bilingual,
                    onCheckedChange = viewModel::onBilingualChange,
                )
            }

            viewModel.blockReason?.let { reason ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = tokens.corners.md,
                ) {
                    Column(Modifier.padding(tokens.spacing.lg)) {
                        Text(
                            reason,
                            style = tokens.type.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                        OutlinedButton(onClick = onOpenLlmSettings) {
                            Text("去 LLM 设置", style = tokens.type.label)
                        }
                    }
                }
            }

            val taskDone = viewModel.taskModeDone
            LaunchedEffect(taskDone) {
                if (taskDone) onTaskModeDone()
            }

            Button(
                onClick = {
                    if (checking) return@Button
                    checking = true
                    scope.launch {
                        val config = viewModel.checkAndBuildConfig()
                        checking = false
                        if (config != null) onStartTranslate()
                        // 任务模式下 checkAndBuildConfig 返回 null 并置 taskModeDone，由 LaunchedEffect 回退
                    }
                },
                enabled = !checking && viewModel.segmentCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    checking -> "检查中…"
                    viewModel.taskMode -> "确认并开始翻译"
                    else -> "开始翻译"
                }
                Text(label, style = tokens.type.label)
            }
        }
    }
}
