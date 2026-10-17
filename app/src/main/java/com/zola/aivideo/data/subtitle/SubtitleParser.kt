package com.zola.aivideo.data.subtitle

import com.zola.aivideo.domain.model.TranscriptSegment

/**
 * 自研字幕解析器（M3，独立实现）：纯 Kotlin，无第三方依赖，可 JVM 单元测试。
 *
 * 支持 srt / vtt / ass，统一输出 [TranscriptSegment]（id 从 0 连续编号）。
 * 容错策略：跳过无法解析的块，保留能解析的；时间非法则整块丢弃。
 */
object SubtitleParser {

    /** 按文件名后缀自动选择解析器；未知后缀抛 [IllegalArgumentException]。 */
    fun parse(fileName: String, text: String): List<TranscriptSegment> {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".srt") -> parseSrt(text)
            lower.endsWith(".vtt") -> parseVtt(text)
            lower.endsWith(".ass") -> parseAss(text)
            else -> throw IllegalArgumentException("不支持的字幕格式：$fileName（仅支持 srt/vtt/ass）")
        }
    }

    // ---------- SRT ----------

    private val SRT_TIME_REGEX =
        Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""")

    fun parseSrt(text: String): List<TranscriptSegment> {
        val blocks = text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        val out = mutableListOf<TranscriptSegment>()
        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            // 第一行可能是序号（纯数字），时间行是含 "-->" 的行
            val timeLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIdx < 0) continue
            val m = SRT_TIME_REGEX.find(lines[timeLineIdx]) ?: continue
            val start = srtToMs(m, 1)
            val end = srtToMs(m, 5)
            if (end <= start) continue
            val content = lines.drop(timeLineIdx + 1)
                .joinToString("\n") { stripSrtTags(it).trim() }
                .trim()
            if (content.isEmpty()) continue
            out.add(TranscriptSegment(id = out.size.toLong(), startMs = start, endMs = end, text = content))
        }
        return out
    }

    private fun srtToMs(m: MatchResult, base: Int): Long {
        val h = m.groupValues[base].toLong()
        val min = m.groupValues[base + 1].toLong()
        val s = m.groupValues[base + 2].toLong()
        val ms = m.groupValues[base + 3].toLong()
        return h * 3_600_000 + min * 60_000 + s * 1000 + ms
    }

    /** 去掉 srt 常见的行内样式标签（<i>、<b>、<font …> 等）。 */
    private fun stripSrtTags(line: String): String =
        line.replace(Regex("</?[^>]+>"), "")

    // ---------- VTT ----------

    private val VTT_TIME_REGEX =
        Regex("""(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})""")

    fun parseVtt(text: String): List<TranscriptSegment> {
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.lines()
        // 跳过 WEBVTT 头与 NOTE 注释块
        val body = StringBuilder()
        var inNote = false
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.startsWith("WEBVTT")) continue
            if (line.startsWith("NOTE")) {
                inNote = true
                continue
            }
            if (inNote) {
                if (line.isBlank()) inNote = false
                continue
            }
            body.appendLine(line)
        }
        val out = mutableListOf<TranscriptSegment>()
        for (block in body.toString().split(Regex("\n\\s*\n"))) {
            val blockLines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (blockLines.isEmpty()) continue
            // cue 可能带 identifier 行：时间行是含 "-->" 的行
            val timeLineIdx = blockLines.indexOfFirst { it.contains("-->") }
            if (timeLineIdx < 0) continue
            // 时间行后可能跟 cue settings（如 align:start），只取 "-->" 前后时间部分
            val timePart = blockLines[timeLineIdx].split(Regex("\\s+"))
                .take(3).joinToString(" ")
            val m = VTT_TIME_REGEX.find(timePart) ?: continue
            val start = vttToMs(m, 1)
            val end = vttToMs(m, 5)
            if (end <= start) continue
            val content = blockLines.drop(timeLineIdx + 1)
                .joinToString("\n") { stripVttTags(it).trim() }
                .trim()
            if (content.isEmpty()) continue
            out.add(TranscriptSegment(id = out.size.toLong(), startMs = start, endMs = end, text = content))
        }
        return out
    }

    private fun vttToMs(m: MatchResult, base: Int): Long {
        val h = m.groupValues[base].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        val min = m.groupValues[base + 1].toLong()
        val s = m.groupValues[base + 2].toLong()
        val ms = m.groupValues[base + 3].toLong()
        return h * 3_600_000 + min * 60_000 + s * 1000 + ms
    }

    /** 去掉 vtt cue 标签（<c>、<v Name>、<b> 等）。 */
    private fun stripVttTags(line: String): String =
        line.replace(Regex("</?[^>]+>"), "")

    // ---------- ASS ----------

    /**
     * 解析 ASS/SSA：只读 [Events] 段的 Dialogue 行。
     * 时间格式 h:mm:ss.cc（厘秒）。
     */
    fun parseAss(text: String): List<TranscriptSegment> {
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.lines()
        val eventsIdx = lines.indexOfFirst { it.trim().equals("[Events]", ignoreCase = true) }
        if (eventsIdx < 0) return emptyList()
        var startCol = -1
        var endCol = -1
        var textCol = -1
        val out = mutableListOf<TranscriptSegment>()
        for (i in eventsIdx + 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("[")) break // 下一个 section
            if (line.startsWith("Format:", ignoreCase = true)) {
                val cols = line.removePrefix("Format:").split(",").map { it.trim().lowercase() }
                startCol = cols.indexOf("start")
                endCol = cols.indexOf("end")
                textCol = cols.indexOf("text")
            } else if (line.startsWith("Dialogue:", ignoreCase = true)) {
                if (startCol < 0 || endCol < 0 || textCol < 0) continue
                // Text 列可能含逗号：按列数切分，最后一列取剩余全部
                val payload = line.substringAfter(":")
                val cols = splitDialogue(payload, textCol + 1)
                if (cols.size <= maxOf(startCol, endCol, textCol)) continue
                val start = assToMs(cols[startCol].trim()) ?: continue
                val end = assToMs(cols[endCol].trim()) ?: continue
                if (end <= start) continue
                val content = stripAssTags(cols[textCol]).replace("\\N", "\n").trim()
                if (content.isEmpty()) continue
                out.add(TranscriptSegment(id = out.size.toLong(), startMs = start, endMs = end, text = content))
            }
        }
        return out.sortedBy { it.startMs }.mapIndexed { idx, seg -> seg.copy(id = idx.toLong()) }
    }

    /** Dialogue 行切分：前 n-1 列按逗号切，最后一列取剩余全部（含逗号）。 */
    private fun splitDialogue(payload: String, columnCount: Int): List<String> {
        val cols = mutableListOf<String>()
        var rest = payload
        repeat(columnCount - 1) {
            val idx = rest.indexOf(',')
            if (idx < 0) {
                cols.add(rest)
                rest = ""
                return@repeat
            }
            cols.add(rest.substring(0, idx))
            rest = rest.substring(idx + 1)
        }
        cols.add(rest)
        return cols
    }

    private val ASS_TIME_REGEX = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2})""")

    private fun assToMs(text: String): Long? {
        val m = ASS_TIME_REGEX.matchEntire(text.trim()) ?: return null
        return try {
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            val s = m.groupValues[3].toLong()
            val cs = m.groupValues[4].toLong()
            h * 3_600_000 + min * 60_000 + s * 1000 + cs * 10
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** 去掉 ASS 覆盖标签 {\...}。 */
    private fun stripAssTags(text: String): String =
        text.replace(Regex("""\{[^}]*}"""), "")
}
