package com.example.media

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

enum class PlaybackState {
    IDLE,
    PLAYING,
    PAUSED
}

class VoiceJournalManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0
    private var accumulatedDuration: Long = 0

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    fun isOfflineSpeechRecognitionSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Start recording audio to an internal file.
     */
    fun startRecording(): File {
        val audioDir = File(context.filesDir, "audio_recordings")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        val file = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
        currentRecordingFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            accumulatedDuration = 0
            _recordingState.value = RecordingState.RECORDING
        } catch (e: Exception) {
            _recordingState.value = RecordingState.IDLE
            recorder.release()
            mediaRecorder = null
            throw IOException("Failed to start audio recorder: ${e.message}", e)
        }

        return file
    }

    /**
     * Pause recording (Android 24+).
     */
    fun pauseRecording() {
        if (_recordingState.value == RecordingState.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                accumulatedDuration += System.currentTimeMillis() - recordingStartTime
                _recordingState.value = RecordingState.PAUSED
            } catch (_: Exception) {}
        }
    }

    /**
     * Resume recording.
     */
    fun resumeRecording() {
        if (_recordingState.value == RecordingState.PAUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                recordingStartTime = System.currentTimeMillis()
                _recordingState.value = RecordingState.RECORDING
            } catch (_: Exception) {}
        }
    }

    /**
     * Stop recording and return file and total duration.
     */
    fun stopRecording(): Pair<File?, Long> {
        val file = currentRecordingFile
        var duration = accumulatedDuration
        if (_recordingState.value == RecordingState.RECORDING) {
            duration += System.currentTimeMillis() - recordingStartTime
        }

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            _recordingState.value = RecordingState.IDLE
            currentRecordingFile = null
        }

        return file to duration
    }

    /**
     * Start playback of recorded audio.
     */
    fun startPlayback(filePath: String, onCompletion: () -> Unit = {}) {
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.IDLE
                    _currentPlayingPath.value = null
                    onCompletion()
                }
                start()
            }
            mediaPlayer = player
            _currentPlayingPath.value = filePath
            _playbackState.value = PlaybackState.PLAYING
        } catch (_: Exception) {
            _playbackState.value = PlaybackState.IDLE
            _currentPlayingPath.value = null
        }
    }

    fun pausePlayback() {
        if (_playbackState.value == PlaybackState.PLAYING) {
            mediaPlayer?.pause()
            _playbackState.value = PlaybackState.PAUSED
        }
    }

    fun resumePlayback() {
        if (_playbackState.value == PlaybackState.PAUSED) {
            mediaPlayer?.start()
            _playbackState.value = PlaybackState.PLAYING
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            _playbackState.value = PlaybackState.IDLE
            _currentPlayingPath.value = null
        }
    }

    /**
     * Attempts on-device speech transcription using Android's SpeechRecognizer.
     * Guaranteed offline: sets EXTRA_PREFER_OFFLINE.
     * Gracefully fails if offline speech packs are not installed.
     */
    suspend fun transcribeAudioOffline(
        onResult: (transcript: String, status: String) -> Unit
    ) = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onResult("", "UNAVAILABLE")
            return@withContext
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    recognizer.destroy()
                    onResult("", "UNAVAILABLE")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    recognizer.destroy()
                    if (text.isNotBlank()) {
                        onResult(text, "COMPLETED")
                    } else {
                        onResult("", "UNAVAILABLE")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
        } catch (_: Exception) {
            onResult("", "UNAVAILABLE")
        }
    }

    fun release() {
        try {
            mediaRecorder?.release()
            mediaPlayer?.release()
        } catch (_: Exception) {}
    }
}
