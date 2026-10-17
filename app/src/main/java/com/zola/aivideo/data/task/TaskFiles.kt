package com.zola.aivideo.data.task

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zola.aivideo.data.llm.TranslatedSegment
import com.zola.aivideo.domain.model.TranscriptSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务文件管理（M5）。
 *
 * 每个任务在 `filesDir/tasks/<taskId>/` 下有独立目录，阶段产物落盘：
 * - transcript.json：转写字幕分段
 * - translated.json：翻译分段
 * - dub_track.wav：配音轨
 * - dub_meta.json：配音分段对齐信息（备用）
 * - output.mp4：FFmpeg 合成中间产物（私有目录）
 * - subs.srt：合成用字幕文件
 * - stock/：M6 素材替换——下载的第三方素材 mp4 + 元信息 sidecar JSON
 * - licenses.json：M6 素材授权存档（来源/作者/授权页，随任务存档）
 * - replaced.mp4：M6 素材替换后的视频（合成配置页可选用）
 *
 * Worker 传参只传 taskId，大对象走文件，避免 WorkManager Data 体积限制；
 * 杀进程后产物仍在，可断点续跑。
 */
@Singleton
class TaskFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {
    /** 任务目录（不存在则创建）。 */
    fun taskDir(taskId: Long): File =
        File(context.filesDir, "tasks/$taskId").apply { mkdirs() }

    fun transcriptJson(taskId: Long): File = File(taskDir(taskId), "transcript.json")
    fun translatedJson(taskId: Long): File = File(taskDir(taskId), "translated.json")
    fun dubTrackWav(taskId: Long): File = File(taskDir(taskId), "dub_track.wav")
    fun dubMetaJson(taskId: Long): File = File(taskDir(taskId), "dub_meta.json")
    fun outputMp4(taskId: Long): File = File(taskDir(taskId), "output.mp4")
    fun subsSrt(taskId: Long): File = File(taskDir(taskId), "subs.srt")

    // ---------- M6：素材替换 ----------

    /** 素材目录：下载的第三方素材片段。 */
    fun stockDir(taskId: Long): File = File(taskDir(taskId), "stock").apply { mkdirs() }

    /** 素材落盘文件（文件名按提供方+片段 id，避免冲突）。 */
    fun stockClipFile(taskId: Long, provider: String, clipId: String): File =
        File(stockDir(taskId), "${provider.lowercase()}_${clipId}.mp4")

    /** 素材元信息 sidecar（下载时保存，供替换页读取作者/授权信息）。 */
    fun stockClipMetaFile(taskId: Long, provider: String, clipId: String): File =
        File(stockDir(taskId), "${provider.lowercase()}_${clipId}.json")

    /** 已下载的素材（含元信息）列表。 */
    fun listStockClips(taskId: Long): List<Pair<File, com.zola.aivideo.data.stock.StockClip?>> {
        val dir = stockDir(taskId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "mp4" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { mp4 ->
                val metaFile = File(dir, mp4.nameWithoutExtension + ".json")
                val clip = if (metaFile.exists()) {
                    runCatching {
                        gson.fromJson(
                            metaFile.readText(),
                            com.zola.aivideo.data.stock.StockClip::class.java,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                mp4 to clip
            }
    }

    /** 授权存档：tasks/<id>/licenses.json（素材来源/作者/授权页，随任务存档）。 */
    fun licensesJson(taskId: Long): File = File(taskDir(taskId), "licenses.json")

    /** 素材替换后的视频（合成配置页可选用它作为合成源）。 */
    fun replacedMp4(taskId: Long): File = File(taskDir(taskId), "replaced.mp4")

    private val licenseListType =
        object : TypeToken<List<com.zola.aivideo.data.stock.StockLicenseRecord>>() {}.type

    /** 读取授权记录；文件不存在/损坏返回空列表。 */
    fun loadLicenses(taskId: Long): List<com.zola.aivideo.data.stock.StockLicenseRecord> {
        val f = licensesJson(taskId)
        if (!f.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<com.zola.aivideo.data.stock.StockLicenseRecord>>(
                f.readText(),
                licenseListType,
            ) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** 追加一条授权记录（幂等：同 provider+clipId+片段去重）。 */
    fun appendLicense(
        taskId: Long,
        record: com.zola.aivideo.data.stock.StockLicenseRecord,
    ): List<com.zola.aivideo.data.stock.StockLicenseRecord> {
        val current = loadLicenses(taskId).toMutableList()
        val dup = current.any {
            it.provider == record.provider &&
                it.clipId == record.clipId &&
                it.replacedSegmentStartSec == record.replacedSegmentStartSec &&
                it.replacedSegmentEndSec == record.replacedSegmentEndSec
        }
        if (!dup) current.add(record)
        licensesJson(taskId).writeText(gson.toJson(current))
        return current
    }

    private val transcriptListType = object : TypeToken<List<TranscriptSegment>>() {}.type
    private val translatedListType = object : TypeToken<List<TranslatedSegment>>() {}.type

    /** 保存转写分段；返回文件。 */
    fun saveTranscript(taskId: Long, segments: List<TranscriptSegment>): File =
        transcriptJson(taskId).also { it.writeText(gson.toJson(segments)) }

    /** 读取转写分段；文件不存在返回空列表。 */
    fun loadTranscript(taskId: Long): List<TranscriptSegment> {
        val f = transcriptJson(taskId)
        if (!f.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<TranscriptSegment>>(f.readText(), transcriptListType) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** 保存翻译分段；返回文件。 */
    fun saveTranslated(taskId: Long, segments: List<TranslatedSegment>): File =
        translatedJson(taskId).also { it.writeText(gson.toJson(segments)) }

    /** 读取翻译分段；文件不存在返回空列表。 */
    fun loadTranslated(taskId: Long): List<TranslatedSegment> {
        val f = translatedJson(taskId)
        if (!f.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<TranslatedSegment>>(f.readText(), translatedListType) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** 删除整个任务目录（含全部中间产物）。 */
    fun deleteTaskDir(taskId: Long): Boolean =
        File(context.filesDir, "tasks/$taskId").deleteRecursively()
}
