package com.zola.aivideo.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TaskAlt
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
 * 首页（2026-10-17 UI 重设计）。
 *
 * 设计原则：一条主路径 + 统一设置入口。
 * - 主路径：导入视频 → 任务流水线（识别→翻译→配音→合成），在任务详情页串起来
 * - 次入口：翻译字幕文件（纯字幕模式）、任务中心（历史任务）
 * - 设置：所有模型/服务配置收拢到统一设置页，首页不再重复摆 5 个设置按钮
 *
 * 版权提示必须位于首屏显著位置（合规要求 §7），用户点"导入视频"前即可看到。
 */
@Composable
fun HomeScreen(
    onVideoPicked: (Uri) -> Unit,
    onImportSubtitle: () -> Unit = {},
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

            // 主路径：导入视频
            Button(
                onClick = { picker.launch("video/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导入本地视频", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.sm))

            // 次入口：字幕翻译 / 任务中心（并排）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                OutlinedButton(
                    onClick = onImportSubtitle,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.spacing.lg),
                    )
                    Spacer(Modifier.width(tokens.spacing.xs))
                    Text("翻译字幕文件", style = tokens.type.label)
                }
                OutlinedButton(
                    onClick = onOpenTasks,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.TaskAlt,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.spacing.lg),
                    )
                    Spacer(Modifier.width(tokens.spacing.xs))
                    Text("任务中心", style = tokens.type.label)
                }
            }
            Spacer(Modifier.height(tokens.spacing.sm))

            // 统一设置入口（模型管理/LLM 设置/配音模型/云 TTS 都在里面）
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(tokens.spacing.lg),
                )
                Spacer(Modifier.width(tokens.spacing.xs))
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
                Row(
                    Modifier.padding(tokens.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Text(
                        text = stringResource(R.string.compliance_notice),
                        style = tokens.type.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
