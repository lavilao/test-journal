package com.example.speech.hotword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.assistant.AssistantIntent
import com.example.assistant.AssistantManager
import com.example.assistant.AssistantParser
import com.example.MainActivity
import com.example.speech.SpeechEngineManager
import com.example.speech.voiceprint.VoicePrintEngine
import com.example.speech.voiceprint.VoicePrintStore
import com.example.speech.voiceprint.VoiceSampleRecorder
import com.example.telemetry.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * SOFTWARE hotword detection + local assistant execution, as a foreground
 * service with the microphone type.
 *
 * HONEST LIMITS (surfaced in the UI too):
 *  - The always-on DSP hotword of Google Assistant / Bixby ("Hey Google" on
 *    the low-power chip) uses privileged system APIs (AlwaysOnHotwordDetector
 *    was removed from the public SDK) — no third-party app can offer it.
 *    What THIS service does is software listening: the engine runs while the
 *    service is alive and Android allows the microphone (screen on / app or
 *    FGS in foreground). It uses battery; that is the price of honesty.
 *  - Google's enrolled Voice Match model is also privileged, so Voice Match
 *    here is OUR OWN on-device voice print (MFCC embedding + cosine
 *    similarity) enrolled by the user.
 */
class HotwordService : Service(), RecognitionListener {

