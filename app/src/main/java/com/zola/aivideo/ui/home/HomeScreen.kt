package com.zola.aivideo.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.zola.aivideo.R
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 首页：导入入口 + 显著版权提示。
 *
 * 版权提示必须位于首屏显著位置（合规要求 §7），用户点"导入视频"前即可看到。
 * M1 仅本地导入；链接下载在后续里程碑（yt-dlp 独立更新通道）落地。
 */
@Composable
fun HomeScreen(
    onVideoPicked: (Uri) -> Unit,
    onOpenModels: () -> Unit = {},
    onOpenLlmSettings: () -> Unit = {},
    onImportSubtitle: () -> Unit = {},
    onOpenTtsModels: () -> Unit = {},
    onOpenTtsSettings: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val tokens = DefaultAiVideoTokens
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            // null 表示用户取消，不做任何事
            if (uri != null) onVideoPicked(uri)
        },
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(tokens.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                modifier = Modifier.size(tokens.spacing.xxl * 2),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(tokens.spacing.lg))
            Text(
                text = "把视频变成另一种语言",
                style = tokens.type.headline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                text = "导入 → 识别 → 翻译 → 配音 → 合成，一条流水线在本机完成",
                style = tokens.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(tokens.spacing.xl))
            Button(
                onClick = { picker.launch("video/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导入本地视频", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M2：语音模型管理入口（下载转写模型、切换模型）
            OutlinedButton(
                onClick = onOpenModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("语音模型管理", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M3：LLM 设置入口（BYOK 配置）
            OutlinedButton(
                onClick = onOpenLlmSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("LLM 设置", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M3：纯字幕独立模式入口（导入 srt/vtt/ass 翻译）
            OutlinedButton(
                onClick = onImportSubtitle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("翻译字幕文件", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M4：配音模型管理入口（Kokoro 模型下载）
            OutlinedButton(
                onClick = onOpenTtsModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("配音模型管理", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M4：云 TTS 设置入口（BYOK 配置）
            OutlinedButton(
                onClick = onOpenTtsSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("云 TTS 设置", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M5：任务中心入口
            OutlinedButton(
                onClick = onOpenTasks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("任务中心", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            // M6：设置入口（统一设置页；旧入口保留，设置页内同样可达）
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("设置", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.lg))
            // 显著版权提示（合规 §7）：Card 样式，不可一划而过
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = tokens.corners.md,
            ) {
                Column(Modifier.padding(tokens.spacing.lg)) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    Text(
                        text = stringResource(R.string.compliance_notice),
                        style = tokens.type.caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
