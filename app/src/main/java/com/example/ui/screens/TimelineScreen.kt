package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.components.GoogleDiscoverCard
import com.example.ui.components.GoogleSearchHubHeader
import com.example.ui.components.StorypadNewNoteFab
import com.example.ui.components.StorypadNoteType
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.WarmAccent
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab

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
    val vaultItems by viewModel.vaultItems.collectAsState()

    var homeSearchQuery by remember { mutableStateOf("") }
    var showQuickDictateModal by remember { mutableStateOf(false) }
    var showAddRssDialog by remember { mutableStateOf(false) }
    var selectedFeedCategory by remember { mutableStateOf("All") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val persistentUri = viewModel.repository.persistImageToLocalStorage(uri)
            viewModel.saveEntry(
                id = 0L,
                title = "Photo Capture",
                body = "Visual memory captured into vault.",
                attachedImageUri = Uri.parse(persistentUri),
                onComplete = { newId ->
                    onNavigateToDetail(newId)
                }
            )
        }
    }

    // Filtered search results for files and notes
    val searchResults = remember(homeSearchQuery, vaultItems) {
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

    val filteredArticles = remember(rssArticles, selectedFeedCategory) {
        if (selectedFeedCategory == "All") {
            rssArticles
        } else {
            rssArticles.filter { it.category.equals(selectedFeedCategory, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_app_feed_list"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Section: Google Brand, Capsule Search Bar & Glance Carousel
            item {
                GoogleSearchHubHeader(
                    searchQuery = homeSearchQuery,
                    onSearchQueryChange = { homeSearchQuery = it },
                    telemetry = telemetry,
                    nextReminder = nextReminder,
                    onVoiceClick = { showQuickDictateModal = true },
                    onCameraClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAiModeClick = {
                        viewModel.selectTab(MainNavTab.SEARCH)
                    },
                    onAudioModeClick = { showQuickDictateModal = true },
                    onAddRssClick = { showAddRssDialog = true }
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
                            text = "Archivos y notas (${searchResults.size})",
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
                // Mode 2: Google Discover RSS Feed Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RssFeed,
                                contentDescription = "Discover RSS",
                                tint = WarmAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Descubrir (Feed RSS)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshRssFeeds() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isRssLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Feeds",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Discover Feed Cards
                items(filteredArticles, key = { it.id }) { article ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        GoogleDiscoverCard(
                            article = article,
                            onSaveToJournal = { savedArticle ->
                                viewModel.saveEntry(
                                    id = 0L,
                                    title = savedArticle.title,
                                    body = "${savedArticle.description}\n\nFuente: ${savedArticle.sourceTitle}\nEnlace: ${savedArticle.link}",
                                    onComplete = { newId ->
                                        Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Quick Capture Storypad FAB (bottom right)
        StorypadNewNoteFab(
            onTriggerType = { type ->
                when (type) {
                    StorypadNoteType.TEXT -> onNavigateToNewEntry()
                    StorypadNoteType.AUDIO -> showQuickDictateModal = true
                    StorypadNoteType.IMAGE -> photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    StorypadNoteType.TEMPLATE -> onNavigateToNewEntry()
                    StorypadNoteType.QUICK -> onNavigateToNewEntry()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )
    }

    // Voice Dictation Modal
    if (showQuickDictateModal) {
        VoiceTranscriptionModal(
            title = "Dictado por Voz",
            viewModel = viewModel,
            onDismiss = { showQuickDictateModal = false },
            onSaveTranscript = { text ->
                if (text.isNotBlank()) {
                    viewModel.saveEntry(
                        id = 0L,
                        title = "Dictado de voz",
                        body = text,
                        onComplete = { newId ->
                            onNavigateToDetail(newId)
                        }
                    )
                }
            }
        )
    }

    // Add Custom RSS Feed Dialog
    if (showAddRssDialog) {
        var rssUrlInput by remember { mutableStateOf("") }
        var rssNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddRssDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RssFeed, contentDescription = null, tint = WarmAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir Feed RSS", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Introduce la URL de cualquier feed RSS (blogs, noticias, Substack, Medium) para verlo en Descubrir:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rssNameInput,
                        onValueChange = { rssNameInput = it },
                        label = { Text("Nombre del feed (ej. Mi Blog)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rssUrlInput,
                        onValueChange = { rssUrlInput = it },
                        label = { Text("URL del RSS (https://...)") },
                        singleLine = true,
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rssUrlInput.isNotBlank()) {
                            viewModel.addCustomRssFeed(
                                url = rssUrlInput.trim(),
                                name = rssNameInput.trim().ifBlank { "Personal RSS" }
                            )
                            showAddRssDialog = false
                            Toast.makeText(context, "Feed RSS añadido con éxito", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRssDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