    companion object {
        const val ACTION_STOP = "com.example.speech.hotword.STOP"
        const val EXTRA_VOICE_COMMAND = "voice_command"

        private const val PREFS = "hotword_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_VOICE_MATCH = "voice_match"
        private const val KEY_TTS_REPLY = "tts_reply"
        const val DEFAULT_WAKE_WORD = "oye mnemosyne"

        private const val NOTIF_ID = 4210
        private const val CHANNEL_ID = "hotword_listening"

        // ---- Observable state (same process as the UI) ----
        val isRunning = MutableStateFlow(false)
        val isListening = MutableStateFlow(false)
        val status = MutableStateFlow("Detenido")
        val lastPartial = MutableStateFlow("")
        val lastCommand = MutableStateFlow<String?>(null)
        val lastReply = MutableStateFlow<String?>(null)
        val voiceMatchScore = MutableStateFlow<Float?>(null)
        val consecutiveErrors = MutableStateFlow(0)

        /** Set by MainActivity so the service knows if the UI is visible. */
        var appInForeground = false

        // ---- Preference helpers ----
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, value: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, value).apply()
        }

        fun wakeWord(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_WAKE_WORD

        fun setWakeWord(context: Context, value: String) {
            val clean = value.trim().take(40)
            if (clean.isNotBlank()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_WAKE_WORD, clean).apply()
            }
        }

        fun voiceMatchEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_VOICE_MATCH, false)

        fun setVoiceMatchEnabled(context: Context, value: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_VOICE_MATCH, value).apply()
        }

        fun ttsReplyEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_TTS_REPLY, true)

        fun setTtsReplyEnabled(context: Context, value: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TTS_REPLY, value).apply()
        }

        fun start(context: Context) {
            val intent = Intent(context, HotwordService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                isRunning.value = false
                status.value = "No se pudo iniciar la escucha (restricción del sistema)"
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, HotwordService::class.java))
            } catch (_: Exception) {}
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = MainScope()

    private lateinit var engineManager: SpeechEngineManager
    private lateinit var assistantManager: AssistantManager
    private lateinit var voicePrintStore: VoicePrintStore

    private var recognizer: SpeechRecognizer? = null
    private var sessionGeneration = 0
    private var armed = false                 // wake word heard, command may follow
    private var awaitingCommandOneShot = false
    private var errorStreak = 0
    private var restartDelay = 250L
    private var toneGenerator: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        engineManager = SpeechEngineManager(applicationContext)
        assistantManager = AssistantManager(applicationContext)
        voicePrintStore = VoicePrintStore(applicationContext)
        createNotificationChannel()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            status.value = "Detenido desde la notificación"
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
        } catch (_: Exception) {
            status.value = "El sistema bloqueó el servicio en segundo plano"
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning.value = true
        armed = false
        awaitingCommandOneShot = false
        errorStreak = 0
        restartLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning.value = false
        isListening.value = false
        serviceScope.cancel()
        destroyRecognizer()
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    // Foreground service plumbing
    // -----------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Escucha del hotword",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aviso permanente mientras la app escucha tu palabra de activación"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification("Escuchando «${wakeWord()}»")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, HotwordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Asistente de voz activo")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(0, "Detener", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    // -----------------------------------------------------------------
    // Listening loop
    // -----------------------------------------------------------------

    private fun wakeWord(): String = wakeWord(this)

    private fun restartLoop() {
        sessionGeneration++
        destroyRecognizer()
        errorStreak = 0
        status.value = "Escuchando «${wakeWord()}»"
        updateNotification("Escuchando «${wakeWord()}»")
        startSession()
    }

    private fun startSession() {
        val generation = sessionGeneration
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
        }
        try {
            val rec = engineManager.createRecognizer()
            rec.setRecognitionListener(this)
            recognizer = rec
            rec.startListening(intent)
        } catch (e: Exception) {
            status.value = "El reconocedor no arrancó: ${e.message}"
            scheduleRestart(generation, 2000L)
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        isListening.value = false
    }

    private fun scheduleRestart(generation: Int, delayMs: Long) {
        mainHandler.postDelayed({
            if (generation == sessionGeneration && isRunning.value) {
                startSession()
            }
        }, delayMs)
    }

    // --- RecognitionListener ---

    override fun onReadyForSpeech(params: Bundle?) {
        isListening.value = true
        errorStreak = 0
        restartDelay = 250L
        consecutiveErrors.value = 0
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        // Session finalizes; onResults will arrive next.
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim() ?: ""
        if (text.isBlank()) return
        lastPartial.value = text
        if (!armed && !awaitingCommandOneShot &&
            WakeWordMatcher.containsWakeWord(text, wakeWord())
        ) {
            onWakeWordDetected(text)
        }
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim() ?: ""
        when {
            awaitingCommandOneShot -> {
                awaitingCommandOneShot = false
                if (text.isNotBlank()) {
                    executeCommand(text)
                } else {
                    status.value = "No escuché el comando"
                    resumeLoopAfter(600)
                }
            }

            armed -> {
                armed = false
                val command = WakeWordMatcher.stripWakeWord(text, wakeWord())
                if (command.isNotBlank()) {
                    executeCommand(command)
                } else {
                    // Only the wake word was said: the next utterance is the command.
                    listenForCommandOneShot()
                }
            }

            else -> {
                if (WakeWordMatcher.containsWakeWord(text, wakeWord())) {
                    onWakeWordDetected(text)
                } else {
                    // Irrelevant speech: keep listening in a new session.
                    resumeLoopAfter(250)
                }
            }
        }
    }

    override fun onError(error: Int) {
        isListening.value = false
        errorStreak++
        consecutiveErrors.value = errorStreak
        val generation = sessionGeneration
        val transient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        when {
            awaitingCommandOneShot && transient -> resumeLoopAfter(400)

            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                status.value = "Motor ocupado (otra app usa el micrófono)…"
                scheduleRestart(generation, 1500L)
            }

            transient -> {
                status.value = "Escuchando «${wakeWord()}»"
                restartDelay = (restartDelay * 2).coerceAtMost(2000L)
                scheduleRestart(generation, restartDelay)
            }

            else -> {
                if (errorStreak > 12) {
                    status.value = "El motor de voz falló demasiado ($errorStreak errores). " +
                            "La escucha se detuvo."
                    stopSelf()
                } else {
                    scheduleRestart(generation, 1000L)
                }
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // -----------------------------------------------------------------
    // Wake word → (optional voice match) → command
    // -----------------------------------------------------------------

    private fun onWakeWordDetected(utteranceSoFar: String) {
        armed = false
        destroyRecognizer()
        beep()

        val useVoiceMatch = voiceMatchEnabled(this) && voicePrintStore.isEnrolled()
        if (useVoiceMatch) {
            status.value = "Detectado — repite tu frase para verificar tu voz"
            updateNotification("Verificando tu voz…")
            VoiceSampleRecorder.recordWithVad(maxDurationMs = 4000, silenceMs = 1300) { result ->
                mainHandler.post {
                    val samples = result.samples
                    if (samples == null) {
                        status.value = result.reason
                        resumeLoopAfter(1200)
                        return@post
                    }
                    computeEmbeddingAsync(samples) { embedding ->
                        if (embedding == null) {
                            status.value = "La muestra de voz no sirvió para verificar"
                            resumeLoopAfter(1200)
                        } else {
                            val score = voicePrintStore.verify(embedding)
                            voiceMatchScore.value = score
                            if (score != null && score >= voicePrintStore.threshold()) {
                                status.value =
                                    "Voz verificada (${(score * 100).toInt()}%) — dime el comando"
                                doubleBeep()
                                listenForCommandOneShot()
                            } else {
                                status.value = "Voz NO reconocida " +
                                        "(similitud ${((score ?: 0f) * 100).toInt()}%)"
                                resumeLoopAfter(1500)
                            }
                        }
                    }
                }
            }
        } else {
            val command = WakeWordMatcher.stripWakeWord(utteranceSoFar, wakeWord())
            if (command.isNotBlank()) {
                executeCommand(command)
            } else {
                status.value = "Detectado — dime el comando"
                listenForCommandOneShot()
            }
        }
    }

    /** One-shot recognition for the command that follows the wake word. */
    private fun listenForCommandOneShot() {
        sessionGeneration++
        destroyRecognizer()
        awaitingCommandOneShot = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        try {
            val rec = engineManager.createRecognizer()
            rec.setRecognitionListener(this)
            recognizer = rec
            rec.startListening(intent)
        } catch (e: Exception) {
            status.value = "No pude escuchar el comando: ${e.message}"
            resumeLoopAfter(1500)
        }
    }

    private fun resumeLoopAfter(delayMs: Long) {
        val generation = sessionGeneration
        mainHandler.postDelayed({
            if (generation == sessionGeneration || isRunning.value) restartLoop()
        }, delayMs)
    }

    private fun computeEmbeddingAsync(samples: ShortArray, onDone: (FloatArray?) -> Unit) {
        serviceScope.launch(Dispatchers.Default) {
            val embedding = try {
                VoicePrintEngine.computeEmbedding(samples)
            } catch (_: Exception) {
                null
            }
            launch(Dispatchers.Main) { onDone(embedding) }
        }
    }

    // -----------------------------------------------------------------
    // Command execution (local, no GenAI)
    // -----------------------------------------------------------------

    private fun executeCommand(command: String) {
        destroyRecognizer()
        lastCommand.value = command
        status.value = "Comando: «$command»"
        serviceScope.launch(Dispatchers.Main) {
            val reply = executeAndReply(command)
            lastReply.value = reply
            status.value = reply
            speak(reply)
            notifyExchange(command, reply)
            delay(900)
            if (isRunning.value) restartLoop()
        }
    }

    private suspend fun executeAndReply(command: String): String {
        val intent = AssistantParser.parse(command)
        return try {
            when (intent) {
                is AssistantIntent.OpenApp -> {
                    val app = assistantManager.findApp(intent.appName)
                    if (app != null) {
                        assistantManager.launchApp(app.packageName)
                        "Abriendo ${app.appName}…"
                    } else {
                        "No encontré una app llamada \"${intent.appName}\"."
                    }
                }

                is AssistantIntent.CallContact -> {
                    val contact = assistantManager.searchContact(intent.contactName)
                    if (contact?.phoneNumber != null) {
                        assistantManager.dialContact(contact.phoneNumber)
                        "Llamando a ${contact.displayName}…"
                    } else {
                        "No encontré a \"${intent.contactName}\" en tus contactos " +
                                "(revisa el permiso de contactos)."
                    }
                }

                is AssistantIntent.SearchDevice ->
                    "Buscando \"${intent.query}\"… abre la app para ver los resultados."

                is AssistantIntent.SearchWeb -> {
                    assistantManager.webSearch(intent.query)
                    "Buscando \"${intent.query}\" en la web…"
                }

                AssistantIntent.Weather -> {
                    val weather = WeatherService(applicationContext).refreshWeather(force = false)
                    assistantManager.describeWeather(weather)
                }

                is AssistantIntent.CreateNote ->
                    "Nota preparada: «${intent.text}». Abre la app para revisarla."

                is AssistantIntent.Calculate -> {
                    val value = AssistantParser.safeEvaluate(intent.expression)
                    if (value == null) {
                        "No pude calcular eso."
                    } else if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        String.format(Locale.getDefault(), "%.4g", value)
                    }
                }

                is AssistantIntent.SetTimer -> {
                    assistantManager.startTimer(intent.minutes)
                    "Temporizador de ${intent.minutes} min en marcha."
                }

                is AssistantIntent.SetAlarm -> {
                    assistantManager.setAlarm(intent.hour, intent.minute)
                    "Alarma a las %02d:%02d.".format(intent.hour, intent.minute)
                }

                is AssistantIntent.Flashlight -> {
                    val ok = assistantManager.setFlashlight(intent.on)
                    if (ok) {
                        if (intent.on) "Linterna encendida." else "Linterna apagada."
                    } else {
                        "No pude controlar la linterna en este dispositivo."
                    }
                }

                AssistantIntent.Battery -> assistantManager.formatBattery()

                AssistantIntent.Lens ->
                    "Abre la app y usa el botón de cámara para escanear."

                is AssistantIntent.Translate -> {
                    val source = com.example.semantic.MlKitAnalyzer.identifyLanguage(intent.text)
                    val result = com.example.semantic.MlKitAnalyzer.translateText(
                        intent.text, source, intent.targetLangHint ?: "en"
                    )
                    result.fold(
                        onSuccess = { "\"${intent.text}\" → $it" },
                        onFailure = { "No pude traducir ahora mismo (¿falta el modelo o la conexión?)." }
                    )
                }

                AssistantIntent.Unknown ->
                    "No entendí el comando. Prueba: «abre whatsapp», " +
                            "«llama a maría», «cuánta batería queda», «linterna», «cuánto es 12 por 7»."
            }
        } catch (e: Exception) {
            "El comando falló: ${e.message}"
        }
    }

    // -----------------------------------------------------------------
    // Feedback: tones, TTS, notifications
    // -----------------------------------------------------------------

    private fun beep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        } catch (_: Exception) {}
    }

    private fun doubleBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
        } catch (_: Exception) {}
    }

    private fun initTts() {
        if (!ttsReplyEnabled(this)) return
        try {
            tts = TextToSpeech(applicationContext) { initStatus ->
                ttsReady = initStatus == TextToSpeech.SUCCESS
            }
        } catch (_: Exception) {}
    }

    private fun speak(text: String) {
        if (!ttsReplyEnabled(this) || !ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hotword_reply")
        } catch (_: Exception) {}
    }

    /**
     * Posts the command/reply exchange. Tapping it opens the assistant with
     * the command so the full in-app flow (notes, device search…) can run —
     * useful when the app was in the background and Android blocked the
     * direct activity launch.
     */
    private fun notifyExchange(command: String, reply: String) {
        try {
            val openIntent = PendingIntent.getActivity(
                this, 2,
                Intent(this, MainActivity::class.java)
                    .putExtra(EXTRA_VOICE_COMMAND, command)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.notify(
                NOTIF_ID + 1,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("«$command»")
                    .setContentText(reply)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(openIntent)
                    .build()
            )
        } catch (_: Exception) {}
    }
}
