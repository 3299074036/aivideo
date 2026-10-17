package com.zola.aivideo.ui.stock

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.zola.aivideo.data.stock.StockProvider
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 素材库设置页（M6）。
 *
 * - Pexels / Pixabay API Key：BYOK，加密存储，不回显明文
 * - 默认提供方选择；「测试连接」做一次真实搜索验证 Key 有效
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSettingsScreen(
    viewModel: StockSettingsViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val s by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("素材库设置", style = tokens.type.title) },
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
            Text("默认素材来源", style = tokens.type.titleSmall)
            StockProvider.entries.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = s.provider == p,
                            onClick = { viewModel.setProvider(p) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = s.provider == p,
                        onClick = { viewModel.setProvider(p) },
                    )
                    Text(p.displayName, style = tokens.type.bodySmall)
                }
            }

            KeyField(
                label = "Pexels API Key",
                value = s.pexelsKeyInput,
                onValueChange = viewModel::setPexelsKeyInput,
                hasSaved = s.hasPexelsKey,
                showKey = s.showKey,
                onToggleShow = viewModel::toggleShowKey,
                onClear = { viewModel.clearKey(StockProvider.PEXELS) },
            )
            KeyField(
                label = "Pixabay API Key",
                value = s.pixabayKeyInput,
                onValueChange = viewModel::setPixabayKeyInput,
                hasSaved = s.hasPixabayKey,
                showKey = s.showKey,
                onToggleShow = viewModel::toggleShowKey,
                onClear = { viewModel.clearKey(StockProvider.PIXABAY) },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = viewModel::testSearch, enabled = !s.testing) {
                    Text("测试连接", style = tokens.type.label)
                }
                Button(onClick = viewModel::save) {
                    Text("保存", style = tokens.type.label)
                }
                if (s.testing) CircularProgressIndicator()
            }
            s.testResult?.let { msg ->
                Text(
                    msg,
                    style = tokens.type.bodySmall,
                    color = if (s.testSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            if (s.saved) {
                Text(
                    "已保存",
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = tokens.corners.md,
            ) {
                Column(Modifier.padding(tokens.spacing.lg)) {
                    Text("Key 申请与授权说明", style = tokens.type.titleSmall)
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        "· Pexels Key：pexels.com/api 免费申请；Pixabay Key：pixabay.com/api/docs 免费申请\n" +
                            "· 密钥加密存储在本机，随应用卸载清除；搜索请求会发送到对应官方 API\n" +
                            "· 素材遵循 Pexels License / Pixabay Content License：可免费商用，" +
                            "App 会把来源、作者、授权页自动记入任务的 licenses.json 存档",
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hasSaved: Boolean,
    showKey: Boolean,
    onToggleShow: () -> Unit,
    onClear: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(if (hasSaved) "已保存（输入新值可覆盖）" else "粘贴 API Key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showKey) "隐藏" else "显示",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasSaved) {
            OutlinedButton(onClick = onClear) {
                Text("清除已存密钥", style = tokens.type.label)
            }
        }
    }
}
