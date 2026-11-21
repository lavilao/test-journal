package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.BuildConfig
import com.example.habit.HabitEngine
import com.example.location.SmartPlaces
import com.example.semantic.MlKitAnalyzer
import com.example.ui.components.CityPickerModal
import com.example.ui.components.ExpandableHint
import com.example.ui.components.GlassCollapsibleSectionCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MlKitDiagnosticsCard
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.ui.theme.TerracottaAccent
import com.example.ui.theme.tabular
import com.example.viewmodel.JournalViewModel
import com.example.wallpaper.SunCycle
import com.example.wallpaper.SunGradientBackground
import com.example.wallpaper.SunWallpaperService
import kotlinx.coroutines.launch

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

    val mlkitBackupFiles = remember {
        mutableStateOf<List<com.example.ai.needle.MlKitTransferManager.ModelFile>>(emptyList())
    }
    val mlkitBackupScanned = remember { mutableStateOf(false) }

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
                isEntityModelDownloaded = MlKitAnalyzer.isEntityModelDownloaded()
                MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
                    downloadedModels[lang.code] = MlKitAnalyzer.isModelDownloaded(lang.code)
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
        mlkitBackupFiles.value = com.example.ai.needle.MlKitTransferManager.discover(context)
        mlkitBackupScanned.value = true
    }

    // ---- dynamic time+weather atmosphere + places state ----
    var surfaceMode by remember {
        mutableStateOf(SunCycle.getSurfaceMode(context))
    }
    var sunWallpaperChecked by remember {
        mutableStateOf(surfaceMode == SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER)
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

    // ---- collapsible section states ----
    var expandClimate by remember { mutableStateOf(true) }
    var expandHomeAndPlaces by remember { mutableStateOf(false) }
    var expandVoiceAndLangs by remember { mutableStateOf(false) }
    var expandRss by remember { mutableStateOf(false) }
    var expandStorageAndBackup by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        GlassTopBar(
            title = "Ajustes",
            subtitle = "Clima, voz, lugares y datos locales",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // =========================================================
            // 1. Clima y atmósfera
            // =========================================================
            val modeLabel = when (surfaceMode) {
                SunCycle.AppSurfaceMode.STATIC_NOTION -> "Minimalista (0% GPU)"
                SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER -> "Atmósfera bajo consumo"
                SunCycle.AppSurfaceMode.CLASSIC_AURORA -> "Aurora estático"
            }
            GlassCollapsibleSectionCard(
                title = "Clima y atmósfera",
                summary = "${selectedCity?.name ?: "Sin ciudad"} · $modeLabel",
                icon = Icons.Default.AutoAwesome,
                expanded = expandClimate,
                onToggle = { expandClimate = !expandClimate }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_city_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
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
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).tabular,
                                    color = MaterialTheme.colorScheme.primary
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

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth().testTag("sun_wallpaper_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fondo atmosférico dinámico",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            ExpandableHint(
                                text = "Por defecto la app usa fondo estático Minimalista Notion (0% GPU y mínimo consumo de batería). Actívalo solo si prefieres cielo dinámico en Inicio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = sunWallpaperChecked,
                            onCheckedChange = { checked ->
                                sunWallpaperChecked = checked
                                val target = if (checked) {
                                    SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER
                                } else {
                                    SunCycle.AppSurfaceMode.STATIC_NOTION
                                }
                                surfaceMode = target
                                SunCycle.setSurfaceMode(context, target)
                            },
                            modifier = Modifier.testTag("sun_wallpaper_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            SunCycle.AppSurfaceMode.STATIC_NOTION to "Notion (0% GPU)",
                            SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER to "Cielo (bajo consumo)",
                            SunCycle.AppSurfaceMode.CLASSIC_AURORA to "Aurora estático"
                        ).forEach { (mode, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = surfaceMode == mode,
                                onClick = {
                                    surfaceMode = mode
                                    sunWallpaperChecked = mode == SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER
                                    SunCycle.setSurfaceMode(context, mode)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var previewPreset by remember { mutableStateOf("En vivo") }
                    val previewOverridePhase = when (previewPreset) {
                        "Amanecer" -> SunCycle.Phase.DAWN
                        "Mediodía" -> SunCycle.Phase.MIDDAY
                        "Atardecer" -> SunCycle.Phase.DUSK
                        "Noche" -> SunCycle.Phase.NIGHT
                        "Tormenta" -> SunCycle.Phase.AFTERNOON
                        "Lluvia" -> SunCycle.Phase.AFTERNOON
                        "Nieve" -> SunCycle.Phase.MORNING
                        else -> null
                    }
                    val previewOverrideWeather = when (previewPreset) {
                        "Amanecer" -> SunCycle.AtmosphereWeather.PARTLY_CLOUDY
                        "Mediodía" -> SunCycle.AtmosphereWeather.CLEAR
                        "Atardecer" -> SunCycle.AtmosphereWeather.CLEAR
                        "Noche" -> SunCycle.AtmosphereWeather.CLEAR
                        "Lluvia" -> SunCycle.AtmosphereWeather.RAIN
                        "Tormenta" -> SunCycle.AtmosphereWeather.STORM
                        "Nieve" -> SunCycle.AtmosphereWeather.SNOW
                        else -> null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        SunGradientBackground(
                            weatherCode = weather.weatherCode,
                            overridePhase = previewOverridePhase,
                            overrideWeather = previewOverrideWeather
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("En vivo", "Amanecer", "Mediodía", "Atardecer", "Noche", "Lluvia", "Tormenta", "Nieve").forEach { preset ->
                            androidx.compose.material3.FilterChip(
                                selected = previewPreset == preset,
                                onClick = { previewPreset = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
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
                    ExpandableHint(
                        text = "Modo Notion y Aurora estático consumen 0% GPU en reposo; el cielo dinámico pausa al salir de Inicio.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // =========================================================
            // 2. Inicio, lugares y rutinas
            // =========================================================
            GlassCollapsibleSectionCard(
                title = "Inicio, lugares y rutinas",
                summary = "${savedPlaces.size} lugar(es) · Hábitos ${if (habitMaster) "activos" else "pausados"}",
                icon = Icons.Default.LocationOn,
                expanded = expandHomeAndPlaces,
                onToggle = { expandHomeAndPlaces = !expandHomeAndPlaces }
            ) {
                Column(modifier = Modifier.fillMaxWidth().testTag("widget_config_card")) {
                    Text(
                        text = "Widget At a Glance",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    com.example.widget.WidgetConfig.MODULES.forEach { module ->
                        var moduleEnabled by remember(module.id) {
                            mutableStateOf(com.example.widget.WidgetConfig.isEnabled(context, module.id))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = module.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = module.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = moduleEnabled,
                                onCheckedChange = { checked ->
                                    moduleEnabled = checked
                                    com.example.widget.WidgetConfig.setEnabled(context, module.id, checked)
                                    com.example.widget.AtAGlanceWidgetProvider.refreshAll(context)
                                }
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
                    var weatherHeader by remember {
                        mutableStateOf(com.example.widget.WidgetConfig.weatherHeaderEnabled(context))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Clima junto a la fecha",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = weatherHeader,
                            onCheckedChange = { checked ->
                                weatherHeader = checked
                                com.example.widget.WidgetConfig.setWeatherHeaderEnabled(context, checked)
                                com.example.widget.AtAGlanceWidgetProvider.refreshAll(context)
                            }
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Column(modifier = Modifier.fillMaxWidth().testTag("places_card")) {
                    Text(
                        text = "Ubicación y recordatorios por lugar",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpandableHint(
                        text = "App privada: guarda tus lugares y recibe recordatorios al acercarte. Las coordenadas viven solo en este teléfono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Permitir ubicación")
                        }
                    } else {
                        Button(
                            onClick = { showAddPlaceDialog = true },
                            enabled = !placeSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().testTag("add_place_btn")
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (placeSaving) "Buscando tu posición…" else "Guardar mi posición actual como lugar")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
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
                            ExpandableHint(
                                text = "Combina los LUGARES de tus eventos del calendario con tu ubicación: aviso de «es hora de salir» con el tiempo de viaje estimado.",
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
                                style = MaterialTheme.typography.labelSmall.tabular,
                                color = MaterialTheme.colorScheme.primary,
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
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(place.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        text = if (place.note.isNotBlank()) place.note else "${place.radiusMeters} m de radio",
                                        style = MaterialTheme.typography.labelSmall.tabular,
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
                        ExpandableHint(
                            text = "La sincronización de 5 minutos avisa cuando estás a menos de ${savedPlaces.first().radiusMeters} m de un lugar (máx. un aviso cada 45 min).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Column(modifier = Modifier.fillMaxWidth().testTag("habit_engine_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Motor de hábitos",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            ExpandableHint(
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

                    Spacer(modifier = Modifier.height(6.dp))

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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("open_routines_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver rutinas aprendidas y el brief del día")
                    }
                }
            }

            // =========================================================
            // 3. Voz, cámara e idiomas
            // =========================================================
            val readyLanguagesCount = downloadedModels.values.count { it }
            GlassCollapsibleSectionCard(
                title = "Voz, cámara e idiomas",
                summary = "$readyLanguagesCount idioma(s) offline · Dictado y OCR local",
                icon = Icons.Default.RecordVoiceOver,
                expanded = expandVoiceAndLangs,
                onToggle = { expandVoiceAndLangs = !expandVoiceAndLangs }
            ) {
                Column(modifier = Modifier.fillMaxWidth().testTag("dictation_status_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dictado e IA local",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val available = viewModel.isDictationAvailable
                        val onDevice = viewModel.hasOnDeviceRecognizer
                        Surface(
                            color = if (available || onDevice) {
                                NowBriefVitalsGreen.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = when {
                                    onDevice -> "En el dispositivo"
                                    available -> "Disponible"
                                    else -> "No disponible"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (available || onDevice) NowBriefVitalsGreen else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = viewModel.dictationEngineDescription() +
                            " Configura modelos .cact (Needle, Whistle, Mirage), hotword y Voice Match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenVoiceSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("open_voice_and_assistant_btn")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voz y asistente: modelos, hotword, Voice Match")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Column(modifier = Modifier.fillMaxWidth().testTag("lens_models_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cámara inteligente y OCR",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = NowBriefVitalsGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Sin conexión",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = NowBriefVitalsGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ExpandableHint(
                        text = "Reconoce texto impreso (OCR), traduce con la cámara, lee códigos QR y escanea documentos en tu dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

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
                        Switch(
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
                                "Extrae texto de las primeras páginas de los PDF compartidos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ocrDocs,
                            onCheckedChange = { checked ->
                                ocrDocs = checked
                                com.example.semantic.OcrIndexer.setDocOcrEnabled(context, checked)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Indexar fotos antiguas", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "OCR en lotes de 40 para fotos sin texto",
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
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Indexar", fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                ModelStatusRow(
                    title = "Detección de idioma",
                    subtitle = "Identifica el idioma de tus notas al instante",
                    statusText = "Activo",
                    isReady = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                ModelStatusRow(
                    title = "Visión, rostros e índice semántico",
                    subtitle = "Etiqueta fotos y relaciona memorias sin internet",
                    statusText = "Activo",
                    isReady = true
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Extracción avanzada de entidades", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (isEntityModelDownloaded) "Modelo neuronal activo en el dispositivo" else "Motor base activo (paquete adicional opcional)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDownloadingEntityModel) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                    } else if (isEntityModelDownloaded) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Activo",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                isDownloadingEntityModel = true
                                scope.launch {
                                    val success = MlKitAnalyzer.downloadEntityModel()
                                    isDownloadingEntityModel = false
                                    isEntityModelDownloaded = success
                                    Toast.makeText(context, if (success) "Modelo listo ✓" else "El motor local sigue activo", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("download_entity_model_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Idiomas para traducción sin conexión:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                MlKitAnalyzer.POPULAR_LANGUAGES.take(6).forEach { lang ->
                    val isDownloaded = downloadedModels[lang.code] == true
                    val isDownloading = downloadingModelCode == lang.code

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lang.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isDownloaded) "Descargado para uso sin conexión" else "Disponible (~30 MB)",
                                style = MaterialTheme.typography.labelSmall.tabular,
                                color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                        } else if (isDownloaded) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Listo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
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
                                            if (success) "${lang.displayName} descargado ✓" else "No se pudo descargar. Revisa tu conexión.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.testTag("download_model_${lang.code}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Descargar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Copia de seguridad de idiomas y visión (.zip):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (mlkitBackupScanned.value) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (mlkitBackupFiles.value.isEmpty()) {
                        Text(
                            text = "Encontrados: 0 archivos de modelos en el almacenamiento.",
                            style = MaterialTheme.typography.labelSmall.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Encontrados: ${mlkitBackupFiles.value.size} archivos · " +
                                com.example.ai.needle.MlKitTransferManager.formatBytes(
                                    mlkitBackupFiles.value.sumOf { it.sizeBytes }
                                ),
                            style = MaterialTheme.typography.labelSmall.tabular,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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

            // =========================================================
            // 4. Noticias RSS (Discover)
            // =========================================================
            GlassCollapsibleSectionCard(
                title = "Noticias RSS (Discover)",
                summary = "${rssSources.size} fuente(s) activas",
                icon = Icons.Default.RssFeed,
                expanded = expandRss,
                onToggle = { expandRss = !expandRss }
            ) {
                Text(
                    text = "Gestiona canales RSS para leer noticias sin conexión y sin rastreadores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                rssSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(source.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(source.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {
                                viewModel.removeRssFeed(source.url)
                                Toast.makeText(context, "«${source.name}» eliminado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar feed",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddRssDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir RSS")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.refreshRssFeeds()
                            Toast.makeText(context, "Feeds actualizados", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualizar")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { opmlExportLauncher.launch("mnemosyne-feeds.opml") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar OPML")
                    }
                    OutlinedButton(
                        onClick = { opmlImportLauncher.launch(arrayOf("text/*", "application/xml", "application/octet-stream")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importar OPML")
                    }
                }
            }

            // =========================================================
            // 5. Almacenamiento, índice y respaldo
            // =========================================================
            GlassCollapsibleSectionCard(
                title = "Almacenamiento, índice y respaldo",
                summary = "${entries.size} memorias · ${entities.size} entidades · 100% local",
                icon = Icons.Default.Storage,
                expanded = expandStorageAndBackup,
                onToggle = { expandStorageAndBackup = !expandStorageAndBackup }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Memorias guardadas:", style = MaterialTheme.typography.bodyMedium)
                    Text("${entries.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold).tabular)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Personas, lugares y conceptos:", style = MaterialTheme.typography.bodyMedium)
                    Text("${entities.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold).tabular)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Episodios agrupados:", style = MaterialTheme.typography.bodyMedium)
                    Text("${events.size}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold).tabular)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Almacenamiento:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "100% privado en el dispositivo",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

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
                    Text("Espacio total usado", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        fmt(totalBytes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold).tabular,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val items = listOf(
                    "Texto (${breakdown.entryCount} notas, ${breakdown.pageCount} pág.)" to breakdown.textEstimatedBytes,
                    "Multimedia (${breakdown.photoCount} fotos)" to breakdown.photoBytes,
                    "Audio (${breakdown.audioCount} notas de voz)" to breakdown.audioBytes,
                    "Caché OCR (${breakdown.ocrCharCount} car.)" to (breakdown.ocrCharCount * 2L),
                    "Conexiones (${breakdown.entityCount} ent., ${breakdown.relationshipCount} enl.)" to (breakdown.entityCount * 64L + breakdown.relationshipCount * 32L),
                    "Base de datos local" to breakdown.databaseBytes
                )
                items.forEach { (label, bytes) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(bytes), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold).tabular)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.clearOcrCache {
                            Toast.makeText(context, "Caché OCR limpiada ✓", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("clear_ocr_cache_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Limpiar caché OCR")
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reindexar notas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Tus notas originales no se modifican", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isRebuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(
                            onClick = {
                                viewModel.rebuildAllSemanticMetadata {
                                    Toast.makeText(context, "Índice reconstruido ✓", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("rebuild_metadata_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reindexar")
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Text("Exportación de datos (Markdown / JSON)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                exportDialogTitle = "Exportación Markdown"
                                exportDialogContent = viewModel.getExportMarkdown()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).testTag("export_markdown_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Markdown")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                exportDialogTitle = "Exportación JSON"
                                exportDialogContent = viewModel.getExportJson()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("JSON")
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                MlKitDiagnosticsCard()
            }

            // =========================================================
            // Acerca de
            // =========================================================
            LiquidGlassCard(
                cornerRadius = 20.dp,
                contentPadding = 16.dp,
                modifier = Modifier.fillMaxWidth().testTag("about_card")
            ) {
                Text(
                    text = "Mnemosyne · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).tabular,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diario semántico privado y 100% en tu dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(96.dp))
        }
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
                        Toast.makeText(context, "Copiado al portapapeles ✓", Toast.LENGTH_SHORT).show()
                        exportDialogContent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("copy_export_button")
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportDialogContent = null }) {
                    Text("Cerrar")
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
                            Toast.makeText(
                                context,
                                "«${rssNameInput.ifBlank { "Canal RSS" }}» añadido — persistirá al reiniciar la app",
                                Toast.LENGTH_SHORT
                            ).show()
                            showAddRssDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                    ExpandableHint(
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            color = if (isReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else TerracottaAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isReady) MaterialTheme.colorScheme.primary else TerracottaAccent,
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
