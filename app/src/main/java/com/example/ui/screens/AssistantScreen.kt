package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.NeedleTools
import com.example.ai.needle.WhistleDictationController
import com.example.assistant.AssistantIntent
import com.example.assistant.AssistantParser
import com.example.semantic.MlKitAnalyzer
import com.example.semantic.NeedleEmbeddings
import com.example.semantic.SemanticSearchEngine
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One exchange in the assistant conversation log. */
data class AssistantMessage(
    val isUser: Boolean,
    val text: String,
    val imageUri: String? = null,
    val saveTitle: String? = null,
    val saveBody: String? = null,
    val saveImageUri: String? = null
)

/**
 * The on-device Assistant: speak or type a command in Spanish and it is
 * parsed and executed locally against phone capabilities, Mirage vision, and the semantic journal.
 */
@Composable
fun AssistantScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onOpenLens: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isDictating by viewModel.isDictating.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val realWeather by viewModel.realWeather.collectAsState()
    val deviceFiles by viewModel.deviceFilesResults.collectAsState()
    val deviceContacts by viewModel.deviceContactsResults.collectAsState()
    val hasContactsPermission = remember { viewModel.deviceSearchManager.hasContactsPermission() }
    val context = LocalContext.current

    var messages by remember { mutableStateOf(listOf(AssistantMessage(false, WELCOME_TEXT))) }
    var typedInput by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    var needleTick by remember { mutableIntStateOf(0) }
    val whistleController = remember { WhistleDictationController() }
    val whistleRecording by whistleController.isRecording.collectAsState()
    val needleReady = remember(needleTick) { NeedleRuntime.isReady() }
    val speechReady = remember(needleTick) { NeedleRuntime.isSpeechModelLoaded() }
    val needleSupported = remember(needleTick) { NeedleRuntime.isSupported() }
    val needleEnabled = remember(needleTick) { NeedleModelManager.isAssistantEnabled(context) }
    val whistleDictation = remember(needleTick) {
        NeedleModelManager.isWhistleDictationEnabled(context) &&
            NeedleModelManager.isWhistleDownloaded(context)
    }
    val needleUsable = needleSupported && needleEnabled && needleReady
    val whistleUsable = needleSupported && whistleDictation && speechReady

    LaunchedEffect(Unit) {
        NeedleModelManager.ensureLoaded(context)
        needleTick++
    }

    fun post(message: AssistantMessage) {
        messages = messages + message
    }

    fun analyzePhoto(uri: Uri) {
        scope.launch {
            val persistedUri = viewModel.persistImageToLocalStorage(uri)
            post(AssistantMessage(true, "Analiza esta foto", imageUri = persistedUri))
            thinking = true
            try {
                if (NeedleModelManager.isMirageDownloaded(context)) {
                    NeedleModelManager.ensureLoaded(context)
                }
                val mirage = if (NeedleRuntime.isVisionModelLoaded()) {
                    withTimeoutOrNull(25_000L) {
                        NeedleRuntime.describeImageFromUri(
                            context = context,
                            uri = Uri.parse(persistedUri),
                            language = "es"
                        )
                    }
                } else null
                val ml = MlKitAnalyzer.analyzeImageFromUri(context, Uri.parse(persistedUri))

                val reply = buildString {
                    if (mirage != null && !mirage.isEmpty()) {
                        if (mirage.description.isNotBlank()) {
                            append(mirage.description.trim())
                        }
                        val objs = mirage.mergedLabels(6)
                        if (objs.isNotEmpty()) {
                            if (isNotEmpty()) append("\n\n")
                            append("Objetos detectados: ").append(objs.joinToString(", "))
                        }
                        if (mirage.relations.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Escena: ").append(mirage.relations.take(3).joinToString(" · "))
                        }
                        val ocr = mirage.mergedOcrText().ifBlank { ml.ocrText }
                        if (ocr.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append("Texto leído (OCR): ").append(ocr.take(260))
                        }
                        if (ml.faceCount > 0) {
                            if (isNotEmpty()) append("\n")
                            append("Rostros: ${ml.faceCount}")
                        }
                        append("\n\n· Mirage (visión local)")
                    } else {
                        val hasMl = ml.labels.isNotEmpty() || ml.ocrText.isNotBlank() || ml.faceCount > 0
                        if (hasMl) {
                            if (ml.labels.isNotEmpty()) {
                                append("Elementos detectados: ").append(ml.labels.joinToString(", "))
                            }
                            if (ml.ocrText.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append("Texto leído (OCR): ").append(ml.ocrText.take(260))
                            }
                            if (ml.faceCount > 0) {
                                if (isNotEmpty()) append("\n")
                                append("Rostros detectados: ${ml.faceCount}")
                            }
                            append("\n\n· Visión local (ML Kit)")
                        } else {
                            append("No detecté texto ni objetos claros en esa imagen.")
                        }
                    }
                }

                val title = mirage?.mergedLabels(2)?.firstOrNull()
                    ?.replaceFirstChar { it.uppercase() }
                    ?.let { "Foto: $it" }
                    ?: "Análisis visual"

                post(
                    AssistantMessage(
                        isUser = false,
                        text = reply,
                        saveTitle = title,
                        saveBody = reply.substringBeforeLast("\n\n· ").trim(),
                        saveImageUri = persistedUri
                    )
                )
            } catch (_: Exception) {
                post(AssistantMessage(false, "No pude analizar esa imagen en este momento."))
            } finally {
                needleTick++
                thinking = false
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) analyzePhoto(uri)
    }

    val whistlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!whistleController.start()) {
                post(AssistantMessage(false, "No pude abrir el micrófono."))
            }
        } else {
            post(AssistantMessage(false, "Sin permiso de micrófono no puedo usar el dictado local."))
        }
    }

    fun startWhistleCapture() {
        if (WhistleDictationController.hasMicrophonePermission(context)) {
            if (!whistleController.start()) {
                post(AssistantMessage(false, "No pude abrir el micrófono."))
            }
        } else {
            whistlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    suspend fun resolveIntents(utterance: String): Pair<List<AssistantIntent>, Boolean> {
        if (needleUsable) {
            val routed = withTimeoutOrNull(25_000) { NeedleTools.route(utterance) }
            if (!routed.isNullOrEmpty()) return routed to true
        }
        return listOf(AssistantParser.parse(utterance)) to false
    }

    suspend fun runIntent(intent: AssistantIntent) {
        when (intent) {
            is AssistantIntent.OpenApp -> {
                val app = viewModel.assistantManager.findApp(intent.appName)
                if (app != null) {
                    viewModel.assistantManager.launchApp(app.packageName)
                    post(AssistantMessage(false, "Abriendo ${app.appName}…"))
                } else {
                    post(AssistantMessage(false, "No encontré una app llamada \"${intent.appName}\"."))
                }
            }
            is AssistantIntent.CallContact -> {
                if (!hasContactsPermission) {
                    post(AssistantMessage(false, "Necesito el permiso de contactos para llamar. Actívalo en Ajustes → Permisos."))
                } else {
                    val contact = viewModel.assistantManager.searchContact(intent.contactName)
                    if (contact?.phoneNumber != null) {
                        viewModel.assistantManager.dialContact(contact.phoneNumber)
                        post(AssistantMessage(false, "Llamando a ${contact.displayName}…"))
                    } else {
                        post(AssistantMessage(false, "No encontré a \"${intent.contactName}\" en tus contactos."))
                    }
                }
            }
            is AssistantIntent.SearchDevice -> {
                post(AssistantMessage(false, "Buscando \"${intent.query}\" en tu teléfono…"))
                viewModel.searchDevice(intent.query)
                kotlinx.coroutines.delay(700)
                val fileCount = viewModel.deviceFilesResults.value.size
                val contactCount = viewModel.deviceContactsResults.value.size
                if (fileCount == 0 && contactCount == 0) {
                    post(AssistantMessage(false, "No encontré \"${intent.query}\" en tu teléfono."))
                } else {
                    post(
                        AssistantMessage(
                            false,
                            "Encontré $fileCount archivo(s) y $contactCount contacto(s) para \"${intent.query}\". Mira los resultados debajo."
                        )
                    )
                }
            }
            is AssistantIntent.SearchWeb -> {
                viewModel.assistantManager.webSearch(intent.query)
                post(AssistantMessage(false, "Buscando \"${intent.query}\" en la web…"))
            }
            AssistantIntent.Weather -> {
                post(AssistantMessage(false, viewModel.assistantManager.describeWeather(realWeather)))
            }
            is AssistantIntent.CreateNote -> {
                viewModel.saveEntry(
                    id = 0L,
                    title = intent.text.take(40),
                    body = intent.text,
                    onComplete = { _ ->
                        post(AssistantMessage(false, "Nota guardada."))
                    }
                )
            }
            is AssistantIntent.Calculate -> {
                val value = AssistantParser.safeEvaluate(intent.expression)
                val reply = if (value == null) {
                    "No pude calcular eso."
                } else {
                    val pretty = if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        String.format(java.util.Locale.getDefault(), "%.4g", value)
                    }
                    pretty
                }
                post(AssistantMessage(false, reply))
            }
            is AssistantIntent.SetTimer -> {
                viewModel.assistantManager.startTimer(intent.minutes)
                post(AssistantMessage(false, "Temporizador de ${intent.minutes} min en marcha."))
            }
            is AssistantIntent.SetAlarm -> {
                viewModel.assistantManager.setAlarm(intent.hour, intent.minute)
                post(
                    AssistantMessage(
                        false,
                        "Alarma a las ${"%02d".format(intent.hour)}:${"%02d".format(intent.minute)}."
                    )
                )
            }
            is AssistantIntent.Flashlight -> {
                val ok = viewModel.assistantManager.setFlashlight(intent.on)
                post(
                    AssistantMessage(
                        false,
                        if (ok) {
                            if (intent.on) "Linterna encendida." else "Linterna apagada."
                        } else {
                            "No pude controlar la linterna en este dispositivo."
                        }
                    )
                )
            }
            AssistantIntent.Battery -> {
                post(AssistantMessage(false, viewModel.assistantManager.formatBattery()))
            }
            AssistantIntent.Lens -> {
                post(AssistantMessage(false, "Abriendo el Lens para escanear…"))
                onOpenLens()
            }
            is AssistantIntent.CloseApp -> {
                post(AssistantMessage(false, viewModel.assistantManager.closeApp(intent.appName)))
            }
            is AssistantIntent.SendMessage -> {
                post(AssistantMessage(false, viewModel.assistantManager.sendMessage(intent.contactName, intent.message)))
            }
            is AssistantIntent.CreateTask -> {
                post(AssistantMessage(false, viewModel.assistantManager.createTask(intent.title, intent.dueInMinutes)))
            }
            is AssistantIntent.CreateEvent -> {
                post(
                    AssistantMessage(
                        false,
                        viewModel.assistantManager.createEvent(
                            intent.title, intent.startInMinutes, intent.durationMinutes, intent.location
                        )
                    )
                )
            }
            AssistantIntent.NextEvent -> {
                post(AssistantMessage(false, viewModel.assistantManager.describeNextEvent()))
            }
            AssistantIntent.Steps -> {
                post(AssistantMessage(false, viewModel.assistantManager.stepsToday()))
            }
            is AssistantIntent.Volume -> {
                post(AssistantMessage(false, viewModel.assistantManager.setVolume(intent.level, intent.mode)))
            }
            is AssistantIntent.RecordVoiceNote -> {
                post(AssistantMessage(false, "Grabando ${intent.seconds} s de audio…"))
                post(AssistantMessage(false, viewModel.assistantManager.recordVoiceNote(intent.seconds, intent.title)))
            }
            is AssistantIntent.SaveCurrentPlace -> {
                post(AssistantMessage(false, viewModel.assistantManager.saveCurrentPlace(intent.name)))
            }
            is AssistantIntent.RunCustomTool -> {
                post(AssistantMessage(false, "Ejecutando tu herramienta «${intent.toolName}»…"))
                post(
                    AssistantMessage(
                        false,
                        viewModel.assistantManager.runCustomTool(intent.toolId, intent.toolName, intent.args)
                    )
                )
            }
            is AssistantIntent.Translate -> {
                val target = intent.targetLangHint ?: "en"
                val source = com.example.semantic.MlKitAnalyzer.identifyLanguage(intent.text)
                val result = com.example.semantic.MlKitAnalyzer.translateText(intent.text, source, target)
                val reply = result.fold(
                    onSuccess = { "\"${intent.text}\" → $it" },
                    onFailure = { "No pude traducir ahora mismo (¿sin conexión para descargar el modelo?)." }
                )
                post(AssistantMessage(false, reply))
            }
            AssistantIntent.Unknown -> {
                post(AssistantMessage(false, HELP_TEXT))
            }
        }
    }

    suspend fun tryAnswerFromJournal(command: String): String? {
        val text = command.lowercase(java.util.Locale.getDefault()).trim()
        val explicitTriggers = listOf(
            "qué escribí", "que escribí", "qué apunté", "que apunte", "qué anoté", "que anote",
            "qué dice mi diario", "que dice mi diario", "mi diario sobre", "mis notas sobre",
            "qué recuerdo", "que recuerdo", "qué sé sobre", "que se sobre", "busca en mi diario"
        )
        val isExplicit = explicitTriggers.any { text.contains(it) }
        val isNaturalQuestion = (text.contains('?') ||
            text.startsWith("qué ") || text.startsWith("que ") ||
            text.startsWith("cuándo ") || text.startsWith("cuando ") ||
            text.startsWith("dónde ") || text.startsWith("donde ") ||
            text.startsWith("quién ") || text.startsWith("quien ") ||
            text.startsWith("cómo ") || text.startsWith("como ") ||
            text.startsWith("cuál ") || text.startsWith("cual ")) &&
            AssistantParser.parse(command) is AssistantIntent.Unknown

        if (!isExplicit && !isNaturalQuestion) return null
        return try {
            val entries = viewModel.repository.allEntriesWithRelations.first().take(600)
            if (entries.isEmpty()) return null

            if (NeedleEmbeddings.isAvailable(context)) {
                val vectorHits = NeedleEmbeddings.search(context, command, entries, topK = 3)
                val bestCosine = vectorHits.firstOrNull()?.second ?: 0f
                if (vectorHits.isNotEmpty() && (isExplicit || bestCosine >= 0.78f)) {
                    val fragments = NeedleEmbeddings.ragFragments(context, command, entries, k = 3)
                    if (fragments.isNotEmpty()) {
                        val aiAnswer = withTimeoutOrNull(25_000) {
                            NeedleTools.answerFromJournal(command, fragments)
                        }
                        if (!aiAnswer.isNullOrBlank()) return aiAnswer
                        return "Encontré esto en tu diario:\n${fragments.take(2).joinToString("\n• ", prefix = "• ")}"
                    }
                }
            }

            if (isExplicit) {
                val lexical = SemanticSearchEngine.instantLexicalHits(command, entries, limit = 2)
                if (lexical.isNotEmpty()) {
                    return lexical.joinToString("\n• ", prefix = "En tus notas encontré:\n• ") { hit ->
                        val snippet = hit.snippet.ifBlank { hit.reason }
                        "«${hit.title}» — ${snippet.take(120)}"
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun execute(utterance: String, fromUser: Boolean = true) {
        val clean = utterance.trim()
        if (clean.isBlank()) return
        if (fromUser) post(AssistantMessage(true, clean))
        thinking = true
        scope.launch {
            val memory = tryAnswerFromJournal(clean)
            if (memory != null) {
                post(AssistantMessage(false, "$memory · IA local (memoria del diario)"))
                thinking = false
                return@launch
            }
            val (intents, viaNeedle) = resolveIntents(clean)
            val suffix = if (viaNeedle) " · IA local" else ""
            post(AssistantMessage(false, describe(intents.first()) + suffix))
            for (intent in intents) runIntent(intent)
            needleTick++
            thinking = false
        }
    }

    fun submit(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        post(AssistantMessage(true, clean))
        typedInput = ""
        execute(clean, fromUser = false)
    }

    fun startVoiceInput() {
        if (whistleUsable) {
            startWhistleCapture()
        } else {
            viewModel.startDictation(
                onResult = { recognized -> submit(recognized) },
                onError = {
                    post(AssistantMessage(false, "No pude escucharte. ¿Hay un servicio de voz en el dispositivo?"))
                }
            )
        }
    }

    fun stopWhistleAndProcess() {
        val pcm = whistleController.stop()
        if (pcm.isEmpty()) {
            post(AssistantMessage(false, "No capté audio."))
            return
        }
        thinking = true
        scope.launch {
            var transcript = ""
            var calls: List<AssistantIntent> = emptyList()

            val keywords = com.example.ai.needle.WhistleKeywordBuilder.forAssistant(context)
            if (NeedleRuntime.isReady()) {
                val raw = withTimeoutOrNull(45_000) {
                    NeedleRuntime.completeAudio(pcm, "es", keywords = keywords)
                }
                val parsed = raw?.let { NeedleTools.parseResponse(it) }
                transcript = parsed?.audioText.orEmpty()
                calls = parsed
                    ?.takeIf { it.calls.isNotEmpty() && it.confidence >= NeedleTools.MIN_CONFIDENCE }
                    ?.let { p -> p.calls.mapNotNull { NeedleTools.callToIntent(it) } }
                    .orEmpty()
            } else {
                val parsed = withTimeoutOrNull(60_000) {
                    NeedleRuntime.transcribeLongAudio(pcm, "es", keywords = keywords)
                }
                transcript = parsed?.text.orEmpty()
            }

            if (transcript.isNotBlank()) {
                post(AssistantMessage(true, transcript))
            }
            if (calls.isNotEmpty()) {
                post(AssistantMessage(false, describe(calls.first()) + " · IA local"))
                for (intent in calls) runIntent(intent)
            } else if (transcript.isNotBlank()) {
                execute(transcript, fromUser = false)
            } else {
                post(AssistantMessage(false, "No te entendí (silencio o ruido)."))
            }
            thinking = false
        }
    }

    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
    val glassBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("assistant_screen")
    ) {
        GlassTopBar(
            title = "Asistente",
            subtitle = if (needleUsable) {
                "100% en tu dispositivo · IA local activa"
            } else {
                "100% en tu dispositivo · sin nube"
            },
            onBack = onBack
        )

        // Conversation
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    onSaveMemory = { title, body, imgUri ->
                        viewModel.saveEntry(
                            id = 0L,
                            title = title,
                            body = body,
                            attachedImageUri = imgUri?.let { Uri.parse(it) },
                            onComplete = { newId ->
                                Toast.makeText(context, "Guardado en tu diario", Toast.LENGTH_SHORT).show()
                                onNavigateToDetail(newId)
                            }
                        )
                    }
                )
            }
            if (thinking) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Pensando…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (deviceFiles.isNotEmpty()) {
                item {
                    Text(
                        "Archivos encontrados",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(deviceFiles.take(4), key = { "a_file_${it.id}" }) { file ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = glassSurface,
                        border = BorderStroke(1.dp, glassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.openDeviceFile(file) }
                    ) {
                        Text(
                            file.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            if (deviceContacts.isNotEmpty()) {
                item {
                    Text(
                        "Contactos encontrados",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NowBriefVitalsGreen,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(deviceContacts.take(4), key = { "a_contact_${it.id}" }) { contact ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = glassSurface,
                        border = BorderStroke(1.dp, glassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                contact.phoneNumber?.let { viewModel.assistantManager.dialContact(it) }
                            }
                    ) {
                        Text(
                            "${contact.displayName}  ·  ${contact.phoneNumber ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Frosted Glass Bottom Dock: suggestion chips + composer
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip("Analizar foto") {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    SuggestionChip("¿Qué tiempo hace?") { submit(it) }
                    SuggestionChip("Abre la linterna") { submit(it) }
                    SuggestionChip("Batería") { submit(it) }
                    SuggestionChip("Cuánto es 12 por 7") { submit(it) }
                    SuggestionChip("Apunta comprar café") { submit(it) }
                    SuggestionChip("Escanea esto") { submit(it) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !thinking,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("assistant_pick_photo")
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Analizar foto con Mirage",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = typedInput,
                        onValueChange = { typedInput = it },
                        placeholder = { Text("Pregunta o pide algo…", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit(typedInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (whistleRecording) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { stopWhistleAndProcess() }
                                .testTag("assistant_whistle_stop")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Procesar con IA local",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else if (isDictating) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { viewModel.stopDictation() }
                                .testTag("assistant_stop_mic")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Detener escucha",
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { startVoiceInput() }
                                .testTag("assistant_mic")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Hablar",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { submit(typedInput) },
                        enabled = typedInput.isNotBlank(),
                        modifier = Modifier.testTag("assistant_send")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = if (typedInput.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }

                if (whistleRecording) {
                    Text(
                        text = "Escuchando (IA local)… pulsa detener para procesar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                } else if (isDictating && partialTranscript.isNotBlank()) {
                    Text(
                        text = "Escuchando: $partialTranscript",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: AssistantMessage,
    onSaveMemory: (title: String, body: String, imageUri: String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            },
            border = if (message.isUser) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            },
            tonalElevation = if (message.isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!message.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Foto analizada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!message.isUser && !message.saveBody.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            onSaveMemory(
                                message.saveTitle ?: "Análisis visual",
                                message.saveBody,
                                message.saveImageUri
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar en diario", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(text) }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private fun describe(intent: AssistantIntent): String = when (intent) {
    is AssistantIntent.OpenApp -> "Buscando \"${intent.appName}\"…"
    is AssistantIntent.CloseApp -> "Cerrando \"${intent.appName}\"…"
    is AssistantIntent.CallContact -> "Buscando a \"${intent.contactName}\"…"
    is AssistantIntent.SendMessage -> "Preparando el mensaje…"
    is AssistantIntent.SearchDevice -> "Buscando \"${intent.query}\" en tu teléfono…"
    is AssistantIntent.SearchWeb -> "Buscando en la web…"
    AssistantIntent.Weather -> "Consultando el clima…"
    is AssistantIntent.CreateNote -> "Guardando tu nota…"
    is AssistantIntent.CreateTask -> "Creando la tarea…"
    is AssistantIntent.CreateEvent -> "Creando el evento…"
    AssistantIntent.NextEvent -> "Leyendo tu agenda…"
    AssistantIntent.Steps -> "Contando tus pasos…"
    is AssistantIntent.Volume -> "Ajustando el volumen…"
    is AssistantIntent.RecordVoiceNote -> "Preparando la grabación…"
    is AssistantIntent.SaveCurrentPlace -> "Guardando este lugar…"
    is AssistantIntent.Calculate -> "Calculando…"
    is AssistantIntent.SetTimer -> "Preparando el temporizador…"
    is AssistantIntent.SetAlarm -> "Preparando la alarma…"
    is AssistantIntent.Flashlight -> "Cambiando la linterna…"
    AssistantIntent.Battery -> "Leyendo la batería…"
    AssistantIntent.Lens -> "Abriendo el Lens…"
    is AssistantIntent.Translate -> "Traduciendo…"
    is AssistantIntent.RunCustomTool -> "Ejecutando tu herramienta «${intent.toolName}»…"
    AssistantIntent.Unknown -> "Perdona, no entendí."
}

private val WELCOME_TEXT = "Hola. Soy tu asistente local — todo corre en tu teléfono, sin nube. " +
    "Prueba: \"qué tiempo hace\", \"abre whatsapp\", \"llama a mamá\", \"apunta comprar pan\", " +
    "\"cuánto es 12 por 7\", \"escanea esto\" o \"enciende la linterna\"."

private val HELP_TEXT = "Puedo:\n" +
    "• Abrir apps — \"abre whatsapp\"\n" +
    "• Llamar a contactos — \"llama a maría\"\n" +
    "• Buscar en el teléfono — \"busca factura\"\n" +
    "• Buscar en la web — \"busca en internet…\"\n" +
    "• Decirte el clima — \"qué tiempo hace\"\n" +
    "• Tomar notas — \"apunta comprar café\"\n" +
    "• Calcular — \"cuánto es 12 por 7\"\n" +
    "• Poner temporizadores y alarmas\n" +
    "• Linterna y batería\n" +
    "• Traducir — \"traduce good morning al español\"\n" +
    "• Escanear con la cámara — \"escanea esto\""
