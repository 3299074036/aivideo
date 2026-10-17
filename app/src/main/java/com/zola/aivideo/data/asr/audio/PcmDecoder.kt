package com.zola.aivideo.data.asr.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 解码产物信息：16kHz 单声道 16-bit PCM 裸流文件。 */
data class PcmInfo(
    /** PCM 文件绝对路径。 */
    val filePath: String,
    /** 输出采样总数（16kHz 单声道）。 */
    val totalSamples: Long,
)

/**
 * 视频音频轨解码器：MediaExtractor + MediaCodec → 16kHz 单声道 16-bit PCM 文件。
 *
 * 设计要点（长视频防 OOM）：
 * - 不经过内存大数组：解码输出按 buffer 流式混音（多声道取平均）→ 线性重采样到
 *   16kHz → 直接写文件；重采样器只保留 1 个历史采样点，块边界无咔哒声。
 * - sherpa-onnx 的 OfflineRecognizer 要求输入 16kHz 单声道 float PCM，
 *   本类输出 16-bit 裸 PCM 文件，转写时按 60 秒块流式读取（见 SherpaAsrEngine），
 *   1 小时视频也不会爆内存。
 *
 * @throws IllegalArgumentException 视频无音频轨或解码器不支持时抛出
 */
@Singleton
class PcmDecoder @Inject constructor() {

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        private const val TIMEOUT_US = 10_000L

        /**
         * 多声道 16-bit PCM → 单声道 float（声道平均），纯函数可测试。
         */
        fun toMonoFloat(pcm16: ShortArray, channelCount: Int): FloatArray {
            require(channelCount >= 1)
            if (channelCount == 1) {
                return FloatArray(pcm16.size) { i -> pcm16[i] / 32768f }
            }
            val frames = pcm16.size / channelCount
            return FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until channelCount) {
                    sum += pcm16[f * channelCount + c] / 32768f
                }
                sum / channelCount
            }
        }
    }

    /**
     * 解码视频文件的音频轨到 PCM 文件。
     *
     * @param videoPath 输入视频绝对路径
     * @param outPcmPath 输出 PCM 文件绝对路径（父目录需存在）
     * @param onProgress 进度回调 0f~1f（按解码时间戳 / 总时长）
     */
    suspend fun decodeToPcmFile(
        videoPath: String,
        outPcmPath: String,
        onProgress: (Float) -> Unit = {},
    ): PcmInfo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoPath)
            val trackIndex = findAudioTrack(extractor)
                ?: throw IllegalArgumentException("该视频没有音频轨，无法进行语音识别")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("无法识别音频编码")
            val durationUs = runCatching { inputFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            extractor.selectTrack(trackIndex)

            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()
                try {
                    return@withContext drainToFile(
                        extractor, decoder, durationUs, outPcmPath, onProgress,
                    )
                } finally {
                    runCatching { decoder.stop() }
                }
            } finally {
                decoder.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun drainToFile(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        durationUs: Long,
        outPcmPath: String,
        onProgress: (Float) -> Unit,
    ): PcmInfo {
        var sampleRate = 0
        var channelCount = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var inputEos = false
        var outputEos = false
        var totalOutSamples = 0L
        var resampler: ChunkResampler? = null
        var lastProgress = -1f

        FileOutputStream(outPcmPath).use { fos ->
            val bufferInfo = MediaCodec.BufferInfo()
            while (!outputEos) {
                // --- 输入 ---
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            decoder.queueInputBuffer(
                                inIndex, 0, sampleSize,
                                extractor.sampleTime, 0,
                            )
                            extractor.advance()
                        }
                    }
                }
                // --- 输出 ---
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding =
                            runCatching { f.getInteger(MediaFormat.KEY_PCM_ENCODING) }
                                .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                        resampler = ChunkResampler(sampleRate, TARGET_SAMPLE_RATE)
                    }
                    outIndex >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outIndex)!!
                        if (bufferInfo.size > 0 && resampler != null) {
                            val shorts = bufferToShorts(outBuf, bufferInfo, pcmEncoding)
                            val mono = toMonoFloat(shorts, channelCount.coerceAtLeast(1))
                            val outBytes = resampler!!.process(mono)
                            fos.write(outBytes)
                            totalOutSamples += outBytes.size / 2
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                        if (durationUs > 0) {
                            val p = (bufferInfo.presentationTimeUs.toFloat() / durationUs)
                                .coerceIn(0f, 1f)
                            if (p - lastProgress >= 0.01f) {
                                lastProgress = p
                                onProgress(p)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                    // INFO_TRY_AGAIN_LATER：继续循环
                }
            }
            // 流结束：发射最后一块遗留的尾部点（与一次性重采样逐采样点一致）
            resampler?.let {
                val tail = it.finish()
                if (tail.isNotEmpty()) {
                    fos.write(tail)
                    totalOutSamples += tail.size / 2
                }
            }
        }
        onProgress(1f)
        return PcmInfo(filePath = outPcmPath, totalSamples = totalOutSamples)
    }

    private fun bufferToShorts(
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        encoding: Int,
    ): ShortArray {
        val dup = buf.duplicate().order(ByteOrder.nativeOrder())
        dup.position(info.offset)
        dup.limit(info.offset + info.size)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = dup.asFloatBuffer()
                ShortArray(fb.remaining()) { i ->
                    (fb.get(i).coerceIn(-1f, 1f) * 32767).roundToInt()
                        .coerceIn(-32768, 32767).toShort()
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val n = dup.remaining()
                ShortArray(n) { i ->
                    (((dup.get(i).toInt() and 0xFF) - 128) * 256)
                        .coerceIn(-32768, 32767).toShort()
                }
            }
            else -> { // ENCODING_PCM_16BIT（解码器最常见输出）
                val sb = dup.asShortBuffer()
                ShortArray(sb.remaining()) { i -> sb.get(i) }
            }
        }
    }
}

