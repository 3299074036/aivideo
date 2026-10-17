package com.zola.aivideo.data.synthesize

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 成品视频导出到公共目录（M5）。
 *
 * 先写应用私有目录（任务目录 output.mp4），完成后经 MediaStore 拷到
 * `Movies/AiVideo/`，按任务命名。返回 content Uri 字符串（失败返回 null，
 * 此时私有目录的产物仍在，任务不算失败）。
 */
@Singleton
class MediaStoreExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val RELATIVE_DIR = "Movies/AiVideo"
    }

    /**
     * @param displayName 不含扩展名的显示名（调用方已做非法字符过滤）
     * @return content Uri 字符串；失败返回 null
     */
    fun exportVideo(file: File, displayName: String): String? {
        if (!file.exists() || file.length() <= 0) return null
        return runCatching {
            val fileName = "$displayName.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_DIR)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "AiVideo",
                    ).apply { mkdirs() }
                    put(MediaStore.Video.Media.DATA, File(dir, fileName).absolutePath)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val uri = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { `in` -> `in`.copyTo(out) }
                } ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri.toString()
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }.getOrNull()
    }
}
