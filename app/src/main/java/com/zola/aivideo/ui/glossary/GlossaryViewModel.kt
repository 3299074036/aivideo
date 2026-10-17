package com.zola.aivideo.ui.glossary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zola.aivideo.data.llm.GlossaryRepository
import com.zola.aivideo.data.llm.GlossaryTerm
import com.zola.aivideo.data.llm.TargetLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 术语表管理 ViewModel（M3）。
 *
 * 术语按目标语言分组；翻译时只取对应语言分组。
 */
@HiltViewModel
class GlossaryViewModel @Inject constructor(
    private val repository: GlossaryRepository,
) : ViewModel() {

    /** 按语言分组的术语。 */
    val groupedTerms = repository.observeAll()
        .map { list -> list.groupBy { it.targetLang } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    var editing by mutableStateOf<GlossaryTerm?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun startAdd(targetLang: String) {
        editing = GlossaryTerm(source = "", target = "", targetLang = targetLang)
        error = null
    }

    fun startEdit(term: GlossaryTerm) {
        editing = term
        error = null
    }

    fun cancelEdit() {
        editing = null
        error = null
    }

    fun save(term: GlossaryTerm) {
        if (term.source.isBlank() || term.target.isBlank()) {
            error = "原文与译文不能为空"
            return
        }
        viewModelScope.launch {
            try {
                repository.save(term)
                editing = null
                error = null
            } catch (e: Exception) {
                error = e.message ?: "保存失败"
            }
        }
    }

    fun delete(term: GlossaryTerm) {
        viewModelScope.launch {
            repository.delete(term)
        }
    }

    companion object {
        /** 新建术语时的默认语言选项。 */
        val langOptions: List<String> =
            TargetLanguage.presets.map { it.displayName }
    }
}
