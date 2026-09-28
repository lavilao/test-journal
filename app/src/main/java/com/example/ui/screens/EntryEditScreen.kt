package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Stop
import com.example.ui.components.VoiceTranscriptionModal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.AudioRecordItem
import com.example.data.model.JournalPage
import com.example.data.model.JournalTemplate
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.media.VoiceJournalManager
import com.example.semantic.MindForgerSemanticEngine
import com.example.ui.components.EditorialCard
import com.example.ui.components.EntityChip
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(
    entryId: Long?,
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val existingEntryDetail by viewModel.selectedEntryDetail.collectAsState()
    val recordingState by viewModel.voiceManager.recordingState.collectAsState()
    val isRecording = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED
    val isPaused = recordingState == RecordingState.PAUSED
    val isDictating by viewModel.isDictating.collectAsState()
    val playbackState by viewModel.voiceManager.playbackState.collectAsState()
    val currentPlayingPath by viewModel.voiceManager.currentPlayingPath.collectAsState()
    val audioRecords = remember { mutableStateListOf<AudioRecordItem>() }
    var editingAudioIndex by remember { mutableStateOf<Int?>(null) }
    var showBodyDictationModal by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<String?>("Thoughtful") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val tagsList = remember { mutableStateListOf<String>() }
    var newTagInput by remember { mutableStateOf("") }

    // Multi-page sections
    val pages = remember { mutableStateListOf<JournalPage>() }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }
    var newPageBody by remember { mutableStateOf("") }

    val moods = listOf("Thoughtful", "Inspired", "Peaceful", "Focused", "Joyful", "Adventurous")

    // Image Picker using zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
        }
    }

    // Audio recording permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice journal notes", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(entryId) {
        if (entryId != null && entryId != 0L) {
            viewModel.selectEntry(entryId)
        } else {
            viewModel.clearSelectedEntry()
        }
    }

    LaunchedEffect(existingEntryDetail) {
        existingEntryDetail?.let { item ->
            title = item.entry.title
            body = item.entry.body
            location = item.entry.location ?: ""
            selectedMood = item.entry.mood
            item.entry.imageUri?.let { attachedImageUri = Uri.parse(it) }
            tagsList.clear()
            tagsList.addAll(item.tags.map { it.name })
            pages.clear()
            pages.addAll(item.pages)
            audioRecords.clear()
            audioRecords.addAll(item.audioRecords)
        }
    }

    // Live Semantic Preview (Entities & Keywords discovered in real-time)
    val liveEntities by remember(title, body) {
        derivedStateOf {
            if (title.isNotBlank() || body.isNotBlank()) {
                MindForgerSemanticEngine.extractEntities(title, body)
            } else emptyList()
        }
    }

    val liveKeywords by remember(title, body) {
        derivedStateOf {
            if (title.isNotBlank() || body.isNotBlank()) {
                MindForgerSemanticEngine.tokenize("$title $body").distinct().take(5)
            } else emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (entryId == null || entryId == 0L) "New Memory" else "Edit Memory",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("edit_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (title.isNotBlank() || body.isNotBlank()) {
                                viewModel.saveEntry(
                                    id = entryId ?: 0L,
                                    title = title,
                                    body = body,
                                    mood = selectedMood,
                                    location = location,
                                    manualTags = tagsList.toList(),
                                    attachedImageUri = attachedImageUri,
                                    pages = pages.toList(),
                                    audioRecords = audioRecords.toList(),
                                    onComplete = onSaved
                                )
                            }
                        },
                        enabled = title.isNotBlank() || body.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.padding(end = 8.dp).testTag("save_entry_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
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
                .padding(20.dp)
        ) {
            // Quick Reusable Templates Picker
            if (entryId == null || entryId == 0L) {
                Text(
                    text = "Journal Templates",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JournalTemplate.ALL_TEMPLATES.forEach { tmpl ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (title.isBlank()) title = tmpl.defaultTitle.ifBlank { tmpl.name }
                                if (body.isBlank()) {
                                    body = tmpl.defaultBody
                                } else {
                                    body = "$body\n\n${tmpl.defaultBody}"
                                }
                                tmpl.defaultTags.forEach { t ->
                                    if (t !in tagsList) tagsList.add(t)
                                }
                                tmpl.defaultMood?.let { m -> selectedMood = m }
                            },
                            label = { Text(tmpl.name) },
                            leadingIcon = {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title / Summary") },
                placeholder = { Text("What's on your mind today?") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_title_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mood Selector Chips
            Text(
                text = "Mood / Mindset",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moods.forEach { mood ->
                    FilterChip(
                        selected = selectedMood == mood,
                        onClick = { selectedMood = if (selectedMood == mood) null else mood },
                        label = { Text(mood) },
                        modifier = Modifier.testTag("mood_chip_$mood")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Location & Image Attach Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    placeholder = { Text("e.g. Blue Bottle, Kyoto...") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestPrimary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("entry_location_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach Photo",
                        tint = ForestPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Attached Photo Preview
            if (attachedImageUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = attachedImageUri,
                        contentDescription = "Attached photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = { attachedImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice Journal Recording Bar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) TerracottaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("voice_recorder_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isRecording) TerracottaAccent else ForestPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isRecording) androidx.compose.ui.graphics.Color.White else ForestPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isRecording) "Recording Voice Note..." else "Voice Journal Note",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isRecording) "Transcribing locally after stop" else "Tap mic to record audio with offline speech-to-text",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isRecording) {
                        IconButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("start_voice_recording_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Start Voice Recording", tint = ForestPrimary)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPaused) {
                                IconButton(onClick = { viewModel.resumeVoiceRecording() }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = ForestPrimary)
                                }
                            } else {
                                IconButton(onClick = { viewModel.pauseVoiceRecording() }) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = ForestPrimary)
                                }
                            }
                            IconButton(
                                onClick = {
                                    val (file, duration) = viewModel.voiceManager.stopRecording()
                                    if (file != null && file.exists()) {
                                        val filePath = file.absolutePath
                                        val newRecord = AudioRecordItem(
                                            entryId = entryId ?: 0L,
                                            filePath = filePath,
                                            durationMs = duration,
                                            transcript = "",
                                            transcriptionStatus = "RECORDED",
                                            title = "Voice Note (${title.ifBlank { "Memory" }})"
                                        )
                                        audioRecords.add(newRecord)
                                        val recordIdx = audioRecords.size - 1
                                        scope.launch {
                                            viewModel.voiceManager.transcribeAudioOffline { transcript, status ->
                                                if (transcript.isNotBlank() && recordIdx in audioRecords.indices) {
                                                    audioRecords[recordIdx] = audioRecords[recordIdx].copy(
                                                        transcript = transcript,
                                                        transcriptionStatus = status
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("stop_voice_recording_btn")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop Recording", tint = TerracottaAccent)
                            }
                        }
                    }
                }
            }

            // Attached Voice Notes in Editor
            if (audioRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                audioRecords.forEachIndexed { index, record ->
                    val isPlaying = playbackState == PlaybackState.PLAYING && currentPlayingPath == record.filePath
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("recorded_audio_$index")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    val sec = record.durationMs / 1000
                                    Text("${sec / 60}m ${sec % 60}s • Attached Audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (record.filePath.isNotBlank()) {
                                        IconButton(onClick = {
                                            if (isPlaying) viewModel.stopAudio() else viewModel.playAudio(record.filePath)
                                        }) {
                                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = ForestPrimary)
                                        }
                                    }
                                    IconButton(
                                        onClick = { editingAudioIndex = index },
                                        modifier = Modifier.testTag("transcribe_audio_edit_btn_$index")
                                    ) {
                                        Icon(
                                            imageVector = if (record.transcript.isBlank()) Icons.Default.RecordVoiceOver else Icons.Default.EditNote,
                                            contentDescription = "Transcribe / Dictate",
                                            tint = if (record.transcript.isBlank()) TerracottaAccent else ForestPrimary
                                        )
                                    }
                                    IconButton(onClick = { audioRecords.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (record.transcript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Transcript: ${record.transcript}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { editingAudioIndex = index },
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp).testTag("edit_transcript_edit_screen_btn_$index")
                                        ) {
                                            Text("Edit", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { editingAudioIndex = index },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("transcribe_audio_prompt_btn_$index")
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Transcribe Audio (Speech-to-Text / Edit)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reflection & Live Dictation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Journal Reflection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDictating) {
                        Button(
                            onClick = { viewModel.stopDictation() },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                            modifier = Modifier.testTag("dictate_stop_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Listening... Stop")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startDictation(
                                        onResult = { recognized ->
                                            body = if (body.isBlank()) recognized else "$body $recognized"
                                        },
                                        onError = {
                                            showBodyDictationModal = true
                                        }
                                    )
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("dictate_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dictate")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showBodyDictationModal = true },
                            modifier = Modifier.size(36.dp).testTag("dictate_sheet_button")
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Dictation Tool", tint = ForestPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body Editor
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Write or dictate your thoughts") },
                placeholder = { Text("Write freely... People (e.g. Sarah), places (e.g. Starbucks), and concepts will be extracted automatically into your knowledge graph.") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                minLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_body_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-Page / Section Support
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sections & Sub-Pages (${pages.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = { showAddPageDialog = true },
                    modifier = Modifier.testTag("add_page_button")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Section")
                }
            }

            if (pages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                pages.forEachIndexed { index, page ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = page.title.ifBlank { "Section ${index + 1}" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = page.body.take(150),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pages.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete section", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live On-Device Semantic Extraction Preview
            if (liveEntities.isNotEmpty() || liveKeywords.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AmberNode,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live On-Device Semantic Intelligence",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (liveEntities.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Discovered entities to be linked in knowledge graph:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                liveEntities.forEach { entity ->
                                    EntityChip(name = entity.displayName, type = entity.type)
                                }
                            }
                        }

                        if (liveKeywords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Extracted keywords for semantic matching: ${liveKeywords.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Tags Editor
            Text(
                text = "Tags",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tagsList.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(ForestPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = ForestPrimary)
                        IconButton(
                            onClick = { tagsList.remove(tag) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add Tag Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    placeholder = { Text("Add tag (e.g. philosophy)") },
                    shape = RoundedCornerShape(10.dp),
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
                    Icon(Icons.Default.Add, contentDescription = "Add tag", tint = ForestPrimary)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Add Section / Sub-Page Dialog
    if (showAddPageDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddPageDialog = false },
            title = { Text("Add Section / Page") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPageTitle,
                        onValueChange = { newPageTitle = it },
                        label = { Text("Section Title (e.g. Day 2, Quotes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPageBody,
                        onValueChange = { newPageBody = it },
                        label = { Text("Content") },
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
                    Text("Add")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingAudioIndex != null) {
        val idx = editingAudioIndex!!
        if (idx in audioRecords.indices) {
            val rec = audioRecords[idx]
            VoiceTranscriptionModal(
                title = "Transcribe Attached Audio",
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

    if (showBodyDictationModal) {
        VoiceTranscriptionModal(
            title = "Dictate Journal Reflection",
            initialTranscript = body,
            audioFilePath = null,
            durationMs = 0L,
            viewModel = viewModel,
            onDismiss = { showBodyDictationModal = false },
            onSaveTranscript = { newTranscript ->
                body = newTranscript
                Toast.makeText(context, "Spoken text applied to reflection!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
