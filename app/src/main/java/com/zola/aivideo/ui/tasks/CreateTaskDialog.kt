package com.zola.aivideo.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zola.aivideo.data.task.CheckSeverity

/**
 * 任务开始前检查弹窗（M5，技术方案 §2.7）。
 *
 * - 有阻断项：只显示错误 + "知道了"（不可继续）
 * - 只有警告：列出警告 + "仍要开始" / "取消"（二次确认）
 * - 无问题：不弹窗，直接建任务（调用方判断）
 */
@Composable
fun CreateTaskDialog(
    pending: PendingCreate,
    hasBlocker: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasBlocker) "无法开始处理" else "开始前确认") },
        text = {
            Column {
                pending.issues.forEach { issue ->
                    val (icon, tint) = if (issue.severity == CheckSeverity.BLOCKER) {
                        Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
                    } else {
                        Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
                    }
                    Icon(icon, contentDescription = null, tint = tint)
                    Spacer(Modifier.height(4.dp))
                    Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            if (hasBlocker) {
                TextButton(onClick = onDismiss) { Text("知道了") }
            } else {
                TextButton(onClick = onConfirm) { Text("仍要开始") }
            }
        },
        dismissButton = {
            if (!hasBlocker) TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
