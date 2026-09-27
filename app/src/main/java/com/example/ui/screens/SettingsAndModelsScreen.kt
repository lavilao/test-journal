package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsAndModelsScreen(viewModel: JournalViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val events by viewModel.events.collectAsState()
    val isRebuilding by viewModel.isRebuildingMetadata.collectAsState()

    var exportDialogContent by remember { mutableStateOf<String?>(null) }
    var exportDialogTitle by remember { mutableStateOf("") }

    val downloadedModels = remember { mutableStateMapOf<String, Boolean>() }
    var downloadingModelCode by remember { mutableStateOf<String?>(null) }
    var isEntityModelDownloaded by remember { mutableStateOf(false) }
    var isDownloadingEntityModel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isEntityModelDownloaded = MlKitAnalyzer.isEntityModelDownloaded()
        MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
            downloadedModels[lang.code] = MlKitAnalyzer.isModelDownloaded(lang.code)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = "Settings & Local ML",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Privacy controls, on-device ML Kit models, and open data export",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Privacy First Badge Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().testTag("privacy_card")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "100% Offline & Private",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Zero tracking. Zero remote LLM calls. All entities, offsets, TF-IDF scores, and journals stay strictly on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

                // ML Kit Entity Extraction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ML Kit Entity Extraction", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "Extracts Dates, Money, URLs, Addresses on-device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDownloadingEntityModel) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ForestPrimary)
                    } else if (isEntityModelDownloaded) {
                        Surface(color = ForestPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text("Ready", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ForestPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                isDownloadingEntityModel = true
                                scope.launch {
                                    val success = MlKitAnalyzer.downloadEntityModel()
                                    isDownloadingEntityModel = false
                                    isEntityModelDownloaded = success
                                    Toast.makeText(context, if (success) "Entity model downloaded!" else "Download failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("download_entity_model_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", style = MaterialTheme.typography.labelSmall)
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
