package com.example.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Decodes any audio file the platform understands (m4a/AAC, mp3, ogg, wav,
 * 3gp…) into 16 kHz MONO float PCM — the exact format Whistle consumes.
 *
 * This is what makes "transcribe an ALREADY SAVED voice note" possible:
 * before, the only Whistle path re-recorded from the microphone instead of
 * decoding the stored file.
 */
object AudioDecode {

    private const val TARGET_RATE = 16_000

    /** Returns float PCM in [-1, 1] at 16 kHz mono, or null when undecodable. */
    suspend fun decodeToPcm16kMono(path: String): FloatArray? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            if (format == null) return@withContext null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null

            codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                return@withContext null
            }
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var outFormat: MediaFormat? = null
            var watchdog = 0

            while (!sawOutputEos && watchdog < 20_000) {
                watchdog++
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIdx = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outBuf.get(chunk)
                            pcm.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            val bytes = pcm.toByteArray()
            if (bytes.size < 2) return@withContext null

            // Output format is authoritative when the codec reports it
            // (channel count / sample rate can change after decode starts).
            val srcRate = (outFormat ?: format).getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            val srcChannels = (outFormat ?: format).getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)

            val sampleCount = bytes.size / (2 * srcChannels)
            if (sampleCount == 0) return@withContext null

            // 16-bit little-endian -> mono floats
            val mono = FloatArray(sampleCount)
            var idx = 0
            for (s in 0 until sampleCount) {
                var acc = 0.0
                for (c in 0 until srcChannels) {
                    val lo = bytes[idx++].toInt() and 0xFF
                    val hi = bytes[idx++].toByte().toInt()
                    acc += ((hi shl 8) or lo) / 32768.0
                }
                mono[s] = (acc / srcChannels).toFloat()
            }

            resampleTo16k(mono, srcRate)
        } catch (_: Exception) {
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /** Linear-interpolation resampler (good enough for ASR input). */
    private fun resampleTo16k(samples: FloatArray, srcRate: Int): FloatArray {
        if (srcRate == TARGET_RATE) return samples
        if (srcRate <= 0 || samples.isEmpty()) return FloatArray(0)
        val outLen = (samples.size.toLong() * TARGET_RATE / srcRate).toInt()
        if (outLen <= 0) return FloatArray(0)
        val out = FloatArray(outLen)
        val step = srcRate.toDouble() / TARGET_RATE
        var pos = 0.0
        for (i in 0 until outLen) {
            val i0 = pos.toInt()
            val i1 = (i0 + 1).coerceAtMost(samples.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = samples[i0] * (1f - frac) + samples[i1] * frac
            pos += step
        }
        return out
    }

    private fun MediaFormat.getIntegerOr(key: String, default: Int): Int =
        try { getInteger(key) } catch (_: Exception) { default }
}
