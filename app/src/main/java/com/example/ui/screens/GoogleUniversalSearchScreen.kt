package com.example.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.DeviceFileInfo
import com.example.rss.RssArticle
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.components.GoogleRed
import com.example.ui.components.RssOfflineReaderModal
import com.example.ui.theme.ForestPrimary
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(UniversalSearchTab.TODO) }

    val entries by viewModel.entries.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val contactResults by viewModel.deviceContactsResults.collectAsState()
    val fileResults by viewModel.deviceFilesResults.collectAsState()

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
    }

    val matchedNotes = remember(searchQuery, entries) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            entries.filter { item ->
                item.entry.title.lowercase().contains(q) ||
                item.entry.body.lowercase().contains(q) ||
                item.tags.any { it.name.lowercase().contains(q) }
            }
        }
    }

    val matchedNews = remember(searchQuery, rssArticles) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            rssArticles.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }

    // Storage permission: without READ_MEDIA_* / READ_EXTERNAL_STORAGE the
    // MediaStore query only returns this app's own files, which made the
    // "Files" tab look permanently empty.
    var storageGranted by remember { mutableStateOf(viewModel.deviceSearchManager.hasStoragePermission()) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        storageGranted = viewModel.deviceSearchManager.hasStoragePermission()
        if (searchQuery.isNotBlank()) viewModel.searchDevice(searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("universal_search_screen")
    ) {
        // Search Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            placeholder = { Text("Buscar contactos, archivos, notas...", fontSize = 15.sp) },
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
                            Icon(Icons.Default.Mic, contentDescription = "Voz", tint = GoogleBlue)
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
                            UniversalSearchTab.NOTAS -> "Notas (${matchedNotes.size})"
                            UniversalSearchTab.NOTICIAS -> "Noticias (${matchedNews.size})"
                        }
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoogleBlue.copy(alpha = 0.15f),
                                selectedLabelColor = GoogleBlue
                            )
                        )
                    }
                }
            }
        }

        // Search Content List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (searchQuery.isBlank()) {
                item {
                    Text(
                        text = "Escribe para buscar contactos en tu agenda, archivos guardados en el teléfono o tus notas personales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Contextual storage permission card
                if (!storageGranted) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
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
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Buscar en tus contactos", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text("Permite a la app encontrar personas en tu agenda telefónica.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                                    ) {
                                        Text("Permitir", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (contactResults.isNotEmpty()) {
                        item {
                            Text("Contactos encontrados", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = GoogleBlue)
                        }
                        items(contactResults, key = { "contact_${it.id}" }) { contact ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = GoogleGreen.copy(alpha = 0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = GoogleGreen, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                        Text(contact.phoneNumber ?: "Sin teléfono", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!contact.phoneNumber.isNullOrBlank()) {
                                        IconButton(onClick = { ContactsHelper.dialContact(context, contact.phoneNumber) }) {
                                            Icon(Icons.Default.Call, contentDescription = "Llamar", tint = GoogleGreen, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { ContactsHelper.messageContact(context, contact.phoneNumber) }) {
                                            Icon(Icons.Default.Message, contentDescription = "Mensaje", tint = GoogleBlue, modifier = Modifier.size(20.dp))
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
                            Text("Archivos en el dispositivo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = GoogleBlue)
                        }
                        items(fileResults, key = { "file_${it.id}" }) { file ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openDeviceFile(file) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val kb = file.sizeBytes / 1024
                                        val sizeStr = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
                                        Text("$sizeStr • ${file.mimeType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.openDeviceFile(file) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Abrir", tint = GoogleBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION: Notas
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.NOTAS) {
                    if (matchedNotes.isNotEmpty()) {
                        item {
                            Text("Notas y memorias", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = GoogleBlue)
                        }
                        items(matchedNotes, key = { "note_${it.entry.id}" }) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDetail(item.entry.id) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(item.entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.entry.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // SECTION: Noticias
                if (selectedTab == UniversalSearchTab.TODO || selectedTab == UniversalSearchTab.NOTICIAS) {
                    if (matchedNews.isNotEmpty()) {
                        item {
                            Text("Noticias", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = GoogleBlue)
                        }
                        items(matchedNews, key = { "news_${it.id}" }) { article ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeReadingArticle = article }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(article.sourceTitle, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = GoogleRed)
                                    Text(article.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
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
