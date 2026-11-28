package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.AudioRecordItem
import com.example.data.model.EntityItem
import com.example.data.model.EntityType
import com.example.data.model.EntryWithRelations
import com.example.data.model.JournalEntry
import com.example.semantic.JournalInsights
import com.example.ui.components.GlassLabel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberNode
import com.example.ui.theme.NowBriefNewsBlue
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.ui.theme.SoftSage
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home desk, in the Artefact bento language: your whole second brain as cards
 * on a desk — a thought to start from, the latest note, open tasks you can
 * tick right here, voice memories, people you write about, and what happened
 * on this day.
 *
 * Capture is the interface, as in Artefact: one + and one mic float at the
 * bottom. Both open a quick-capture panel right here, so a thought is saved
 * without leaving the desk; the mic starts dictating into that panel and the
 * assistant moved to a long press (and to the capture card) instead of being
 * a destination of its own.
 */
private enum class DeskShelf(val label: String) {
    TODO("Todo"),
    NOTAS("Notas"),
    TAREAS("Tareas"),
    VOZ("Voz"),
    PERSONAS("Personas")
}

private data class DeskTask(
    val entryId: Long,
    val lineIndex: Int,
    val text: String,
    val sourceTitle: String
)

private val DESK_TASK_REGEX = Regex("^\\s*[-*+]\\s+\\[ ]\\s+(.+)$")

private val DESK_PROMPTS = listOf(
    "¿Qué pregunta te ronda hoy?",
    "Escribe una idea que no quieras perder.",
    "¿Qué aprendiste ayer que hoy aplicas?",
    "Una decisión que llevas aplazando…",
    "¿A quién deberías escribirle hoy?",
    "El detalle pequeño que hizo bien el día.",
    "¿Qué estás evitando mirar de frente?"
)

