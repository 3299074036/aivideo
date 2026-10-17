package com.zola.aivideo.ui.glossary

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zola.aivideo.data.llm.GlossaryTerm
import com.zola.aivideo.ui.theme.DefaultAiVideoTokens

/**
 * 术语表管理页（M3）。
 *
 * - 按目标语言分组展示；增删改
 * - 翻译时：注入 prompt + 输出后处理二次替换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(
    viewModel: GlossaryViewModel,
    onBack: () -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    val grouped by viewModel.groupedTerms.collectAsState()
    val editing = viewModel.editing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("术语表", style = tokens.type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.startAdd(
                        grouped.keys.firstOrNull()
                            ?: GlossaryViewModel.langOptions.first(),
                    )
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增术语")
            }
        },
    ) { innerPadding ->
        if (grouped.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(tokens.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "还没有术语",
                    style = tokens.type.titleSmall,
                )
                Spacer(Modifier.height(tokens.spacing.sm))
                Text(
                    "例如：machine learning → 机器学习。翻译时将强制使用这里的译法。",
                    style = tokens.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                grouped.forEach { (lang, terms) ->
                    item(key = "header_$lang") {
                        Text(
                            lang,
                            style = tokens.type.titleSmall,
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.xl,
                                vertical = tokens.spacing.sm,
                            ),
                        )
                    }
                    items(terms, key = { it.id }) { term ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = tokens.spacing.xl),
                            shape = tokens.corners.md,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(tokens.spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(term.source, style = tokens.type.body)
                                    Text(
                                        "→ ${term.target}",
                                        style = tokens.type.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { viewModel.startEdit(term) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { viewModel.delete(term) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(tokens.spacing.xxl)) }
            }
        }
    }

    editing?.let { term ->
        GlossaryEditDialog(
            term = term,
            error = viewModel.error,
            onDismiss = viewModel::cancelEdit,
            onSave = viewModel::save,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlossaryEditDialog(
    term: GlossaryTerm,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (GlossaryTerm) -> Unit,
) {
    val tokens = DefaultAiVideoTokens
    var source by remember(term.id) { mutableStateOf(term.source) }
    var target by remember(term.id) { mutableStateOf(term.target) }
    var lang by remember(term.id) { mutableStateOf(term.targetLang) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (term.id == 0L) "新增术语" else "编辑术语", style = tokens.type.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = lang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标语言分组") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        GlossaryViewModel.langOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    lang = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("原文术语") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("目标译法") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = tokens.type.caption, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(term.copy(source = source.trim(), target = target.trim(), targetLang = lang))
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
