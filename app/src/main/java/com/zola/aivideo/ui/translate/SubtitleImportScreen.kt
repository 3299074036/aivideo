package com.zola.aivideo.ui.translate

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zola.aivideo.data.subtitle.SubtitleParser
import com.zola.aivideo.domain.model.TranscriptSegment
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 纯字幕独立模式入口（M3）：导入 srt/vtt/ass → 解析 → 进入翻译配置页。
 *
 * 解析器自研（[SubtitleParser]），不引入重型库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleImportScreen(
    holder: TranslationFlowHolder,
    onBack: () -> Unit,
    onParsed: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf<String?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsedCount by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            parsing = true
            error = null
            parsedCount = null
            scope.launch {
                try {
                    val name = queryFileName(context, uri) ?: "subtitle.srt"
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                            ?: throw IllegalStateException("无法读取文件")
                    }
                    val segments = withContext(Dispatchers.Default) {
                        SubtitleParser.parse(name, text)
                    }
                    if (segments.isEmpty()) {
                        error = "未能解析出有效字幕分段，请检查文件格式"
                    } else {
                        fileName = name
                        parsedCount = segments.size
                        holder.putSource(
                            segments,
                            name.substringBeforeLast('.'),
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "不支持的字幕格式"
                } catch (e: Exception) {
                    error = "解析失败：${e.message}"
                } finally {
                    parsing = false
                }
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入字幕文件", style = tokens.type.title) },
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
                .padding(tokens.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "仅翻译字幕文件：导入 srt / vtt / ass，输出翻译后字幕",
                style = tokens.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(tokens.spacing.xl))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !parsing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择字幕文件", style = tokens.type.label)
            }
            Spacer(Modifier.height(tokens.spacing.md))
            when {
                parsing -> CircularProgressIndicator()
                error != null -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = tokens.corners.md,
                ) {
                    Text(
                        error.orEmpty(),
                        style = tokens.type.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(tokens.spacing.lg),
                    )
                }
                parsedCount != null -> {
                    Text(
                        "已解析 $fileName：$parsedCount 段",
                        style = tokens.type.body,
                    )
                    Spacer(Modifier.height(tokens.spacing.lg))
                    Button(
                        onClick = onParsed,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("去配置翻译", style = tokens.type.label)
                    }
                    Spacer(Modifier.height(tokens.spacing.sm))
                    OutlinedButton(
                        onClick = {
                            fileName = null
                            parsedCount = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重新选择", style = tokens.type.label)
                    }
                }
            }
        }
    }
}

/** 读取 SAF 文档的文件名。 */
private fun queryFileName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
