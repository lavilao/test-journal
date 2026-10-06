package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assistant.AssistantIntent
import com.example.assistant.AssistantParser
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.theme.ForestPrimary
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch

/** One exchange in the assistant conversation log. */
data class AssistantMessage(
    val isUser: Boolean,
    val text: String
)

/**
 * The on-device Google-Assistant replacement: speak or type a command in
 * Spanish and it is parsed LOCALLY (rule-based, no cloud, no GenAI) and
 * executed against real phone capabilities — open apps, call contacts,
 * search the device, read the weather, set timers/alarms, toggle the
 * flashlight, do math, take notes, launch the Lens scanner.
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

    var messages by remember { mutableStateOf(listOf(AssistantMessage(false, WELCOME_TEXT))) }
    var typedInput by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    fun post(message: AssistantMessage) {
        messages = messages + message
    }

    /** Executes a parsed intent; appends the assistant's reply. */
    fun execute(utterance: String) {
        val intent = AssistantParser.parse(utterance)
        post(AssistantMessage(false, describe(intent)))
        thinking = true
        scope.launch {
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
                    // searchDevice launches its own coroutine; give it a beat
                    // to finish before counting the results.
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
                        onComplete = { newId ->
                            post(AssistantMessage(false, "Nota guardada. Toca para abrirla."))
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
                        "$pretty"
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
            thinking = false
        }
    }

    fun submit(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        post(AssistantMessage(true, clean))
        typedInput = ""
        execute(clean)
    }

    // Voice input: one-shot system dictation
    fun startVoiceInput() {
        viewModel.startDictation(
            onResult = { recognized -> submit(recognized) },
            onError = {
                post(AssistantMessage(false, "No pude escucharte. ¿Hay un servicio de voz en el dispositivo?"))
            }
        )
    }

    // Keep the log scrolled to the latest message
    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("assistant_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Asistente",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "100% en tu dispositivo · sin nube",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Conversation
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
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
                            color = GoogleBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Search results inline (from the last device search)
            if (deviceFiles.isNotEmpty()) {
                item {
                    Text(
                        "Archivos encontrados",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GoogleBlue,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                items(deviceFiles.take(4), key = { "a_file_${it.id}" }) { file ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openDeviceFile(file) }
                    ) {
                        Text(
                            file.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
            if (deviceContacts.isNotEmpty()) {
                item {
                    Text(
                        "Contactos encontrados",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GoogleGreen,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                items(deviceContacts.take(4), key = { "a_contact_${it.id}" }) { contact ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                contact.phoneNumber?.let { viewModel.assistantManager.dialContact(it) }
                            }
                    ) {
                        Text(
                            "${contact.displayName}  ·  ${contact.phoneNumber ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Suggestion chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip("¿Qué tiempo hace?") { submit(it) }
            SuggestionChip("Abre la linterna") { submit(it) }
            SuggestionChip("Batería") { submit(it) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip("Cuánto es 12 por 7") { submit(it) }
            SuggestionChip("Apunta comprar café") { submit(it) }
            SuggestionChip("Escanea esto") { submit(it) }
        }

        // Input row: text field + mic + send
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                placeholder = { Text("Pregunta o pide algo…", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(typedInput) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoogleBlue.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isDictating) {
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
                    color = GoogleBlue,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { startVoiceInput() }
                        .testTag("assistant_mic")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Hablar",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { submit(typedInput) },
                enabled = typedInput.isNotBlank(),
                modifier = Modifier.testTag("assistant_send")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (typedInput.isNotBlank()) GoogleBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        // Live partial transcript caption
        if (isDictating && partialTranscript.isNotBlank()) {
            Text(
                text = "Escuchando: $partialTranscript",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(message: AssistantMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) GoogleBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) {
                    androidx.compose.ui.graphics.Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ForestPrimary.copy(alpha = 0.10f),
        modifier = Modifier.clickable { onClick(text) }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = ForestPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun describe(intent: AssistantIntent): String = when (intent) {
    is AssistantIntent.OpenApp -> "Buscando \"${intent.appName}\"…"
    is AssistantIntent.CallContact -> "Buscando a \"${intent.contactName}\"…"
    is AssistantIntent.SearchDevice -> "Buscando \"${intent.query}\" en tu teléfono…"
    is AssistantIntent.SearchWeb -> "Buscando en la web…"
    AssistantIntent.Weather -> "Consultando el clima…"
    is AssistantIntent.CreateNote -> "Guardando tu nota…"
    is AssistantIntent.Calculate -> "Calculando…"
    is AssistantIntent.SetTimer -> "Preparando el temporizador…"
    is AssistantIntent.SetAlarm -> "Preparando la alarma…"
    is AssistantIntent.Flashlight -> "Cambiando la linterna…"
    AssistantIntent.Battery -> "Leyendo la batería…"
    AssistantIntent.Lens -> "Abriendo el Lens…"
    is AssistantIntent.Translate -> "Traduciendo…"
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
