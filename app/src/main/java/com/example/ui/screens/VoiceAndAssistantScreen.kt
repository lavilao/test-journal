package com.example.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ai.needle.ModelTransferManager
import com.example.ai.needle.NeedleTools
import com.example.ai.needle.CustomToolRegistry
import com.example.data.model.CustomTool
import com.example.ai.needle.WhistleDictationController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.speech.OfflineSpeechSupport
import com.example.speech.RecognitionEngineInfo
import com.example.speech.hotword.HotwordService
import com.example.speech.voiceprint.VoicePrintEngine
import com.example.speech.voiceprint.VoicePrintStore
import com.example.speech.voiceprint.VoiceSampleRecorder
import com.example.ui.components.GoogleGreen
import com.example.ui.theme.ForestPrimary
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Voz y asistente" settings: recognition engine picker (fixes the
 * Gboard/Samsung voice confusion), offline model manager (with in-app
 * download on Android 13+), software hotword, our own Voice Match and the
 * system-assistant role. Everything here is honest about platform limits.
 */
@Composable
fun VoiceAndAssistantScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineManager = viewModel.speechEngineManager
    val store = remember { VoicePrintStore(context) }

    var refreshTick by remember { mutableIntStateOf(0) }

    // ---- engine state ----
    val engines = remember(refreshTick) { engineManager.availableEngines() }
    val currentSelection = remember(refreshTick) { engineManager.currentSelection() }
    val resolvedEngine = remember(refreshTick) { engineManager.resolvedEngineInfo() }
    var showEngineDialog by remember { mutableStateOf(false) }

    // ---- offline models state ----
    var offlineSupport by remember { mutableStateOf<OfflineSpeechSupport?>(null) }
    var offlineCheckInProgress by remember { mutableStateOf(false) }
    var offlineMessage by remember { mutableStateOf<String?>(null) }
    val localeTag = Locale.getDefault().toLanguageTag()

    // ---- hotword state ----
    val hotwordRunning by HotwordService.isRunning.collectAsState()
    val hotwordListening by HotwordService.isListening.collectAsState()
    val hotwordStatus by HotwordService.status.collectAsState()
    val hotwordPartial by HotwordService.lastPartial.collectAsState()
    val hotwordCommand by HotwordService.lastCommand.collectAsState()
    val hotwordReply by HotwordService.lastReply.collectAsState()
    val matchScore by HotwordService.voiceMatchScore.collectAsState()
    val hotwordEngine by HotwordService.activeEngine.collectAsState()
    var wakeWordField by remember(refreshTick) { mutableStateOf(HotwordService.wakeWord(context)) }
    var hotwordChecked by remember(refreshTick) {
        mutableStateOf(HotwordService.isEnabled(context) || hotwordRunning)
    }
    var ttsChecked by remember(refreshTick) { mutableStateOf(HotwordService.ttsReplyEnabled(context)) }
    var voiceMatchChecked by remember(refreshTick) {
        mutableStateOf(HotwordService.voiceMatchEnabled(context))
    }

    // ---- voice print state ----
    val enrolled = remember(refreshTick) { store.isEnrolled() }
    val sampleCount = remember(refreshTick) { store.sampleCount() }
    val thresholdValue = remember(refreshTick) { store.threshold() }
    var enrollPhase by remember { mutableStateOf<String?>(null) } // countdown|recording|computing
    var countdown by remember { mutableIntStateOf(3) }
    var voicePrintMessage by remember { mutableStateOf<String?>(null) }
    var testScore by remember { mutableStateOf<Float?>(null) }
    var testVerdict by remember { mutableStateOf<Boolean?>(null) }
    // Which recording mode is active (set before starting the countdown).
    var enrollMode by remember { mutableStateOf("enroll") }

    // ---- assistant role state ----
    var roleHeld by remember { mutableStateOf(isAssistantRoleHeld(context)) }

    // ---- local AI (Cactus Needle 3) state ----
    var needleTick by remember { mutableIntStateOf(0) }
    val needleDownload by NeedleModelManager.downloadState.collectAsState()
    val needleSupported = remember(needleTick) { NeedleRuntime.isSupported() }
    val needleSupportError = remember(needleTick) { NeedleRuntime.supportError() }
    val needleRuntimeError = remember(needleTick) { NeedleRuntime.lastErrorMessage }
    val needleDownloaded = remember(needleTick) { NeedleModelManager.isNeedleDownloaded(context) }
    val whistleDownloaded = remember(needleTick) { NeedleModelManager.isWhistleDownloaded(context) }
    val needleLoaded = remember(needleTick) { NeedleRuntime.isTextModelLoaded() }
    val whistleLoaded = remember(needleTick) { NeedleRuntime.isSpeechModelLoaded() }
    var assistantAiChecked by remember(needleTick) {
        mutableStateOf(NeedleModelManager.isAssistantEnabled(context))
    }
    var whistleDictChecked by remember(needleTick) {
        mutableStateOf(NeedleModelManager.isWhistleDictationEnabled(context))
    }

    LaunchedEffect(Unit) {
        // PERF (regresión reportada): entrar aquí NO debe cargar los modelos.
        // ensureLoaded() cargaba needle3+whistle y re-tokenizaba el catálogo
        // completo — segundos de bloqueo, además encolado detrás del stream
        // del hotword (1 pasada/s por el mismo hilo del motor). Ahora solo
        // refrescamos el registro de herramientas personalizadas y leemos el
        // estado ACTUAL; los self-tests cargan modelos bajo demanda.
        CustomToolRegistry.refresh(context)
        needleTick++
    }

    // Declared EARLY because the SAF transfer launchers below call it from
    // their result callbacks (local functions must precede their first use).
    fun refreshAll() { refreshTick++ }

    // ---- integrated self-tests (Needle / Whistle) ----
    var needleTestState by remember { mutableStateOf<String?>(null) } // running|pass|fail
    var needleTestDetail by remember { mutableStateOf<String?>(null) }
    var whistleTestState by remember { mutableStateOf<String?>(null) }
    var whistleTestDetail by remember { mutableStateOf<String?>(null) }
    val whistleTestController = remember { WhistleDictationController() }
    val whistleTestRecording by whistleTestController.isRecording.collectAsState()

    // ---- model backup / restore via SAF ----
    var transferAction by remember { mutableStateOf<String?>(null) }

    fun handleTransferResult(result: ModelTransferManager.TransferResult) {
        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        if (result.ok) needleTick++
    }

    val exportModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val action = transferAction
        transferAction = null
        if (uri != null && action != null) {
            scope.launch { handleTransferResult(ModelTransferManager.exportModel(context, action, uri)) }
        }
    }
    val exportVoicePrintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        transferAction = null
        if (uri != null) {
            scope.launch { handleTransferResult(ModelTransferManager.exportVoicePrint(context, uri)) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val action = transferAction
        transferAction = null
        if (uri != null && action != null) {
            scope.launch {
                val result = when (action) {
                    "import_voiceprint" -> ModelTransferManager.importVoicePrint(context, uri)
                    else -> ModelTransferManager.importModel(context, action, uri)
                }
                handleTransferResult(result)
                refreshAll()
            }
        }
    }

    fun runNeedleTest() {
        when {
            !needleSupported -> {
                needleTestState = "fail"
                needleTestDetail = "Este teléfono no tiene motor nativo para el modelo."
            }
            !needleDownloaded -> {
                needleTestState = "fail"
                needleTestDetail = "Descarga primero el modelo Needle 3."
            }
            else -> {
                needleTestState = "running"
                needleTestDetail = null
                scope.launch {
                    val started = System.currentTimeMillis()
                    var pass = false
                    var detail: String
                    try {
                        val raw = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                            NeedleRuntime.completeText("enciende la linterna")
                        }
                        val parsed = raw?.let { NeedleTools.parseResponse(it) }
                        val call = parsed?.calls?.firstOrNull()
                        pass = call != null && call.name == "linterna"
                        detail = if (parsed == null) {
                            "El motor no devolvió respuesta (¿memoria?)."
                        } else {
                            "Herramienta: ${call?.name ?: "(ninguna)"} · confianza " +
                                "${(parsed.confidence * 100).toInt()}% · " +
                                "${"%.1f".format((System.currentTimeMillis() - started) / 1000.0)} s"
                        }
                    } catch (t: Throwable) {
                        detail = t.message ?: t.javaClass.simpleName
                    }
                    needleTestDetail = detail
                    needleTestState = if (pass) "pass" else "fail"
                    needleTick++
                }
            }
        }
    }

    /**
     * The Whistle self-test with REAL feedback at every phase — the whole
     * point of a test is knowing whether the model loaded, errored or just
     * ran slowly, instead of an endless spinner.
     */
    fun beginWhistleTest() {
        whistleTestState = "running"
        whistleTestDetail = "Cargando el modelo de voz desde el almacenamiento…"
        scope.launch {
            val speechLoaded = try {
                NeedleModelManager.ensureLoaded(context)
                NeedleRuntime.isSpeechModelLoaded()
            } catch (_: Exception) {
                false
            }
            if (!speechLoaded) {
                whistleTestDetail = "El modelo NO cargó: " +
                        (NeedleRuntime.lastErrorMessage ?: "el motor no reportó la causa") +
                        " · archivo: ${NeedleModelManager.whistleFile(context).length() / (1024 * 1024)} MB"
                whistleTestState = "fail"
                needleTick++
                return@launch
            }
            whistleTestDetail = "Modelo cargado ✔ (máscara ${NeedleRuntime.loadedModels()}). " +
                    "Grabando 3 s — habla ahora…"
            whistleTestController.start()
        }
    }

    val whistleTestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginWhistleTest()
        } else {
            whistleTestState = "fail"
            whistleTestDetail = "Sin micrófono no puedo probar Whistle."
        }
    }

    fun runWhistleTest() {
        when {
            !needleSupported -> {
                whistleTestState = "fail"
                whistleTestDetail = "Este teléfono no tiene motor nativo para el modelo."
            }
            !whistleDownloaded -> {
                whistleTestState = "fail"
                whistleTestDetail = "Descarga primero el modelo Whistle."
            }
            else -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    beginWhistleTest()
                } else {
                    whistleTestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    // Auto-stop the Whistle test after 3 seconds and transcribe it, with
    // LIVE progress: inference on slow phones takes tens of seconds and the
    // old UI stayed silently spinning.
    LaunchedEffect(whistleTestRecording) {
        if (whistleTestRecording) {
            delay(3000)
            val pcm = whistleTestController.stop()
            val started = System.currentTimeMillis()
            val ticker = launch {
                while (true) {
                    val elapsed = (System.currentTimeMillis() - started) / 1000.0
                    whistleTestDetail =
                        "Transcribiendo con Whistle en tu teléfono… ${"%.0f".format(elapsed)} s " +
                                "(en móviles lentos puede tardar)"
                    delay(500)
                }
            }
            val raw = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                NeedleRuntime.transcribe(pcm, "es")
            }
            ticker.cancel()
            val parsed = raw?.let { r ->
                try { org.json.JSONObject(r) } catch (_: Exception) { null }
            }
            val text = parsed?.optString("text")?.trim() ?: ""
            val lang = parsed?.optString("language")?.trim() ?: ""
            val ttft = parsed?.optDouble("ttft_ms", 0.0) ?: 0.0
            val tps = parsed?.optDouble("decode_tps", 0.0) ?: 0.0
            val secs = (System.currentTimeMillis() - started) / 1000.0
            whistleTestDetail = when {
                raw == null ->
                    "El motor no respondió en 30 s (móvil muy lento o motor ocupado). " +
                            "Reintenta con la app recién abierta."
                text.isBlank() ->
                    "El motor respondió (${"%.1f".format(secs)} s) pero no detectó voz: " +
                            "habla más fuerte y repite."
                else ->
                    "Escuchado: «$text» · idioma ${lang.ifBlank { "?" }} · 1ª palabra ${"%.2f".format(ttft / 1000.0)} s · ${"%.0f".format(tps)} tok/s · total ${"%.1f".format(secs)} s"
            }
            // PASS = the engine ran end-to-end on this device; an empty
            // transcript is reported honestly rather than scored as success.
            whistleTestState = if (raw != null) "pass" else "fail"
            needleTick++
        }
    }

    // Refresh role state every time the screen resumes (user may have
    // changed the default assistant in system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                roleHeld = isAssistantRoleHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ------------------------------------------------------------------
    // Permission launchers
    // ------------------------------------------------------------------

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hotwordChecked = true
            HotwordService.setEnabled(context, true)
            HotwordService.start(context)
        } else {
            hotwordChecked = false
            Toast.makeText(context, "Sin micrófono no hay hotword ni Voice Match", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start either way: the service is valid without notification
        // visibility, it just won't show its ongoing card on Android 13+.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            hotwordChecked = true
            HotwordService.setEnabled(context, true)
            HotwordService.start(context)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleHeld = isAssistantRoleHeld(context)
        Toast.makeText(
            context,
            if (roleHeld) "Mnemosyne es ahora tu asistente" else "No se cambió el asistente",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ------------------------------------------------------------------
    // Recording flows (enrollment / verification)
    // ------------------------------------------------------------------

    // While recording a sample the hotword loop must release the microphone,
    // otherwise the enrollment AudioRecord never initializes — THAT was the
    // "se queda sin inscribir" bug.
    var hotwordWasRunning by remember { mutableStateOf(false) }

    fun startVoiceRecording(mode: String) {
        hotwordWasRunning = HotwordService.isRunning.value
        if (hotwordWasRunning) {
            HotwordService.stop(context)
        }
        enrollMode = mode
        testScore = null
        testVerdict = null
        enrollPhase = "${mode}-countdown"
    }

    fun finishVoiceRecording() {
        if (hotwordWasRunning && HotwordService.isEnabled(context)) {
            HotwordService.start(context)
        }
        hotwordWasRunning = false
    }

    val voiceEnrollPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording("enroll")
        } else {
            voicePrintMessage = "Sin el permiso de micrófono no puedo grabar tu huella de voz."
        }
    }

    fun runRecordingSample(durationMs: Long, onSample: (ShortArray?, String) -> Unit) {
        VoiceSampleRecorder.record(durationMs) { result ->
            scope.launch(Dispatchers.Main) { onSample(result.samples, result.reason) }
        }
    }

    fun handleEnrollSample(samples: ShortArray?, reason: String) {
        if (samples == null) {
            enrollPhase = null
            voicePrintMessage = "No pude grabar la muestra: $reason. Intenta de nuevo."
            finishVoiceRecording()
            return
        }
        enrollPhase = "computing"
        scope.launch(Dispatchers.Default) {
            val embedding = VoicePrintEngine.computeEmbedding(samples)
            launch(Dispatchers.Main) {
                enrollPhase = null
                if (embedding == null) {
                    voicePrintMessage =
                        "La muestra salió muy corta o silenciosa: di una frase completa " +
                                "(«hola mnemosyne, buenos días») cerca del micrófono."
                } else if (!store.isEnrolled()) {
                    store.startEnrollment(embedding)
                    voicePrintMessage = "¡Huella creada! Graba otra muestra para reforzarla."
                } else {
                    store.addSample(embedding)
                    voicePrintMessage = "Muestra ${store.sampleCount()} guardada."
                }
                testScore = null
                testVerdict = null
                finishVoiceRecording()
                refreshAll()
            }
        }
    }

    fun handleTestSample(samples: ShortArray?, reason: String) {
        if (samples == null) {
            enrollPhase = null
            voicePrintMessage = "No pude grabar la prueba: $reason."
            finishVoiceRecording()
            return
        }
        enrollPhase = "computing"
        scope.launch(Dispatchers.Default) {
            val embedding = VoicePrintEngine.computeEmbedding(samples)
            val verdict = embedding?.let { store.verifyVerdict(it) }
            launch(Dispatchers.Main) {
                enrollPhase = null
                testScore = verdict?.first
                testVerdict = verdict?.second
                voicePrintMessage = when {
                    verdict == null -> "No hay huella de voz inscrita todavía."
                    verdict.second -> "Coincide: es tu voz (similitud ${(verdict.first * 100).toInt()}%)."
                    else -> "NO coincide (similitud ${(verdict.first * 100).toInt()}%): " +
                            "otra voz o condiciones distintas."
                }
                finishVoiceRecording()
                refreshAll()
            }
        }
    }

    // Countdown → record → process, for both enrollment and testing.
    LaunchedEffect(enrollPhase) {
        when (enrollPhase) {
            "enroll-countdown" -> {
                countdown = 3
                repeat(3) {
                    delay(1000)
                    countdown--
                }
                delay(400)
                enrollPhase = "recording"
            }

            "test-countdown" -> {
                countdown = 3
                repeat(3) {
                    delay(1000)
                    countdown--
                }
                delay(400)
                enrollPhase = "recording"
            }

            "recording" -> {
                runRecordingSample(durationMs = 3200) { samples, reason ->
                    when {
                        enrollMode == "enroll" -> handleEnrollSample(samples, reason)
                        else -> handleTestSample(samples, reason)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Offline support check
    // ------------------------------------------------------------------

    fun checkOfflineSupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            offlineSupport = null
            offlineMessage = "Este Android es anterior a 13: la descarga directa de modelos " +
                    "no existe; usa el botón de abajo para abrir el ajuste del sistema."
            return
        }
        offlineCheckInProgress = true
        engineManager.checkOfflineSupport(
            onResult = { support ->
                offlineSupport = support
                offlineCheckInProgress = false
                offlineMessage = null
            },
            onError = { code ->
                offlineCheckInProgress = false
                offlineMessage = "El motor respondió con error $code al consultar los modelos."
            }
        )
    }

    LaunchedEffect(Unit) { checkOfflineSupport() }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Voz y asistente",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Motor, modelos sin conexión, hotword y Voice Match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ============ 1. Recognition engine ============
        SettingsCard(
            icon = { Icon(Icons.Default.RecordVoiceOver, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Motor de reconocimiento",
            badge = {
                BadgePill(text = resolvedEngine.label, color = GoogleGreen)
            }
        ) {
            Text(
                text = "El dictado usa el motor que elijas aquí. En los Samsung el " +
                        "predeterminado del sistema es «Samsung Voice Input», el mismo motor " +
                        "que usa el teclado de voz de Gboard — por eso las voces sonaban a " +
                        "Gboard. Si quieres las voces de Google, elige «Speech Services by " +
                        "Google»; si prefieres las de Samsung, elige el suyo. «Automático» " +
                        "prioriza el motor en el dispositivo y luego el de Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showEngineDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("engine_picker_btn")
            ) {
                Text("Elegir motor (${engines.size} disponibles)")
            }
            engineManager.lastError.value?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // ============ 2. Offline voice models ============
        SettingsCard(
            icon = { Icon(Icons.Default.CloudOff, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Modelos de voz sin conexión",
            badge = {
                val installed = offlineSupport?.let { s ->
                    s.installed.any { it == localeTag || it.startsWith(localeTag.substringBefore('-')) }
                }
                if (installed == true) BadgePill("Instalado", GoogleGreen)
                else BadgePill(
                    if (offlineSupport == null) "Android 12−" else "Sin instalar",
                    Color(0xFFB3261E)
                )
            }
        ) {
            Text(
                text = "Idioma del teléfono: $localeTag.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            offlineSupport?.let { s ->
                Text(
                    text = buildString {
                        append("Instalados: ")
                        append(if (s.installed.isEmpty()) "—" else s.installed.joinToString())
                        append("\nPendientes: ")
                        append(if (s.pending.isEmpty()) "—" else s.pending.joinToString())
                        append("\nDescargables: ")
                        append(if (s.downloadable.isEmpty()) "—" else s.downloadable.joinToString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (offlineCheckInProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Consultando al motor…", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            offlineMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        engineManager.triggerOfflineModelDownload(
                            onScheduled = {
                                offlineMessage = "Descarga PEDIDA al motor. La baja «Servicios de " +
                                        "Voz de Google» en segundo plano; reconsulta en un rato " +
                                        "(puede tardar y necesita Wi-Fi o datos)."
                                scope.launch { delay(3000); checkOfflineSupport() }
                            },
                            onUnavailable = {
                                offlineMessage = "En este Android la app no puede pedir la " +
                                        "descarga: abre el ajuste del sistema con el botón de al lado."
                            }
                        )
                    },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.weight(1f).testTag("download_offline_model_btn")
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Descargar", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        val ok = engineManager.openOfflineModelsSettings()
                        if (!ok) Toast.makeText(context, "No encontré el ajuste de voz", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajustes", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "¿Se pueden descargar los modelos de Google desde la app? En Android 13+ " +
                        "SÍ: el botón «Descargar» se lo pide directamente al motor. En Android 12 " +
                        "o menos solo se puede desde el ajuste del sistema. Sobre tu caso: los " +
                        "modelos offline pertenecen a «Servicios de Voz de Google»; cuando la " +
                        "app de Google se actualiza puede retirar los modelos de la versión " +
                        "anterior (por eso dejaban de funcionar tras actualizar y solo volvían " +
                        "al desinstalar actualizaciones). Volver a descargarlos — desde aquí o " +
                        "desde el ajuste del sistema — los deja disponibles de nuevo sin " +
                        "desinstalar nada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { checkOfflineSupport() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reconsultar")
            }
        }

        // ============ 2.5 Local AI (Cactus Needle 3, optional download) ============
        SettingsCard(
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "IA local opcional — Cactus Needle 3",
            badge = {
                when {
                    !needleSupported -> BadgePill("No soportado", Color(0xFFB3261E))
                    needleDownloaded && needleLoaded -> BadgePill("Activa", GoogleGreen)
                    needleDownloaded -> BadgePill("Descargado", GoogleGreen)
                    else -> BadgePill("No instalado", MaterialTheme.colorScheme.outline)
                }
            }
        ) {
            Text(
                text = "Needle 3 es un modelo de IA de 35 MB hecho para móviles: entiende lo " +
                        "que pides, elige la herramienta correcta del asistente y rellena los " +
                        "argumentos — todo dentro del teléfono, sin nube. Es opcional: " +
                        "descárgalo con el botón y pruébalo; el asistente funciona igual sin él " +
                        "(con el analizador de reglas). Coste real: ~80 MB de RAM mientras " +
                        "responde y de 1 a 8 s por respuesta según el teléfono. Nada se " +
                        "descarga solo ni se envía nada afuera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!needleSupported) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Este procesador no tiene motor nativo disponible (el motor de " +
                            "Cactus se compila para ARM de 32 y 64 bits). " +
                            (needleSupportError ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            needleDownload?.let { dl ->
                Spacer(modifier = Modifier.height(10.dp))
                val label = if (dl.model == NeedleModelManager.NEEDLE_FILE) "Needle 3" else "Whistle"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Descargando $label…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(dl.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = ForestPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { dl.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${NeedleModelManager.formatBytes(dl.receivedBytes)} / " +
                            NeedleModelManager.formatBytes(dl.totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                dl.error?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { NeedleModelManager.cancelDownload() }) {
                    Text("Cancelar descarga")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = NeedleModelManager.download(context, NeedleModelManager.NEEDLE_FILE)
                            needleTick++
                            if (!ok) Toast.makeText(context, "No se pudo descargar el modelo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = needleSupported && !needleDownloaded && needleDownload == null
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modelo Needle 3 · 35 MB")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = NeedleModelManager.download(context, NeedleModelManager.WHISTLE_FILE)
                            needleTick++
                            if (!ok) Toast.makeText(context, "No se pudo descargar Whistle", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = needleSupported && !whistleDownloaded && needleDownload == null
                ) {
                    Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Whistle (voz a texto) · 17 MB")
                }
            }
            if (whistleDownloaded) {
                Text(
                    text = "Whistle transcribe español en local; con él, el micrófono del " +
                            "asistente va directo del audio a la acción sin pasar por el " +
                            "reconocedor del sistema.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Usar la IA local en el asistente", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (needleLoaded) "Modelo cargado y listo" else "Se activa al descargar el modelo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = assistantAiChecked,
                    onCheckedChange = {
                        assistantAiChecked = it
                        NeedleModelManager.setAssistantEnabled(context, it)
                    },
                    enabled = needleDownloaded
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dictado del asistente con Whistle", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (whistleLoaded) "Modelo de voz cargado" else "Requiere descargar Whistle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = whistleDictChecked,
                    onCheckedChange = {
                        whistleDictChecked = it
                        NeedleModelManager.setWhistleDictationEnabled(context, it)
                    },
                    enabled = whistleDownloaded
                )
            }
            needleRuntimeError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aviso del motor: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (needleDownloaded || whistleDownloaded) {
                TextButton(
                    onClick = {
                        scope.launch {
                            NeedleModelManager.delete(context, null)
                            needleTick++
                        }
                    }
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Borrar los modelos descargados")
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Copia de seguridad local: exporta los archivos del modelo y " +
                            "reimpórtalos cuando quieras (otro teléfono, o si el servidor " +
                            "retirara las descargas). Los archivos no caducan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            transferAction = NeedleModelManager.NEEDLE_FILE
                            exportModelLauncher.launch("needle3.cact")
                        },
                        enabled = needleDownloaded,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exportar Needle", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            transferAction = NeedleModelManager.NEEDLE_FILE
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Importar Needle", fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            transferAction = NeedleModelManager.WHISTLE_FILE
                            exportModelLauncher.launch("whistle.cact")
                        },
                        enabled = whistleDownloaded,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exportar Whistle", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            transferAction = NeedleModelManager.WHISTLE_FILE
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Importar Whistle", fontSize = 11.sp)
                    }
                }
            }
        }

        // ============ 2.55 Assistant tools (per-tool gating) ============
        SettingsCard(
            icon = { Icon(Icons.Default.Tune, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Herramientas del asistente",
            badge = {
                val active = remember(needleTick) {
                    NeedleTools.TOOL_CATALOG.count { NeedleTools.isToolEnabled(context, it.id) }
                }
                BadgePill("$active / ${NeedleTools.TOOL_CATALOG.size}", ForestPrimary)
            }
        ) {
            Text(
                text = "Needle es un modelo pequeño y su ventana de contexto es limitada: cada " +
                        "herramienta activa ocupa tokens de esa ventana (el «context bloat» que " +
                        "también degrada a los modelos grandes). Las herramientas desactivadas NO " +
                        "se le declaran: el enrutador gana precisión y velocidad. Enciende lo que " +
                        "uses, apaga lo que no.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (needleDownloaded) {
                Text(
                    text = if (NeedleRuntime.staticPrefixTokens > 0) {
                        "Prefijo estático medido por el motor: ${NeedleRuntime.staticPrefixTokens} tokens."
                    } else {
                        "El motor medirá el prefijo al inicializar."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Optimistic overrides: the switch moves INSTANTLY; the engine
            // re-declares the catalogue in the background (it may queue
            // behind the hotword stream — that used to make the switches
            // look dead for seconds).
            var toolOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
            NeedleTools.TOOL_CATALOG.forEach { spec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            spec.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = toolOverrides[spec.id]
                            ?: NeedleTools.isToolEnabled(context, spec.id),
                        onCheckedChange = { checked ->
                            toolOverrides = toolOverrides + (spec.id to checked)
                            NeedleTools.setToolEnabled(context, spec.id, checked)
                            needleTick++ // instant UI feedback
                            scope.launch {
                                try {
                                    NeedleRuntime.applyToolGating(context)
                                } catch (_: Exception) {
                                }
                                needleTick++
                            }
                        }
                    )
                }
            }
            needleRuntimeError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aviso del motor: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = {
                NeedleTools.resetGating(context)
                toolOverrides = emptyMap()
                needleTick++
                scope.launch {
                    try {
                        NeedleRuntime.applyToolGating(context)
                    } catch (_: Exception) {
                    }
                    needleTick++
                }
            }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restaurar los valores por defecto")
            }
        }

        // ============ 2.56 Custom (user-made) tools ============
        CustomToolsCard(
            context = context,
            scope = scope,
            needleTick = needleTick,
            onChanged = {
                scope.launch {
                    CustomToolRegistry.refresh(context)
                    try {
                        NeedleRuntime.applyToolGating(context)
                    } catch (_: Exception) {
                    }
                    needleTick++
                }
            }
        )

        // ============ 2.6 Integrated self-tests ============
        SettingsCard(
            icon = { Icon(Icons.Default.Science, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Pruebas integradas de la IA local",
            badge = {
                val anyFail = needleTestState == "fail" || whistleTestState == "fail"
                val allPass = needleTestState == "pass" || whistleTestState == "pass"
                when {
                    anyFail -> BadgePill("Revisar", MaterialTheme.colorScheme.error)
                    allPass -> BadgePill("OK", GoogleGreen)
                    else -> BadgePill("Sin correr", MaterialTheme.colorScheme.outline)
                }
            }
        ) {
            Text(
                text = "Comprueba que Needle y Whistle funcionan DE VERDAD en este " +
                        "teléfono, con una prueba reproducible: el router debe devolver la " +
                        "herramienta correcta para «enciende la linterna», y Whistle debe " +
                        "transcribir 3 segundos de tu voz sin pasar por Google ni Gboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            @Composable
            fun TestStateChip(state: String?) {
                when (state) {
                    "running" -> {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    "pass" -> BadgePill("CORRECTO", GoogleGreen)
                    "fail" -> BadgePill("FALLÓ", MaterialTheme.colorScheme.error)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Needle 3 (router de herramientas)", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = needleTestDetail ?: "Envía «enciende la linterna» al modelo local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TestStateChip(needleTestState)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { runNeedleTest() },
                    modifier = Modifier.testTag("needle_selftest_btn")
                ) {
                    Text("Probar", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Whistle (voz a texto local)", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = whistleTestDetail ?: "Graba 3 s de tu voz y transcribe en local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TestStateChip(whistleTestState)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { runWhistleTest() },
                    modifier = Modifier.testTag("whistle_selftest_btn")
                ) {
                    Text("Probar", fontSize = 12.sp)
                }
            }
        }

        // ============ 3. Hotword ============
        SettingsCard(
            icon = { Icon(Icons.Default.GraphicEq, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Hotword — «$wakeWordField»",
            badge = {
                if (hotwordRunning) {
                    BadgePill(if (hotwordListening) "Escuchando" else "Activo", GoogleGreen)
                } else {
                    BadgePill("Apagado", MaterialTheme.colorScheme.outline)
                }
            }
        ) {
            if (hotwordRunning) {
                Text(
                    text = "Motor de escucha: ${hotwordEngine.ifBlank { "decidiendo…" }}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = wakeWordField,
                onValueChange = { wakeWordField = it.take(40) },
                label = { Text("Palabra de activación") },
                supportingText = { Text("Dila seguida del comando: «$wakeWordField abre whatsapp»") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        HotwordService.setWakeWord(context, wakeWordField)
                        if (hotwordRunning) {
                            HotwordService.stop(context)
                            HotwordService.start(context)
                        }
                        Toast.makeText(context, "Palabra guardada: «${HotwordService.wakeWord(context)}»", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Guardar palabra")
                }
                OutlinedButton(
                    onClick = {
                        if (hotwordRunning) HotwordService.stop(context)
                        else {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                HotwordService.start(context)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    if (hotwordRunning) {
                        Text("Detener")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probar")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Escucha activa (servicio)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Servicio en primer plano con micrófono",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hotwordChecked,
                    onCheckedChange = { checked ->
                        hotwordChecked = checked
                        HotwordService.setEnabled(context, checked)
                        if (checked) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                HotwordService.start(context)
                            }
                        } else {
                            HotwordService.stop(context)
                        }
                    },
                    modifier = Modifier.testTag("hotword_switch")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hotwordStatus,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (hotwordPartial.isNotBlank()) {
                Text(
                    text = "«$hotwordPartial»",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hotwordCommand != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Último comando: «$hotwordCommand»", style = MaterialTheme.typography.labelMedium)
                hotwordReply?.let { r ->
                    Text("Respuesta: $r", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            matchScore?.let { s ->
                Text("Voice Match: ${(s * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Límites honestos: el hotword de bajo consumo tipo «Hey Google» corre en " +
                        "el chip DSP con APIs privilegiadas que Android YA NO expone a apps de " +
                        "terceros (AlwaysOnHotwordDetector salió del SDK público) — ni siquiera " +
                        "siendo el asistente del sistema. Este hotword es por software: escucha " +
                        "mientras el servicio esté vivo y Android permita el micrófono (consume " +
                        "más batería). Con la pantalla apagada, algunas ROMs lo pausan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============ 4. Voice Match ============
        SettingsCard(
            icon = { Icon(Icons.Default.Fingerprint, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Voice Match (huella de voz propia)",
            badge = {
                if (enrolled) BadgePill("Inscrito · $sampleCount muestras", GoogleGreen)
                else BadgePill("Sin inscribir", MaterialTheme.colorScheme.outline)
            }
        ) {
            Text(
                text = "La huella de voz de Google («Hey Google») es privilegiada y no está " +
                        "disponible para terceros. Esta es NUESTRA huella on-device: 12 " +
                        "coeficientes MFCC por tramo sonoro → embedding que se compara por " +
                        "similitud coseno. Es más débil que un modelo neuronal, pero es real " +
                        "y local: tras detectar el hotword se te pedirá repetir tu frase y " +
                        "solo tu voz ejecutará el comando.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (enrollPhase == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                startVoiceRecording("enroll")
                            } else {
                                voiceEnrollPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.weight(1f).testTag("voice_enroll_btn")
                    ) {
                        Icon(if (enrolled) Icons.Default.Refresh else Icons.Default.Mic, null,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (enrolled) "Añadir muestra" else "Inscribir mi voz", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                startVoiceRecording("test")
                            } else {
                                voiceEnrollPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = enrolled,
                        modifier = Modifier.weight(1f).testTag("voice_test_btn")
                    ) {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probar mi voz", fontSize = 12.sp)
                    }
                }
            } else {
                RecordingIndicator(
                    phase = enrollPhase ?: "",
                    countdown = countdown,
                    label = when (enrollMode) {
                        "test" -> "Prueba de voz"
                        else -> "Muestra de enrolamiento"
                    }
                )
            }

            testScore?.let { s ->
                Spacer(modifier = Modifier.height(8.dp))
                val good = testVerdict == true
                Surface(
                    color = (if (good) GoogleGreen else MaterialTheme.colorScheme.error).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Similitud ${(s * 100).toInt()}% · umbral ${(thresholdValue * 100).toInt()}% → " +
                                (if (good) "ACEPTADA" else "RECHAZADA"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (good) GoogleGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            voicePrintMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            if (enrolled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Sensibilidad del umbral", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "low" to "Sensible",
                        "medium" to "Media",
                        "high" to "Estricta"
                    ).forEach { (key, label) ->
                        val selected = store.threshold() == when (key) {
                            "low" -> VoicePrintStore.THRESHOLD_LOW
                            "high" -> VoicePrintStore.THRESHOLD_HIGH
                            else -> VoicePrintStore.THRESHOLD_MEDIUM
                        }
                        OutlinedButton(
                            onClick = { store.setThreshold(key); refreshAll() },
                            colors = if (selected) {
                                ButtonDefaults.outlinedButtonColors(contentColor = ForestPrimary)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verificar la voz tras el hotword", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Antes de ejecutar el comando debes repetir tu frase",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = voiceMatchChecked,
                    enabled = enrolled,
                    onCheckedChange = { checked ->
                        voiceMatchChecked = checked
                        HotwordService.setVoiceMatchEnabled(context, checked)
                    },
                    modifier = Modifier.testTag("voice_match_switch")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Responder por voz (TTS)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Lee en voz alta la respuesta del asistente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ttsChecked,
                    onCheckedChange = { checked ->
                        ttsChecked = checked
                        HotwordService.setTtsReplyEnabled(context, checked)
                    }
                )
            }
            if (enrolled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportVoicePrintLauncher.launch("mnemosyne-voiceprint.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar huella", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            transferAction = "import_voiceprint"
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Importar huella", fontSize = 11.sp)
                    }
                }
            }

            if (enrolled) {
                TextButton(onClick = {
                    store.clear()
                    HotwordService.setVoiceMatchEnabled(context, false)
                    voiceMatchChecked = false
                    voicePrintMessage = "Huella de voz borrada"
                    testScore = null
                    testVerdict = null
                    refreshAll()
                }) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Borrar huella de voz")
                }
            }
        }

        // ============ 5. System assistant role ============
        SettingsCard(
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
            title = "Asistente del sistema",
            badge = {
                if (roleHeld) BadgePill("Asistente activo", GoogleGreen)
                else BadgePill("No configurado", MaterialTheme.colorScheme.outline)
            }
        ) {
            Text(
                text = "Haz de Mnemosyne tu asistente predeterminado: el gesto de asistente " +
                        "(pulsación larga del inicio / deslizamiento) abrirá el asistente local " +
                        "en vez de Google Assistant o Bixby. Selecciona «Mnemosyne» en el " +
                        "diálogo del sistema. Puedes volver atrás cuando quieras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val rm = context.getSystemService(RoleManager::class.java)
                            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                                return@Button
                            }
                        } catch (_: Exception) {
                        }
                    }
                    // Android 9-11 (the assistant ROLE only exists on newer
                    // systems): "Assist & voice input" is the screen that
                    // actually lists VoiceInteractionServices; Default apps
                    // is the fallback when the ROM hides it.
                    val voiceInput = android.content.Intent(
                        android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (tryStart(context, voiceInput)) {
                        Toast.makeText(
                            context,
                            "Toca «App de asistencia» (o «Asistente digital») y elige «Asistente Mnemosyne»",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    try {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Toast.makeText(
                            context,
                            "Abre «Apps predeterminadas → App de asistencia» y elige Mnemosyne",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "No pude abrir el ajuste", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                modifier = Modifier.fillMaxWidth().testTag("request_assistant_role_btn")
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (roleHeld) "Cambiar / revisar asistente" else "Configurar como asistente")
            }
            Spacer(modifier = Modifier.height(6.dp))
            val currentAssistant = remember(refreshTick) { currentAssistantComponent(context) }
            Text(
                text = if (roleHeld) {
                    "✔ Activo: el gesto de asistente (pulsación larga del botón de inicio o el " +
                        "gesto configurado) abre Mnemosyne. El hotword de bajo consumo «Hey Google» " +
                        "sigue siendo privilegiado; el de esta app es el de software de la tarjeta " +
                        "de arriba."
                } else {
                    buildString {
                        append("En Android 11: Ajustes → Apps y notificaciones → Avanzado → Apps ")
                        append("predeterminadas → App de asistencia → «Asistente Mnemosyne» (o «Mnemosyne»). ")
                        append("Desde esta versión la app aparece por DOS vías: como servicio de ")
                        append("interacción de voz Y como app de asistencia clásica, así que debe ")
                        append("salir en la lista aunque la ROM tarde en refrescar. Si aun así no ")
                        append("aparece: reinicia el teléfono una vez tras instalar (algunas ROMs ")
                        append("cachear la lista) y vuelve a entrar en ese ajuste.")
                        currentAssistant?.let {
                            append("\nAsistente actual del sistema: ${it.substringAfterLast('.')}.")
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Motor de reconocimiento de voz") },
            text = {
                Column {
                    engines.forEach { engine: RecognitionEngineInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = engine.id == currentSelection,
                                onClick = {
                                    engineManager.selectEngine(engine.id)
                                    refreshAll()
                                    showEngineDialog = false
                                }
                            )
                            Column {
                                Text(engine.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    engine.sublabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (engines.size <= 2) {
                        Text(
                            "Pocas opciones: el sistema solo expone estos servicios de voz.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEngineDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

@Composable
private fun SettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    badge: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                badge()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RecordingIndicator(phase: String, countdown: Int, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            when (phase) {
                "countdown", "enroll-countdown", "test-countdown" -> {
                    Text(
                        text = "$countdown",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text("preparado(a) para hablar…", style = MaterialTheme.typography.bodySmall)
                }
                "recording" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grabando — di tu frase normal", fontWeight = FontWeight.SemiBold)
                    }
                }
                "computing" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculando huella de voz…")
                    }
                }
            }
        }
    }
}

private fun isAssistantRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            ) {
                return true
            }
        } catch (_: Exception) {
        }
    }
    // Android 9-11: the RoleManager may not know the assistant role, but the
    // system still records the active VoiceInteractionService in
    // Settings.Secure — read it directly so the status is honest everywhere.
    return currentAssistantComponent(context)?.contains(context.packageName) == true
}

/** The active system assistant as "package/class", or null when unset. */
private fun currentAssistantComponent(context: Context): String? = try {
    android.provider.Settings.Secure.getString(context.contentResolver, "assistant")
        ?.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}

private fun tryStart(context: Context, intent: android.content.Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: Exception) {
    false
}

// ----------------------------------------------------------------------
// Custom (user-made) tools — build your own Needle tools in-app
// ----------------------------------------------------------------------

@Composable
private fun CustomToolsCard(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    needleTick: Int,
    onChanged: () -> Unit
) {
    var tools by remember(needleTick) { mutableStateOf(CustomToolRegistry.all()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomTool?>(null) }

    SettingsCard(
        icon = { Icon(Icons.Default.Build, null, tint = ForestPrimary, modifier = Modifier.size(22.dp)) },
        title = "Tus propias herramientas",
        badge = {
            val active = tools.count { it.enabled }
            BadgePill("$active / ${tools.size} activas", ForestPrimary)
        }
    ) {
        Text(
            text = "Crea herramientas que el asistente local puede llamar por ti. " +
                "Reglas de oro (de la guía de diseño de herramientas para Needle): " +
                "UNA acción por herramienta; describe las ACCIONES que cubre, no una categoría; " +
                "cada argumento debe describir DÓNDE está el valor en lo que dices (p. ej. «el importe que mencione»); " +
                "si un valor no siempre se dice, márcalo opcional.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (tools.isEmpty()) {
            Text(
                "Aún no tienes herramientas propias. Ejemplo: «registrar gasto» con un " +
                    "parámetro importe → crea una nota con plantilla «Gasto: {{importe}}».",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        tools.forEach { tool ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${tool.name} · ${actionLabel(tool.action)} · ${CustomToolRegistry.parseParams(tool.paramsJson).size} parám.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = tool; showEditor = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(16.dp), tint = ForestPrimary)
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                com.example.data.local.AppDatabase.getInstance(context)
                                    .customToolDao().deleteById(tool.id)
                            } catch (_: Exception) {
                            }
                            onChanged()
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, "Eliminar",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = tool.enabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            try {
                                com.example.data.local.AppDatabase.getInstance(context)
                                    .customToolDao().update(tool.copy(enabled = checked))
                                CustomToolRegistry.refresh(context)
                            } catch (_: Exception) {
                            }
                            onChanged()
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { editing = null; showEditor = true },
            colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Crear herramienta", fontSize = 13.sp)
        }
    }

    if (showEditor) {
        CustomToolEditorDialog(
            context = context,
            scope = scope,
            initial = editing,
            onDismiss = { showEditor = false },
            onSaved = onChanged
        )
    }
}

private fun actionLabel(action: String): String = when (action) {
    "note" -> "crea nota"
    "task" -> "crea tarea"
    "event" -> "crea evento"
    "append_note" -> "anexa a nota"
    "open_url" -> "abre URL"
    else -> "responde texto"
}

@Composable
private fun CustomToolEditorDialog(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    initial: CustomTool?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var labelField by remember { mutableStateOf(initial?.label.orEmpty()) }
    var nameField by remember { mutableStateOf(initial?.name.orEmpty()) }
    var descField by remember { mutableStateOf(initial?.description.orEmpty()) }
    var action by remember { mutableStateOf(initial?.action ?: "note") }
    var configField by remember {
        mutableStateOf(initial?.actionConfig.orEmpty().ifBlank {
            if (initial == null) "{{texto}}" else ""
        })
    }
    var params by remember {
        mutableStateOf<List<CustomToolRegistry.Param>>(
            CustomToolRegistry.parseParams(initial?.paramsJson ?: "[]")
        )
    }
    var nameError by remember { mutableStateOf<String?>(null) }

    val typeCycle = listOf("texto" to "string", "número" to "integer", "sí/no" to "boolean")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva herramienta" else "Editar herramienta") },
        text = {
            Column {
                OutlinedTextField(
                    value = labelField,
                    onValueChange = { labelField = it },
                    label = { Text("Nombre visible (p. ej. «Registrar gasto»)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Nombre para el modelo (snake_case)") },
                    supportingText = { Text("Se genera del nombre visible si lo dejas vacío.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = descField,
                    onValueChange = { descField = it },
                    label = { Text("Descripción (las ACCIONES que cubre)") },
                    supportingText = {
                        Text("Ej.: «Registra un gasto cuando el usuario diga cuánto gastó y en qué».")
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Qué hace:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                listOf(
                    "note" to "Crear una nota",
                    "task" to "Crear una tarea",
                    "event" to "Crear un evento",
                    "append_note" to "Anexar a una nota",
                    "open_url" to "Abrir una URL",
                    "reply" to "Solo responder texto"
                ).forEach { (key, text) ->
                    TextButton(
                        onClick = { action = key },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (action == key) "●" else "○", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = configField,
                    onValueChange = { configField = it },
                    label = {
                        Text(
                            when (action) {
                                "open_url" -> "Plantilla de URL"
                                "append_note" -> "Título (o parte) de la nota objetivo"
                                else -> "Plantilla (1.ª línea = título)"
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            when (action) {
                                "open_url" -> "https://ejemplo.com/buscar?q={{consulta}}"
                                "append_note" -> "El texto renderizado se anexa a la nota más reciente con ese título."
                                else -> "Usa {{nombre_param}} donde vaya cada valor. Ej.: «Gasto: {{importe}} en {{lugar}}»"
                            }
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Parámetros (argumentos que el modelo rellena):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                params.forEachIndexed { idx, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = p.name,
                            onValueChange = { new -> params = params.toMutableList().also { it[idx] = p.copy(name = new) } },
                            label = { Text("nombre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = p.description,
                            onValueChange = { new -> params = params.toMutableList().also { it[idx] = p.copy(description = new) } },
                            label = { Text("descripción") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val nextType = typeCycle.map { it.second }
                                .let { cycle -> cycle[(cycle.indexOf(p.type) + 1) % cycle.size] }
                            params = params.toMutableList().also { it[idx] = p.copy(type = nextType) }
                        }) {
                            Text(
                                "tipo: ${typeCycle.firstOrNull { it.second == p.type }?.first ?: p.type}",
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = {
                            params = params.toMutableList().also { it[idx] = p.copy(required = !p.required) }
                        }) {
                            Text(if (p.required) "obligatorio ✓" else "opcional", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { params = params.filterIndexed { i, _ -> i != idx } }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, "Quitar", modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = {
                    params = params + CustomToolRegistry.Param(name = "", type = "string", description = "")
                }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir parámetro", fontSize = 12.sp)
                }
                nameError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = CustomToolRegistry.normalizeName(
                        nameField.ifBlank { labelField },
                        CustomToolRegistry.all(),
                        initial?.id ?: 0L
                    )
                    if (finalName == null) {
                        nameError = "Nombre inválido o ya usado (usa letras/números/guion_bajo)."
                        return@Button
                    }
                    if (descField.isBlank()) {
                        nameError = "La descripción es lo que el modelo lee: sin ella no sabrá cuándo usarla."
                        return@Button
                    }
                    scope.launch {
                        try {
                            val dao = com.example.data.local.AppDatabase.getInstance(context).customToolDao()
                            val tool = CustomTool(
                                id = initial?.id ?: 0L,
                                name = finalName,
                                label = labelField.ifBlank { finalName },
                                description = descField.trim(),
                                paramsJson = CustomToolRegistry.encodeParams(params),
                                action = action,
                                actionConfig = configField.trim(),
                                enabled = initial?.enabled ?: true
                            )
                            if (initial == null) dao.insert(tool) else dao.update(tool)
                            CustomToolRegistry.refresh(context)
                        } catch (_: Exception) {
                        }
                        onSaved()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
