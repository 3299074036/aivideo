package com.zola.aivideo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 术语表条目（M3）。
 *
 * @param source 原文术语，如 "machine learning"
 * @param target 目标译法，如 "机器学习"
 * @param targetLang 目标语言分组，如"英语"（与 [TargetLanguage.displayName] 对应）
 */
@Entity(tableName = "glossary")
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val target: String,
    val targetLang: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary ORDER BY targetLang, createdAt DESC")
    fun observeAll(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossary WHERE targetLang = :lang ORDER BY LENGTH(source) DESC")
    suspend fun getByTargetLang(lang: String): List<GlossaryEntity>

    @Insert
    suspend fun insert(entity: GlossaryEntity): Long

    @Update
    suspend fun update(entity: GlossaryEntity)

    @Delete
    suspend fun delete(entity: GlossaryEntity)

    @Query("SELECT COUNT(*) FROM glossary")
    suspend fun count(): Int
}
