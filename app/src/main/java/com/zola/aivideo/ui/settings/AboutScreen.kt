package com.zola.aivideo.ui.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zola.aivideo.BuildConfig
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 关于页（M6）：版本信息 + GPL 合规声明 + 第三方组件清单。
 *
 * GPL 落地清单第 2 项：关于页声明 espeak-ng 随附及源码获取方式。
 * 第 3 项（分发 APK 时提供源码获取方式）的清单文档见
 * docs/research/aivideo/源码分发清单.md，本页给出上游直链。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onViewLicenseText: (assetPath: String, title: String) -> Unit,
) {
    val tokens = DefaultAiVideoTokens

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于与开源许可", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
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
                Spacer(Modifier.height(tokens.spacing.sm))
                Text("影译", style = tokens.type.headline)
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // GPL-3.0 合规声明
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = tokens.corners.md,
                ) {
                    Column(Modifier.padding(tokens.spacing.lg)) {
                        Text("开源许可声明（GPL-3.0）", style = tokens.type.titleSmall)
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Text(
                            "本应用包含 espeak-ng 代码（GPL-3.0-or-later，" +
                                "经 sherpa-onnx 原生库内置，用于语音合成的音素处理）。\n\n" +
                                "根据 GPL-3.0，本应用整体以 GPL-3.0 分发，" +
                                "您可以自由使用、修改与再分发。\n\n" +
                                "源码获取：\n" +
                                "· espeak-ng 上游：https://github.com/espeak-ng/espeak-ng\n" +
                                "· sherpa-onnx 上游：https://github.com/k2-fsa/sherpa-onnx\n" +
                                "· 本应用完整源码（含构建说明）：" +
                                "随 APK 分发时提供获取方式，详见《源码分发清单》",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                            OutlinedButton(
                                onClick = {
                                    onViewLicenseText("licenses/gpl-3.0.txt", "GNU GPL-3.0 全文")
                                },
                            ) { Text("GPL-3.0 全文", style = tokens.type.label) }
                            OutlinedButton(
                                onClick = {
                                    onViewLicenseText(
                                        "licenses/espeak-ng-COPYING.txt",
                                        "espeak-ng 许可全文",
                                    )
                                },
                            ) { Text("espeak-ng 许可", style = tokens.type.label) }
                        }
                    }
                }
            }

            item {
                Text("第三方组件", style = tokens.type.titleSmall)
            }
            items(ThirdPartyLicenses.all, key = { it.name }) { item ->
                Card(shape = tokens.corners.md) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(tokens.spacing.lg),
                    ) {
                        Text(item.name, style = tokens.type.bodySmall)
                        Spacer(Modifier.height(tokens.spacing.sm))
                        Text(
                            "许可证：${item.license}",
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            item.note,
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            item.source,
                            style = tokens.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.assetPath?.let { path ->
                            Spacer(Modifier.height(tokens.spacing.sm))
                            OutlinedButton(
                                onClick = { onViewLicenseText(path, "${item.name} 许可全文") },
                            ) { Text("查看许可全文", style = tokens.type.label) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(tokens.spacing.xl)) }
        }
    }
}
