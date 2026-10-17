package com.zola.aivideo.ui.dub

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * TTS 设置页（M4）：云 TTS（OpenAI 兼容 /v1/audio/speech）配置。
 *
 * - Base URL / API Key（加密存储，不回显明文）/ 模型名 / 音色名 / 语速
 * - 测试连接：请求 1 段短文本合成，验证连通性
 * - 隐私告知：配音文本会发送到云端服务商
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val tokens = DefaultAiVideoTokens
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("云 TTS 设置", style = tokens.type.title) },
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Text(
                    "云 TTS 为备选方案：端侧 Kokoro 未下载模型或不可用时使用。" +
                        "API Key 由你自备（BYOK），仅保存在本机加密存储中；" +
                        "合成时配音文本会发送到你所填的服务商，按量计费请自理。",
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(tokens.spacing.md),
                )
            }

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = viewModel::setApiKeyInput,
                label = {
                    Text(if (uiState.hasApiKey) "API Key（已保存，可覆盖）" else "API Key")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (uiState.showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleShowApiKey) {
                        Text(
                            if (uiState.showApiKey) "隐藏" else "显示",
                            style = tokens.type.caption,
                        )
                    }
                },
            )

            OutlinedTextField(
                value = uiState.model,
                onValueChange = viewModel::setModel,
                label = { Text("模型名") },
                placeholder = { Text("tts-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.voice,
                onValueChange = viewModel::setVoice,
                label = { Text("音色名") },
                placeholder = { Text("alloy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "仅 Wi-Fi 下载 TTS 模型",
                    style = tokens.type.body,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存", style = tokens.type.label)
            }

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = uiState.canTest && !uiState.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (uiState.testing) "测试中…" else "测试连接（合成一段短文本）",
                    style = tokens.type.label,
                )
            }

            uiState.testResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        result.message,
                        style = tokens.type.body,
                        modifier = Modifier.padding(tokens.spacing.md),
                    )
                }
            }

            if (uiState.hasApiKey) {
                OutlinedButton(
                    onClick = viewModel::clearApiKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清除 API Key", style = tokens.type.label)
                }
            }

            Spacer(Modifier.height(tokens.spacing.xl))
        }
    }
}
