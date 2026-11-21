package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.contacts.ContactsHelper
import com.example.contacts.DeviceContactInfo
import com.example.data.model.AudioRecordItem
import com.example.data.model.JournalPage
import com.example.data.model.JournalTemplate
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.ui.components.EmojiMoodPicker
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiveMarkdownEditor
import com.example.ui.components.MdBlockAction
import com.example.ui.components.applyMdBlockAction
import com.example.ui.components.detectSlashCommandQuery
import com.example.ui.components.replaceSlashTriggerWith
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NOTION_COVER_PRESETS = listOf(
    "#2F4F4F" to "Pizarra",
    "#3B4D6B" to "Cobalto",
    "#4A5D4E" to "Salvia",
    "#5C4033" to "Cedro",
    "#5B4B6B" to "Ciruela",
    "#7C3F38" to "Terracota"
)

private val NOTION_EMOJI_PRESETS = listOf(
    "📄", "📝", "🚀", "💡", "🧠", "🗓️", "📚", "✅", "🎯", "🌿", "⚡", "🔍", "📌", "🎨"
)

private val WORKSPACE_STATUS_OPTIONS = listOf("Inbox", "En curso", "Listo", "Archivado")
private val WORKSPACE_PRIORITY_OPTIONS = listOf("Baja", "Normal", "Alta")

private data class SlashCommandItem(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val keywords: String
)

private val SLASH_COMMANDS = listOf(
    SlashCommandItem("h1", "H1", "Título 1", "Encabezado principal de sección", "h1 titulo encabezado"),
    SlashCommandItem("h2", "H2", "Título 2", "Subtítulo estructurado", "h2 subtitulo"),
    SlashCommandItem("h3", "H3", "Título 3", "Apartado pequeño", "h3 apartado"),
    SlashCommandItem("todo", "☑", "Lista de tareas", "Casilla interactiva (- [ ])", "todo tarea check checklist"),
    SlashCommandItem("bullet", "•", "Lista con viñetas", "Lista simple de puntos", "lista bullet vinetas"),
    SlashCommandItem("numbered", "1.", "Lista numerada", "Pasos ordenados 1, 2, 3", "numero ordenada pasos"),
    SlashCommandItem("callout", "💡", "Callout destacado", "Nota resaltada con icono", "callout aviso nota"),
    SlashCommandItem("quote", "❝", "Cita", "Bloque de texto citado", "cita quote"),
    SlashCommandItem("code", "</>", "Código", "Fragmento monoespaciado", "codigo code"),
    SlashCommandItem("divider", "—", "Divisor", "Línea separadora horizontal", "divisor linea hr"),
    SlashCommandItem("wikilink", "[[ ]]", "Enlace a memoria", "Conectar con [[otra página]]", "enlace wiki link"),
    SlashCommandItem("voice", "🎙", "Nota de voz", "Grabar con transcripción local", "voz audio microfono dictar"),
    SlashCommandItem("photo", "🖼", "Adjuntar foto", "Desde la galería del teléfono", "foto imagen galeria"),
    SlashCommandItem("lens", "📷", "Escanear con Lens", "OCR y traducción en cámara", "lens escanear camara ocr")
)

