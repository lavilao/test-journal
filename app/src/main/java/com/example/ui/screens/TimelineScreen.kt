package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppInterfaceMode
import com.example.rss.RssArticle
import com.example.ui.components.DailyBriefingCard
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.components.GoogleDiscoverCard
import com.example.ui.components.GoogleMemoryCard
import com.example.ui.components.GoogleSearchHubHeader
import com.example.ui.components.RssOfflineReaderModal
import com.example.ui.components.StorypadNewNoteFab
import com.example.ui.components.StorypadNoteType
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.theme.ForestPrimary
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class HomeFeedView {
    NOTICIAS,
    MEMORIAS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val vaultItems by viewModel.vaultItems.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val nextReminder by viewModel.nextActiveReminder.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val isRssLoading by viewModel.isRssLoading.collectAsState()
    val interfaceMode by viewModel.interfaceMode.collectAsState()
    val realWeather by viewModel.realWeather.collectAsState()
    val calendarEvents by viewModel.upcomingCalendarEvents.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    var homeSearchQuery by remember { mutableStateOf("") }
    var showQuickDictateModal by remember { mutableStateOf(false) }
    var selectedFeedView by remember { mutableStateOf(HomeFeedView.NOTICIAS) }
    var selectedRssArticleForReading by remember { mutableStateOf<RssArticle?>(null) }
    var showPhotoChoiceDialog by remember { mutableStateOf(false) }

    // Gallery Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val persistentUri = viewModel.repository.persistImageToLocalStorage(uri)
            viewModel.saveEntry(
                id = 0L,
                title = "Foto de Galería",
                body = "Recuerdo visual guardado en el dispositivo.",
                attachedImageUri = Uri.parse(persistentUri),
                onComplete = { newId -> onNavigateToDetail(newId) }
            )
        }
    }

    // Camera Capture Launcher (Takes real photos)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val photoDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
                val photoFile = File(photoDir, "IMG_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val localUri = Uri.fromFile(photoFile)
                viewModel.saveEntry(
                    id = 0L,
                    title = "Foto de Cámara",
                    body = "Foto capturada con la cámara y guardada en el dispositivo.",
                    attachedImageUri = localUri,
                    onComplete = { newId -> onNavigateToDetail(newId) }
                )
                Toast.makeText(context, "Foto guardada en tus recuerdos", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Real-time Search Results for Files and Notes
    val searchResults = remember(homeSearchQuery, vaultItems, entries) {
        if (homeSearchQuery.isBlank()) {
            emptyList()
        } else {
            val q = homeSearchQuery.trim().lowercase()
            vaultItems.filter { item ->
                item.title.lowercase().contains(q) ||
                item.fileName.lowercase().contains(q) ||
                item.previewText.lowercase().contains(q) ||
                item.tags.any { it.name.lowercase().contains(q) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("timeline_screen_root")
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing || isRssLoading,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshRssFeeds()
                    viewModel.refreshWeather()
                    viewModel.refreshCalendarEvents()
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header (Google Pixel Style with borderless transparent At a Glance)
                item {
                    GoogleSearchHubHeader(
                        searchQuery = homeSearchQuery,
                        onSearchQueryChange = { homeSearchQuery = it },
                        telemetry = telemetry,
                        nextReminder = nextReminder,
                        weather = realWeather,
                        calendarEvent = calendarEvents.firstOrNull(),
                        onVoiceClick = { showQuickDictateModal = true },
                        onCameraClick = { showPhotoChoiceDialog = true },
                        onWeatherClick = { viewModel.openSystemWeatherApp() },
                        onReminderClick = { viewModel.selectTab(MainNavTab.NOTIFICACIONES) },
                        onCalendarClick = { viewModel.calendarSyncManager.openCalendarApp() },
                        onSettingsClick = { viewModel.selectTab(MainNavTab.SETTINGS) }
                    )
                }

                // Samsung Mode (NowBrief Widget)
                if (interfaceMode == AppInterfaceMode.SAMSUNG && homeSearchQuery.isBlank()) {
                    item {
                        DailyBriefingCard(
                            telemetry = telemetry,
                            nextReminder = nextReminder,
                            topArticle = rssArticles.firstOrNull(),
                            onWriteReflection = { title, prompt ->
                                viewModel.saveEntry(
                                    id = 0L,
                                    title = title,
                                    body = prompt,
                                    onComplete = { newId ->
                                        onNavigateToDetail(newId)
                                    }
                                )
                            }
                        )
                    }
                }

                // Mode 1: Search Query active -> Show Search Results for Files and Notes
                if (homeSearchQuery.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resultados en este dispositivo (${searchResults.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { homeSearchQuery = "" }) {
                                Text("Limpiar", fontSize = 12.sp)
                            }
                        }
                    }

                    if (searchResults.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No se encontraron coincidencias para \"$homeSearchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.saveEntry(
                                                id = 0L,
                                                title = homeSearchQuery.replaceFirstChar { it.uppercase() },
                                                body = "",
                                                onComplete = { newId -> onNavigateToDetail(newId) }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Crear nota con este título")
                                    }
                                }
                            }
                        }
                    } else {
                        items(searchResults, key = { it.id }) { fileItem ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clickable { onNavigateToDetail(fileItem.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when {
                                        fileItem.fileExtension == "m4a" -> Icons.Default.AudioFile
                                        fileItem.fileExtension == "jpg" -> Icons.Default.Image
                                        else -> Icons.Default.Description
                                    }
                                    val tint = when {
                                        fileItem.fileExtension == "m4a" -> GoogleGreen
                                        fileItem.fileExtension == "jpg" -> GoogleBlue
                                        else -> ForestPrimary
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tint.copy(alpha = 0.12f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileItem.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${fileItem.fileName} • ${fileItem.formattedSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Section Toggle: Noticias vs Mis Memorias
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedFeedView == HomeFeedView.NOTICIAS,
                                    onClick = { selectedFeedView = HomeFeedView.NOTICIAS },
                                    leadingIcon = {
                                        Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("Noticias") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoogleBlue.copy(alpha = 0.15f),
                                        selectedLabelColor = GoogleBlue
                                    )
                                )

                                FilterChip(
                                    selected = selectedFeedView == HomeFeedView.MEMORIAS,
                                    onClick = { selectedFeedView = HomeFeedView.MEMORIAS },
                                    leadingIcon = {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("Memorias (${entries.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestPrimary.copy(alpha = 0.15f),
                                        selectedLabelColor = ForestPrimary
                                    )
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.refreshRssFeeds()
                                        viewModel.refreshWeather()
                                        viewModel.refreshCalendarEvents()
                                        Toast.makeText(context, "Actualizando noticias y clima...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualizar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Feed Content
                    if (selectedFeedView == HomeFeedView.NOTICIAS) {
                        items(rssArticles, key = { it.id }) { article ->
                            GoogleDiscoverCard(
                                article = article,
                                onClick = { selectedRssArticleForReading = article },
                                onSaveToJournal = { savedArticle ->
                                    viewModel.saveEntry(
                                        id = 0L,
                                        title = savedArticle.title,
                                        body = "${savedArticle.fullContent}\n\nFuente: ${savedArticle.link}",
                                        onComplete = {
                                            Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                        }
                    } else {
                        if (entries.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Aún no tienes notas escritas",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tus reflexiones, notas de voz y fotos aparecerán organizadas aquí.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(entries, key = { it.entry.id }) { entryWithRel ->
                                GoogleMemoryCard(
                                    entryWithRelations = entryWithRel,
                                    onClick = { onNavigateToDetail(entryWithRel.entry.id) },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Storypad Floating Action Button for New Note
        StorypadNewNoteFab(
            onTriggerType = { type ->
                when (type) {
                    StorypadNoteType.TEXT -> onNavigateToNewEntry()
                    StorypadNoteType.AUDIO -> showQuickDictateModal = true
                    StorypadNoteType.IMAGE -> showPhotoChoiceDialog = true
                    StorypadNoteType.TEMPLATE -> onNavigateToNewEntry()
                    StorypadNoteType.QUICK -> onNavigateToNewEntry()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp)
        )
    }

    // Photo Choice Dialog (Camera or Gallery)
    if (showPhotoChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoChoiceDialog = false },
            title = { Text("Agregar Foto a tus Recuerdos") },
            text = { Text("Elige cómo deseas adjuntar la imagen:") },
            confirmButton = {
                Button(
                    onClick = {
                        showPhotoChoiceDialog = false
                        takePictureLauncher.launch(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cámara")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPhotoChoiceDialog = false
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galería")
                }
            }
        )
    }

    // Full Article Reader Modal
    selectedRssArticleForReading?.let { article ->
        RssOfflineReaderModal(
            article = article,
            onDismiss = { selectedRssArticleForReading = null },
            onSaveToNotes = { savedArticle ->
                viewModel.saveEntry(
                    id = 0L,
                    title = savedArticle.title,
                    body = "${savedArticle.fullContent}\n\nFuente: ${savedArticle.link}",
                    onComplete = { newId ->
                        onNavigateToDetail(newId)
                    }
                )
            },
            onFetchFullText = { art ->
                viewModel.rssFeedManager.fetchFullArticleText(art)
            }
        )
    }

    // Voice Dictation & Audio Note Modal
    if (showQuickDictateModal) {
        VoiceTranscriptionModal(
            title = "Dictado de Voz Rápido",
            viewModel = viewModel,
            onDismiss = { showQuickDictateModal = false },
            onSaveTranscript = { text ->
                if (text.isNotBlank()) {
                    viewModel.saveEntry(
                        id = 0L,
                        title = "Nota de Voz",
                        body = text,
                        onComplete = { newId -> onNavigateToDetail(newId) }
                    )
                }
                showQuickDictateModal = false
            }
        )
    }
}
