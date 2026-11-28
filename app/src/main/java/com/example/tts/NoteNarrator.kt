package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.tts.kitten.KittenTtsEngine
import com.example.tts.kitten.KittenTtsModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Reads a journal page out loud.
 *
 * Use case: hands-free review of a note (walking, driving, cooking) and
 * comfortable reading for long entries — everything stays on the phone.
 *
 * It uses the engine picked in Settings → Voz y asistente: KittenTTS 2 (pico,
 * fully on-device) when selected and downloaded, and otherwise falls back to
 * the Android system TextToSpeech so the feature never silently does nothing.
 */
class NoteNarrator(context: Context) : AutoCloseable {

    enum class State { IDLE, PREPARING, SPEAKING }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Incremented on every stop/speak so stale callbacks are ignored. */
    private var session = 0
    private var speechJob: Job? = null

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var pendingTtsAction: ((TextToSpeech) -> Unit)? = null

    fun toggle(text: String) {
        if (_state.value == State.IDLE) speak(text) else stop()
    }

    fun speak(text: String) {
        val clean = forSpeech(text)
        if (clean.isBlank()) return
        stop()
        val mySession = session
        _error.value = null
        _state.value = State.PREPARING

        speechJob = scope.launch {
            val kittenSpoken = if (KittenTtsModelStore.isKittenSelected(appContext)) {
                withContext(Dispatchers.IO) {
                    try {
                        KittenTtsEngine.speakSelected(appContext, clean)
                    } catch (_: Throwable) {
                        false
                    }
                }
            } else {
                false
            }
            if (mySession != session) return@launch
            if (kittenSpoken) {
                _state.value = State.IDLE
            } else {
                speakWithSystemTts(mySession, clean)
            }
        }
    }

    fun stop() {
        session += 1
        speechJob?.cancel()
        speechJob = null
        try {
            ttsEngine?.stop()
        } catch (_: Throwable) {
        }
        try {
            KittenTtsEngine.stop()
        } catch (_: Throwable) {
        }
        _state.value = State.IDLE
    }

    fun clearError() {
        _error.value = null
    }

    override fun close() {
        stop()
        try {
            ttsEngine?.shutdown()
        } catch (_: Throwable) {
        }
        ttsEngine = null
        ttsReady = false
        pendingTtsAction = null
        scope.cancel()
    }

    // ------------------------------------------------------------------
    // System TextToSpeech fallback
    // ------------------------------------------------------------------

    private fun speakWithSystemTts(mySession: Int, text: String) {
        if (mySession != session) return
        val utterance = text.take(TextToSpeech.getMaxSpeechInputLength())

        withTts { engine ->
            if (mySession != session) return@withTts
            _state.value = State.SPEAKING
            val result = engine.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "note-$mySession")
            if (result == TextToSpeech.ERROR) {
                _state.value = State.IDLE
                _error.value = "La voz del sistema no pudo leer esta nota."
            }
        }
    }

    private fun withTts(action: (TextToSpeech) -> Unit) {
        val engine = ttsEngine
        if (engine != null && ttsReady) {
            action(engine)
            return
        }
        pendingTtsAction = action
        if (engine == null) {
            ttsEngine = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                val created = ttsEngine
                val pending = pendingTtsAction
                pendingTtsAction = null
                if (ttsReady && created != null) {
                    try {
                        created.setLanguage(Locale.getDefault())
                        created.setOnUtteranceProgressListener(progressListener)
                    } catch (_: Throwable) {
                    }
                    pending?.invoke(created)
                } else {
                    _error.value = "No hay un motor de voz disponible en el teléfono."
                    _state.value = State.IDLE
                }
            }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.value = State.SPEAKING
        }

        override fun onDone(utteranceId: String?) {
            _state.value = State.IDLE
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onTtsError()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            onTtsError()
        }

        private fun onTtsError() {
            _state.value = State.IDLE
            _error.value = "La lectura se interrumpió (voz del sistema)."
        }
    }

    // ------------------------------------------------------------------
    // Text preparation
    // ------------------------------------------------------------------

    /** Removes markdown noise so the voice reads words, not symbols. */
    private fun forSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[\\[([^\\]]+)]]"), "$1")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*[-*+]\\s+\\[[ xX]]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*>\\s?", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*|__|~~|`|==|---"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
