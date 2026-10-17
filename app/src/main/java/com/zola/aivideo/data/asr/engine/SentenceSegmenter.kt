package com.zola.aivideo.data.asr.engine

import com.zola.aivideo.domain.model.TranscriptSegment

/**
 * 分句器：词级时间戳 → 字幕分段。纯 Kotlin，无 Android/原生依赖，可 JVM 单元测试。
 *
 * 规则（技术方案 §2.2）：
 * 1. 按中英文句子结束标点分句：。！？；… . ! ? ;
 * 2. 单句时长上限 30 秒：超限时在当前词之前强制断句（避免一句字幕占满半分钟）；
 *    单个词本身超 30 秒（极端情况）则自成一段。
 * 3. 文本拼接：连续两个"西文词"之间补空格（如 "hello world"），
 *    含 CJK 字符时直接拼接（如 "你好世界"）。
 * 4. 无时间戳兜底（SenseVoice 等模型不输出词级时间戳）：按标点分句，
 *    按字数比例分配时长（见 [distributeEvenly]）。
 */
object SentenceSegmenter {

    /** 单句时长上限（毫秒）。 */
    const val MAX_SEGMENT_MS = 30_000L

    /** 无时间戳兜底时单句最小时长（毫秒），避免闪现字幕。 */
    const val MIN_SEGMENT_MS = 500L

    private val END_PUNCTS = setOf('。', '！', '？', '；', '…', '.', '!', '?', ';')

    /**
     * 按词级时间戳分句。
     *
     * @param words 词序列（时间递增）
     * @param timeOffsetMs 加到每段时间戳上的偏移（分块转写时为块起始时间）
     * @param idOffset 分段 id 起始偏移（多块拼接时保证唯一）
     */
    fun segment(
        words: List<WordTiming>,
        timeOffsetMs: Long = 0,
        idOffset: Long = 0,
    ): List<TranscriptSegment> {
        val clean = words.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return emptyList()

        val out = mutableListOf<TranscriptSegment>()
        val buf = mutableListOf<WordTiming>()

        fun flush() {
            if (buf.isEmpty()) return
            val start = buf.first().startMs + timeOffsetMs
            // 结束时间取最后一个词的结束；若为 0（缺失）则用下句开始兜底为 start+1s
            val end = (buf.last().endMs.takeIf { it > buf.last().startMs }
                ?: (buf.last().startMs + 1000)) + timeOffsetMs
            out.add(
                TranscriptSegment(
                    id = idOffset + out.size,
                    startMs = start.coerceAtLeast(0),
                    endMs = maxOf(end, start + 1),
                    text = joinWords(buf.map { it.text }),
                ),
            )
            buf.clear()
        }

        for (w in clean) {
            // 超 30 秒：在当前词之前强制断句（buf 非空时）
            val bufStart = buf.firstOrNull()?.startMs ?: w.startMs
            if (buf.isNotEmpty() && w.endMs - bufStart > MAX_SEGMENT_MS) {
                flush()
            }
            buf.add(w)
            if (isSentenceEnd(w.text)) {
                flush()
            }
        }
        flush()
        return out
    }

    /**
     * 无时间戳兜底：纯文本按标点分句，按字数比例分配 [startMs, endMs] 区间。
     *
     * @param idOffset 分段 id 起始偏移
     */
    fun distributeEvenly(
        text: String,
        startMs: Long,
        endMs: Long,
        idOffset: Long = 0,
    ): List<TranscriptSegment> {
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return emptyList()
        val total = (endMs - startMs).coerceAtLeast(0)
        if (sentences.size == 1 || total <= 0) {
            return listOf(
                TranscriptSegment(idOffset, startMs, endMs.coerceAtLeast(startMs + 1), text.trim()),
            )
        }
        // 按字数比例分配，保证每句 >= MIN_SEGMENT_MS，总和精确等于 total
        val weights = sentences.map { it.length.coerceAtLeast(1).toDouble() }
        val weightSum = weights.sum()
        var cursor = startMs
        return sentences.mapIndexed { index, s ->
            val isLast = index == sentences.lastIndex
            val dur = if (isLast) {
                endMs - cursor
            } else {
                maxOf(
                    MIN_SEGMENT_MS,
                    (total * weights[index] / weightSum).toLong(),
                ).coerceAtMost(endMs - cursor - MIN_SEGMENT_MS * (sentences.size - index - 1))
            }
            val seg = TranscriptSegment(
                id = idOffset + index,
                startMs = cursor,
                endMs = (cursor + dur).coerceAtLeast(cursor + 1),
                text = s.trim(),
            )
            cursor += dur
            seg
        }
    }

    /** 是否为句子结束：最后一个字符是结束标点。 */
    internal fun isSentenceEnd(word: String): Boolean {
        val last = word.trimEnd().lastOrNull() ?: return false
        return last in END_PUNCTS
    }

    /** 按标点切句子（标点归属前句），保留原文。 */
    internal fun splitSentences(text: String): List<String> {
        val raw = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in text) {
            buf.append(ch)
            if (ch in END_PUNCTS) {
                // "..." 省略号：连续 '.' 不提前断句，攒完再说
                if (ch == '.' && buf.length >= 2 && buf[buf.length - 2] == '.') continue
                raw.add(buf.toString())
                buf.clear()
            }
        }
        if (buf.isNotBlank()) raw.add(buf.toString())
        // 纯标点段（如 "……" 的第二个 '…'、"..." 的尾部 ".."）并入前一段
        val out = mutableListOf<String>()
        for (s in raw) {
            val t = s.trim()
            if (t.isEmpty()) continue
            if (out.isNotEmpty() && t.all { it in END_PUNCTS || it.isWhitespace() }) {
                out[out.lastIndex] = out.last() + t
            } else {
                out.add(t)
            }
        }
        return out
    }

    /** 拼接词：西文词之间补空格，CJK 直接拼接。 */
    internal fun joinWords(words: List<String>): String {
        if (words.isEmpty()) return ""
        val sb = StringBuilder(words[0])
        for (i in 1 until words.size) {
            val prev = sb.lastOrNull()
            val cur = words[i].firstOrNull()
            if (prev != null && cur != null && isLatinWordChar(prev) && isLatinWordChar(cur)) {
                sb.append(' ')
            }
            sb.append(words[i])
        }
        return sb.toString().trim()
    }

    private fun isLatinWordChar(ch: Char): Boolean =
        ch.isLetterOrDigit() && ch.code < 0x80
}
