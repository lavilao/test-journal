package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import com.example.data.AppInterfaceMode
import com.example.ui.components.GoogleBlue
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.semantic.MlKitAnalyzer
import com.example.ui.components.EditorialCard
import com.example.ui.components.GoogleGreen
import com.example.ui.components.CityPickerModal
import com.example.ui.components.MlKitDiagnosticsCard
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.habit.HabitEngine
import com.example.location.SmartPlaces
import com.example.wallpaper.SunGradientBackground
import com.example.wallpaper.SunWallpaperService

@Composable
fun SettingsAndModelsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onOpenRoutines: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val events by viewModel.events.collectAsState()
    val isRebuilding by viewModel.isRebuildingMetadata.collectAsState()
    val interfaceMode by viewModel.interfaceMode.collectAsState()
    val selectedCity by viewModel.selectedWeatherCity.collectAsState()
    val weather by viewModel.realWeather.collectAsState()
    var showCityPicker by remember { mutableStateOf(false) }

    var exportDialogContent by remember { mutableStateOf<String?>(null) }
    var exportDialogTitle by remember { mutableStateOf("") }

    val downloadedModels = remember { mutableStateMapOf<String, Boolean>() }
    var downloadingModelCode by remember { mutableStateOf<String?>(null) }
    var isEntityModelDownloaded by remember { mutableStateOf(false) }
    var isDownloadingEntityModel by remember { mutableStateOf(false) }
    val storageBreakdown by viewModel.storageBreakdown.collectAsState()
    val rssSources by viewModel.rssFeedManager.sources.collectAsState()
    var showAddRssDialog by remember { mutableStateOf(false) }

    // ---- OPML export / import launchers (feeds travel between apps) ----
    val opmlExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(viewModel.exportRssOpml().toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "OPML exportado ✔", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "No pude exportar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val opmlImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    viewModel.importRssOpml(text) { added ->
                        Toast.makeText(
                            context,
                            if (added > 0) "$added feed(s) importados ✔" else "Nada nuevo que importar (o ya los tenías)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "No pude leer el OPML: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageBreakdown()
        isEntityModelDownloaded = MlKitAnalyzer.isEntityModelDownloaded()
        MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
            downloadedModels[lang.code] = MlKitAnalyzer.isModelDownloaded(lang.code)
        }
    }

    // ---- experimental sun wallpaper + places state ----
    var sunWallpaperChecked by remember {
        mutableStateOf(
            context.getSharedPreferences("sun_wallpaper", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        )
    }
    var placesTick by remember { mutableStateOf(0) }
    val savedPlaces = remember(placesTick) { SmartPlaces.places(context) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var placeSaving by remember { mutableStateOf(false) }

    // ---- habit engine toggles ----
    var habitMaster by remember { mutableStateOf(HabitEngine.isMasterEnabled(context)) }
    var habitUsage by remember { mutableStateOf(HabitEngine.isUsageCollectorEnabled(context)) }
    var habitNotifs by remember { mutableStateOf(HabitEngine.isNotificationCollectorEnabled(context)) }
    var habitLocation by remember { mutableStateOf(HabitEngine.isLocationCollectorEnabled(context)) }
    var habitBluetooth by remember { mutableStateOf(HabitEngine.isBluetoothCollectorEnabled(context)) }

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
                        text = "Now brief: tarjetas de vidrio líquido, fondo aurora y cápsula flotante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // SECTION: Weather city
        Text(
            text = "Clima",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Elige la ciudad para el clima de tus pantallas de inicio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weather_city_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = GoogleBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedCity?.name ?: "Sin ciudad elegida",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        val citySubtitle = selectedCity?.let {
                            listOfNotNull(it.admin, it.country).joinToString(", ")
                        } ?: "Usa GPS o elige una ciudad manualmente"
                        Text(
                            text = citySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    weather.temperature?.let { temp ->
                        Text(
                            text = "$temp°",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = ForestPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showCityPicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("change_city_btn")
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedCity == null) "Elegir mi ciudad" else "Cambiar ciudad")
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

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Copia de seguridad de los modelos ML Kit:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Exporta los modelos descargados (traducción incluida) a un zip y " +
                            "reimpórtalos cuando quieras — otro teléfono o una reinstalación sin " +
                            "volver a bajar ~30 MB por idioma. Desde v1.5.2 la importación restaura " +
                            "cada archivo en su ubicación original (files/no_backup) para que ML Kit " +
                            "los reconozca de verdad; los zips antiguos se restauran en ambas " +
                            "ubicaciones. Lo que Google guarde fuera del almacenamiento de la app " +
                            "no aparece aquí: se muestra exactamente lo encontrado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val mlkitBackupFiles = remember { mutableStateOf<List<com.example.ai.needle.MlKitTransferManager.ModelFile>>(emptyList()) }
                val mlkitBackupScanned = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    mlkitBackupFiles.value = com.example.ai.needle.MlKitTransferManager.discover(context)
                    mlkitBackupScanned.value = true
                }
                if (mlkitBackupScanned.value) {
                    if (mlkitBackupFiles.value.isEmpty()) {
                        Text(
                            text = "Encontrados: 0 archivos de modelos en el almacenamiento de la app.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Encontrados: ${mlkitBackupFiles.value.size} archivos · " +
                                    com.example.ai.needle.MlKitTransferManager.formatBytes(
                                        mlkitBackupFiles.value.sumOf { it.sizeBytes }
                                    ) + " (traducción, visión, digital ink…)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForestPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val mlkitExportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val result = com.example.ai.needle.MlKitTransferManager.exportAll(context, uri)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                val mlkitImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val result = com.example.ai.needle.MlKitTransferManager.importAll(context, uri)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            mlkitBackupFiles.value = com.example.ai.needle.MlKitTransferManager.discover(context)
                            // v1.5.2: re-check the REAL ML Kit state right after the
                            // import so the language list stops demanding internet
                            // downloads for models that are already back on disk.
                            isEntityModelDownloaded = MlKitAnalyzer.isEntityModelDownloaded()
                            MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
                                downloadedModels[lang.code] = MlKitAnalyzer.isModelDownloaded(lang.code)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { mlkitExportLauncher.launch("mnemosyne-mlkit-models.zip") },
                        enabled = mlkitBackupFiles.value.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar zip", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { mlkitImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importar zip", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Honest dictation status card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("dictation_status_card")
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
                            text = "Dictado de voz",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val available = viewModel.isDictationAvailable
                    val onDevice = viewModel.hasOnDeviceRecognizer
                    Surface(
                        color = when {
                            onDevice -> GoogleGreen.copy(alpha = 0.12f)
                            available -> GoogleGreen.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                onDevice -> "En el dispositivo"
                                available -> "Disponible"
                                else -> "No disponible"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (available) GoogleGreen else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = viewModel.dictationEngineDescription() +
                            " Elige el motor (Google / Samsung / en el dispositivo), consulta y descarga los " +
                            "modelos sin conexión, el hotword y el Voice Match en «Voz y asistente».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenVoiceSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("open_voice_and_assistant_btn")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voz y asistente: motor, hotword, Voice Match")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No se pudo abrir el ajuste de entrada de voz", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_voice_input_settings_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gestionar idiomas de voz sin conexión")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // On-Device ML Kit & Model Diagnosis
        MlKitDiagnosticsCard()

        Spacer(modifier = Modifier.height(20.dp))

        // Camera Lens & Scanner (on-device ML Kit vision)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lens_models_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lens de cámara y escáner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        color = GoogleGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Sin conexión",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GoogleGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "El Lens (botón de cámara de la app) ejecuta en tu teléfono: reconocimiento " +
                            "de texto (OCR), traducción con cámara, códigos QR y de barras, etiquetado " +
                            "de objetos y escaneo de documentos con limpieza de página. Los modelos " +
                            "de OCR, códigos y etiquetado van incluidos en la app; el modelo de " +
                            "traducción de cada idioma se descarga una vez y luego funciona sin internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.RssFeed, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(source.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(source.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        // Delete ANY feed (defaults included — the storage is
                        // now a plain editable list).
                        androidx.compose.material3.IconButton(
                            onClick = {
                                viewModel.removeRssFeed(source.url)
                                Toast.makeText(context, "«${source.name}» eliminado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar feed",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
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

                Spacer(modifier = Modifier.height(8.dp))

                // ---- OPML import / export (feeds travel between apps) ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { opmlExportLauncher.launch("mnemosyne-feeds.opml") }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar OPML")
                    }
                    OutlinedButton(
                        onClick = { opmlImportLauncher.launch(arrayOf("text/*", "application/xml", "application/octet-stream")) }
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importar OPML")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ---- OCR & search indexing (with the anti-"petar el móvil" toggles) ----
        Text(
            text = "OCR y búsqueda en imágenes",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "El OCR local (ML Kit, en el dispositivo) hace que el texto de tus fotos " +
                "y documentos compartidos aparezca en la búsqueda. Como puede cargar el " +
                "teléfono, cada pieza va con su interruptor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        EditorialCard {
            Column(modifier = Modifier.padding(16.dp)) {
                var ocrImages by remember { mutableStateOf(com.example.semantic.OcrIndexer.isImageOcrEnabled(context)) }
                var ocrDocs by remember { mutableStateOf(com.example.semantic.OcrIndexer.isDocOcrEnabled(context)) }
                var ocrBackfillRunning by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OCR de fotos al adjuntar", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Etiquetas + caras + texto de cada foto nueva",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = ocrImages,
                        onCheckedChange = { checked ->
                            ocrImages = checked
                            com.example.semantic.OcrIndexer.setImageOcrEnabled(context, checked)
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OCR de documentos (PDF)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Renderiza y OCR-ea las primeras páginas de los PDF compartidos — más pesado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = ocrDocs,
                        onCheckedChange = { checked ->
                            ocrDocs = checked
                            com.example.semantic.OcrIndexer.setDocOcrEnabled(context, checked)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Indexar fotos antiguas", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "OCR en segundo plano de las fotos guardadas sin texto (en lotes de 40)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        enabled = !ocrBackfillRunning && ocrImages,
                        onClick = {
                            ocrBackfillRunning = true
                            scope.launch {
                                val done = try {
                                    com.example.semantic.OcrIndexer.backfillImageOcr(context)
                                } catch (_: Exception) {
                                    0
                                }
                                ocrBackfillRunning = false
                                Toast.makeText(
                                    context,
                                    if (done > 0) "$done foto(s) indexadas — su texto ya es buscable."
                                    else "Nada nuevo que indexar (o OCR desactivado).",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (ocrBackfillRunning) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Indexar", fontSize = 12.sp)
                        }
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

        // =============================================================
        // MOTOR DE HÁBITOS: aprende rutinas de la telemetría local
        // =============================================================
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Motor de hábitos",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "La app aprende de tu rutina (apps, sueño, lugares, batería, interrupciones) para crear funciones útiles. Todo se queda en tu teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("habit_engine_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aprender de mis hábitos",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Registro local de uso, sueño y lugares con retención de ${HabitEngine.retentionDays(context)} días.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = habitMaster,
                        onCheckedChange = { checked ->
                            habitMaster = checked
                            HabitEngine.setMasterEnabled(context, checked)
                        },
                        modifier = Modifier.testTag("habit_engine_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                HabitCollectorRow("Apps y desbloqueos (acceso de uso)", habitUsage) { checked ->
                    habitUsage = checked
                    HabitEngine.setUsageCollectorEnabled(context, checked)
                }
                HabitCollectorRow("Notificaciones (solo emisor y hora)", habitNotifs) { checked ->
                    habitNotifs = checked
                    HabitEngine.setNotificationCollectorEnabled(context, checked)
                }
                HabitCollectorRow("Ubicación y redes Wi-Fi", habitLocation) { checked ->
                    habitLocation = checked
                    HabitEngine.setLocationCollectorEnabled(context, checked)
                }
                HabitCollectorRow("Dispositivos Bluetooth", habitBluetooth) { checked ->
                    habitBluetooth = checked
                    HabitEngine.setBluetoothCollectorEnabled(context, checked)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenRoutines,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("open_routines_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver rutinas aprendidas y el brief del día")
                }
            }
        }

        // =============================================================
        // EXPERIMENTAL: dynamic sun background (Samsung mode)
        // =============================================================
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Experimental",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pruebas en desarrollo: actívalas, pruébalas y decide si se quedan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("sun_wallpaper_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fondo dinámico del sol",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "El sol recorre el cielo de tu ubicación y el tono del gradiente cambia del amanecer a la noche (modo Samsung).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sunWallpaperChecked,
                        onCheckedChange = { checked ->
                            sunWallpaperChecked = checked
                            context.getSharedPreferences("sun_wallpaper", Context.MODE_PRIVATE)
                                .edit().putBoolean("enabled", checked).apply()
                        },
                        modifier = Modifier.testTag("sun_wallpaper_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live preview of the same renderer the wallpaper uses.
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    SunGradientBackground()
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(
                                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, SunWallpaperService::class.java)
                                )
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback: generic wallpaper picker.
                            try {
                                context.startActivity(
                                    Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, "No pude abrir el selector de fondos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Poner como fondo de pantalla del sistema")
                }
                Text(
                    text = "El interruptor cambia el fondo DENTRO de la app (modo Samsung); el botón lo instala como fondo de pantalla del teléfono. Come de la última ubicación que usó el clima.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // =============================================================
        // Ubicación y recordatorios por lugar
        // =============================================================
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Ubicación y lugares",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "App privada: guarda tus lugares y recibe recordatorios al acercarte. Las coordenadas viven solo en este teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("places_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!SmartPlaces.hasLocationPermission(context)) {
                    Text(
                        text = "Concede el permiso de ubicación para guardar lugares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                    ) {
                        Text("Permitir ubicación")
                    }
                } else {
                    Button(
                        onClick = { showAddPlaceDialog = true },
                        enabled = !placeSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("add_place_btn")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (placeSaving) "Buscando tu posición…" else "Guardar mi posición actual como lugar")
                    }
                }

                // ---- Spatial reminders: calendar places × current location ----
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                var spatialOn by remember(placesTick) {
                    mutableStateOf(com.example.location.SpatialContextEngine.isEnabled(context))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Recordatorios espaciales del calendario",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Combina los LUGARES de tus eventos del calendario con tu " +
                                    "ubicación: aviso de «es hora de salir» con el tiempo de viaje " +
                                    "estimado (a pie o en coche, según tu actividad detectada) y " +
                                    "notificación de llegada. Dimensión espacial + temporal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = spatialOn,
                        onCheckedChange = { checked ->
                            spatialOn = checked
                            com.example.location.SpatialContextEngine.setEnabled(context, checked)
                        },
                        modifier = Modifier.testTag("spatial_reminders_switch")
                    )
                }
                if (spatialOn) {
                    var spatialPreview by remember(placesTick) { mutableStateOf<String?>(null) }
                    LaunchedEffect(placesTick) {
                        spatialPreview = try {
                            com.example.location.SpatialContextEngine.nextLocatedEvent(context)?.let { le ->
                                "Próximo con lugar: «${le.event.title.take(30)}» — " +
                                        "a ${if (le.distanceMeters >= 1000) "%.1f km".format(le.distanceMeters / 1000.0) else "${le.distanceMeters.toInt()} m"} " +
                                        "(~${le.travelMinutes} min ${le.travelMode})"
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    spatialPreview?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForestPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (savedPlaces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    savedPlaces.forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = ForestPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = if (place.note.isNotBlank()) place.note else "${place.radiusMeters} m de radio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                SmartPlaces.removePlace(context, place.id)
                                placesTick++
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text(
                        text = "La sincronización de 5 minutos avisa cuando estás a menos de ${savedPlaces.first().radiusMeters} m de un lugar (máx. un aviso cada 45 min). Para que suceda con la app cerrada, activa «Ubicación en segundo plano» para Mnemosyne en Ajustes del sistema.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // =============================================================
        // Acerca de (real in-app versioning)
        // =============================================================
        Spacer(modifier = Modifier.height(22.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("about_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mnemosyne · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diario semántico 100% en el dispositivo. Las versiones se firmarán con la misma clave para poder actualizarse sin perder datos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/lavilao/test-journal/releases"))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "No pude abrir el navegador", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Buscar actualizaciones (Releases)", color = ForestPrimary)
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

    // Add-place dialog: captures one fresh fix + a name/note.
    if (showAddPlaceDialog) {
        var placeName by remember { mutableStateOf("") }
        var placeNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!placeSaving) showAddPlaceDialog = false },
            title = { Text("Nuevo lugar") },
            text = {
                Column {
                    Text(
                        text = "Se usará UNA lectura de tu posición actual. Ejemplos: Casa, Trabajo, Mercadona del barrio — con una nota como «comprar pan» o «lleva el paquete».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Nombre del lugar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = placeNote,
                        onValueChange = { placeNote = it },
                        label = { Text("Nota / recordatorio (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (placeName.isNotBlank()) {
                            placeSaving = true
                            scope.launch {
                                val loc = SmartPlaces.freshLocation(context)
                                if (loc != null) {
                                    SmartPlaces.addPlace(context, placeName, loc.latitude, loc.longitude, placeNote)
                                    placesTick++
                                    Toast.makeText(context, "Lugar «$placeName» guardado", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "No pude obtener tu posición. Activa la ubicación e inténtalo de nuevo.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                placeSaving = false
                                showAddPlaceDialog = false
                            }
                        }
                    },
                    enabled = placeName.isNotBlank() && !placeSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text(if (placeSaving) "Guardando…" else "Guardar lugar")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!placeSaving) showAddPlaceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // City picker for the weather card
    if (showCityPicker) {
        CityPickerModal(
            viewModel = viewModel,
            onDismiss = { showCityPicker = false }
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

/** One habit-engine telemetry source with its own switch. */
@Composable
private fun HabitCollectorRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("habit_collector_switch")
        )
    }
}
