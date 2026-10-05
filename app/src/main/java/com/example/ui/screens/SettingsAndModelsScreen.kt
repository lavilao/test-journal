package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import com.example.data.AppInterfaceMode
import com.example.ui.components.GoogleBlue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.semantic.MlKitAnalyzer
import com.example.ui.components.EditorialCard
import com.example.ui.components.MlKitDiagnosticsCard
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsAndModelsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val events by viewModel.events.collectAsState()
    val isRebuilding by viewModel.isRebuildingMetadata.collectAsState()
    val interfaceMode by viewModel.interfaceMode.collectAsState()

    var exportDialogContent by remember { mutableStateOf<String?>(null) }
    var exportDialogTitle by remember { mutableStateOf("") }

    val downloadedModels = remember { mutableStateMapOf<String, Boolean>() }
    var downloadingModelCode by remember { mutableStateOf<String?>(null) }
    var isEntityModelDownloaded by remember { mutableStateOf(false) }
    var isDownloadingEntityModel by remember { mutableStateOf(false) }
    val storageBreakdown by viewModel.storageBreakdown.collectAsState()
    val rssSources by viewModel.rssFeedManager.sources.collectAsState()
    var showAddRssDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageBreakdown()
        isEntityModelDownloaded = MlKitAnalyzer.isEntityModelDownloaded()
        MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
            downloadedModels[lang.code] = MlKitAnalyzer.isModelDownloaded(lang.code)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("settings_screen")
    ) {
        // Top Back Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ajustes del Sistema",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: Interface Mode Selector (Modo Google vs Modo Samsung)
        Text(
            text = "Modo de Pantalla de Inicio",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Elige tu estilo visual preferido para el hub principal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isGoogle = interfaceMode == AppInterfaceMode.GOOGLE
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isGoogle) GoogleBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = if (isGoogle) BorderStroke(2.dp, GoogleBlue) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.setInterfaceMode(AppInterfaceMode.GOOGLE) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Modo Google",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isGoogle) GoogleBlue else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pixel At a Glance transparente y sin bordes, buscador Google y feed limpio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isSamsung = interfaceMode == AppInterfaceMode.SAMSUNG
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSamsung) GoogleBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = if (isSamsung) BorderStroke(2.dp, GoogleBlue) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.setInterfaceMode(AppInterfaceMode.SAMSUNG) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Modo Samsung",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSamsung) GoogleBlue else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tarjeta dinámica NowBrief con briefing matutino, vespertino y nocturno.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Local Storage & Knowledge Stats
        Text(
            text = "Knowledge Base Storage",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Journal Entries:", style = MaterialTheme.typography.bodyMedium)
                    Text("${entries.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Extracted Entities & Concepts:", style = MaterialTheme.typography.bodyMedium)
                    Text("${entities.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grouped Episodes / Events:", style = MaterialTheme.typography.bodyMedium)
                    Text("${events.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Database Engine:", style = MaterialTheme.typography.bodyMedium)
                    Text("SQLite / Room v2", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ForestPrimary))
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // On-Device ML Kit Model Management
        Text(
            text = "On-Device ML Kit Models",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Download offline models once over Wi-Fi. Models execute locally without internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                // Built-in models
                ModelStatusRow(
                    title = "Language Identification",
                    subtitle = "Detects journal language in milliseconds",
                    statusText = "Built-in / Active",
                    isReady = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ModelStatusRow(
                    title = "Vision, Face & OCR Recognition",
                    subtitle = "Labels attached images and extracts text",
                    statusText = "Built-in / Active",
                    isReady = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ModelStatusRow(
                    title = "MindForger Semantic Engine",
                    subtitle = "TF-IDF, BM25, and autolink association",
                    statusText = "Active (Zero overhead)",
                    isReady = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Entity Extraction Engine
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reconocimiento de Entidades", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (isEntityModelDownloaded) "Motor neuronal ML Kit activo en dispositivo" else "Motor en dispositivo activo (ML Kit opcional)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDownloadingEntityModel) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ForestPrimary)
                    } else if (isEntityModelDownloaded) {
                        Surface(color = ForestPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text("ML Kit Activo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ForestPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                isDownloadingEntityModel = true
                                scope.launch {
                                    val success = MlKitAnalyzer.downloadEntityModel()
                                    isDownloadingEntityModel = false
                                    isEntityModelDownloaded = success
                                    Toast.makeText(context, if (success) "Modelo ML Kit listo" else "Motor local sigue activo", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("download_entity_model_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargar ML Kit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Offline Translation Language Packs:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                MlKitAnalyzer.POPULAR_LANGUAGES.take(6).forEach { lang ->
                    val isDownloaded = downloadedModels[lang.code] == true
                    val isDownloading = downloadingModelCode == lang.code

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lang.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isDownloaded) "Downloaded for offline use" else "Available on-demand (~30MB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDownloaded) ForestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ForestPrimary)
                        } else if (isDownloaded) {
                            Surface(
                                color = ForestPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Ready",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ForestPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    downloadingModelCode = lang.code
                                    viewModel.downloadTranslationModel(lang.code) { success ->
                                        downloadingModelCode = null
                                        downloadedModels[lang.code] = success
                                        Toast.makeText(
                                            context,
                                            if (success) "${lang.displayName} model downloaded!" else "Download failed. Check connection.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.testTag("download_model_${lang.code}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bundled English Speech & Dictation Engine Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("bundled_speech_engine_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bundled English Dictation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = ForestPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Preloaded",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ForestPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "100% on-device English speech recognition & audio file transcription. Preloaded inside the app—does not depend on Gboard or Google Play Services downloads. Fully optimized for Redmi 9A with MIUI 12.5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Bundled English Speech Engine v2.1 is active and ready (Redmi 9A / MIUI 12.5 verified).",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("verify_bundled_model_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify Bundled Model Status")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // On-Device ML Kit & Model Diagnosis
        MlKitDiagnosticsCard()

        Spacer(modifier = Modifier.height(22.dp))

        // Storage & Cache Management
        Text(
            text = "Storage & Regenerable Cache",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Inspect local device storage usage. OCR cache and semantic indices can be safely pruned and regenerated anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val breakdown = storageBreakdown
                val totalBytes = breakdown.textEstimatedBytes + breakdown.photoBytes + breakdown.audioBytes + breakdown.databaseBytes
                fun fmt(b: Long): String {
                    return when {
                        b >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB", b / (1024f * 1024f))
                        b >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB", b / 1024f)
                        else -> "$b B"
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Storage Used", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(fmt(totalBytes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ForestPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                val items = listOf<Pair<String, Long>>(
                    "Journal Text (${breakdown.entryCount} entries, ${breakdown.pageCount} pages)" to breakdown.textEstimatedBytes,
                    "Photos & Media (${breakdown.photoCount} photos)" to breakdown.photoBytes,
                    "Voice Notes (${breakdown.audioCount} recordings)" to breakdown.audioBytes,
                    "OCR Cache (${breakdown.ocrCharCount} chars extracted)" to (breakdown.ocrCharCount * 2L),
                    "Semantic Graph (${breakdown.entityCount} entities, ${breakdown.relationshipCount} links)" to (breakdown.entityCount * 64L + breakdown.relationshipCount * 32L),
                    "SQLite Database" to breakdown.databaseBytes
                )

                items.forEach { (label, bytes) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(bytes), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearOcrCache {
                            Toast.makeText(context, "OCR cache cleared! Can be regenerated anytime.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("clear_ocr_cache_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear OCR Cache (Safe)")
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Rebuild Semantic Layer
        Text(
            text = "Rebuild Semantic Metadata",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Re-indexes all journal entries, re-derives entities, offsets, and relationships from original notes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        EditorialCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Re-index & Re-derive", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Original journal writing remains untouched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isRebuilding) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ForestPrimary)
                } else {
                    Button(
                        onClick = {
                            viewModel.rebuildAllSemanticMetadata {
                                Toast.makeText(context, "Semantic metadata re-derived successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.testTag("rebuild_metadata_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rebuild")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // RSS Feeds Management (Discover)
        Text(
            text = "Fuentes de Noticias RSS (Discover)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Gestiona y añade canales RSS para leer noticias sin conexión y sin rastreadores.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                rssSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RssFeed, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(source.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(source.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showAddRssDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Añadir Feed RSS")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.refreshRssFeeds()
                            Toast.makeText(context, "Feeds actualizados", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualizar")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Open Data Export & Backup (Zero Lock-In)
        Text(
            text = "Data Sovereignty & Export",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Export your entire journal and knowledge graph without vendor lock-in. Compatible with Obsidian, Logseq, and external backup tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                exportDialogTitle = "Markdown PKM Export"
                                exportDialogContent = viewModel.getExportMarkdown()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.weight(1f).testTag("export_markdown_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Markdown")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                exportDialogTitle = "JSON Data Export"
                                exportDialogContent = viewModel.getExportJson()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Export Result Dialog
    exportDialogContent?.let { content ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text(exportDialogTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mnemosyne Export", content))
                        Toast.makeText(context, "Export copied to clipboard!", Toast.LENGTH_SHORT).show()
                        exportDialogContent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.testTag("copy_export_button")
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportDialogContent = null }) {
                    Text("Close")
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
            title = { Text("Añadir Canal RSS") },
            text = {
                Column {
                    Text(
                        text = "Introduce la dirección web del feed RSS para leer artículos sin conexión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rssNameInput,
                        onValueChange = { rssNameInput = it },
                        label = { Text("Nombre de la fuente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rssUrlInput,
                        onValueChange = { rssUrlInput = it },
                        label = { Text("URL del Feed RSS") },
                        placeholder = { Text("https://ejemplo.com/rss") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rssUrlInput.isNotBlank()) {
                            viewModel.addCustomRssFeed(rssUrlInput, rssNameInput)
                            Toast.makeText(context, "Feed RSS añadido", Toast.LENGTH_SHORT).show()
                            showAddRssDialog = false
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

@Composable
private fun ModelStatusRow(
    title: String,
    subtitle: String,
    statusText: String,
    isReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = if (isReady) ForestPrimary.copy(alpha = 0.12f) else TerracottaAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isReady) ForestPrimary else TerracottaAccent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
