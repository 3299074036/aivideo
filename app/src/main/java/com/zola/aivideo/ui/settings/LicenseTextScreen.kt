package com.zola.aivideo.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 许可证全文查看页（M6）：从 assets/licenses/ 读取文本展示。
 *
 * @param assetPath 如 "licenses/gpl-3.0.txt"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseTextScreen(
    assetPath: String,
    title: String,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val context = LocalContext.current
    val text = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { "读取失败：$assetPath" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.xl),
        )
    }
}
