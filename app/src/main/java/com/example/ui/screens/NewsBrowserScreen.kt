package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rss.RssArticle
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleDiscoverCard
import com.example.ui.components.RssOfflineReaderModal
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch

/**
 * v1.6 — Full news browser, shared by BOTH interface modes (feature parity:
 * everything you can see and DO in one mode exists in the other). Samsung
 * mode gets it on the aurora background, Google mode on the plain surface —
 * but the cards, the reading, the saving and the source filters are exactly
 * the same components.
 *
 * Adds what neither home had: per-source filtering of the whole article
 * list, plus an explicit refresh with visible progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsBrowserScreen(
    viewModel: JournalViewModel,
    isSamsungMode: Boolean,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val isRssLoading by viewModel.isRssLoading.collectAsState()
    val rssSources by viewModel.rssSources.collectAsState()

    var selectedSource by remember { mutableStateOf<String?>(null) }
    var readerArticle by remember { mutableStateOf<RssArticle?>(null) }

    val visibleArticles = remember(selectedSource, rssArticles) {
        if (selectedSource == null) rssArticles
        else rssArticles.filter { it.sourceTitle == selectedSource }
    }

    Scaffold(
        containerColor = if (isSamsungMode) MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Noticias", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (isRssLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.refreshRssFeeds()
                                Toast.makeText(context, "Actualizando noticias…", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSamsungMode) MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSamsungMode) {
                AuroraBackground(modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Source filter chips (horizontally scrollable)
                if (rssSources.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSource == null,
                            onClick = { selectedSource = null },
                            leadingIcon = {
                                Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = { Text("Todas (${rssArticles.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoogleBlue.copy(alpha = 0.15f),
                                selectedLabelColor = GoogleBlue
                            )
                        )
                        rssSources.forEach { source ->
                            val count = rssArticles.count { it.sourceTitle == source.name }
                            FilterChip(
                                selected = selectedSource == source.name,
                                onClick = {
                                    selectedSource = if (selectedSource == source.name) null else source.name
                                },
                                label = { Text("${source.name} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                when {
                    visibleArticles.isEmpty() && isRssLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Descargando tus fuentes…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    visibleArticles.isEmpty() -> {
                        // Honest empty state: no fabricated articles.
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.RssFeed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (rssSources.isEmpty()) "No tienes fuentes aún"
                                    else "Sin artículos de ${selectedSource ?: "tus fuentes"}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    if (rssSources.isEmpty())
                                        "Añade canales RSS en Ajustes → Fuentes de noticias, o importa tu OPML."
                                    else "Toca actualizar o revisa tu conexión — la lista se guarda tal cual estaba.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                                        scope.launch { viewModel.refreshRssFeeds() }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Actualizar", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(visibleArticles, key = { it.id }) { article ->
                                GoogleDiscoverCard(
                                    article = article,
                                    onClick = { readerArticle = article },
                                    onSaveToJournal = { savedArticle ->
                                        viewModel.saveEntry(
                                            id = 0L,
                                            title = savedArticle.title,
                                            body = "${savedArticle.fullContent}\n\nFuente: ${savedArticle.link}",
                                            onComplete = { newId ->
                                                Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                                                onNavigateToDetail(newId)
                                            }
                                        )
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Same offline reader as both homes — parity of the reading experience.
    readerArticle?.let { article ->
        RssOfflineReaderModal(
            article = article,
            onDismiss = { readerArticle = null },
            onSaveToNotes = { savedArticle ->
                viewModel.saveEntry(
                    id = 0L,
                    title = savedArticle.title,
                    body = "${savedArticle.fullContent}\n\nFuente: ${savedArticle.link}",
                    onComplete = { newId -> onNavigateToDetail(newId) }
                )
            },
            onFetchFullText = { art ->
                viewModel.rssFeedManager.fetchFullArticleText(art)
            }
        )
    }
}
