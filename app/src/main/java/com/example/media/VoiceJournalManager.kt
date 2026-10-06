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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Real voice journal manager.
 *
 * Dictation uses the Android system SpeechRecognizer (the same engine behind
 * Gboard voice typing). While a voice note is being recorded, a recognition
 * session runs in parallel and accumulates the recognized text in
 * [liveTranscript], so audio files get a REAL transcript without any fake
 * bundled model. When the system recognizer is unavailable (no speech
 * service), [isDictationAvailable] reports false and the UI can tell the
 * user the honest truth instead of pretending.
 */
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

    // ---- Dictation state ----
    private var dictationRecognizer: SpeechRecognizer? = null

    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    /** Accumulated final results of the current dictation/recording session. */
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    /** Current partial (in-progress) recognition fragment. */
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    /** Honest availability check: is there any speech service on this device? */
    val isDictationAvailable: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

    /** Whether the device exposes the dedicated on-device recognizer (API 31+). */
    val hasOnDeviceRecognizer: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                try {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                } catch (_: Exception) {
                    false
                }

    /** Last dictation error code, so the UI can show what really happened. */
    private val _lastDictationError = MutableStateFlow(0)
    val lastDictationError: StateFlow<Int> = _lastDictationError.asStateFlow()

    private var restartDictationWhenDone = false
    private var dictationCallback: ((String) -> Unit)? = null

    // ---------------------------------------------------------------
    // Audio recording
    // ---------------------------------------------------------------

    /**
     * Start recording audio to an internal file, with a live dictation session
     * running in parallel so the note gets a real transcript.
     */
    fun startRecordingWithLiveDictation(): File {
        val file = startRecording()
        startDictationSession(restartOnResult = true) { recognized ->
            _liveTranscript.value = _liveTranscript.value
                .let { if (it.isBlank()) recognized else "$it $recognized" }
        }
        return file
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
     * Stop recording and return file and total duration. Also stops the live
     * dictation session and returns its final transcript through [liveTranscript].
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

        stopDictation()
        return file to duration
    }

    /** Transcript captured live for the last recording session. */
    fun consumeLiveTranscript(): String {
        val value = _liveTranscript.value.trim()
        return value
    }

    private fun clearTranscriptState() {
        _liveTranscript.value = ""
        _partialTranscript.value = ""
    }

    // ---------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Live dictation (system SpeechRecognizer)
    // ---------------------------------------------------------------

    /**
     * Start a dictation session. If [restartOnResult] is true the recognizer
     * keeps listening after every final result (used while recording voice
     * notes). [onResult] is invoked with every recognized fragment.
     */
    fun startDictationSession(
        restartOnResult: Boolean = false,
        resetTranscript: Boolean = true,
        onResult: (text: String) -> Unit
    ) {
        if (!isDictationAvailable) {
            _isDictating.value = false
            return
        }
        stopDictation()
        if (resetTranscript) clearTranscriptState()
        restartDictationWhenDone = restartOnResult
        dictationCallback = onResult
        beginRecognition()
    }

    private fun beginRecognition() {
        if (!isDictationAvailable) {
            _isDictating.value = false
            return
        }

        val localeTag = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Exception) {
            "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            // Ask the engine to prefer its offline model when available.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }

        try {
            val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isDictating.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    _lastDictationError.value = error
                    _isDictating.value = false
                    // Client-side errors are transient: keep the session alive
                    // while we are recording. Errors like "no match" or
                    // "speech timeout" should not kill a long voice note.
                    val transient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    if (transient && restartDictationWhenDone) {
                        restartAfterDelay()
                    } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        // Another recognizer took the mic; do not crash, just stop cleanly.
                        restartDictationWhenDone = false
                    }
                }
                override fun onResults(results: Bundle?) {
                    _isDictating.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) {
                        dictationCallback?.invoke(text)
                    }
                    if (restartDictationWhenDone) {
                        restartAfterDelay()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    _partialTranscript.value = text
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            dictationRecognizer = recognizer
            recognizer.startListening(intent)
        } catch (_: Exception) {
            _isDictating.value = false
        }
    }

    private fun restartAfterDelay() {
        val recognizerRef = dictationRecognizer
        if (recognizerRef == null) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Only restart if we are still in the same session and still active.
            if (restartDictationWhenDone && dictationRecognizer === recognizerRef) {
                beginRecognition()
            }
        }, RESTART_DELAY_MS)
    }

    /**
     * One-shot live dictation into arbitrary text (kept for compatibility
     * with existing call sites such as the journal editor).
     */
    fun startLiveDictation(
        onResult: (text: String) -> Unit,
        onError: () -> Unit = {}
    ) {
        if (!isDictationAvailable) {
            _isDictating.value = false
            onError()
            return
        }
        stopDictation()
        restartDictationWhenDone = false
        dictationCallback = onResult
        clearTranscriptState()

        if (!isDictationAvailable) {
            onError()
            return
        }

        val localeTag = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Exception) {
            "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            dictationRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isDictating.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isDictating.value = false
                    }
                    override fun onError(error: Int) {
                        _lastDictationError.value = error
                        _isDictating.value = false
                        onError()
                    }
                    override fun onResults(results: Bundle?) {
                        _isDictating.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            onResult(text)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            onResult(text)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        } catch (_: Exception) {
            _isDictating.value = false
            onError()
        }
    }

    fun stopDictation() {
        restartDictationWhenDone = false
        try {
            dictationRecognizer?.stopListening()
            dictationRecognizer?.destroy()
        } catch (_: Exception) {}
        dictationRecognizer = null
        _isDictating.value = false
    }

    fun release() {
        stopDictation()
        try {
            mediaRecorder?.release()
            mediaPlayer?.release()
        } catch (_: Exception) {}
    }

    companion object {
        private const val RESTART_DELAY_MS = 150L
    }
}
