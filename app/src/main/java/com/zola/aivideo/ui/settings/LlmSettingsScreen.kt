package com.zola.aivideo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.zola.aivideo.data.llm.LlmEngineId
import com.zola.aivideo.data.llm.LlmProviderCatalog
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * LLM 设置页（M3，M7 加服务商预设）。
 *
 * - 服务商预设一键填：Base URL / 模型候选 / 获取 Key 链接；保留"自定义"手填
 * - OpenAI 兼容接口：Base URL / API Key / 模型名，BYOK（用户自备）
 * - 「测试翻译」发一句真实短翻译（英→中），验证整条链路
 * - 隐私告知：只有用户主动发起的翻译请求会离开本机；视频与文本默认只存本机
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LlmSettingsScreen(
    viewModel: LlmSettingsViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s = viewModel.uiState
    val uriHandler = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM 设置", style = tokens.type.title) },
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
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            // 引擎切换（M7）：API / 本地
            Text("引擎", style = tokens.type.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                LlmEngineId.entries.forEach { engine ->
                    FilterChip(
                        selected = s.engine == engine,
                        onClick = { viewModel.setEngine(engine) },
                        label = { Text(engine.displayName, style = tokens.type.label) },
                    )
                }
            }
            if (s.engine == LlmEngineId.LOCAL) {
                Text(
                    "本地模型已就绪：需先在模型管理页下载 Qwen 模型；断网可用，长视频翻译比 API 慢且更烫",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "默认走 API，又快又稳；本地模型适合离线 / 隐私场景",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (s.engine == LlmEngineId.LOCAL) {
                // 本地引擎：只展示本地模型卡片 + 本地测试，API 配置区隐藏
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = tokens.corners.md,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(tokens.spacing.lg)) {
                        Text("本地模型", style = tokens.type.titleSmall)
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Text(
                            "当前：${s.localModelDisplayName.ifBlank { "未选择" }}",
                            style = tokens.type.bodySmall,
                        )
                        Text(
                            "去「模型管理」页可下载 / 切换模型（Qwen3-0.6B / 1.7B）",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = viewModel::testConnection,
                        enabled = !s.testing,
                    ) {
                        Text("测试本地翻译", style = tokens.type.label)
                    }
                    if (s.testing) CircularProgressIndicator()
                }
                s.testMessage?.let { msg ->
                    Text(
                        msg,
                        style = tokens.type.bodySmall,
                        color = if (s.testSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            } else {
            // 服务商预设
            Text("服务商预设", style = tokens.type.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                LlmProviderCatalog.all.forEach { provider ->
                    FilterChip(
                        selected = s.providerId == provider.id,
                        onClick = { viewModel.selectProvider(provider) },
                        label = { Text(provider.displayName, style = tokens.type.label) },
                    )
                }
            }
            // 预设计费提示 + 获取 Key 链接
            val provider = s.provider
            if (provider.keyHint.isNotBlank()) {
                Text(
                    provider.keyHint,
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (provider.keyUrl.isNotBlank()) {
                val linkText = buildAnnotatedString {
                    append("没有 Key？去")
                    pushStringAnnotation(tag = "url", annotation = provider.keyUrl)
                    pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
                    append("${provider.displayName}官网获取")
                    pop()
                    append(" →")
                }
                ClickableText(
                    text = linkText,
                    style = tokens.type.caption,
                    onClick = { offset ->
                        linkText.getStringAnnotations(tag = "url", start = offset, end = offset)
                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                    },
                )
            }

            OutlinedTextField(
                value = s.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("接口地址（Base URL）") },
                placeholder = { Text("https://api.siliconflow.cn/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API Key") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.hasSavedKey) {
                Text(
                    "已保存密钥（显示为掩码即未改动）；清空输入框后保存可清除",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::clearKey) {
                    Text("清除已存密钥", style = tokens.type.label)
                }
            }
            OutlinedTextField(
                value = s.model,
                onValueChange = viewModel::onModelChange,
                label = { Text("模型名") },
                placeholder = { Text("deepseek-ai/DeepSeek-V3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 模型候选（一键填入）
            if (provider.models.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    provider.models.forEach { candidate ->
                        FilterChip(
                            selected = s.model == candidate,
                            onClick = { viewModel.onModelChange(candidate) },
                            label = { Text(candidate, style = tokens.type.caption) },
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::testConnection,
                    enabled = !s.testing,
                ) {
                    Text("测试翻译", style = tokens.type.label)
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !s.saving,
                ) {
                    Text("保存", style = tokens.type.label)
                }
                if (s.testing || s.saving) CircularProgressIndicator()
            }
            s.testMessage?.let { msg ->
                Text(
                    msg,
                    style = tokens.type.bodySmall,
                    color = if (s.testSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            } // else：API 引擎配置区结束
            Spacer(Modifier.height(tokens.spacing.sm))
            // 隐私告知
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = tokens.corners.md,
            ) {
                Column(Modifier.padding(tokens.spacing.lg)) {
                    Text("隐私说明", style = tokens.type.titleSmall)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        if (s.engine == LlmEngineId.LOCAL) {
                            "· 本地模型完全在本机运行，翻译不经过网络\n" +
                                "· 模型文件存于应用私有目录，随应用卸载清除\n" +
                                "· 长视频翻译比 API 慢、更耗电发热，适合离线 / 隐私场景"
                        } else {
                            "· 密钥加密存储在本机（EncryptedSharedPreferences），随应用卸载清除\n" +
                                "· 只有你主动发起的翻译请求会发送到上面配置的服务；视频与文本默认只存本机\n" +
                                "· 第三方 AI 服务按量计费，费用由你自理，请在服务商处查看账单"
                        },
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
