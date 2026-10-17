package com.zola.aivideo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 设置页（M6）：全应用设置的统一入口。
 *
 * 分组：模型与服务 / 素材与下载 / 应用。各项复用既有页面，
 * 本页只做导航聚合，不重复造配置 UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenTtsModels: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenStockSettings: () -> Unit,
    onOpenYtDlp: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = tokens.type.title) },
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
                .padding(horizontal = tokens.spacing.xl),
        ) {
            Spacer(Modifier.height(tokens.spacing.sm))
            SettingsGroup("模型与服务") {
                SettingsRow("语音模型管理", "Whisper / SenseVoice 下载与切换", onOpenModels)
                SettingsRow("配音模型管理", "Kokoro TTS 模型下载", onOpenTtsModels)
                SettingsRow("LLM 设置", "翻译用大模型（BYOK）", onOpenLlmSettings)
                SettingsRow("云 TTS 设置", "云端配音（BYOK，备选）", onOpenTtsSettings)
                SettingsRow("术语表", "翻译术语固定译法", onOpenGlossary)
            }
            SettingsGroup("素材与下载") {
                SettingsRow("素材库设置", "Pexels / Pixabay API Key（BYOK）", onOpenStockSettings)
                SettingsRow("链接下载", "yt-dlp 批量下载（独立更新）", onOpenYtDlp)
            }
            SettingsGroup("应用") {
                SettingsRow("关于与开源许可", "版本、GPL 声明、第三方组件", onOpenAbout)
            }
            Spacer(Modifier.height(tokens.spacing.xl))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Column {
        Text(
            title,
            style = tokens.type.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = tokens.spacing.sm),
        )
        content()
        Spacer(Modifier.height(tokens.spacing.md))
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = tokens.type.bodySmall)
                Text(
                    subtitle,
                    style = tokens.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}
