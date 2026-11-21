package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contacts.ContactsHelper
import com.example.rss.RssArticle
import com.example.semantic.SemanticSearchEngine
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.RssOfflineReaderModal
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.ui.theme.tabular
import com.example.viewmodel.JournalViewModel

enum class UniversalSearchTab {
    TODO,
    CONTACTOS,
    ARCHIVOS,
    NOTAS,
    NOTICIAS
}

@Composable
fun GoogleUniversalSearchScreen(
    viewModel: JournalViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onOpenVoiceModal: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(UniversalSearchTab.TODO) }

    val entries by viewModel.entries.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val contactResults by viewModel.deviceContactsResults.collectAsState()
    val fileResults by viewModel.deviceFilesResults.collectAsState()
    val semanticSearch by viewModel.semanticSearch.collectAsState()

    var activeReadingArticle by remember { mutableStateOf<RssArticle?>(null) }

    val hasContactsPermission = remember { viewModel.deviceSearchManager.hasContactsPermission() }
    var contactsPermissionGranted by remember { mutableStateOf(hasContactsPermission) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
        if (granted) {
            viewModel.searchDevice(searchQuery)
        }
    }

    LaunchedEffect(searchQuery) {
        viewModel.searchDevice(searchQuery)
        viewModel.onSemanticSearchChanged(searchQuery)
    }

    val instantHits = remember(searchQuery, entries) {
        if (searchQuery.isBlank()) emptyList()
        else SemanticSearchEngine.instantLexicalHits(searchQuery, entries)
    }

    val matchedNoteHits = remember(searchQuery, semanticSearch, instantHits) {
        when {
            searchQuery.isBlank() -> emptyList()
            semanticSearch.query == searchQuery && semanticSearch.hits.isNotEmpty() -> semanticSearch.hits
            semanticSearch.query == searchQuery && !semanticSearch.isSearching -> semanticSearch.hits
            else -> instantHits
        }
    }

    val matchedNews = remember(searchQuery, rssArticles) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            rssArticles.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }

    var storageGranted by remember { mutableStateOf(viewModel.deviceSearchManager.hasStoragePermission()) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        storageGranted = viewModel.deviceSearchManager.hasStoragePermission()
        if (searchQuery.isNotBlank()) viewModel.searchDevice(searchQuery)
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val glassBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("universal_search_screen")
    ) {
        // Frosted Glass Search Header Bar extending cleanly beneath status bar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por significado, fotos, OCR, contactos...", fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = onOpenVoiceModal) {
                            Icon(Icons.Default.Mic, contentDescription = "Voz", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Horizontal Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UniversalSearchTab.values().forEach { tab ->
                        val label = when (tab) {
                            UniversalSearchTab.TODO -> "Todo"
                            UniversalSearchTab.CONTACTOS -> "Contactos (${contactResults.size})"
                            UniversalSearchTab.ARCHIVOS -> "Archivos (${fileResults.size})"
                            UniversalSearchTab.NOTAS -> "Notas (${matchedNoteHits.size})"
                            UniversalSearchTab.NOTICIAS -> "Noticias (${matchedNews.size})"
                        }
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(label, fontSize = 12.sp, style = MaterialTheme.typography.labelMedium.tabular) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

        // Search Content List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = navBottom + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (searchQuery.isBlank()) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        contentPadding = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Búsqueda universal y multimodal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Encuentra contactos, archivos del teléfono, noticias y notas por significado — incluido texto dentro de fotos (OCR), objetos detectados por Mirage y notas de voz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (!storageGranted) {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 18.dp,
                            contentPadding = 14.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Buscar en los archivos del teléfono",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "Con este permiso podrás encontrar fotos, música y descargas de todo el dispositivo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        storagePermissionLauncher.launch(
                                            viewModel.requiredStoragePermissions()
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Permitir", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // SECTION: Contactos
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.CONTACTOS) {
                    if (!contactsPermissionGranted) {
                        item {
                            LiquidGlassCard(
                                cornerRadius = 18.dp,
                                contentPadding = 14.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Buscar en tus contactos",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Permite a la app encontrar personas en tu agenda telefónica.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Permitir", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (contactResults.isNotEmpty()) {
                        item {
                            Text(
                                "Contactos encontrados",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(contactResults, key = { "contact_${it.id}" }) { contact ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = glassSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = NowBriefVitalsGreen.copy(alpha = 0.16f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = NowBriefVitalsGreen, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            contact.phoneNumber ?: "Sin teléfono",
                                            style = MaterialTheme.typography.bodySmall.tabular,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!contact.phoneNumber.isNullOrBlank()) {
                                        IconButton(onClick = { ContactsHelper.dialContact(context, contact.phoneNumber) }) {
                                            Icon(Icons.Default.Call, contentDescription = "Llamar", tint = NowBriefVitalsGreen, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { ContactsHelper.messageContact(context, contact.phoneNumber) }) {
                                            Icon(Icons.Default.Message, contentDescription = "Mensaje", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION: Archivos del Teléfono (MediaStore)
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.ARCHIVOS) {
                    if (fileResults.isEmpty() && storageGranted) {
                        item {
                            Text(
                                "No se encontraron archivos con ese nombre en el almacenamiento.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else if (fileResults.isNotEmpty()) {
                        item {
                            Text(
                                "Archivos en el dispositivo",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(fileResults, key = { "file_${it.id}" }) { file ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = glassSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openDeviceFile(file) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val kb = file.sizeBytes / 1024
                                        val sizeStr = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
                                        Text(
                                            "$sizeStr • ${file.mimeType}",
                                            style = MaterialTheme.typography.labelSmall.tabular,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.openDeviceFile(file) }) {
                                        Icon(
                                            Icons.Default.OpenInNew,
                                            contentDescription = "Abrir",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION: Notas (Búsqueda semántica, visual y de voz)
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.NOTAS) {
                    if (matchedNoteHits.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Notas y memorias",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (semanticSearch.isSearching) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(13.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    val aiActive = semanticSearch.viaNeedle || semanticSearch.viaEmbeddings
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = (if (aiActive) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (aiActive) "IA local · semántica" else "Semántica + multimedia",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (aiActive) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                        items(matchedNoteHits, key = { "note_${it.entryId}" }) { hit ->
                            val viaAi = semanticSearch.viaNeedle && hit.entryId in semanticSearch.needleRanking
                            val tint = if (viaAi) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = glassSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDetail(hit.entryId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tint.copy(alpha = 0.14f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (viaAi) Icons.Default.AutoAwesome else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hit.snippet.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = hit.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (hit.reason.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = hit.reason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION: Noticias
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.NOTICIAS) {
                    if (matchedNews.isNotEmpty()) {
                        item {
                            Text(
                                "Noticias",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(matchedNews, key = { "news_${it.id}" }) { article ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = glassSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeReadingArticle = article }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        article.sourceTitle,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        article.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeReadingArticle?.let { article ->
        RssOfflineReaderModal(
            article = article,
            onDismiss = { activeReadingArticle = null },
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
}
