package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.LiveMarkdownEditor
import com.example.ui.components.GoogleBlue
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal journal editor, Day One / Obsidian style: a clean writing surface
 * with the live markdown renderer as the ONLY editing mode, a compact mood
 * row, one row of attachment actions, and everything else tucked behind a
 * "details" expander. Contextual metadata (weather) is captured
 * automatically on save instead of being a form field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(
    entryId: Long?,
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenLens: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val existingEntryDetail by viewModel.selectedEntryDetail.collectAsState()
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

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val tagsList = remember { mutableStateListOf<String>() }
    var newTagInput by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }

    // Multi-page sections
    val pages = remember { mutableStateListOf<JournalPage>() }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }
    var newPageBody by remember { mutableStateOf("") }

    // Linked contacts
    val linkedContacts = remember { mutableStateListOf<DeviceContactInfo>() }
    var selectedContactAction by remember { mutableStateOf<DeviceContactInfo?>(null) }

    val dateFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM · H:mm", Locale.getDefault()) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val contactInfo = ContactsHelper.resolveContact(context, contactUri)
            if (contactInfo != null) {
                if (linkedContacts.none { it.displayName == contactInfo.displayName }) {
                    linkedContacts.add(contactInfo)
                }
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
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val permanentUriString = viewModel.repository.persistImageToLocalStorage(uri)
            attachedImageUri = Uri.parse(permanentUriString)
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
        if (!hasInitializedData && existingEntryDetail != null) {
            existingEntryDetail?.let { item ->
                title = item.entry.title
                body = item.entry.body
                selectedMood = item.entry.mood
                item.entry.imageUri?.let { attachedImageUri = Uri.parse(it) }
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

    // Saving: the current entry, enriched with automatic contextual metadata
    // (weather) when the user did not type a location themselves — Day One style.
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
            attachedImageUri = attachedImageUri,
            pages = pages.toList(),
            audioRecords = audioRecords.toList(),
            onComplete = onComplete
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (entryId == null || entryId == 0L) "Nueva memoria" else "Editar memoria",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Back = save (Day One behavior) when there is content.
                        if (title.isNotBlank() || body.isNotBlank()) {
                            persistEntry { onBack() }
                        } else {
                            onBack()
                        }
                    }, modifier = Modifier.testTag("edit_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (entryId == null || entryId == 0L) {
                        IconButton(onClick = { showTemplateMenu = true }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Plantillas")
                        }
                        DropdownMenu(
                            expanded = showTemplateMenu,
                            onDismissRequest = { showTemplateMenu = false }
                        ) {
                            JournalTemplate.ALL_TEMPLATES.take(8).forEach { tmpl ->
                                DropdownMenuItem(
                                    text = { Text(tmpl.name) },
                                    onClick = {
                                        showTemplateMenu = false
                                        if (title.isBlank()) title = tmpl.defaultTitle.ifBlank { tmpl.name }
                                        if (body.isBlank()) body = tmpl.defaultBody
                                        tmpl.defaultMood?.let { selectedMood = it }
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() || body.isNotBlank()) {
                                persistEntry(onSaved)
                            }
                        },
                        enabled = title.isNotBlank() || body.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_entry_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Auto date + weather context line
            Text(
                text = dateFormat.format(Date()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            realWeather.temperature?.let { temp ->
                Text(
                    text = "· $temp° ${realWeather.conditionText ?: ""} ${realWeather.locationName ?: ""}".trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title: clean, borderless
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Título") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_title_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Compact mood row
            EmojiMoodPicker(
                selectedMood = selectedMood,
                onMoodSelected = { selectedMood = it },
                modifier = Modifier.testTag("emoji_mood_picker")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // The writing surface: live markdown, the only mode (Obsidian style)
            LiveMarkdownEditor(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_body_input"),
                placeholder = "Escribe libremente… **negrita**, # títulos, - listas, [[enlaces]]"
            )

            // Voice note: compact recorder with REAL live transcript
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

            // Attached voice notes
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

            // Attached photo
            if (attachedImageUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = attachedImageUri,
                        contentDescription = "Foto adjunta",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    IconButton(
                        onClick = { attachedImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar foto", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single action row: photo / voice / contact / lens / details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionChip(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "Foto",
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                ActionChip(
                    icon = Icons.Default.Mic,
                    label = "Voz",
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            viewModel.startVoiceRecordingWithDictation()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                ActionChip(
                    icon = Icons.Default.PersonAdd,
                    label = "Contacto",
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            contactPickerLauncher.launch(null)
                        } else {
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                )
                ActionChip(
                    icon = Icons.Default.DocumentScanner,
                    label = "Escanear",
                    onClick = {
                        // Day One philosophy: save the draft before leaving
                        // to scan, so nothing typed is ever lost.
                        if (title.isNotBlank() || body.isNotBlank()) {
                            persistEntry { onOpenLens() }
                        } else {
                            onOpenLens()
                        }
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Icon(
                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(if (showDetails) "Menos" else "Detalles")
                }
            }

            // Linked contacts chips
            if (linkedContacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
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
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                            ) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TerracottaAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar",
                                    tint = TerracottaAccent,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { linkedContacts.remove(contact) }
                                )
                            }
                        }
                    }
                }
            }

            // Collapsed details: tags, sections, linked contact actions
            AnimatedVisibility(visible = showDetails) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    // Tags
                    Text(
                        text = "Etiquetas",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tagsList.forEach { tag ->
                            key(tag) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ForestPrimary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = ForestPrimary)
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Quitar etiqueta",
                                            tint = ForestPrimary,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { tagsList.remove(tag) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            placeholder = { Text("Añadir etiqueta") },
                            shape = RoundedCornerShape(10.dp),
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
                            Icon(Icons.Default.Add, contentDescription = "Añadir etiqueta", tint = ForestPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sections / sub-pages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secciones (${pages.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showAddPageDialog = true }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Añadir")
                        }
                    }
                    pages.forEachIndexed { index, page ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = page.title.ifBlank { "Sección ${index + 1}" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = page.body.take(120),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { pages.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar sección", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Add Section dialog
    if (showAddPageDialog) {
        AlertDialog(
            onDismissRequest = { showAddPageDialog = false },
            title = { Text("Añadir sección") },
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
                            pages.add(
                                JournalPage(
                                    entryId = entryId ?: 0L,
                                    pageIndex = pages.size,
                                    title = newPageTitle.trim(),
                                    body = newPageBody.trim()
                                )
                            )
                            newPageTitle = ""
                            newPageBody = ""
                            showAddPageDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPageDialog = false }) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
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
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
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
        shape = RoundedCornerShape(14.dp),
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
                            tint = ForestPrimary
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
            // Real live transcript while speaking
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayToggle) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = ForestPrimary
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
                    Icon(Icons.Default.Edit, contentDescription = "Editar transcripción", tint = ForestPrimary)
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
