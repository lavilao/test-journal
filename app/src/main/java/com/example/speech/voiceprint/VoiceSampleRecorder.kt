package com.example.speech.voiceprint

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

/**
 * Records a fixed-length voice sample as raw PCM16 mono 16 kHz, with a
 * simple energy-based VAD so silence at the beginning/end gets trimmed.
 *
 * Runs on a background thread; the callback fires on the same thread —
 * callers switch to main if needed.
 */
object VoiceSampleRecorder {

    class Result(
        val samples: ShortArray?,
        val reason: String
    ) {
        val success: Boolean get() = samples != null
    }

    /**
     * Records [durationMs] of audio. Requires RECORD_AUDIO to be already
     * granted (callers check before invoking).
     */
    @SuppressLint("MissingPermission")
    fun record(durationMs: Long, onDone: (Result) -> Unit) {
        Thread {
            val minBuffer = AudioRecord.getMinBufferSize(
                VoicePrintEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                onDone(Result(null, "Este dispositivo no permite grabar audio ahora"))
                return@Thread
            }
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    VoicePrintEngine.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, VoicePrintEngine.SAMPLE_RATE * 2)
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    onDone(Result(null, "El micrófono está ocupado o no disponible"))
                    return@Thread
                }
                val totalFrames = (durationMs * VoicePrintEngine.SAMPLE_RATE / 1000).toInt()
                val raw = ShortArray(totalFrames)
                var filled = 0
                record.startRecording()
                while (filled < totalFrames) {
                    val n = record.read(raw, filled, totalFrames - filled)
                    if (n <= 0) break
                    filled += n
                }
                try { record.stop() } catch (_: Exception) {}

                val captured = if (filled == totalFrames) raw else raw.copyOf(filled)
                val trimmed = trimSilence(captured)
                if (trimmed.size < VoicePrintEngine.SAMPLE_RATE / 2) {
                    onDone(Result(null, "No te escuché bien: habla más fuerte o más cerca"))
                } else {
                    onDone(Result(trimmed, "ok"))
                }
            } catch (e: Exception) {
                onDone(Result(null, "Error al grabar: ${e.message}"))
            } finally {
                try { record?.release() } catch (_: Exception) {}
            }
        }.start()
    }

    /**
     * Records with a timeout and end-of-speech detection: stops after
     * [maxDurationMs] or after [silenceMs] of silence following speech.
     */
    @SuppressLint("MissingPermission")
    fun recordWithVad(maxDurationMs: Long, silenceMs: Long = 1400, onDone: (Result) -> Unit) {
        Thread {
            val minBuffer = AudioRecord.getMinBufferSize(
                VoicePrintEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                onDone(Result(null, "Este dispositivo no permite grabar audio ahora"))
                return@Thread
            }
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    VoicePrintEngine.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, VoicePrintEngine.SAMPLE_RATE * 2)
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    onDone(Result(null, "El micrófono está ocupado o no disponible"))
                    return@Thread
                }
                val chunk = 1600 // 100 ms
                val collected = mutableListOf<ShortArray>()
                var total = 0
                var heardSpeech = false
                var silenceRun = 0
                val maxTotal = (maxDurationMs * VoicePrintEngine.SAMPLE_RATE / 1000).toInt()
                val silenceLimit = (silenceMs / 100).toInt()

                record.startRecording()
                while (total < maxTotal) {
                    val buffer = ShortArray(chunk)
                    val n = record.read(buffer, 0, chunk)
                    if (n <= 0) break
                    collected.add(buffer.copyOf(n))
                    total += n
                    val level = rms(buffer, n)
                    if (level > 0.012f) {
                        heardSpeech = true
                        silenceRun = 0
                    } else if (heardSpeech) {
                        silenceRun++
                        if (silenceRun >= silenceLimit) break
                    }
                }
                try { record.stop() } catch (_: Exception) {}

                val all = ShortArray(total)
                var offset = 0
                collected.forEach { c ->
                    System.arraycopy(c, 0, all, offset, c.size)
                    offset += c.size
                }
                val trimmed = trimSilence(all)
                if (!heardSpeech || trimmed.size < VoicePrintEngine.SAMPLE_RATE / 2) {
                    onDone(Result(null, "No te escuché bien: inténtalo de nuevo"))
                } else {
                    onDone(Result(trimmed, "ok"))
                }
            } catch (e: Exception) {
                onDone(Result(null, "Error al grabar: ${e.message}"))
            } finally {
                try { record?.release() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun rms(buffer: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var acc = 0.0
        for (i in 0 until n) {
            val v = buffer[i] / 32768.0
            acc += v * v
        }
        return kotlin.math.sqrt(acc / n).toFloat()
    }

    private fun trimSilence(samples: ShortArray): ShortArray {
        val threshold = 700
        var start = 0
        while (start < samples.size && abs(samples[start].toInt()) < threshold) start++
        var end = samples.size - 1
        while (end > start && abs(samples[end].toInt()) < threshold) end--
        if (end <= start) return samples
        // small padding around the detected speech
        val from = (start - 400).coerceAtLeast(0)
        val to = (end + 400).coerceAtMost(samples.size - 1)
        return samples.copyOfRange(from, to + 1)
    }
}
