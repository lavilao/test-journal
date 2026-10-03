package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleRed
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.WarmAccent
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.VaultFileItem

enum class SearchTabFilter {
    TODO,
    EN_DISPOSITIVO,
    IMAGENES,
    NOTICIAS
}

@Composable
fun GoogleUniversalSearchScreen(
    viewModel: JournalViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onOpenVoiceModal: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchTabFilter.TODO) }
    val vaultItems by viewModel.vaultItems.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()

    val recentSearches = remember {
        mutableStateListOf("reunión", "presupuesto", "ideas proyecto", "salud")
    }

    val filteredFiles = remember(searchQuery, selectedFilter, vaultItems) {
        if (searchQuery.isBlank() && selectedFilter != SearchTabFilter.EN_DISPOSITIVO) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            vaultItems.filter { item ->
                val matchesText = q.isBlank() ||
                        item.title.lowercase().contains(q) ||
                        item.fileName.lowercase().contains(q) ||
                        item.previewText.lowercase().contains(q) ||
                        item.tags.any { it.name.lowercase().contains(q) }

                val matchesType = when (selectedFilter) {
                    SearchTabFilter.TODO -> true
                    SearchTabFilter.EN_DISPOSITIVO -> true
                    SearchTabFilter.IMAGENES -> item.fileExtension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
                    SearchTabFilter.NOTICIAS -> false
                }
                matchesText && matchesType
            }
        }
    }

    val filteredNews = remember(searchQuery, selectedFilter, rssArticles) {
        if (searchQuery.isBlank() && selectedFilter != SearchTabFilter.NOTICIAS) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            rssArticles.filter { article ->
                val matchesText = q.isBlank() ||
                        article.title.lowercase().contains(q) ||
                        article.description.lowercase().contains(q) ||
                        article.sourceTitle.lowercase().contains(q)

                val matchesType = selectedFilter == SearchTabFilter.TODO || selectedFilter == SearchTabFilter.NOTICIAS
                matchesText && matchesType
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("google_universal_search_screen")
    ) {
        // Top Search Bar (Google Pill Style)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Buscar en este dispositivo e internet...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("universal_search_input")
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onOpenVoiceModal,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voz",
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Google Search Filter Tabs: Todo, En este dispositivo, Imágenes, Noticias
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == SearchTabFilter.TODO,
                        onClick = { selectedFilter = SearchTabFilter.TODO },
                        label = { Text("Todo") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoogleBlue.copy(alpha = 0.15f),
                            selectedLabelColor = GoogleBlue
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SearchTabFilter.EN_DISPOSITIVO,
                        onClick = { selectedFilter = SearchTabFilter.EN_DISPOSITIVO },
                        leadingIcon = {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("En este dispositivo") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = ForestPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SearchTabFilter.IMAGENES,
                        onClick = { selectedFilter = SearchTabFilter.IMAGENES },
                        leadingIcon = {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Imágenes") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoogleRed.copy(alpha = 0.15f),
                            selectedLabelColor = GoogleRed
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SearchTabFilter.NOTICIAS,
                        onClick = { selectedFilter = SearchTabFilter.NOTICIAS },
                        leadingIcon = {
                            Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Noticias (RSS)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarmAccent.copy(alpha = 0.15f),
                            selectedLabelColor = WarmAccent
                        )
                    )
                }
            }
        }

        // Content Body
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // If search query is empty and filter is default: Show Recent Searches & Cloud File Summary
            if (searchQuery.isBlank() && selectedFilter == SearchTabFilter.TODO) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(
                            text = "Búsquedas recientes",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        recentSearches.forEach { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { searchQuery = term }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = term,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                item {
                    // Cloud on Device Info Header
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ForestPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = ForestPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Archivos locales como tu nube personal",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${vaultItems.size} archivos indexados en este dispositivo. 0 bytes enviados a internet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Results Section: Files on this device (like Drive in Google Search)
            if (filteredFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Archivos en este dispositivo (${filteredFiles.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                items(filteredFiles, key = { "file_${it.id}" }) { fileItem ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onNavigateToDetail(fileItem.id) }
                            .testTag("search_file_card_${fileItem.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (fileItem.fileExtension.lowercase()) {
                                    "m4a", "aac" -> GoogleRed.copy(alpha = 0.12f)
                                    "jpg", "png" -> GoogleBlue.copy(alpha = 0.12f)
                                    else -> ForestPrimary.copy(alpha = 0.12f)
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val icon = when (fileItem.fileExtension.lowercase()) {
                                        "m4a", "aac" -> Icons.Default.AudioFile
                                        "jpg", "png" -> Icons.Default.Image
                                        else -> Icons.Default.Description
                                    }
                                    val iconTint = when (fileItem.fileExtension.lowercase()) {
                                        "m4a", "aac" -> GoogleRed
                                        "jpg", "png" -> GoogleBlue
                                        else -> ForestPrimary
                                    }
                                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                                }
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = fileItem.previewText.ifBlank { fileItem.fileName },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = fileItem.fileExtension.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = fileItem.formattedSize,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Results Section: News & RSS Articles
            if (filteredNews.isNotEmpty()) {
                item {
                    Text(
                        text = "Resultados en noticias e internet (${filteredNews.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                items(filteredNews, key = { "news_${it.id}" }) { article ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = article.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${article.sourceTitle} • ${article.pubDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = {
                                    viewModel.saveEntry(
                                        id = 0L,
                                        title = article.title,
                                        body = "${article.description}\n\nFuente: ${article.sourceTitle}\nEnlace: ${article.link}",
                                        onComplete = { onNavigateToDetail(it) }
                                    )
                                }) {
                                    Text("Guardar en archivos", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Empty state if searching with no results
            if (searchQuery.isNotBlank() && filteredFiles.isEmpty() && filteredNews.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No se encontraron resultados para \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
