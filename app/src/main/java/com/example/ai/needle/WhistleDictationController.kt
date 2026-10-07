package com.example.ai.needle

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Captures microphone audio for the local Whistle transcription: 16 kHz mono
 * PCM converted to the float range [-1, 1] that the needle engine expects.
 *
 * The engine transcribes at most ~30 s per call, so recording auto-stops just
 * before that limit.
 */
class WhistleDictationController {

    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var buffer = ShortArray(0)
    private var writeIndex = 0

    private val recordingFlow = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = recordingFlow

    private var worker: Thread? = null

    val sampleRate: Int = 16_000

    /** Maximum useful duration: the speech model handles ~30 s per call. */
    private val maxSamples: Int = sampleRate * 29

    companion object {
        fun hasMicrophonePermission(context: android.content.Context): Boolean =
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Starts capturing. Returns false when the mic permission is missing or
     * the recorder could not be opened.
     */
    @SuppressLint("MissingPermission") // checked by the caller / hasMicrophonePermission
    fun start(): Boolean {
        if (recording) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 8 * 1024)
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        buffer = ShortArray(maxSamples)
        writeIndex = 0
        audioRecord = record
        recording = true
        recordingFlow.value = true
        record.startRecording()
        worker = Thread {
            val chunk = ShortArray(2048)
            try {
                while (recording && writeIndex < maxSamples) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n > 0) {
                        val space = minOf(n, maxSamples - writeIndex)
                        System.arraycopy(chunk, 0, buffer, writeIndex, space)
                        writeIndex += space
                        if (space < n) break // hit the 29 s cap
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (recording) stopInternal()
            }
        }.apply { name = "whistle-capture"; start() }
        return true
    }

    /** Stops capturing and returns the recorded audio as float PCM. */
    fun stop(): FloatArray {
        stopInternal()
        val samples = writeIndex
        if (samples <= 0) return FloatArray(0)
        val pcm = FloatArray(samples)
        for (i in 0 until samples) {
            pcm[i] = buffer[i] / 32768f
        }
        return pcm
    }

    private fun stopInternal() {
        recording = false
        recordingFlow.value = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    /** Stops and discards. */
    fun cancel() {
        stopInternal()
        writeIndex = 0
    }
}