@Composable
fun HomeDeskScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onOpenLens: () -> Unit = {},
    onOpenAssistant: () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val memories by viewModel.onThisDayMemories.collectAsState()
    val weather by viewModel.realWeather.collectAsState()

    val stats = remember(entries) { JournalInsights.computeWritingStats(entries) }
    val active = remember(entries) { entries.filter { !it.entry.isArchived } }
    val recent = remember(active) { active.sortedByDescending { it.entry.updatedAt } }
    val latest = recent.firstOrNull()
    val tasks = remember(active) { collectDeskTasks(active) }
    val voiceNotes = remember(recent) {
        recent.filter { it.audioRecords.isNotEmpty() }.take(4)
    }
    val people = remember(entities) { entities.sortedByDescending { it.mentionCount }.take(6) }
    val memory = memories.firstOrNull()

    var shelfLabel by rememberSaveable { mutableStateOf(DeskShelf.TODO.label) }
    val shelf = DeskShelf.values().firstOrNull { it.label == shelfLabel } ?: DeskShelf.TODO

    val context = LocalContext.current
    val isDictating by viewModel.isDictating.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()

    // Quick capture: write or dictate without leaving the desk, and save only
    // when you decide to. ownDictation marks the sessions this screen started,
    // so leaving the desk never kills the assistant's own dictation.
    var captureOpen by rememberSaveable { mutableStateOf(false) }
    var captureText by rememberSaveable { mutableStateOf("") }
    var ownDictation by remember { mutableStateOf(false) }

    fun startCaptureDictation() {
        ownDictation = true
        viewModel.startDictation(
            onResult = { recognized ->
                val clean = recognized.trim()
                if (clean.isNotEmpty()) {
                    captureText = if (captureText.isBlank()) {
                        clean
                    } else {
                        captureText.trimEnd() + "\n" + clean
                    }
                }
            },
            onError = { ownDictation = false }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCaptureDictation() else ownDictation = false
    }

    fun stopOwnDictation() {
        if (ownDictation && isDictating) viewModel.stopDictation()
        ownDictation = false
    }

    fun toggleCaptureDictation() {
        if (ownDictation && isDictating) {
            stopOwnDictation()
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCaptureDictation()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openCapture(dictate: Boolean) {
        captureOpen = true
        if (dictate) toggleCaptureDictation()
    }

    DisposableEffect(Unit) {
        onDispose { stopOwnDictation() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp)
        ) {
            DeskHeader(
                weatherLine = weather.temperature?.let { temp ->
                    listOfNotNull(weather.conditionText, "$temp°", weather.locationName)
                        .joinToString(" · ")
                },
                onSearch = { viewModel.selectTab(MainNavTab.BUSCAR) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            DeskShelfChips(
                selected = shelf,
                onSelect = { shelfLabel = it.label }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (shelf) {
                DeskShelf.TODO -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DeskThoughtCard(
                                prompt = DESK_PROMPTS[dayOfYear() % DESK_PROMPTS.size],
                                dateLabel = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                    .format(Date()),
                                onClick = onNavigateToNewEntry
                            )
                            DeskRecentCard(
                                item = latest,
                                onClick = { latest?.let { onNavigateToDetail(it.entry.id) } }
                            )
                            memory?.let { old ->
                                DeskOnThisDayCard(
                                    entry = old,
                                    onClick = { onNavigateToDetail(old.id) }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DeskTasksCard(
                                tasks = tasks,
                                onToggle = { task -> viewModel.toggleChecklistLine(task.entryId, task.lineIndex) },
                                onOpen = { task -> onNavigateToDetail(task.entryId) },
                                maxVisible = 4
                            )
                            DeskCaptureCard(
                                onWrite = { openCapture(dictate = false) },
                                onDictate = { openCapture(dictate = true) },
                                onAsk = onOpenAssistant,
                                onScan = onOpenLens
                            )
                            voiceNotes.firstOrNull()?.let { note ->
                                DeskVoiceCard(note = note, onClick = { onNavigateToDetail(note.entry.id) })
                            }
                            if (people.isNotEmpty()) {
                                DeskPeopleCard(
                                    people = people.take(3),
                                    onClick = { entity ->
                                        viewModel.selectEntity(entity.id)
                                        viewModel.selectTab(MainNavTab.ENTITIES)
                                    }
                                )
                            }
                        }
                    }
                }

                DeskShelf.NOTAS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeskRecentCard(
                            item = latest,
                            onClick = { latest?.let { onNavigateToDetail(it.entry.id) } }
                        )
                        if (recent.size > 1) {
                            recent.drop(1).take(3).forEach { item ->
                                DeskNoteRow(item = item, onClick = { onNavigateToDetail(item.entry.id) })
                            }
                        }
                        memory?.let { old ->
                            DeskOnThisDayCard(entry = old, onClick = { onNavigateToDetail(old.id) })
                        }
                        DeskStatsCard(stats = stats, words = active.sumOf { it.entry.wordCount })
                    }
                }

                DeskShelf.TAREAS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeskTasksCard(
                            tasks = tasks,
                            onToggle = { task -> viewModel.toggleChecklistLine(task.entryId, task.lineIndex) },
                            onOpen = { task -> onNavigateToDetail(task.entryId) },
                            maxVisible = 12
                        )
                        DeskStatsCard(stats = stats, words = active.sumOf { it.entry.wordCount })
                    }
                }

                DeskShelf.VOZ -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (voiceNotes.isEmpty()) {
                            DeskEmptyCard(
                                title = "Sin notas de voz todavía",
                                body = "Graba lo que piensas mientras caminas; se transcribe en el teléfono.",
                                onClick = onOpenAssistant
                            )
                        } else {
                            voiceNotes.forEach { note ->
                                DeskVoiceCard(note = note, onClick = { onNavigateToDetail(note.entry.id) })
                            }
                        }
                        DeskCaptureCard(
                            onWrite = { openCapture(dictate = false) },
                            onDictate = { openCapture(dictate = true) },
                            onAsk = onOpenAssistant,
                            onScan = onOpenLens
                        )
                    }
                }

                DeskShelf.PERSONAS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (people.isEmpty()) {
                            DeskEmptyCard(
                                title = "Aún no hay personas",
                                body = "Escribe sobre alguien y aparecerá aquí para conectar recuerdos.",
                                onClick = onNavigateToNewEntry
                            )
                        } else {
                            DeskPeopleCard(
                                people = people,
                                onClick = { entity ->
                                    viewModel.selectEntity(entity.id)
                                    viewModel.selectTab(MainNavTab.ENTITIES)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(210.dp))
        }

        // Capture cluster, as in Artefact: write and dictate, nothing else.
        // The assistant and the camera stay reachable with a long press.
        if (!captureOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeskCircleAction(
                    icon = Icons.Default.Mic,
                    label = "Dictar",
                    onClick = { openCapture(dictate = true) },
                    onLongClick = onOpenAssistant
                )
                DeskCircleAction(
                    icon = Icons.Default.Add,
                    label = "Nueva nota",
                    onClick = { openCapture(dictate = false) },
                    onLongClick = onOpenLens,
                    primary = true
                )
            }
        }

        if (captureOpen) {
            DeskCapturePanel(
                text = captureText,
                onTextChange = { captureText = it },
                listening = ownDictation && isDictating,
                partial = partialTranscript,
                onMic = { toggleCaptureDictation() },
                onScan = {
                    stopOwnDictation()
                    onOpenLens()
                },
                onClose = {
                    stopOwnDictation()
                    captureOpen = false
                },
                onSave = {
                    val text = captureText.trim()
                    if (text.isNotEmpty()) {
                        stopOwnDictation()
                        val title = text.lineSequence()
                            .firstOrNull { it.isNotBlank() }
                            ?.trim()
                            ?.take(80)
                            ?: "Nota rápida"
                        viewModel.saveEntry(
                            title = title,
                            body = text,
                            iconEmoji = "✍️",
                            status = "Inbox"
                        )
                        captureText = ""
                        captureOpen = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 92.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Header and shelves
// ---------------------------------------------------------------------------

@Composable
private fun DeskHeader(weatherLine: String?, onSearch: () -> Unit) {
    val now = remember { Calendar.getInstance() }
    val greeting = when (now.get(Calendar.HOUR_OF_DAY)) {
        in 6..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tus artefactos",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 38.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()),
                    weatherLine?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            border = androidx.compose.foundation.BorderStroke(
                0.75.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
            ),
            modifier = Modifier
                .padding(top = 4.dp)
                .size(46.dp)
                .clip(CircleShape)
                .clickable(onClick = onSearch)
                .testTag("desk_search_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun DeskShelfChips(selected: DeskShelf, onSelect: (DeskShelf) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeskShelf.values().forEach { shelf ->
            val isSelected = shelf == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    AmberNode.copy(alpha = 0.30f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 1.dp else 0.75.dp,
                    if (isSelected) {
                        AmberNode.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                    }
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(shelf) }
                    .testTag("desk_shelf_${shelf.name.lowercase()}")
            ) {
                Text(
                    text = shelf.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun DeskCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        contentPadding = 16.dp,
        containerColor = accent.copy(alpha = 0.26f),
        borderColor = accent.copy(alpha = 0.42f),
        onClick = onClick,
        content = content
    )
}

@Composable
private fun DeskThoughtCard(prompt: String, dateLabel: String, onClick: () -> Unit) {
    DeskCard(accent = MaterialTheme.colorScheme.primary, onClick = onClick) {
        GlassLabel(
            text = "Pensamiento del día",
            accent = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = prompt,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 27.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeskCaptureCard(
    onWrite: () -> Unit,
    onDictate: () -> Unit,
    onAsk: () -> Unit,
    onScan: () -> Unit
) {
    DeskCard(accent = NowBriefNewsBlue) {
        GlassLabel(text = "Captura rápida", accent = NowBriefNewsBlue)
        Spacer(modifier = Modifier.height(6.dp))
        DeskActionRow(icon = Icons.Default.Add, title = "Escribir una nota", onClick = onWrite)
        DeskActionRow(icon = Icons.Default.Mic, title = "Dictar una nota", onClick = onDictate)
        DeskActionRow(icon = Icons.Default.RecordVoiceOver, title = "Preguntar al asistente", onClick = onAsk)
        DeskActionRow(icon = Icons.Default.CameraAlt, title = "Escanear con Lens", onClick = onScan)
    }
}

/**
 * Quick capture on the desk itself: the text stays here until you save it, and
 * dictation drops into the same field so its mistakes can be fixed by hand.
 */
@Composable
private fun DeskCapturePanel(
    text: String,
    onTextChange: (String) -> Unit,
    listening: Boolean,
    partial: String,
    onMic: () -> Unit,
    onScan: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("desk_capture_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Captura rápida",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar captura",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Escribe o dicta lo que no quieres perder…") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .testTag("desk_capture_field")
            )
            if (listening) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = partial.ifBlank { "Escuchando…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (listening) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onMic)
                        .testTag("desk_capture_mic")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (listening) "Detener dictado" else "Dictar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (listening) "Detener" else "Dictar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onScan, enabled = !listening) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Escanear con Lens",
                        modifier = Modifier.size(20.dp)
                    )
                }
                TextButton(onClick = onSave, enabled = text.isNotBlank()) {
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun DeskActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DeskRecentCard(item: EntryWithRelations?, onClick: () -> Unit) {
    if (item == null) {
        DeskEmptyCard(
            title = "Tu primera nota",
            body = "Escribe lo que tienes en la cabeza; el resto lo ordena la app.",
            onClick = onClick
        )
        return
    }
    val entry = item.entry
    val photos = item.mediaItems.sortedBy { it.sortOrder }.take(3)
    DeskCard(accent = AmberNode, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = entry.iconEmoji.ifBlank { "📄" }, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                GlassLabel(text = "Nota reciente", accent = AmberNode)
                Text(
                    text = deskRelative(entry.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = entry.title.ifBlank { "Sin título" },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 23.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.body.take(160),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (photos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photos.forEach { media ->
                    AsyncImage(
                        model = media.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildString {
                append("${entry.wordCount} palabras")
                if (item.tags.isNotEmpty()) append(" · ${item.tags.size} etiquetas")
                if (item.pages.isNotEmpty()) append(" · ${item.pages.size} secciones")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeskNoteRow(item: EntryWithRelations, onClick: () -> Unit) {
    DeskCard(accent = AmberNode, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.entry.iconEmoji.ifBlank { "📄" }, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.entry.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = deskRelative(item.entry.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DeskTasksCard(
    tasks: List<DeskTask>,
    onToggle: (DeskTask) -> Unit,
    onOpen: (DeskTask) -> Unit,
    maxVisible: Int
) {
    DeskCard(accent = NowBriefVitalsGreen) {
        GlassLabel(text = "Tareas abiertas", accent = NowBriefVitalsGreen)
        Spacer(modifier = Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            Text(
                text = "Nada pendiente. Escribe «- [ ] algo» en una nota y aparecerá aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tasks.take(maxVisible).forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(
                                1.6.dp,
                                NowBriefVitalsGreen.copy(alpha = 0.85f),
                                CircleShape
                            )
                            .clickable { onToggle(task) }
                            .testTag("desk_toggle_task_${task.entryId}_${task.lineIndex}")
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(task) }
                    ) {
                        Text(
                            text = task.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (task.sourceTitle.isNotBlank()) {
                            Text(
                                text = task.sourceTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (tasks.size > maxVisible) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${tasks.size - maxVisible} más",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeskVoiceCard(note: EntryWithRelations, onClick: () -> Unit) {
    val record: AudioRecordItem = note.audioRecords.first()
    val accent = NowBriefNewsBlue
    DeskCard(accent = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassLabel(text = "Nota de voz", accent = accent)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = deskDuration(record.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(26) { index ->
                val barHeight = 5 + ((index * 37 + note.entry.id.toInt()) % 20)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.copy(alpha = 0.55f))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = record.transcript.ifBlank { note.entry.title.ifBlank { "Sin transcripción" } }
                .take(110),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeskPeopleCard(people: List<EntityItem>, onClick: (EntityItem) -> Unit) {
    DeskCard(accent = SoftSage) {
        GlassLabel(text = "En tus notas", accent = SoftSage)
        Spacer(modifier = Modifier.height(8.dp))
        people.forEach { entity ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClick(entity) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (entity.type == EntityType.PERSON) Icons.Default.Person else Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entity.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = entityTypeLabel(entity.type),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeskOnThisDayCard(entry: JournalEntry, onClick: () -> Unit) {
    DeskCard(accent = TerracottaAccent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = TerracottaAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassLabel(text = "En este día", accent = TerracottaAccent)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entry.title.ifBlank { "Un recuerdo" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(entry.journalDate)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeskStatsCard(stats: JournalInsights.WritingStats, words: Int) {
    DeskCard(accent = MaterialTheme.colorScheme.tertiary) {
        GlassLabel(text = "Tu cerebro en números", accent = MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DeskStat(value = "${stats.totalNotes}", label = "notas")
            DeskStat(value = "${stats.streakDays}", label = "días seguidos")
            DeskStat(value = deskCompactNumber(words), label = "palabras")
        }
    }
}

@Composable
private fun DeskStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeskEmptyCard(title: String, body: String, onClick: () -> Unit) {
    DeskCard(accent = MaterialTheme.colorScheme.primary, onClick = onClick) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeskCircleAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val size = if (primary) 68.dp else 52.dp
    Surface(
        shape = CircleShape,
        color = if (primary) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
        ),
        shadowElevation = if (primary) 10.dp else 5.dp,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("desk_action_${label.lowercase().replace(' ', '_')}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (primary) 30.dp else 22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun collectDeskTasks(entries: List<EntryWithRelations>, maxItems: Int = 18): List<DeskTask> {
    val out = mutableListOf<DeskTask>()
    entries.forEach { item ->
        if (out.size >= maxItems) return out
        item.entry.body.lineSequence().forEachIndexed { index, line ->
            val match = DESK_TASK_REGEX.find(line)
            if (match != null && out.size < maxItems) {
                out.add(
                    DeskTask(
                        entryId = item.entry.id,
                        lineIndex = index,
                        text = match.groupValues[1].trim(),
                        sourceTitle = item.entry.title
                    )
                )
            }
        }
    }
    return out
}

private fun deskRelative(millis: Long): String {
    val diffMinutes = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "ahora"
        diffMinutes < 60 -> "hace $diffMinutes min"
        diffMinutes < 1_440 -> "hace ${diffMinutes / 60} h"
        diffMinutes < 2_880 -> "ayer"
        diffMinutes < 10_080 -> "hace ${diffMinutes / 1_440} días"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

private fun deskDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${String.format(Locale.US, "%02d", totalSeconds % 60)}"
}

private fun deskCompactNumber(value: Int): String = when {
    value >= 100_000 -> "${value / 1_000}k"
    value >= 10_000 -> "${value / 1_000}.${(value % 1_000) / 100}k"
    else -> value.toString()
}

private fun dayOfYear(): Int =
    Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

private fun entityTypeLabel(type: EntityType): String = when (type) {
    EntityType.PERSON -> "persona"
    EntityType.PLACE -> "lugar"
    EntityType.ORGANIZATION -> "org"
    EntityType.EVENT -> "evento"
    EntityType.DATE -> "fecha"
    EntityType.TOPIC -> "tema"
    EntityType.URL -> "enlace"
    EntityType.EMAIL -> "email"
    EntityType.PHONE -> "teléfono"
    EntityType.ADDRESS -> "dirección"
    EntityType.MONEY -> "dinero"
    EntityType.PROJECT -> "proyecto"
    EntityType.OTHER -> "otro"
}
