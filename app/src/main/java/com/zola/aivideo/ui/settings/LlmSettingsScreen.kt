package com.zola.aivideo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * LLM 设置页（M3）。
 *
 * - OpenAI 兼容接口：Base URL / API Key / 模型名，BYOK（用户自备）
 * - 「测试连接」调 /v1/models，不发起真实翻译
 * - 隐私告知：只有用户主动发起的翻译请求会离开本机；视频与文本默认只存本机
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    viewModel: LlmSettingsViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s = viewModel.uiState
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
            OutlinedTextField(
                value = s.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("接口地址（Base URL）") },
                placeholder = { Text("https://api.openai.com") },
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
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::testConnection,
                    enabled = !s.testing,
                ) {
                    Text("测试连接", style = tokens.type.label)
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
                        "· 密钥加密存储在本机（EncryptedSharedPreferences），随应用卸载清除\n" +
                            "· 只有你主动发起的翻译请求会发送到上面配置的服务；视频与文本默认只存本机\n" +
                            "· 第三方 AI 服务按量计费，费用由你自理，请在服务商处查看账单",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