fun parseCoverHexOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(
    entryId: Long?,
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenLens: () -> Unit = {},
    initialParentEntryId: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingEntryDetail by viewModel.selectedEntryDetail.collectAsState()
    val allEntries by viewModel.entries.collectAsState()
    val recordingState by viewModel.voiceManager.recordingState.collectAsState()
    val isRecording = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED
    val isPaused = recordingState == RecordingState.PAUSED
    val isDictating by viewModel.isDictating.collectAsState()
    val liveTranscript by viewModel.liveRecordingTranscript.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val playbackState by viewModel.voiceManager.playbackState.collectAsState()
    val currentPlayingPath by viewModel.voiceManager.currentPlayingPath.collectAsState()
    val realWeather by viewModel.realWeather.collectAsState()

    val audioRecords = remember { mutableStateListOf<AudioRecordItem>() }
    var editingAudioIndex by remember { mutableStateOf<Int?>(null) }

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var bodyFieldValue by remember { mutableStateOf(TextFieldValue(body)) }
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }

    // Notion Workspace Metadata
    var iconEmoji by rememberSaveable { mutableStateOf("📄") }
    var coverColorHex by rememberSaveable { mutableStateOf<String?>(null) }
    var parentEntryId by rememberSaveable { mutableStateOf<Long?>(initialParentEntryId) }
    var pageType by rememberSaveable { mutableStateOf("PAGE") }
    var status by rememberSaveable { mutableStateOf("Inbox") }
    var priority by rememberSaveable { mutableStateOf("Normal") }
    var isPinned by rememberSaveable { mutableStateOf(false) }

    val attachedImages = remember { mutableStateListOf<Uri>() }
    val tagsList = remember { mutableStateListOf<String>() }
    var newTagInput by rememberSaveable { mutableStateOf("") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showTemplateMenu by rememberSaveable { mutableStateOf(false) }
    var showEmojiPickerDialog by remember { mutableStateOf(false) }
    var showCoverPickerDialog by remember { mutableStateOf(false) }
    var showParentPickerDialog by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }

    val pages = remember { mutableStateListOf<JournalPage>() }
    var showAddPageDialog by rememberSaveable { mutableStateOf(false) }
    var newPageTitle by rememberSaveable { mutableStateOf("") }
    var newPageBody by rememberSaveable { mutableStateOf("") }
    var editingPageIndex by remember { mutableStateOf<Int?>(null) }

    val linkedContacts = remember { mutableStateListOf<DeviceContactInfo>() }
    var selectedContactAction by remember { mutableStateOf<DeviceContactInfo?>(null) }

    val dateFormat = remember { SimpleDateFormat("d MMM · H:mm", Locale.getDefault()) }

    LaunchedEffect(body) {
        if (body != bodyFieldValue.text) {
            val sel = bodyFieldValue.selection
            bodyFieldValue = TextFieldValue(
                text = body,
                selection = TextRange(
                    sel.min.coerceAtMost(body.length),
                    sel.max.coerceAtMost(body.length)
                )
            )
        }
    }

    val slashTrigger = remember(bodyFieldValue) { detectSlashCommandQuery(bodyFieldValue) }
    val filteredSlashCommands = remember(slashTrigger) {
        val q = slashTrigger?.query?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) SLASH_COMMANDS
        else SLASH_COMMANDS.filter {
            it.title.lowercase().contains(q) || it.keywords.contains(q)
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val contactInfo = ContactsHelper.resolveContact(context, contactUri)
            if (contactInfo != null && linkedContacts.none { it.displayName == contactInfo.displayName }) {
                linkedContacts.add(contactInfo)
            }
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "Se necesita el permiso de contactos para enlazarlos", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val permanentUriString = viewModel.repository.persistImageToLocalStorage(uri)
                if (attachedImages.none { it.toString() == permanentUriString }) {
                    attachedImages.add(Uri.parse(permanentUriString))
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecordingWithDictation()
        } else {
            Toast.makeText(context, "El micrófono es necesario para las notas de voz", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(entryId) {
        if (entryId != null && entryId != 0L) {
            viewModel.selectEntry(entryId)
        } else {
            viewModel.clearSelectedEntry()
        }
    }

    var hasInitializedData by remember(entryId) { mutableStateOf(false) }

    LaunchedEffect(existingEntryDetail) {
        if (!hasInitializedData && existingEntryDetail != null && (entryId != null && entryId != 0L)) {
            existingEntryDetail?.let { item ->
                title = item.entry.title
                body = item.entry.body
                bodyFieldValue = TextFieldValue(item.entry.body, TextRange(item.entry.body.length))
                selectedMood = item.entry.mood
                iconEmoji = item.entry.iconEmoji.ifBlank { "📄" }
                coverColorHex = item.entry.coverColorHex
                parentEntryId = item.entry.parentEntryId
                pageType = item.entry.pageType
                status = item.entry.status
                priority = item.entry.priority
                isPinned = item.entry.isPinned
                attachedImages.clear()
                item.entry.imageUri?.let { attachedImages.add(Uri.parse(it)) }
                item.mediaItems
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    .forEach { media ->
                        val uri = Uri.parse(media.uri)
                        if (attachedImages.none { it.toString() == media.uri }) {
                            attachedImages.add(uri)
                        }
                    }
                tagsList.clear()
                tagsList.addAll(item.tags.map { it.name })
                pages.clear()
                pages.addAll(item.pages)
                audioRecords.clear()
                audioRecords.addAll(item.audioRecords)
                hasInitializedData = true
            }
        }
    }

    fun persistEntry(onComplete: (Long) -> Unit) {
        val autoContext = buildString {
            val weather = realWeather
            if (weather.temperature != null) {
                append(weather.locationName ?: "")
                append(" · ${weather.temperature}°C ${weather.conditionText ?: ""}")
            }
        }.trim().trimStart('·').trim()

        viewModel.saveEntry(
            id = entryId ?: 0L,
            title = title,
            body = body,
            mood = selectedMood,
            location = autoContext.ifBlank { null },
            manualTags = tagsList.toList(),
            attachedImages = attachedImages.toList(),
            pages = pages.toList(),
            audioRecords = audioRecords.toList(),
            parentEntryId = parentEntryId,
            iconEmoji = iconEmoji,
            coverColorHex = coverColorHex,
            pageType = pageType,
            status = status,
            priority = priority,
            isPinned = isPinned,
            onComplete = onComplete
        )
    }

    fun triggerVoiceCapture() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.startVoiceRecordingWithDictation()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleSlashCommand(item: SlashCommandItem) {
        val trig = slashTrigger ?: return
        val cleared = replaceSlashTriggerWith(bodyFieldValue, trig, "")
        when (item.id) {
            "h1" -> applyMdBlockAction(MdBlockAction.H1, cleared)
            "h2" -> applyMdBlockAction(MdBlockAction.H2, cleared)
            "h3" -> applyMdBlockAction(MdBlockAction.H3, cleared)
            "todo" -> applyMdBlockAction(MdBlockAction.TODO, cleared)
            "bullet" -> applyMdBlockAction(MdBlockAction.BULLET, cleared)
            "numbered" -> applyMdBlockAction(MdBlockAction.NUMBERED, cleared)
            "callout" -> applyMdBlockAction(MdBlockAction.CALLOUT, cleared)
            "quote" -> applyMdBlockAction(MdBlockAction.QUOTE, cleared)
            "code" -> applyMdBlockAction(MdBlockAction.CODE, cleared)
            "divider" -> applyMdBlockAction(MdBlockAction.DIVIDER, cleared)
            "wikilink" -> applyMdBlockAction(MdBlockAction.WIKILINK, cleared)
            "voice" -> {
                bodyFieldValue = cleared
                body = cleared.text
                triggerVoiceCapture()
                return
            }
            "photo" -> {
                bodyFieldValue = cleared
                body = cleared.text
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                return
            }
            "lens" -> {
                bodyFieldValue = cleared
                body = cleared.text
                if (title.isNotBlank() || body.isNotBlank()) {
                    persistEntry { onOpenLens() }
                } else {
                    onOpenLens()
                }
                return
            }
            else -> cleared
        }.let { updated ->
            bodyFieldValue = updated
            body = updated.text
        }
    }

    val parentEntry = remember(parentEntryId, allEntries) {
        parentEntryId?.let { pid -> allEntries.firstOrNull { it.entry.id == pid }?.entry }
    }
    val coverColor = remember(coverColorHex) { parseCoverHexOrNull(coverColorHex) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (title.isNotBlank() || body.isNotBlank()) {
                                persistEntry { onBack() }
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("edit_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Breadcrumb path
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showParentPickerDialog = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (parentEntry != null) {
                                "${parentEntry.iconEmoji} ${parentEntry.title.ifBlank { "Padre" }} /"
                            } else {
                                "Espacio /"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title.ifBlank { "Sin título" },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.testTag("edit_pin_button")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Fijar página",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (entryId == null || entryId == 0L) {
                        Box {
                            IconButton(onClick = { showTemplateMenu = true }) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = "Plantillas",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showTemplateMenu,
                                onDismissRequest = { showTemplateMenu = false }
                            ) {
                                JournalTemplate.ALL_TEMPLATES.take(8).forEach { tmpl ->
                                    DropdownMenuItem(
                                        text = { Text("${tmpl.defaultEmoji}  ${tmpl.name}") },
                                        onClick = {
                                            showTemplateMenu = false
                                            iconEmoji = tmpl.defaultEmoji
                                            pageType = tmpl.defaultPageType
                                            status = tmpl.defaultStatus
                                            if (title.isBlank()) title = tmpl.defaultTitle.ifBlank { tmpl.name }
                                            if (body.isBlank()) {
                                                body = tmpl.defaultBody
                                                bodyFieldValue = TextFieldValue(tmpl.defaultBody, TextRange(tmpl.defaultBody.length))
                                            }
                                            tmpl.defaultMood?.let { selectedMood = it }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (title.isNotBlank() || body.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = title.isNotBlank() || body.isNotBlank()) {
                                persistEntry(onSaved)
                            }
                            .testTag("save_entry_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Guardar",
                                tint = if (title.isNotBlank() || body.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Listo",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (title.isNotBlank() || body.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Floating "/" Slash Command Palette above dock
                AnimatedVisibility(visible = slashTrigger != null && filteredSlashCommands.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .testTag("slash_command_menu")
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = if (slashTrigger?.query.isNullOrBlank()) {
                                    "Insertar bloque (/)"
                                } else {
                                    "Bloques para «/${slashTrigger?.query}»"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredSlashCommands.forEach { cmd ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { handleSlashCommand(cmd) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cmd.badge,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = cmd.title,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = cmd.subtitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sticky Notion compact formatting & media dock
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Insert "/" trigger
                        TextButton(
                            onClick = {
                                val text = bodyFieldValue.text
                                val pos = bodyFieldValue.selection.max.coerceIn(0, text.length)
                                val needSpace = pos > 0 && !text[pos - 1].isWhitespace()
                                val ins = if (needSpace) " /" else "/"
                                val nextText = text.substring(0, pos) + ins + text.substring(pos)
                                val nextVal = TextFieldValue(nextText, TextRange(pos + ins.length))
                                bodyFieldValue = nextVal
                                body = nextText
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "/ Bloque",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        DockDivider()

                        DockTextBtn("H1") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.H1, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockTextBtn("H2") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.H2, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.CheckBoxOutlineBlank, "Tarea") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.TODO, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.FormatListBulleted, "Viñetas") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.BULLET, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.FormatListNumbered, "Numerada") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.NUMBERED, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.Lightbulb, "Callout") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.CALLOUT, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.FormatQuote, "Cita") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.QUOTE, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.FormatBold, "Negrita") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.BOLD, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.FormatItalic, "Cursiva") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.ITALIC, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.Code, "Código") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.CODE, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.HorizontalRule, "Divisor") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.DIVIDER, bodyFieldValue)
                            body = bodyFieldValue.text
                        }
                        DockIconBtn(Icons.Default.Link, "Enlace") {
                            bodyFieldValue = applyMdBlockAction(MdBlockAction.WIKILINK, bodyFieldValue)
                            body = bodyFieldValue.text
                        }

                        DockDivider()

                        DockIconBtn(Icons.Default.Mic, "Nota de voz", tint = TerracottaAccent) {
                            triggerVoiceCapture()
                        }
                        DockIconBtn(Icons.Default.AddPhotoAlternate, "Adjuntar foto") {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        DockIconBtn(Icons.Default.DocumentScanner, "Escanear Lens") {
                            if (title.isNotBlank() || body.isNotBlank()) {
                                persistEntry { onOpenLens() }
                            } else {
                                onOpenLens()
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Optional Muted Notion Cover Banner
            if (coverColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(coverColor.copy(alpha = 0.85f), coverColor.copy(alpha = 0.62f))
                            )
                        )
                        .clickable { showCoverPickerDialog = true }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Cambiar portada",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(if (coverColor != null) 10.dp else 14.dp))

                // Page Emoji + Quick Customization actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showEmojiPickerDialog = true }
                                .testTag("page_emoji_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = iconEmoji, fontSize = 24.sp)
                            }
                        }
                        if (coverColor == null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showCoverPickerDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Portada", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    val weatherContext = realWeather.temperature?.let { " · $it°" }.orEmpty()
                    Text(
                        text = dateFormat.format(Date()) + weatherContext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val suggestedTitle = remember(title, body) {
                    if (title.isBlank() && body.trim().length >= 35) {
                        com.example.semantic.LocalTextIntelligence.suggestTitleIfBlank(body)
                    } else {
                        ""
                    }
                }
                if (suggestedTitle.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { title = suggestedTitle }
                            .testTag("suggest_title_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Título sugerido: «$suggestedTitle»",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Clean Notion Borderless Document Headline
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (title.isEmpty()) {
                                Text(
                                    text = "Página sin título",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                    )
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notion Compact Inline Properties Block
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Row 1: Estado & Prioridad
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Estado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(72.dp)
                            )
                            Box {
                                PropertyPill(
                                    text = status,
                                    accent = statusColorFor(status),
                                    onClick = { showStatusMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showStatusMenu,
                                    onDismissRequest = { showStatusMenu = false }
                                ) {
                                    WORKSPACE_STATUS_OPTIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = {
                                                status = opt
                                                showStatusMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Prioridad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp)
                            )
                            Box {
                                PropertyPill(
                                    text = priority,
                                    accent = if (priority == "Alta") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    onClick = { showPriorityMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showPriorityMenu,
                                    onDismissRequest = { showPriorityMenu = false }
                                ) {
                                    WORKSPACE_PRIORITY_OPTIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = {
                                                priority = opt
                                                showPriorityMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Row 2: Carpeta padre
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showParentPickerDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ubicación",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(72.dp)
                            )
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = parentEntry?.let { "${it.iconEmoji} ${it.title}" } ?: "Espacio de trabajo (raíz)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Row 3: Etiquetas inline + More properties toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "Etiquetas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(67.dp)
                                )
                                if (tagsList.isEmpty()) {
                                    Text(
                                        text = "+ Añadir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { showDetails = true }
                                    )
                                } else {
                                    tagsList.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showDetails = !showDetails }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (showDetails) "Menos" else "Propiedades",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Expanded properties (Mood, Tag input, Contacts, Internal Sections)
                        AnimatedVisibility(visible = showDetails) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Ánimo / Tono",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                EmojiMoodPicker(
                                    selectedMood = selectedMood,
                                    onMoodSelected = { selectedMood = it },
                                    modifier = Modifier.testTag("emoji_mood_picker")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                if (tagsList.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        tagsList.forEach { tag ->
                                            key(tag) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                                    ) {
                                                        Text("#$tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Quitar etiqueta",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .size(13.dp)
                                                                .clickable { tagsList.remove(tag) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newTagInput,
                                        onValueChange = { newTagInput = it },
                                        placeholder = { Text("Nueva etiqueta…", fontSize = 13.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("new_tag_input")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            val clean = newTagInput.trim().removePrefix("#")
                                            if (clean.isNotBlank() && clean !in tagsList) {
                                                tagsList.add(clean)
                                                newTagInput = ""
                                            }
                                        },
                                        modifier = Modifier.testTag("add_tag_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Añadir etiqueta", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.READ_CONTACTS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                contactPickerLauncher.launch(null)
                                            } else {
                                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Enlazar persona", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        onClick = { showAddPageDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Sección (${pages.size})", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (linkedContacts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        linkedContacts.forEach { contact ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = TerracottaAccent.copy(alpha = 0.12f),
                                                modifier = Modifier.clickable { selectedContactAction = contact }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    Text(contact.displayName, style = MaterialTheme.typography.labelSmall, color = TerracottaAccent)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Quitar",
                                                        tint = TerracottaAccent,
                                                        modifier = Modifier.size(13.dp).clickable { linkedContacts.remove(contact) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                pages.forEachIndexed { index, page ->
                                    LiquidGlassCard(
                                        cornerRadius = 12.dp,
                                        contentPadding = 0.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clickable {
                                                editingPageIndex = index
                                                newPageTitle = page.title
                                                newPageBody = page.body
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = page.title.ifBlank { "Sección ${index + 1}" },
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = page.body.take(120),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            val moved = pages.removeAt(index)
                                                            pages.add(index - 1, moved)
                                                        }
                                                    },
                                                    enabled = index > 0,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index < pages.size - 1) {
                                                            val moved = pages.removeAt(index)
                                                            pages.add(index + 1, moved)
                                                        }
                                                    },
                                                    enabled = index < pages.size - 1,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { pages.removeAt(index) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar sección", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Distraction-free Live Markdown Editor
                LiveMarkdownEditor(
                    value = body,
                    onValueChange = { body = it },
                    showToolbar = false,
                    externalFieldValue = bodyFieldValue,
                    onTextFieldValueChange = { next ->
                        bodyFieldValue = next
                        body = next.text
                    },
                    placeholder = "Escribe libremente o teclea «/» para insertar títulos, tareas, callouts o enlazar [[otra página]]…",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_body_input")
                )

                // Voice recording bar
                if (isRecording) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VoiceRecordingBar(
                        isPaused = isPaused,
                        isDictating = isDictating,
                        liveTranscript = liveTranscript,
                        partialTranscript = partialTranscript,
                        onPause = { viewModel.pauseVoiceRecording() },
                        onResume = { viewModel.resumeVoiceRecording() },
                        onStop = {
                            val transcript = viewModel.voiceManager.consumeLiveTranscript()
                            val (file, duration) = viewModel.voiceManager.stopRecording()
                            if (file != null && file.exists()) {
                                audioRecords.add(
                                    AudioRecordItem(
                                        entryId = entryId ?: 0L,
                                        filePath = file.absolutePath,
                                        durationMs = duration,
                                        transcript = transcript,
                                        transcriptionStatus = if (transcript.isNotBlank()) "COMPLETED" else "RECORDED",
                                        title = "Nota de voz"
                                    )
                                )
                            }
                        }
                    )
                }

                audioRecords.forEachIndexed { index, record ->
                    key(record.filePath) {
                        AttachedVoiceNote(
                            record = record,
                            isPlaying = playbackState == PlaybackState.PLAYING && currentPlayingPath == record.filePath,
                            onPlayToggle = {
                                if (playbackState == PlaybackState.PLAYING && currentPlayingPath == record.filePath) {
                                    viewModel.stopAudio()
                                } else {
                                    viewModel.playAudio(record.filePath)
                                }
                            },
                            onEdit = { editingAudioIndex = index },
                            onDelete = { audioRecords.removeAt(index) }
                        )
                    }
                }

                if (attachedImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedImages.forEachIndexed { index, imgUri ->
                            Box(
                                modifier = Modifier
                                    .size(width = 128.dp, height = 94.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = imgUri,
                                    contentDescription = "Foto ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { attachedImages.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar foto", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // Emoji icon picker dialog
    if (showEmojiPickerDialog) {
        var customEmoji by remember { mutableStateOf(iconEmoji) }
        AlertDialog(
            onDismissRequest = { showEmojiPickerDialog = false },
            title = { Text("Icono de página") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NOTION_EMOJI_PRESETS.forEach { em ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (em == iconEmoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        iconEmoji = em
                                        showEmojiPickerDialog = false
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(em, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { customEmoji = it.take(4) },
                        label = { Text("O escribe un emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customEmoji.isNotBlank()) iconEmoji = customEmoji.trim()
                    showEmojiPickerDialog = false
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showEmojiPickerDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Cover color picker dialog
    if (showCoverPickerDialog) {
        AlertDialog(
            onDismissRequest = { showCoverPickerDialog = false },
            title = { Text("Portada de página") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NOTION_COVER_PRESETS.forEach { (hex, name) ->
                        val c = parseCoverHexOrNull(hex) ?: MaterialTheme.colorScheme.primary
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    coverColorHex = hex
                                    showCoverPickerDialog = false
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(name, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            coverColorHex = null
                            showCoverPickerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar portada")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCoverPickerDialog = false }) { Text("Cerrar") }
            }
        )
    }

    // Parent Page picker dialog
    if (showParentPickerDialog) {
        val eligibleParents = remember(allEntries, entryId) {
            allEntries.filter { it.entry.id != (entryId ?: -1L) && !it.entry.isArchived }
        }
        AlertDialog(
            onDismissRequest = { showParentPickerDialog = false },
            title = { Text("Mover dentro de…") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            parentEntryId = null
                            showParentPickerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🏠 Espacio de trabajo (raíz)", modifier = Modifier.fillMaxWidth())
                    }
                    eligibleParents.take(25).forEach { candidate ->
                        TextButton(
                            onClick = {
                                parentEntryId = candidate.entry.id
                                showParentPickerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${candidate.entry.iconEmoji} ${candidate.entry.title.ifBlank { "Sin título" }}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showParentPickerDialog = false }) { Text("Cerrar") }
            }
        )
    }

    // Add / Edit Section dialog
    if (showAddPageDialog || editingPageIndex != null) {
        val isEditing = editingPageIndex != null
        AlertDialog(
            onDismissRequest = {
                showAddPageDialog = false
                editingPageIndex = null
                newPageTitle = ""
                newPageBody = ""
            },
            title = { Text(if (isEditing) "Editar sección" else "Añadir sección") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPageTitle,
                        onValueChange = { newPageTitle = it },
                        label = { Text("Título de la sección") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPageBody,
                        onValueChange = { newPageBody = it },
                        label = { Text("Contenido") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPageTitle.isNotBlank() || newPageBody.isNotBlank()) {
                            if (isEditing) {
                                val idx = editingPageIndex!!
                                if (idx in pages.indices) {
                                    pages[idx] = pages[idx].copy(
                                        title = newPageTitle.trim(),
                                        body = newPageBody.trim()
                                    )
                                }
                            } else {
                                pages.add(
                                    JournalPage(
                                        entryId = entryId ?: 0L,
                                        pageIndex = pages.size,
                                        title = newPageTitle.trim(),
                                        body = newPageBody.trim()
                                    )
                                )
                            }
                            newPageTitle = ""
                            newPageBody = ""
                            showAddPageDialog = false
                            editingPageIndex = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isEditing) "Guardar cambios" else "Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddPageDialog = false
                    editingPageIndex = null
                    newPageTitle = ""
                    newPageBody = ""
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Edit transcript of an attached voice note
    if (editingAudioIndex != null) {
        val idx = editingAudioIndex!!
        if (idx in audioRecords.indices) {
            val rec = audioRecords[idx]
            com.example.ui.components.VoiceTranscriptionModal(
                title = "Transcripción de la nota de voz",
                initialTranscript = rec.transcript,
                audioFilePath = rec.filePath,
                durationMs = rec.durationMs,
                viewModel = viewModel,
                onDismiss = { editingAudioIndex = null },
                onSaveTranscript = { newTranscript ->
                    if (idx in audioRecords.indices) {
                        audioRecords[idx] = audioRecords[idx].copy(
                            transcript = newTranscript,
                            transcriptionStatus = "COMPLETED"
                        )
                    }
                }
            )
        }
    }

    // Contact quick actions dialog
    if (selectedContactAction != null) {
        val contact = selectedContactAction!!
        AlertDialog(
            onDismissRequest = { selectedContactAction = null },
            title = { Text(contact.displayName) },
            text = {
                Column {
                    contact.phoneNumber?.let {
                        Text("Tel: $it", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    contact.email?.let {
                        Text("Email: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (contact.phoneNumber != null) {
                        Button(
                            onClick = {
                                ContactsHelper.dialContact(context, contact.phoneNumber)
                                selectedContactAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Llamar")
                        }
                        Button(
                            onClick = {
                                ContactsHelper.messageContact(context, contact.phoneNumber)
                                selectedContactAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("SMS")
                        }
                    }
                    if (contact.email != null) {
                        Button(
                            onClick = {
                                ContactsHelper.emailContact(context, contact.email)
                                selectedContactAction = null
                            }
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Email")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedContactAction = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun PropertyPill(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.14f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun statusColorFor(status: String): Color = when (status) {
    "Listo" -> Color(0xFF2E7D32)
    "En curso" -> MaterialTheme.colorScheme.primary
    "Archivado" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> TerracottaAccent
}

@Composable
private fun DockIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DockTextBtn(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DockDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}

@Composable
private fun VoiceRecordingBar(
    isPaused: Boolean,
    isDictating: Boolean,
    liveTranscript: String,
    partialTranscript: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TerracottaAccent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("voice_recorder_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = TerracottaAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDictating) "Escuchando…" else "Grabando…",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TerracottaAccent
                    )
                }
                Row {
                    IconButton(onClick = if (isPaused) onResume else onPause) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pausa/Reanudar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("stop_voice_recording_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Detener", tint = TerracottaAccent)
                    }
                }
            }
            val transcriptPreview = (liveTranscript + " " + partialTranscript).trim()
            if (transcriptPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = transcriptPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AttachedVoiceNote(
    record: AudioRecordItem,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayToggle) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val sec = record.durationMs / 1000
                    Text("Nota de voz · ${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}", style = MaterialTheme.typography.titleSmall)
                    if (record.transcript.isBlank()) {
                        Text(
                            "Sin transcripción",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar transcripción", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (record.transcript.isNotBlank()) {
                Text(
                    text = record.transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}
