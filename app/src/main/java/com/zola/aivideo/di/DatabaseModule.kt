package com.zola.aivideo.di

import android.content.Context
import androidx.room.Room
import com.zola.aivideo.data.local.AppDatabase
import com.zola.aivideo.data.local.GlossaryDao
import com.zola.aivideo.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room 数据库注入模块。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "aivideo.db")
            // M1 开发期：schema 变更直接重建（正式版前无兼容负担）
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideGlossaryDao(db: AppDatabase): GlossaryDao = db.glossaryDao()
}
