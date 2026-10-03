package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rss.RssArticle
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleDiscoverCard
import com.example.ui.components.GoogleMemoryCard
import com.example.ui.components.GoogleSearchHubHeader
import com.example.ui.components.RssOfflineReaderModal
import com.example.ui.components.StorypadNewNoteFab
import com.example.ui.components.StorypadNoteType
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.WarmAccent
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import java.io.File
import java.io.FileOutputStream

enum class HomeFeedView {
    NOTICIAS,
    MEMORIAS
}

@Composable
fun TimelineScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit
) {
    val context = LocalContext.current

    val telemetry by viewModel.telemetry.collectAsState()
    val nextReminder by viewModel.nextActiveReminder.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val isRssLoading by viewModel.isRssLoading.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val vaultItems by viewModel.vaultItems.collectAsState()

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Google App Inspired Clean Search Header
            item {
                GoogleSearchHubHeader(
                    searchQuery = homeSearchQuery,
                    onSearchQueryChange = { homeSearchQuery = it },
                    telemetry = telemetry,
                    nextReminder = nextReminder,
                    onVoiceClick = { showQuickDictateModal = true },
                    onCameraClick = { showPhotoChoiceDialog = true },
                    onAiModeClick = { viewModel.selectTab(MainNavTab.BUSCAR) },
                    onAudioModeClick = { showQuickDictateModal = true },
                    onSettingsClick = { viewModel.selectTab(MainNavTab.SETTINGS) }
                )
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
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No se encontraron archivos para \"$homeSearchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(searchResults, key = { it.id }) { fileItem ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable { onNavigateToDetail(fileItem.id) }
                                .testTag("search_result_item_${fileItem.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ForestPrimary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = fileItem.fileExtension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ForestPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileItem.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
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
                // Section Toggle: Noticias (Discover) vs Mis Memorias
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
                                label = { Text("Noticias (Discover)") },
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

                        if (selectedFeedView == HomeFeedView.NOTICIAS) {
                            IconButton(
                                onClick = { viewModel.refreshRssFeeds() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isRssLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Actualizar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedFeedView == HomeFeedView.NOTICIAS) {
                    // Discover RSS Feed Cards (Tap opens offline reader modal)
                    items(rssArticles, key = { it.id }) { article ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable { selectedRssArticleForReading = article }
                        ) {
                            GoogleDiscoverCard(
                                article = article,
                                onSaveToJournal = { savedArticle ->
                                    viewModel.saveEntry(
                                        id = 0L,
                                        title = savedArticle.title,
                                        body = "${savedArticle.description}\n\nFuente: ${savedArticle.sourceTitle}\nEnlace: ${savedArticle.link}",
                                        onComplete = {
                                            Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Google HIG Compliant Memory Cards (Polished, no text clutter)
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
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Aún no tienes notas o recuerdos guardados.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onNavigateToNewEntry,
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                                    ) {
                                        Text("Crear primera nota")
                                    }
                                }
                            }
                        }
                    } else {
                        items(entries, key = { it.entry.id }) { entryWithRel ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                GoogleMemoryCard(
                                    entryWithRelations = entryWithRel,
                                    onClick = { onNavigateToDetail(entryWithRel.entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Capture Storypad FAB (clean, no text hint above)
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
                .padding(end = 20.dp, bottom = 24.dp)
        )
    }

    // Photo Action Choice Dialog: Take Photo with Camera vs Gallery
    if (showPhotoChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoChoiceDialog = false },
            title = { Text("Añadir Foto", fontWeight = FontWeight.Bold) },
            text = { Text("Elige si deseas tomar una foto con la cámara o seleccionarla desde tus archivos.") },
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
                    Text("Tomar Foto")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoChoiceDialog = false
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galería")
                }
            }
        )
    }

    // Offline RSS Article Reader Modal
    selectedRssArticleForReading?.let { article ->
        RssOfflineReaderModal(
            article = article,
            onDismiss = { selectedRssArticleForReading = null },
            onSaveToNotes = { savedArticle ->
                viewModel.saveEntry(
                    id = 0L,
                    title = savedArticle.title,
                    body = "${savedArticle.description}\n\nFuente: ${savedArticle.sourceTitle}\nEnlace: ${savedArticle.link}",
                    onComplete = {
                        Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // Voice Dictation Modal
    if (showQuickDictateModal) {
        VoiceTranscriptionModal(
            title = "Dictado por Voz",
            viewModel = viewModel,
            onDismiss = { showQuickDictateModal = false },
            onSaveTranscript = { transcript ->
                if (transcript.isNotBlank()) {
                    viewModel.saveEntry(
                        id = 0L,
                        title = "Nota de Voz",
                        body = transcript,
                        onComplete = { newId ->
                            showQuickDictateModal = false
                            onNavigateToDetail(newId)
                        }
                    )
                } else {
                    showQuickDictateModal = false
                }
            }
        )
    }
}
