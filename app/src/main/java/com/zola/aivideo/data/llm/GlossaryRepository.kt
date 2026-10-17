package com.zola.aivideo.data.llm

import com.zola.aivideo.data.local.GlossaryDao
import com.zola.aivideo.data.local.GlossaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 术语表仓库（M3，独立实现）：Room 持久化 + 领域模型转换。
 *
 * 按目标语言分组：翻译成英语时只用"英语"分组的术语，避免串语言。
 */
@Singleton
class GlossaryRepository @Inject constructor(
    private val dao: GlossaryDao,
) {
    fun observeAll(): Flow<List<GlossaryTerm>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getByTargetLang(targetLang: String): List<GlossaryTerm> =
        dao.getByTargetLang(targetLang).map { it.toDomain() }

    /** 新增或更新（id=0 为新增）。空术语抛 IllegalArgumentException。 */
    suspend fun save(term: GlossaryTerm) {
        require(term.source.isNotBlank() && term.target.isNotBlank()) { "原文与译文不能为空" }
        val entity = GlossaryEntity(
            id = term.id,
            source = term.source.trim(),
            target = term.target.trim(),
            targetLang = term.targetLang,
        )
        if (term.id == 0L) dao.insert(entity) else dao.update(entity)
    }

    suspend fun delete(term: GlossaryTerm) {
        dao.delete(
            GlossaryEntity(
                id = term.id,
                source = term.source,
                target = term.target,
                targetLang = term.targetLang,
            ),
        )
    }

    private fun GlossaryEntity.toDomain() = GlossaryTerm(
        id = id,
        source = source,
        target = target,
        targetLang = targetLang,
    )
}
