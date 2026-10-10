package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.components.MarkdownText
import com.example.data.model.AudioRecordItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.AutolinkSpan
import com.example.data.model.EntityType
import com.example.data.model.JournalPage
import com.example.data.model.SuggestedTag
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.media.SubjectSegmentationManager
import com.example.media.AudioDecode
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.WhistleResult
import com.example.semantic.MlKitAnalyzer
import com.example.ui.components.EditorialCard
import com.example.ui.components.EntityChip
import com.example.ui.components.TagChip
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    entryId: Long,
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit,
    onNavigateToEntry: (Long) -> Unit
) {
    LaunchedEffect(entryId) {
        viewModel.selectEntry(entryId)
    }

    val itemWithRelations by viewModel.selectedEntryDetail.collectAsState()
    val relatedEntries by viewModel.relatedEntries.collectAsState()
    val autolinkSpans by viewModel.autolinks.collectAsState()
    val suggestedTags by viewModel.suggestedTags.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    val allEntities by viewModel.entities.collectAsState()
    val playbackState by viewModel.voiceManager.playbackState.collectAsState()
    val currentPlayingPath by viewModel.voiceManager.currentPlayingPath.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var faceToLinkIndex by remember { mutableStateOf<Int?>(null) }
    var transcribingRecord by remember { mutableStateOf<AudioRecordItem?>(null) }
    var showDictateNoteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordingState by viewModel.voiceManager.recordingState.collectAsState()
    val isRecording = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED

    var showAddSectionDialog by remember { mutableStateOf(false) }
    var newSectionTitle by remember { mutableStateOf("") }
    var newSectionBody by remember { mutableStateOf("") }

    // ---- One-tap Whistle transcription of saved voice notes ----
    var transcribingIds by remember(entryId) { mutableStateOf(setOf<Long>()) }
    val whistleUsable = remember {
        com.example.ai.needle.NeedleModelManager.isWhistleDictationEnabled(context) &&
            com.example.ai.needle.NeedleModelManager.isWhistleDownloaded(context)
    }

    fun oneTapTranscribe(record: AudioRecordItem) {
        if (record.filePath.isBlank()) {
            Toast.makeText(context, "Esta nota no tiene archivo de audio.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            transcribingIds = transcribingIds + record.id
            try { com.example.ai.needle.NeedleModelManager.ensureLoaded(context) } catch (_: Exception) {}
            val pcm = com.example.media.AudioDecode.decodeToPcm16kMono(record.filePath)
            val raw = if (pcm != null && pcm.isNotEmpty()) {
                withTimeoutOrNull(90_000) { NeedleRuntime.transcribe(pcm, "es") }
            } else null
            val parsed = WhistleResult.parse(raw)
            transcribingIds = transcribingIds - record.id
            if (parsed != null && parsed.text.isNotBlank()) {
                viewModel.updateAudioTranscript(record.id, entryId, parsed.text)
                val stats = parsed.statsLine()
                Toast.makeText(
                    context,
                    if (stats != null) "Transcrito con IA local ✓ ($stats)" else "Transcrito con IA local ✓",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "No pude transcribir este audio (¿silencio o formato?).", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- On-device photo surgery (subject segmentation) ----
    var photoProcessing by remember(entryId) { mutableStateOf<String?>(null) }
    var photoResultUri by remember(entryId) { mutableStateOf<android.net.Uri?>(null) }

    fun runPhotoOp(sourceUri: String?, op: SubjectSegmentationManager.Op) {
        if (sourceUri.isNullOrBlank()) {
            Toast.makeText(context, "No hay foto que procesar.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            photoProcessing = if (op == SubjectSegmentationManager.Op.REMOVE_BACKGROUND) "quitando el fondo" else "quitando el sujeto"
            val out = try {
                SubjectSegmentationManager.process(context, android.net.Uri.parse(sourceUri), op)
            } catch (_: Exception) {
                null
            }
            photoProcessing = null
            if (out != null) {
                photoResultUri = android.net.Uri.fromFile(out)
                viewModel.addPhoto(entryId, photoResultUri!!)
                Toast.makeText(context, "Foto procesada y adjuntada (IA local).", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "La segmentación falló (¿sin Play Services o modelo no descargado aún?).", Toast.LENGTH_LONG).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addPhoto(entryId, uri)
            Toast.makeText(context, "Photo attached and scanned with ML Kit OCR!", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecordingWithDictation()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault())

    val entry = itemWithRelations?.entry

    if (entry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ForestPrimary)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Details", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTranslateSheet = true },
                        modifier = Modifier.testTag("detail_translate_button")
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = "Offline Translation", tint = ForestPrimary)
                    }
                    IconButton(
                        onClick = { onEdit(entry.id) },
                        modifier = Modifier.testTag("detail_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Entry")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Entry", tint = MaterialTheme.colorScheme.error)
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
            // Journal Date & Mood & Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(entry.journalDate)),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!entry.mood.isNullOrBlank()) {
                    Surface(
                        color = TerracottaAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = entry.mood,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TerracottaAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!entry.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = ForestPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.location,
                        style = MaterialTheme.typography.labelMedium,
                        color = ForestPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title
            Text(
                text = entry.title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Pills Row: Quick tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Translate Memory
                OutlinedButton(
                    onClick = { showTranslateSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("prominent_translate_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = ForestPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Translate Memory",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ForestPrimary
                    )
                }

                // Record Voice Note
                if (!isRecording) {
                    OutlinedButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                viewModel.startVoiceRecordingWithDictation()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("record_voice_pill_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Voice", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TerracottaAccent)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.stopVoiceRecording(entryId, title = "Voice Note (${entry.title.ifBlank { "Memory" }})")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("stop_voice_pill_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop & Transcribe", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Attach Photo
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("add_photo_pill_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Photo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ForestPrimary)
                }

                // Add Section
                OutlinedButton(
                    onClick = { showAddSectionDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("add_section_pill_button")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Section", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ForestPrimary)
                }
            }

            // Optional Image Attachment & ML Kit Vision Metadata
            if (!entry.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                AsyncImage(
                    model = entry.imageUri,
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                val media = itemWithRelations?.mediaItems?.firstOrNull()
                Spacer(modifier = Modifier.height(8.dp))
                // ---- On-device photo surgery: quitar fondo / quitar sujeto ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { runPhotoOp(entry.imageUri, SubjectSegmentationManager.Op.REMOVE_BACKGROUND) },
                        enabled = photoProcessing == null,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).testTag("photo_remove_bg_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (photoProcessing == "quitando el fondo") "Procesando…" else "Quitar fondo",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedButton(
                        onClick = { runPhotoOp(entry.imageUri, SubjectSegmentationManager.Op.REMOVE_SUBJECT) },
                        enabled = photoProcessing == null,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).testTag("photo_remove_subject_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (photoProcessing == "quitando el sujeto") "Procesando…" else "Quitar sujeto",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (photoResultUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = photoResultUri,
                        contentDescription = "Foto procesada con IA local",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                if (media != null && (media.labelsJson.isNotBlank() || media.faceCount > 0 || media.ocrText.isNotBlank())) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberNode, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "On-Device ML Vision Intelligence",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (media.faceCount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Face, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${media.faceCount} face${if (media.faceCount > 1) "s" else ""} detected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { faceToLinkIndex = 0 },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("link_face_button")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Identify Person", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                    }
                                }
                            }
                            if (media.labelsJson.isNotBlank() && media.labelsJson != "[]") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Detected objects: ${media.labelsJson.replace("[\"", "").replace("\"]", "").replace("\",\"", ", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (media.ocrText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TextFields, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "OCR text: ${media.ocrText.take(120)}${if (media.ocrText.length > 120) "..." else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Body rendered as MARKDOWN — the same engine as the WYSIWYG
            // editor, so «lo que escribiste es lo que ves» también al volver.
            // Wikilinks [[así]] abren la página de esa entidad.
            MarkdownText(
                text = entry.body,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 28.sp
                ),
                onWikilinkClick = { name ->
                    allEntities.firstOrNull { e ->
                        e.displayName.equals(name, ignoreCase = true) ||
                            e.canonicalName.equals(name, ignoreCase = true) ||
                            e.aliases.split(',').any { it.trim().equals(name, ignoreCase = true) }
                    }?.let { found -> onNavigateToEntity(found.id) }
                }
            )

            // Multi-Page Sections
            val pages = itemWithRelations?.pages.orEmpty()
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(14.dp))
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
                    onClick = { showAddSectionDialog = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp).testTag("detail_add_section_btn")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Section", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (pages.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Organize this memory into sub-pages or chapters (e.g. Day 1, Quotes, Decisions). Tap 'Add Section' above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                pages.forEachIndexed { idx, p ->
                    EditorialCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("entry_page_${p.id}")) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = p.title.ifBlank { "Section ${idx + 1}" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            MarkdownText(
                                text = p.body,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                            )
                        }
                    }
                }
            }

            // Voice Journal / Audio Recordings
            val audioRecords = itemWithRelations?.audioRecords.orEmpty()
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Voice Journal (${audioRecords.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startVoiceRecordingWithDictation()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("detail_record_note_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Note", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { showDictateNoteDialog = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("detail_dictate_note_btn")
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dictate Note", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.stopVoiceRecording(entryId, title = "Voice Note (${entry.title.ifBlank { "Memory" }})")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("detail_stop_record_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop Recording", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (isRecording) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TerracottaAccent.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TerracottaAccent, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recording voice reflection... Tap Stop when done to save and transcribe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TerracottaAccent
                        )
                    }
                }
            }

            if (audioRecords.isEmpty() && !isRecording) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No audio reflections recorded for this memory yet. Tap 'Record Note' to record audio or 'Dictate Note' to transcribe speech on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                audioRecords.forEach { record ->
                    val isThisPlaying = playbackState == PlaybackState.PLAYING && currentPlayingPath == record.filePath
                    EditorialCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("audio_record_${record.id}")) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.title.ifBlank { "Voice Note" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    val durationSec = record.durationMs / 1000
                                    Text(
                                        text = "${durationSec / 60}m ${durationSec % 60}s • Local audio recording",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (record.filePath.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    viewModel.stopAudio()
                                                } else {
                                                    viewModel.playAudio(record.filePath)
                                                }
                                            },
                                            modifier = Modifier.testTag("play_pause_audio_${record.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisPlaying) "Pause" else "Play",
                                                tint = ForestPrimary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { transcribingRecord = record },
                                        modifier = Modifier.testTag("transcribe_audio_btn_${record.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (record.transcript.isBlank()) Icons.Default.RecordVoiceOver else Icons.Default.EditNote,
                                            contentDescription = "Transcribe / Dictate",
                                            tint = if (record.transcript.isBlank()) TerracottaAccent else ForestPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteAudio(record.id, entryId) },
                                        modifier = Modifier.testTag("delete_audio_${record.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (record.transcript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "On-Device Transcription:",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ForestPrimary
                                            )
                                            TextButton(
                                                onClick = { transcribingRecord = record },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                modifier = Modifier.height(24.dp).testTag("edit_transcript_btn_${record.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Edit", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = record.transcript,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (whistleUsable) {
                                    // ONE-TAP local transcription of the saved file.
                                    Button(
                                        onClick = { oneTapTranscribe(record) },
                                        enabled = record.id !in transcribingIds,
                                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("one_tap_transcribe_btn_${record.id}")
                                    ) {
                                        if (record.id in transcribingIds) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Transcribiendo con IA local…", style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Transcribir con IA local (1 toque)", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "O toca el icono para dictar encima / editar a mano.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { transcribingRecord = record },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("transcribe_audio_action_btn_${record.id}")
                                    ) {
                                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Transcribe Audio (Speech-to-Text / Edit)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Related entries via Needle embeddings (semantic neighbours) ----
            // Duplicate flagging needs LEXICAL corroboration: the small
            // model's raw embeddings are anisotropic (everything scores
            // 0.90-0.95 cosine), so similitud alone cried "duplicado" on every
            // note. Now: embedding sim >= 0.95 AND >=60% of the same words.
            val relatedContext = LocalContext.current
            data class RelatedRow(val id: Long, val title: String, val sim: Float, val lexical: Float)
            var semanticNeighbours by remember(entryId) {
                mutableStateOf<List<RelatedRow>>(emptyList())
            }
            fun normalizeForDupes(s: String): Set<String> =
                java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}"), "")
                    .split(Regex("[^a-z0-9ñà-ÿ]+"))
                    .filter { it.length > 2 && it !in SPANISH_STOPWORDS }
                    .toSet()
            LaunchedEffect(entryId) {
                semanticNeighbours = emptyList()
                if (com.example.semantic.NeedleEmbeddings.isAvailable(relatedContext)) {
                    semanticNeighbours = try {
                        val entries = viewModel.repository.allEntriesWithRelations.first().take(400)
                        val current = entries.firstOrNull { it.entry.id == entryId }
                        val currentTokens = normalizeForDupes(
                            (current?.entry?.title.orEmpty() + " " + current?.entry?.body.orEmpty())
                        )
                        com.example.semantic.NeedleEmbeddings
                            .relatedEntries(relatedContext, entryId, entries, topK = 3)
                            .mapNotNull { (id, sim) ->
                                entries.firstOrNull { it.entry.id == id }?.let { e ->
                                    val otherTokens = normalizeForDupes(
                                        e.entry.title + " " + e.entry.body
                                    )
                                    val overlap = (
                                        if (currentTokens.isEmpty() || otherTokens.isEmpty()) 0f
                                        else currentTokens.intersect(otherTokens).size.toFloat() /
                                            maxOf(currentTokens.size, otherTokens.size)
                                        )
                                    RelatedRow(
                                        id = id,
                                        title = e.entry.title.ifBlank { "(sin título)" },
                                        sim = sim,
                                        lexical = overlap
                                    )
                                }
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            if (semanticNeighbours.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForestPrimary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("semantic_related_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Relacionados por significado (IA local)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        semanticNeighbours.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToEntry(row.id) }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.title.take(48),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (row.sim >= 0.95f && row.lexical >= 0.6f) {
                                    Text(
                                        text = "posible duplicado",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "${(row.sim * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Similitud por embeddings de Needle (el aviso de duplicado exige también ≥60% de vocabulario común, porque el modelo pequeño inflaba la similitud de notas no relacionadas).",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Suggested Topics / Tags (Review section)
            val pendingSuggested = suggestedTags.filter { it.status == "PENDING" }
            if (pendingSuggested.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberNode.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("suggested_tags_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberNode, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Suggested Topics (Auto-detected)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pendingSuggested.forEach { suggested ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberNode.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Text("#${suggested.name}", style = MaterialTheme.typography.labelSmall)
                                        IconButton(
                                            onClick = { viewModel.acceptSuggestedTag(suggested) },
                                            modifier = Modifier.size(24.dp).testTag("accept_tag_${suggested.name}")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept Tag", tint = ForestPrimary, modifier = Modifier.size(13.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissSuggestedTag(suggested.id) },
                                            modifier = Modifier.size(24.dp).testTag("dismiss_tag_${suggested.name}")
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Dismiss Tag", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Extracted Entities
            if (!itemWithRelations?.entities.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Discovered Entities & Concepts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemWithRelations?.entities?.forEach { entity ->
                        EntityChip(
                            name = entity.displayName,
                            type = entity.type,
                            onClick = { onNavigateToEntity(entity.id) }
                        )
                    }
                }
            }

            // Permanent Tags
            if (!itemWithRelations?.tags.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemWithRelations?.tags?.forEach { tag ->
                        androidx.compose.runtime.key(tag.id) {
                            TagChip(
                                tag = tag.name,
                                onRemove = {
                                    viewModel.removeTagFromEntry(entryId, tag.id)
                                }
                            )
                        }
                    }
                }
            }

            // Explainable Semantic Connections (MindForger / PKM inspired)
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = AmberNode,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Explainable Related Memories (${relatedEntries.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (relatedEntries.isEmpty()) {
                Text(
                    text = "No semantic links found above threshold yet. As you write more entries, associations will automatically form.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                relatedEntries.forEach { relDetail ->
                    EditorialCard(
                        onClick = { onNavigateToEntry(relDetail.entry.id) },
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .testTag("related_entry_${relDetail.entry.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = relDetail.entry.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = ForestPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${(relDetail.relationship.score * 100).toInt()}% match",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ForestPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = relDetail.relationship.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Face Link Dialog
    faceToLinkIndex?.let { faceIdx ->
        val people = allEntities.filter { it.type == EntityType.PERSON }
        val mediaId = itemWithRelations?.mediaItems?.firstOrNull()?.id ?: 0L

        AlertDialog(
            onDismissRequest = { faceToLinkIndex = null },
            title = { Text("Link Face to Person") },
            text = {
                Column {
                    Text("Select a known person entity to associate with this face:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (people.isEmpty()) {
                        Text("No person entities detected yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        people.forEach { person ->
                            TextButton(
                                onClick = {
                                    viewModel.associateFaceWithPerson(mediaId, faceIdx, person.id)
                                    faceToLinkIndex = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(person.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { faceToLinkIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Memory?") },
            text = { Text("This will permanently delete this journal entry and remove all its associations from the knowledge graph.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entry.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Offline Translation Bottom Sheet
    if (showTranslateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTranslateSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = ForestPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "On-Device Offline Translation",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Powered by Google ML Kit. Models execute entirely locally on-device without internet once downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                var expanded by remember { mutableStateOf(false) }
                val targetLang = translationState.targetLanguage
                val selectedLangName = MlKitAnalyzer.POPULAR_LANGUAGES.find { it.code == targetLang }?.displayName ?: targetLang

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = selectedLangName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Translate into") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName) },
                                onClick = {
                                    expanded = false
                                    viewModel.checkTranslationModel(lang.code)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.translateEntry(
                            entryText = "${entry.title}\n\n${entry.body}",
                            sourceLang = entry.language ?: "en",
                            targetLang = targetLang
                        )
                    },
                    enabled = !translationState.isTranslating,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("perform_translate_button")
                ) {
                    if (translationState.isTranslating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translating On-Device...")
                    } else {
                        Text("Translate Offline")
                    }
                }

                if (translationState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notice: ${translationState.errorMessage}. Language pack can be downloaded in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (translationState.translatedText != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Translated Text ($selectedLangName):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = translationState.translatedText ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddSectionDialog) {
        AlertDialog(
            onDismissRequest = { showAddSectionDialog = false },
            title = { Text("Add Section / Page") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = newSectionTitle,
                        onValueChange = { newSectionTitle = it },
                        label = { Text("Section Title (e.g. Day 2, Quotes)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_section_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newSectionBody,
                        onValueChange = { newSectionBody = it },
                        label = { Text("Content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("new_section_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSectionTitle.isNotBlank() || newSectionBody.isNotBlank()) {
                            val newPage = JournalPage(
                                entryId = entryId,
                                pageIndex = itemWithRelations?.pages?.size ?: 0,
                                title = newSectionTitle.trim(),
                                body = newSectionBody.trim()
                            )
                            viewModel.savePage(newPage, entryId)
                            newSectionTitle = ""
                            newSectionBody = ""
                            showAddSectionDialog = false
                            Toast.makeText(context, "Section added to memory!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.testTag("confirm_add_section_btn")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (transcribingRecord != null) {
        VoiceTranscriptionModal(
            title = "Transcribe Voice Note",
            initialTranscript = transcribingRecord?.transcript.orEmpty(),
            audioFilePath = transcribingRecord?.filePath,
            durationMs = transcribingRecord?.durationMs ?: 0L,
            viewModel = viewModel,
            onDismiss = { transcribingRecord = null },
            onSaveTranscript = { newTranscript ->
                transcribingRecord?.let { rec ->
                    viewModel.updateAudioTranscript(rec.id, entryId, newTranscript)
                    Toast.makeText(context, "Transcript saved and knowledge connections updated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDictateNoteDialog) {
        VoiceTranscriptionModal(
            title = "Dictate Spoken Reflection",
            initialTranscript = "",
            audioFilePath = null,
            durationMs = 0L,
            viewModel = viewModel,
            onDismiss = { showDictateNoteDialog = false },
            onSaveTranscript = { newTranscript ->
                if (newTranscript.isNotBlank()) {
                    viewModel.addAudioRecord(
                        entryId = entryId,
                        title = "Dictated Thought",
                        filePath = "",
                        durationMs = 0L,
                        transcript = newTranscript,
                        status = "COMPLETED",
                        onDone = {
                            Toast.makeText(context, "Dictated reflection saved and linked in knowledge graph!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }
}

/**
 * Renders body text with interactive autolink spans.
 * Tapping an annotated entity jumps directly to its knowledge page.
 */
@Composable
private fun AutolinkedText(
    fullText: String,
    spans: List<AutolinkSpan>,
    onEntityClick: (Long) -> Unit
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val sortedSpans = spans.sortedBy { it.startOffset }

        for (span in sortedSpans) {
            if (span.startOffset < currentIndex || span.endOffset > fullText.length) continue

            // Text before the entity
            if (span.startOffset > currentIndex) {
                append(fullText.substring(currentIndex, span.startOffset))
            }

            // Entity Span
            pushStringAnnotation(tag = "ENTITY_LINK", annotation = span.entityId.toString())
            pushStyle(
                SpanStyle(
                    color = ForestPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            )
            append(fullText.substring(span.startOffset, span.endOffset))
            pop()
            pop()

            currentIndex = span.endOffset
        }

        // Remainder of text
        if (currentIndex < fullText.length) {
            append(fullText.substring(currentIndex))
        }
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(
            fontSize = 17.sp,
            lineHeight = 28.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurface
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "ENTITY_LINK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    annotation.item.toLongOrNull()?.let { entityId ->
                        onEntityClick(entityId)
                    }
                }
        }
    )
}

/** Stopwords for the duplicate-detection lexical overlap (3+ letters only). */
private val SPANISH_STOPWORDS = setOf(
    "que", "los", "las", "una", "uno", "por", "para", "como", "pero", "cuando",
    "con", "sin", "sobre", "entre", "este", "esta", "estos", "estas", "eso",
    "esa", "esos", "esas", "más", "muy", "todo", "toda", "todos", "todas",
    "fue", "son", "está", "estan", "están", "han", "hay", "ser", "estar",
    "también", "tambien", "the", "and", "for", "with", "this", "that", "have",
    "from", "are", "was", "were", "not", "you", "your"
)