/**
 * 流式线性重采样器：任意输入采样率的单声道 float → 16kHz 16-bit PCM 字节。
 *
 * 原理：输出采样点按固定步长（fromRate/toRate）落在输入坐标系中，线性插值取值；
 * 块间通过 [phase] 传递小数位置，保证整体采样率精确、无重复无丢失。
 * 内存占用恒定（与音频总时长无关）。
 *
 * 块边界无缝：每块只发射坐标 p < n-1 的点，落在 [n-1, n) 的点留给下一块
 * （携带上一块最后一个采样点做左边缘，坐标可为负），因此与一次性重采样
 * 逐采样点完全一致；流结束时 [flush] 把尾部发射完（右边缘钳制）。
 */
class ChunkResampler(
    fromRate: Int,
    toRate: Int = PcmDecoder.TARGET_SAMPLE_RATE,
) {
    // 下一个输出采样点在"当前输入块坐标系"中的位置，可为负（落在 prevSample 上）
    private var phase = 0.0
    private var prevSample = 0f
    private var hasPrev = false
    private val step: Double = fromRate.toDouble() / toRate

    /**
     * 处理一块单声道 float 输入，返回 16-bit 小端 PCM 字节。
     *
     * @param isLast 是否为流的最后一块：为 true 时把尾部 [n-1, n) 也发射完
     * （右边缘钳制），调用后本实例不应再用。
     */
    fun process(input: FloatArray, isLast: Boolean = false): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        require(input.size >= 2) { "输入块至少需要 2 个采样点" }
        val n = input.size
        val out = mutableListOf<Short>()
        var p = phase
        val limit = if (isLast) n.toDouble() else (n - 1).toDouble()
        while (p < limit) {
            out.add(quantize(interpolate(input, n, p)))
            p += step
        }
        phase = p - n
        prevSample = input[n - 1]
        hasPrev = true
        return toBytes(out)
    }

    /**
     * 流结束：发射上一块遗留的尾部点（坐标落在 [-1, 0)，插值左右端点均为
     * 上一块最后一个采样，与一次性重采样的尾部钳制逐采样点一致）。
     * phase >= 0 时无遗留，返回空数组。
     */
    fun finish(): ByteArray {
        val out = mutableListOf<Short>()
        var p = phase
        while (p < 0) {
            out.add(quantize(prevSample))
            p += step
        }
        phase = p
        return toBytes(out)
    }

    private fun interpolate(input: FloatArray, n: Int, p: Double): Float {
        val fl = kotlin.math.floor(p)
        val i = fl.toInt()
        val frac = (p - fl).toFloat()
        val a = sampleAt(input, n, i)
        val b = sampleAt(input, n, i + 1)
        return a + (b - a) * frac
    }

    private fun sampleAt(input: FloatArray, n: Int, idx: Int): Float = when {
        idx < 0 -> if (hasPrev) prevSample else input[0]
        idx >= n -> input[n - 1]
        else -> input[idx]
    }

    private fun quantize(v: Float): Short =
        (v.coerceIn(-1f, 1f) * 32767).roundToInt().coerceIn(-32768, 32767).toShort()

    private fun toBytes(out: List<Short>): ByteArray {
        val bytes = ByteArray(out.size * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        out.forEach { bb.putShort(it) }
        return bytes
    }

    /** 一次性重采样（单元测试用）：等价于单个 process(input, isLast = true) 调用。 */
    fun resampleAll(input: FloatArray): ShortArray {
        val bytes = process(input, isLast = true)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(bb.remaining()) { bb.get(it) }
    }
}
