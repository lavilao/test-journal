package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.WhistleKeywordBuilder
import com.example.data.model.AudioRecordItem
import com.example.data.model.AutolinkSpan
import com.example.data.model.EntityType
import com.example.data.model.JournalEntry
import com.example.data.model.JournalPage
import com.example.media.AudioDecode
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.media.SubjectSegmentationManager
import com.example.semantic.MlKitAnalyzer
import com.example.ui.components.EditorialCard
import com.example.ui.components.EntityChip
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarkdownText
import com.example.ui.components.TagChip
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.theme.AmberNode
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.tts.NoteNarrator
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    onNavigateToEntry: (Long) -> Unit,
    onCreateSubPage: ((Long) -> Unit)? = null
) {
    LaunchedEffect(entryId) {
        viewModel.selectEntry(entryId)
    }

    val itemWithRelations by viewModel.selectedEntryDetail.collectAsState()
    val relatedEntries by viewModel.relatedEntries.collectAsState()
    val suggestedTags by viewModel.suggestedTags.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    val allEntities by viewModel.entities.collectAsState()
    val playbackState by viewModel.voiceManager.playbackState.collectAsState()
    val currentPlayingPath by viewModel.voiceManager.currentPlayingPath.collectAsState()
    val childPages by remember(entryId) { viewModel.getChildPages(entryId) }.collectAsState(initial = emptyList())

    var breadcrumbs by remember(entryId) { mutableStateOf<List<JournalEntry>>(emptyList()) }
    LaunchedEffect(entryId, itemWithRelations?.entry?.parentEntryId, itemWithRelations?.entry?.title) {
        breadcrumbs = viewModel.getBreadcrumbs(entryId)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var faceToLinkIndex by remember { mutableStateOf<Int?>(null) }
    var transcribingRecord by remember { mutableStateOf<AudioRecordItem?>(null) }
    var showDictateNoteDialog by remember { mutableStateOf(false) }
    var showCreateSubPageDialog by remember { mutableStateOf(false) }
    var newSubPageTitle by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordingState by viewModel.voiceManager.recordingState.collectAsState()
    val isRecording = recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED

    var showAddSectionDialog by remember { mutableStateOf(false) }
    var newSectionTitle by remember { mutableStateOf("") }
    var newSectionBody by remember { mutableStateOf("") }
    var editingSectionId by remember { mutableStateOf<Long?>(null) }
    var sectionPendingDelete by remember { mutableStateOf<JournalPage?>(null) }

    // Read-aloud: KittenTTS when selected, system voice otherwise.
    val narrator = remember { NoteNarrator(context) }
    val narrationState by narrator.state.collectAsState()
    val narrationError by narrator.error.collectAsState()
    DisposableEffect(narrator) {
        onDispose { narrator.close() }
    }
    LaunchedEffect(entryId) { narrator.stop() }
    LaunchedEffect(narrationError) {
        narrationError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            narrator.clearError()
        }
    }

    var transcribingIds by remember(entryId) { mutableStateOf(setOf<Long>()) }
    val whistleUsable = remember {
        NeedleModelManager.isWhistleDictationEnabled(context) &&
            NeedleModelManager.isWhistleDownloaded(context)
    }

    fun oneTapTranscribe(record: AudioRecordItem) {
        if (record.filePath.isBlank()) {
            Toast.makeText(context, "Esta nota no tiene archivo de audio.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            transcribingIds = transcribingIds + record.id
            try { NeedleModelManager.ensureLoaded(context) } catch (_: Exception) {}
            val pcm = AudioDecode.decodeToPcm16kMono(record.filePath)
            val entityNames = itemWithRelations?.entities?.map { it.displayName }.orEmpty()
            val keywords = WhistleKeywordBuilder.forJournal(context, entityNames)
            val parsed = if (pcm != null && pcm.isNotEmpty()) {
                withTimeoutOrNull(120_000) {
                    NeedleRuntime.transcribeLongAudio(pcm, "es", keywords)
                }
            } else null
            transcribingIds = transcribingIds - record.id
            if (parsed != null && parsed.text.isNotBlank()) {
                viewModel.updateAudioTranscript(record.id, entryId, parsed.text)
                Toast.makeText(context, "Nota de voz transcrita ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No pude transcribir este audio (¿silencio o formato?).", Toast.LENGTH_LONG).show()
            }
        }
    }

    var photoProcessing by remember(entryId) { mutableStateOf<String?>(null) }
    var photoResultUri by remember(entryId) { mutableStateOf<android.net.Uri?>(null) }
    var selectedMediaId by remember(entryId) { mutableStateOf<Long?>(null) }
    var viewerMirageBusy by remember(entryId) { mutableStateOf(false) }
    var viewerPhotoUri by remember(entryId) { mutableStateOf<String?>(null) }

    fun runPhotoOp(sourceUri: String?, op: SubjectSegmentationManager.Op) {
        if (sourceUri.isNullOrBlank()) {
            Toast.makeText(context, "No hay foto que procesar.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = android.net.Uri.parse(sourceUri)
        scope.launch {
            val baseLabel = if (op == SubjectSegmentationManager.Op.REMOVE_BACKGROUND) "quitando el fondo" else "quitando el sujeto"
            photoProcessing = baseLabel
            var result = try {
                SubjectSegmentationManager.process(context, uri, op)
            } catch (e: Exception) {
                if (SubjectSegmentationManager.isModelMissing(e)) {
                    SubjectSegmentationManager.SegResult.ModelNeeded(e.message ?: "falta modelo")
                } else {
                    SubjectSegmentationManager.SegResult.Failed(e.message ?: "error inesperado")
                }
            }

            if (result is SubjectSegmentationManager.SegResult.ModelNeeded) {
                photoProcessing = "descargando el modelo (una sola vez)"
                val ready = SubjectSegmentationManager.ensureModel(context) { state, pct ->
                    photoProcessing = if (pct >= 0) "$state $pct%" else state
                }
                if (ready) {
                    for (attempt in 0 until 2) {
                        photoProcessing = baseLabel
                        result = try {
                            SubjectSegmentationManager.process(context, uri, op)
                        } catch (e: Exception) {
                            SubjectSegmentationManager.SegResult.Failed(e.message ?: "error inesperado")
                        }
                        if (result is SubjectSegmentationManager.SegResult.ModelNeeded && attempt == 0) {
                            delay(1200)
                        } else {
                            break
                        }
                    }
                }
            }

            if (result is SubjectSegmentationManager.SegResult.ModelNeeded) {
                photoProcessing = if (op == SubjectSegmentationManager.Op.REMOVE_BACKGROUND) {
                    "recorte rápido (sin IA)"
                } else {
                    "difuminando sujeto (local)"
                }
                val fallback = SubjectSegmentationManager.softwareFallback(context, uri, op)
                photoProcessing = null
                if (fallback != null) {
                    photoResultUri = android.net.Uri.fromFile(fallback)
                    viewModel.addPhoto(entryId, photoResultUri!!)
                    val msg = if (op == SubjectSegmentationManager.Op.REMOVE_BACKGROUND) {
                        "Recorte rápido local aplicado (sin modelo ML Kit en este dispositivo)."
                    } else {
                        "Sujeto difuminado en modo local rápido (sin modelo ML Kit en este dispositivo)."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        "No se pudo procesar la imagen en modo local (${result.reason}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            photoProcessing = null
            when (result) {
                is SubjectSegmentationManager.SegResult.Success -> {
                    photoResultUri = android.net.Uri.fromFile(result.file)
                    viewModel.addPhoto(entryId, photoResultUri!!)
                    Toast.makeText(context, "Foto procesada y adjuntada (IA local).", Toast.LENGTH_SHORT).show()
                }
                is SubjectSegmentationManager.SegResult.ModelNeeded -> Unit
                is SubjectSegmentationManager.SegResult.Failed -> {
                    Toast.makeText(context, "La segmentación falló: ${result.reason}.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addPhoto(entryId, uri)
            Toast.makeText(context, "Foto adjuntada y analizada con IA local.", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecordingWithDictation()
        } else {
            Toast.makeText(context, "Se necesita permiso de micrófono para grabar notas de voz", Toast.LENGTH_SHORT).show()
        }
    }

    val dateFormat = remember { SimpleDateFormat("d 'de' MMM yyyy · H:mm", Locale.getDefault()) }
    val entry = itemWithRelations?.entry

    if (entry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val coverColor = remember(entry.coverColorHex) { parseCoverHexOrNull(entry.coverColorHex) }

    fun narrationText(): String = buildString {
        append(entry.title.ifBlank { "Nota sin título" })
        append(". ")
        if (entry.body.isNotBlank()) {
            append(entry.body)
            append(' ')
        }
        itemWithRelations?.pages.orEmpty().forEach { page ->
            if (page.title.isNotBlank()) {
                append(page.title)
                append(". ")
            }
            if (page.body.isNotBlank()) {
                append(page.body)
                append(' ')
            }
        }
    }

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
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }

                    // Breadcrumbs (Espacio › Padre › Página)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Espacio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onBack)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        breadcrumbs.forEachIndexed { idx, crumb ->
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                            val isCurrent = idx == breadcrumbs.lastIndex
                            Text(
                                text = "${crumb.iconEmoji} ${crumb.title.ifBlank { "Página" }.take(18)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isCurrent) { onNavigateToEntry(crumb.id) }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleEntryPin(entry.id, !entry.isPinned) },
                        modifier = Modifier.testTag("detail_pin_button")
                    ) {
                        Icon(
                            imageVector = if (entry.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Fijar",
                            tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { showTranslateSheet = true },
                        modifier = Modifier.testTag("detail_translate_button")
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = "Traducir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = { narrator.toggle(narrationText()) },
                        modifier = Modifier.testTag("detail_narrate_button")
                    ) {
                        val reading = narrationState != NoteNarrator.State.IDLE
                        Icon(
                            imageVector = if (reading) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (reading) "Detener lectura" else "Escuchar nota",
                            tint = if (reading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { onEdit(entry.id) },
                        modifier = Modifier.testTag("detail_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
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
            // Optional Muted Notion Cover
            if (coverColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(coverColor.copy(alpha = 0.86f), coverColor.copy(alpha = 0.62f))
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Page Emoji + Headline Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = entry.iconEmoji.ifBlank { "📄" }, fontSize = 24.sp)
                        }
                    }
                    Text(
                        text = entry.title.ifBlank { "Página sin título" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            lineHeight = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notion Properties Grid
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Estado (tap cycles Inbox -> En curso -> Listo)
                        val nextStatus = when (entry.status) {
                            "Inbox" -> "En curso"
                            "En curso" -> "Listo"
                            "Listo" -> "Inbox"
                            else -> "Inbox"
                        }
                        val statusTint = when (entry.status) {
                            "Listo" -> NowBriefVitalsGreen
                            "En curso" -> MaterialTheme.colorScheme.primary
                            "Archivado" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> TerracottaAccent
                        }
                        DetailPropertyRow(label = "Estado") {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusTint.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.updateEntryStatus(entry.id, nextStatus) }
                            ) {
                                Text(
                                    text = "${entry.status} ↻",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = statusTint,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Prioridad: ${entry.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DetailPropertyRow(label = "Fecha") {
                            Text(
                                text = dateFormat.format(Date(entry.journalDate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!entry.mood.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TerracottaAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = entry.mood,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TerracottaAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!entry.location.isNullOrBlank()) {
                            DetailPropertyRow(label = "Contexto") {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = entry.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subtle single-line page tool bar (preserving testTags)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailToolChip(
                        icon = Icons.Default.Add,
                        label = "Sub-página",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            if (onCreateSubPage != null) {
                                onCreateSubPage(entry.id)
                            } else {
                                showCreateSubPageDialog = true
                            }
                        }
                    )
                    DetailToolChip(
                        icon = Icons.Default.Translate,
                        label = "Traducir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("prominent_translate_button"),
                        onClick = { showTranslateSheet = true }
                    )
                    if (!isRecording) {
                        DetailToolChip(
                            icon = Icons.Default.Mic,
                            label = "Grabar voz",
                            tint = TerracottaAccent,
                            modifier = Modifier.testTag("record_voice_pill_button"),
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) viewModel.startVoiceRecordingWithDictation()
                                else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    } else {
                        Button(
                            onClick = {
                                viewModel.stopVoiceRecording(entryId, title = "Nota de voz (${entry.title.ifBlank { "Página" }})")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("stop_voice_pill_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detener", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    DetailToolChip(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Foto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("add_photo_pill_button"),
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    DetailToolChip(
                        icon = Icons.Default.NoteAdd,
                        label = "Sección",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("add_section_pill_button"),
                        onClick = { showAddSectionDialog = true }
                    )
                }

                // Photo gallery + Mirage + Subject Segmentation
                val galleryMedia = itemWithRelations?.mediaItems.orEmpty()
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val galleryPhotos: List<String> = (
                    galleryMedia.map { it.uri } +
                        (if (galleryMedia.isEmpty() && !entry.imageUri.isNullOrBlank()) listOf(entry.imageUri) else emptyList())
                    ).distinct()
                val selectedIdx = galleryMedia.indexOfFirst { it.id == selectedMediaId }.takeIf { it >= 0 } ?: 0
                val media = galleryMedia.getOrNull(selectedIdx)

                if (galleryPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        galleryPhotos.forEachIndexed { index, uri ->
                            val matchingMedia = galleryMedia.firstOrNull { it.uri == uri }
                            Box(
                                modifier = Modifier
                                    .size(width = 128.dp, height = 96.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        matchingMedia?.let { selectedMediaId = it.id }
                                        viewerPhotoUri = uri
                                    }
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Foto ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { runPhotoOp(galleryPhotos.first(), SubjectSegmentationManager.Op.REMOVE_BACKGROUND) },
                            enabled = photoProcessing == null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("photo_remove_bg_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when {
                                    photoProcessing?.startsWith("quitando el fondo") == true -> "Procesando…"
                                    photoProcessing?.startsWith("descargando") == true -> "Modelo…"
                                    photoProcessing?.startsWith("recorte") == true -> "Recortando…"
                                    else -> "Quitar fondo"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = { runPhotoOp(galleryPhotos.first(), SubjectSegmentationManager.Op.REMOVE_SUBJECT) },
                            enabled = photoProcessing == null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("photo_remove_subject_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when {
                                    photoProcessing?.startsWith("quitando el sujeto") == true -> "Procesando…"
                                    photoProcessing?.startsWith("descargando") == true -> "Modelo…"
                                    photoProcessing?.startsWith("difuminando") == true -> "Borrando…"
                                    else -> "Quitar sujeto"
                                },
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
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }

                val mirageAvailable = NeedleModelManager.isMirageDownloaded(context)
                var mirageAnalyzing by remember(media?.id) { mutableStateOf(false) }
                if (media != null && (media.caption.isNotBlank() || media.labelsJson.isNotBlank() || media.faceCount > 0 || media.ocrText.isNotBlank() || mirageAvailable)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassCard(
                        cornerRadius = 12.dp,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberNode, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Análisis visual · ${selectedIdx + 1}/${galleryMedia.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (mirageAvailable) {
                                OutlinedButton(
                                    onClick = {
                                        mirageAnalyzing = true
                                        viewModel.describePhotoWithMirageDetailed(media.id, entry.id) { ok, err ->
                                            mirageAnalyzing = false
                                            if (!ok) {
                                                val detail = err?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                                                Toast.makeText(context, "Mirage no pudo describir la foto$detail", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !mirageAnalyzing,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("mirage_describe_photo_btn")
                                ) {
                                    Text(
                                        if (mirageAnalyzing) "Analizando…" else if (media.caption.isBlank()) "Describir Mirage" else "Reanalizar",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                        if (media.caption.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = media.caption, style = MaterialTheme.typography.bodySmall)
                        }
                        if (media.faceCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${media.faceCount} rostro(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = { faceToLinkIndex = 0 },
                                    modifier = Modifier.height(28.dp).testTag("link_face_button")
                                ) {
                                    Text("Identificar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (media.ocrText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OCR: ${media.ocrText.take(120)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive Notion Document Body (tappable checklists + Wikilink MarkdownText)
                InteractiveNotionBody(
                    body = entry.body,
                    onToggleChecklistLine = { lineIndex ->
                        viewModel.toggleChecklistLine(entry.id, lineIndex)
                    },
                    onWikilinkClick = { name ->
                        allEntities.firstOrNull { e ->
                            e.displayName.equals(name, ignoreCase = true) ||
                                e.canonicalName.equals(name, ignoreCase = true) ||
                                e.aliases.split(',').any { it.trim().equals(name, ignoreCase = true) }
                        }?.let { found -> onNavigateToEntity(found.id) }
                    }
                )

                // Sub-pages (Notion hierarchy)
                Spacer(modifier = Modifier.height(22.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sub-páginas (${childPages.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            if (onCreateSubPage != null) onCreateSubPage(entry.id)
                            else showCreateSubPageDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("detail_add_subpage_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nueva sub-página", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (childPages.isEmpty()) {
                    Text(
                        text = "Sin sub-páginas anidadas todavía.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        childPages.forEach { child ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onNavigateToEntry(child.entry.id) }
                                    .testTag("subpage_row_${child.entry.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(child.entry.iconEmoji.ifBlank { "📄" }, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = child.entry.title.ifBlank { "Sin título" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = child.entry.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Multi-Page Sections (in-page blocks)
                val pages = itemWithRelations?.pages.orEmpty()
                if (pages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secciones (${pages.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = { showAddSectionDialog = true },
                            modifier = Modifier.height(30.dp).testTag("detail_add_section_btn")
                        ) {
                            Text("+ Añadir", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    fun openSectionEditor(page: JournalPage) {
                        editingSectionId = page.id
                        newSectionTitle = page.title
                        newSectionBody = page.body
                        showAddSectionDialog = true
                    }
                    pages.forEachIndexed { idx, p ->
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("entry_page_${p.id}"),
                            onClick = { openSectionEditor(p) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = p.title.ifBlank { "Sección ${idx + 1}" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { openSectionEditor(p) },
                                        modifier = Modifier.size(30.dp).testTag("detail_edit_section_${p.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar sección",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { sectionPendingDelete = p },
                                        modifier = Modifier.size(30.dp).testTag("detail_delete_section_${p.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar sección",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
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

                // Voice Notes
                val audioRecords = itemWithRelations?.audioRecords.orEmpty()
                if (audioRecords.isNotEmpty() || isRecording) {
                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = TerracottaAccent, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notas de voz (${audioRecords.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        if (!isRecording) {
                            Row {
                                TextButton(
                                    onClick = {
                                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) viewModel.startVoiceRecordingWithDictation()
                                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.testTag("detail_record_note_btn")
                                ) { Text("Grabar", fontSize = 12.sp) }
                                TextButton(
                                    onClick = { showDictateNoteDialog = true },
                                    modifier = Modifier.testTag("detail_dictate_note_btn")
                                ) { Text("Dictar", fontSize = 12.sp) }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.stopVoiceRecording(entryId, title = "Nota de voz") },
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                                modifier = Modifier.height(30.dp).testTag("detail_stop_record_btn")
                            ) {
                                Text("Detener", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    audioRecords.forEach { record ->
                        val isThisPlaying = playbackState == PlaybackState.PLAYING && currentPlayingPath == record.filePath
                        EditorialCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("audio_record_${record.id}")) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.title.ifBlank { "Nota de voz" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        val durationSec = record.durationMs / 1000
                                        Text("${durationSec / 60}m ${durationSec % 60}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (record.filePath.isNotBlank()) {
                                            IconButton(
                                                onClick = { if (isThisPlaying) viewModel.stopAudio() else viewModel.playAudio(record.filePath) },
                                                modifier = Modifier.testTag("play_pause_audio_${record.id}")
                                            ) {
                                                Icon(if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        IconButton(
                                            onClick = { transcribingRecord = record },
                                            modifier = Modifier.testTag("transcribe_audio_btn_${record.id}")
                                        ) {
                                            Icon(if (record.transcript.isBlank()) Icons.Default.RecordVoiceOver else Icons.Default.EditNote, contentDescription = "Transcribir", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteAudio(record.id, entryId) },
                                            modifier = Modifier.testTag("delete_audio_${record.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                if (record.transcript.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(record.transcript, style = MaterialTheme.typography.bodySmall)
                                } else if (whistleUsable) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { oneTapTranscribe(record) },
                                        enabled = record.id !in transcribingIds,
                                        modifier = Modifier.fillMaxWidth().testTag("one_tap_transcribe_btn_${record.id}")
                                    ) {
                                        Text(if (record.id in transcribingIds) "Transcribiendo…" else "Transcribir con Whistle", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { transcribingRecord = record },
                                        modifier = Modifier.fillMaxWidth().testTag("transcribe_audio_action_btn_${record.id}")
                                    ) {
                                        Text("Transcribir o editar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Actionable tasks detected in note body
                val detectedActions = remember(entryId, entry.body, itemWithRelations?.audioRecords) {
                    com.example.semantic.LocalTextIntelligence.extractActionItems(
                        title = entry.title,
                        body = entry.body,
                        transcripts = itemWithRelations?.audioRecords?.map { it.transcript }.orEmpty()
                    )
                }
                var addedActionKeys by remember(entryId) { mutableStateOf(setOf<String>()) }
                if (detectedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LiquidGlassCard(
                        cornerRadius = 12.dp,
                        contentPadding = 12.dp,
                        containerColor = NowBriefVitalsGreen.copy(alpha = 0.06f),
                        borderColor = NowBriefVitalsGreen.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth().testTag("detected_actions_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Acciones detectadas",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = NowBriefVitalsGreen
                            )
                            detectedActions.forEach { action ->
                                val created = action.title in addedActionKeys
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val offsetMs = (action.dueOffsetMinutes ?: 120) * 60_000L
                                            viewModel.addReminder(
                                                title = action.title,
                                                category = "Nota",
                                                dueTimestamp = System.currentTimeMillis() + offsetMs
                                            )
                                            addedActionKeys = addedActionKeys + action.title
                                            Toast.makeText(context, "Tarea añadida ✓", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = !created,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(if (created) "Añadida" else "Crear tarea", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Semantic neighbours (Needle embeddings)
                val relatedContext = LocalContext.current
                data class RelatedRow(val id: Long, val title: String, val sim: Float, val lexical: Float)
                var semanticNeighbours by remember(entryId) { mutableStateOf<List<RelatedRow>>(emptyList()) }
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
                            val currentTokens = normalizeForDupes((current?.entry?.title.orEmpty() + " " + current?.entry?.body.orEmpty()))
                            com.example.semantic.NeedleEmbeddings
                                .relatedEntries(relatedContext, entryId, entries, topK = 3)
                                .mapNotNull { (id, sim) ->
                                    entries.firstOrNull { it.entry.id == id }?.let { e ->
                                        val otherTokens = normalizeForDupes(e.entry.title + " " + e.entry.body)
                                        val overlap = if (currentTokens.isEmpty() || otherTokens.isEmpty()) 0f
                                        else currentTokens.intersect(otherTokens).size.toFloat() / maxOf(currentTokens.size, otherTokens.size)
                                        RelatedRow(id, e.entry.title.ifBlank { "(sin título)" }, sim, overlap)
                                    }
                                }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                if (semanticNeighbours.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LiquidGlassCard(
                        cornerRadius = 12.dp,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth().testTag("semantic_related_card")
                    ) {
                        Text("Relacionadas por significado", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        semanticNeighbours.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigateToEntry(row.id) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.title.take(48), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("${(row.sim * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Suggested tags
                val pendingSuggested = suggestedTags.filter { it.status == "PENDING" }
                if (pendingSuggested.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LiquidGlassCard(
                        cornerRadius = 12.dp,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth().testTag("suggested_tags_card")
                    ) {
                        Text("Etiquetas sugeridas", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pendingSuggested.forEach { suggested ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, AmberNode.copy(alpha = 0.5f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 2.dp)) {
                                        Text("#${suggested.name}", style = MaterialTheme.typography.labelSmall)
                                        IconButton(onClick = { viewModel.acceptSuggestedTag(suggested) }, modifier = Modifier.size(24.dp).testTag("accept_tag_${suggested.name}")) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept Tag", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                        }
                                        IconButton(onClick = { viewModel.dismissSuggestedTag(suggested.id) }, modifier = Modifier.size(24.dp).testTag("dismiss_tag_${suggested.name}")) {
                                            Icon(Icons.Default.Close, contentDescription = "Dismiss Tag", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Entities & Tags
                if (!itemWithRelations?.entities.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text("Personas, lugares y conceptos", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemWithRelations?.entities?.forEach { entity ->
                            EntityChip(name = entity.displayName, type = entity.type, onClick = { onNavigateToEntity(entity.id) })
                        }
                    }
                }

                if (!itemWithRelations?.tags.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemWithRelations?.tags?.forEach { tag ->
                            key(tag.id) {
                                TagChip(tag = tag.name, onRemove = { viewModel.removeTagFromEntry(entryId, tag.id) })
                            }
                        }
                    }
                }

                // Graph Connections
                if (relatedEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, contentDescription = null, tint = AmberNode, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Páginas conectadas (${relatedEntries.size})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    relatedEntries.forEach { relDetail ->
                        EditorialCard(
                            onClick = { onNavigateToEntry(relDetail.entry.id) },
                            modifier = Modifier.padding(vertical = 4.dp).testTag("related_entry_${relDetail.entry.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${relDetail.entry.iconEmoji} ${relDetail.entry.title.ifBlank { "Sin título" }}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(relDetail.relationship.score * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(relDetail.relationship.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.navigationBarsPadding().height(36.dp))
            }
        }
    }

    // Create Sub-page Quick Dialog (fallback when no parent callback passed)
    if (showCreateSubPageDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSubPageDialog = false },
            title = { Text("Nueva sub-página") },
            text = {
                OutlinedTextField(
                    value = newSubPageTitle,
                    onValueChange = { newSubPageTitle = it },
                    label = { Text("Título de la sub-página") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val t = newSubPageTitle.trim().ifBlank { "Nueva sub-página" }
                    showCreateSubPageDialog = false
                    newSubPageTitle = ""
                    viewModel.createSubPage(entry.id, t) { newId ->
                        onNavigateToEntry(newId)
                    }
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSubPageDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Face Link Dialog
    faceToLinkIndex?.let { faceIdx ->
        val people = allEntities.filter { it.type == EntityType.PERSON }
        val mediaId = itemWithRelations?.mediaItems?.firstOrNull()?.id ?: 0L
        AlertDialog(
            onDismissRequest = { faceToLinkIndex = null },
            title = { Text("Vincular rostro con persona") },
            text = {
                Column {
                    if (people.isEmpty()) {
                        Text("Aún no hay personas registradas en tus páginas.", style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = { faceToLinkIndex = null }) { Text("Cancelar") }
            }
        )
    }

    // Fullscreen photo viewer
    viewerPhotoUri?.let { photoUri ->
        val mediaRow = itemWithRelations?.mediaItems
            ?.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            ?.firstOrNull { it.uri == photoUri }
        Dialog(onDismissRequest = { viewerPhotoUri = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewerPhotoUri = null }
                    ) {
                        AsyncImage(model = photoUri, contentDescription = "Foto ampliada", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runPhotoOp(photoUri, SubjectSegmentationManager.Op.REMOVE_BACKGROUND) },
                            enabled = photoProcessing == null && !viewerMirageBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Quitar fondo", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                        OutlinedButton(
                            onClick = { runPhotoOp(photoUri, SubjectSegmentationManager.Op.REMOVE_SUBJECT) },
                            enabled = photoProcessing == null && !viewerMirageBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Quitar sujeto", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                        if (mediaRow != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deletePhoto(mediaRow.id, entryId)
                                    viewerPhotoUri = null
                                },
                                modifier = Modifier.weight(0.6f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar foto", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar esta página?") },
            text = { Text("Esta acción eliminará permanentemente esta página y sus conexiones.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entry.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTranslateSheet) {
        ModalBottomSheet(onDismissRequest = { showTranslateSheet = false }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Traducción sin conexión", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(14.dp))
                var expanded by remember { mutableStateOf(false) }
                val targetLang = translationState.targetLanguage
                val selectedLangName = MlKitAnalyzer.POPULAR_LANGUAGES.find { it.code == targetLang }?.displayName ?: targetLang
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    TextField(
                        value = selectedLangName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Traducir a") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                        viewModel.translateEntry("${entry.title}\n\n${entry.body}", entry.language ?: "es", targetLang)
                    },
                    enabled = !translationState.isTranslating,
                    modifier = Modifier.fillMaxWidth().testTag("perform_translate_button")
                ) {
                    Text(if (translationState.isTranslating) "Traduciendo…" else "Traducir")
                }
                translationState.translatedText?.let { tr ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(tr, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddSectionDialog) {
        val editingPage = itemWithRelations?.pages?.firstOrNull { it.id == editingSectionId }
        AlertDialog(
            onDismissRequest = {
                showAddSectionDialog = false
                editingSectionId = null
            },
            title = { Text(if (editingPage != null) "Editar sección" else "Añadir sección") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSectionTitle,
                        onValueChange = { newSectionTitle = it },
                        label = { Text("Título") },
                        modifier = Modifier.fillMaxWidth().testTag("new_section_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSectionBody,
                        onValueChange = { newSectionBody = it },
                        label = { Text("Contenido") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("new_section_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSectionTitle.isNotBlank() || newSectionBody.isNotBlank()) {
                            viewModel.savePage(
                                JournalPage(
                                    id = editingPage?.id ?: 0L,
                                    entryId = entryId,
                                    pageIndex = editingPage?.pageIndex ?: (itemWithRelations?.pages?.size ?: 0),
                                    title = newSectionTitle.trim(),
                                    body = newSectionBody.trim()
                                ),
                                entryId
                            )
                            newSectionTitle = ""
                            newSectionBody = ""
                            editingSectionId = null
                            showAddSectionDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_section_btn")
                ) { Text(if (editingPage != null) "Guardar" else "Añadir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSectionDialog = false
                    editingSectionId = null
                }) { Text("Cancelar") }
            }
        )
    }

    sectionPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { sectionPendingDelete = null },
            title = { Text("Eliminar sección") },
            text = {
                Text("¿Eliminar «${pending.title.ifBlank { "Sección" }}»? No se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePage(pending.id, entryId)
                        sectionPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_section_btn")
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sectionPendingDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (transcribingRecord != null) {
        VoiceTranscriptionModal(
            title = "Transcribir nota de voz",
            initialTranscript = transcribingRecord?.transcript.orEmpty(),
            audioFilePath = transcribingRecord?.filePath,
            durationMs = transcribingRecord?.durationMs ?: 0L,
            viewModel = viewModel,
            onDismiss = { transcribingRecord = null },
            onSaveTranscript = { newTranscript ->
                transcribingRecord?.let { rec -> viewModel.updateAudioTranscript(rec.id, entryId, newTranscript) }
            }
        )
    }

    if (showDictateNoteDialog) {
        VoiceTranscriptionModal(
            title = "Dictar reflexión",
            initialTranscript = "",
            audioFilePath = null,
            durationMs = 0L,
            viewModel = viewModel,
            onDismiss = { showDictateNoteDialog = false },
            onSaveTranscript = { newTranscript ->
                if (newTranscript.isNotBlank()) {
                    viewModel.addAudioRecord(entryId, "Reflexión dictada", "", 0L, newTranscript, "COMPLETED")
                }
            }
        )
    }
}

/**
 * Renders Notion-style document body: plain/markdown blocks use MarkdownText
 * (supporting clickable [[wikilinks]]), while `- [ ]` / `- [x]` checklist
 * lines become real interactive Checkboxes persisted to Room.
 */
@Composable
private fun InteractiveNotionBody(
    body: String,
    onToggleChecklistLine: (Int) -> Unit,
    onWikilinkClick: (String) -> Unit
) {
    val lines = remember(body) { body.lines() }
    val taskRegex = remember { Regex("^(\\s*[-*+]\\s+)\\[([ xX])\\]\\s*(.*)$") }

    val blocks = remember(lines) {
        val out = mutableListOf<Any>()
        val proseBuffer = mutableListOf<String>()
        fun flushProse() {
            if (proseBuffer.isNotEmpty()) {
                out.add(proseBuffer.joinToString("\n"))
                proseBuffer.clear()
            }
        }
        lines.forEachIndexed { idx, line ->
            val match = taskRegex.find(line)
            if (match != null) {
                flushProse()
                val checked = match.groupValues[2].equals("x", ignoreCase = true)
                val label = match.groupValues[3]
                out.add(Triple(idx, checked, label))
            } else {
                proseBuffer.add(line)
            }
        }
        flushProse()
        out
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is String -> {
                    if (block.isNotBlank()) {
                        MarkdownText(
                            text = block,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            ),
                            onWikilinkClick = onWikilinkClick
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                is Triple<*, *, *> -> {
                    val lineIdx = block.first as Int
                    val checked = block.second as Boolean
                    val label = block.third as String
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleChecklistLine(lineIdx) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleChecklistLine(lineIdx) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPropertyRow(
    label: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun DetailToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val SPANISH_STOPWORDS = setOf(
    "que", "los", "las", "una", "uno", "por", "para", "como", "pero", "cuando",
    "con", "sin", "sobre", "entre", "este", "esta", "estos", "estas", "eso",
    "esa", "esos", "esas", "más", "muy", "todo", "toda", "todos", "todas",
    "fue", "son", "está", "estan", "están", "han", "hay", "ser", "estar",
    "también", "tambien", "the", "and", "for", "with", "this", "that", "have",
    "from", "are", "was", "were", "not", "you", "your"
)
