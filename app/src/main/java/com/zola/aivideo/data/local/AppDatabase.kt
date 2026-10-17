package com.zola.aivideo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 影译 Room 数据库。
 *
 * - M1：tasks 表占位
 * - M3：glossary 术语表
 * - M5：tasks 表扩展为完整流水线任务（阶段状态机/进度/产物路径/错误/配置 JSON）
 *
 * 开发期采用 destructiveMigration（正式版前无用户数据兼容负担）。
 */
@Database(
    entities = [TaskEntity::class, GlossaryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun glossaryDao(): GlossaryDao
}
