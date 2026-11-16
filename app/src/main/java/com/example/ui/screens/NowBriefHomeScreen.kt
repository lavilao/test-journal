package com.example.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.contacts.ContactsHelper
import com.example.data.DeviceCalendarEvent
import com.example.data.model.EntryWithRelations
import com.example.habit.HabitMiners
import com.example.rss.RssArticle
import com.example.telemetry.ForecastData
import com.example.telemetry.RealWeatherData
import com.example.telemetry.WeatherCity
import com.example.ui.components.CityPickerModal
import com.example.ui.components.GlassLabel
import com.example.ui.components.GlassRing
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.components.GoogleRed
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.RssOfflineReaderModal
import com.example.ui.components.VoiceTranscriptionModal
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.NowBriefNewsBlue
import com.example.ui.theme.NowBriefPrimaryDark
import com.example.ui.theme.NowBriefPrimaryLight
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import com.example.wallpaper.NowBriefAppBackground
import com.example.wallpaper.SunCycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unified NowBrief home: Samsung "Now brief" liquid-glass visual system
 * (animated aurora / dynamic sun background, frosted glass cards, vitals rings,
 * living clock & weather hero) seamlessly enriched with Google universal search
 * (semantic notes via Needle, MediaStore files, Contacts), pull-to-refresh,
 * contextual device alerts (next alarm, low battery coach), and a cohesive
 * Noticias / Memorias feed switcher with rich discover cards & multi-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowBriefHomeScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onOpenLens: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenRoutines: () -> Unit = {},
    onOpenNews: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val weather by viewModel.realWeather.collectAsState()
    val weatherForecast by viewModel.weatherForecast.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val isRssLoading by viewModel.isRssLoading.collectAsState()
    val calendarEvents by viewModel.upcomingCalendarEvents.collectAsState()
    val nextReminder by viewModel.nextActiveReminder.collectAsState()
    val selectedCity by viewModel.selectedWeatherCity.collectAsState()
    val habitDigest by viewModel.habitDigest.collectAsState()
    val deviceFiles by viewModel.deviceFilesResults.collectAsState()
    val deviceContacts by viewModel.deviceContactsResults.collectAsState()
    val semanticSearch by viewModel.semanticSearch.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()

    var hasContactsPermission by remember {
        mutableStateOf(viewModel.deviceSearchManager.hasContactsPermission())
    }

    var homeSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFeedView by rememberSaveable { mutableStateOf(HomeFeedView.NOTICIAS) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showCityPicker by remember { mutableStateOf(false) }
    var showPhotoChoiceDialog by remember { mutableStateOf(false) }
    var readerArticle by remember { mutableStateOf<RssArticle?>(null) }
    var showQuickDictateModal by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    // Multi-select memories (long-press to enter, tap to toggle, batch delete)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMemoryIds by remember { mutableStateOf(setOf<Long>()) }

    fun exitSelection() {
        selectionMode = false
        selectedMemoryIds = emptySet()
    }

    val deleteSelected: () -> Unit = {
        val ids = selectedMemoryIds.toList()
        if (ids.isNotEmpty()) {
            ids.forEach { viewModel.deleteEntry(it) }
            Toast.makeText(context, "${ids.size} memorias eliminadas", Toast.LENGTH_SHORT).show()
            exitSelection()
        }
    }

    // Runtime permission launchers (contextual to cards / search)
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshTelemetry() }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.refreshCalendarEvents()
        viewModel.refreshTelemetry()
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshStoragePermission()
        if (homeSearchQuery.isNotBlank()) viewModel.searchDevice(homeSearchQuery)
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted && homeSearchQuery.isNotBlank()) viewModel.searchDevice(homeSearchQuery)
    }

    // Gallery Picker & Camera Capture launchers for the Search Bar camera button
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
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

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
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

    // Device-wide & semantic note search while typing
    LaunchedEffect(homeSearchQuery) {
        viewModel.searchDevice(homeSearchQuery)
        viewModel.onSemanticSearchChanged(homeSearchQuery)
    }

    // Living clock, refreshed every 30 seconds
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // System alarm & low battery signals from AtAGlanceBar (unique, not duplicated elsewhere)
    val nextAlarmText = remember(nowMillis) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val trigger = am?.nextAlarmClock?.triggerTime
            if (trigger != null && trigger > System.currentTimeMillis()) {
                val f = SimpleDateFormat("EEE d · HH:mm", Locale.getDefault())
                "Alarma: ${f.format(Date(trigger))}"
            } else null
        } catch (_: Exception) {
            null
        }
    }
    val lowBatteryText = remember(nowMillis) {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (capacity in 1..20) "Batería baja: $capacity%" else null
        } catch (_: Exception) {
            null
        }
    }

    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()) }

    val isDark = isSystemInDarkTheme()
    val dynamicBgOn = remember { SunCycle.isDynamicAtmosphereEnabled(context) }
    val skyCoords = remember(nowMillis) { SunCycle.lastCoordinates(context) }
    val skyDaylight = remember(nowMillis, skyCoords) {
        SunCycle.daylightFor(nowMillis, skyCoords?.first, skyCoords?.second)
    }
    val skyPhase = SunCycle.phaseAt(nowMillis, skyDaylight)
    val skyWeather = SunCycle.weatherFromCode(weather.weatherCode ?: SunCycle.lastWeatherCode(context))
    val useLightHeaderText = if (dynamicBgOn) {
        SunCycle.isDarkHeaderBackdrop(skyPhase, skyWeather, isDark)
    } else {
        isDark
    }
    val heroColor = when {
        !dynamicBgOn -> if (isDark) NowBriefPrimaryDark else NowBriefPrimaryLight
        useLightHeaderText -> Color.White
        else -> Color(0xFF1F2A38)
    }
    val headerSecondaryColor = when {
        !dynamicBgOn -> MaterialTheme.colorScheme.onSurfaceVariant
        useLightHeaderText -> Color.White.copy(alpha = 0.86f)
        else -> Color(0xFF3A495C)
    }
    val headerAccentColor = when {
        !dynamicBgOn -> MaterialTheme.colorScheme.primary
        useLightHeaderText -> Color(0xFFFFD8A8)
        else -> MaterialTheme.colorScheme.primary
    }

    val greeting = remember(nowMillis) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "Buenas noches"
            hour < 12 -> "Buenos días"
            hour < 19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    val searchResults = semanticSearch.hits

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("nowbrief_home_root")
    ) {
        NowBriefAppBackground(
            modifier = Modifier.fillMaxSize(),
            weatherCode = weather.weatherCode
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing || isRssLoading,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshRssFeeds()
                    viewModel.syncNow()
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Living clock + date + clickable city + clean Settings & Refresh actions
                // (No duplicate Assistant/Lens buttons here — Mic & Camera live inside the Search Pill)
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = timeFormat.format(Date(nowMillis)),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 30.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = heroColor
                            )
                            Text(
                                text = dateFormat.format(Date(nowMillis)).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 11.sp
                                ),
                                color = headerSecondaryColor
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showCityPicker = true }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Cambiar ciudad",
                                    tint = headerAccentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = weather.locationName
                                        ?: selectedCity?.name
                                        ?: "Elige tu ciudad",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    color = headerSecondaryColor
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.refreshRssFeeds()
                                viewModel.syncNow()
                            },
                            modifier = Modifier.testTag("home_refresh_btn")
                        ) {
                            if (isRssLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = headerAccentColor
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Actualizar",
                                    tint = headerSecondaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.selectTab(MainNavTab.SETTINGS) },
                            modifier = Modifier.testTag("top_settings_avatar")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                tint = headerSecondaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Greeting hero (collapses automatically while searching)
                if (homeSearchQuery.isBlank()) {
                    item(key = "greeting") {
                        Text(
                            text = "¡$greeting!",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp
                            ),
                            color = heroColor,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Universal Liquid-Glass Pill Search Bar (from Google mode, styled in Samsung glass)
                item(key = "universal_search_bar") {
                    GlassUniversalSearchBar(
                        query = homeSearchQuery,
                        onQueryChange = { homeSearchQuery = it },
                        onVoiceClick = onOpenAssistant,
                        onCameraClick = { showPhotoChoiceDialog = true }
                    )
                }

                if (homeSearchQuery.isNotBlank()) {
                    // Contextual file / contacts permission chips if still missing during search
                    if (!hasStoragePermission || !hasContactsPermission) {
                        item(key = "search_permissions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!hasStoragePermission) {
                                    GlassPermissionPill(
                                        icon = Icons.Default.Folder,
                                        text = "Permitir buscar archivos",
                                        tint = ForestPrimary,
                                        onClick = {
                                            storagePermissionLauncher.launch(
                                                viewModel.requiredStoragePermissions()
                                            )
                                        }
                                    )
                                }
                                if (!hasContactsPermission) {
                                    GlassPermissionPill(
                                        icon = Icons.Default.Contacts,
                                        text = "Permitir contactos",
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Phone-wide search header + semantic AI indicator
                    item(key = "search_results_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "En este dispositivo",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (semanticSearch.isSearching) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = (if (semanticSearch.viaNeedle) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (semanticSearch.viaNeedle) "Semántica · IA local" else "Semántica",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (semanticSearch.viaNeedle) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { homeSearchQuery = "" }) {
                                    Text("Limpiar", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (searchResults.isEmpty() && deviceFiles.isEmpty() && deviceContacts.isEmpty()) {
                        item(key = "search_empty") {
                            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No se encontraron coincidencias para \"$homeSearchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.saveEntry(
                                                id = 0L,
                                                title = homeSearchQuery.replaceFirstChar { it.uppercase() },
                                                body = "",
                                                onComplete = { newId -> onNavigateToDetail(newId) }
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Crear nota con este título")
                                    }
                                }
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        item(key = "search_notes_label") {
                            GlassLabel(
                                text = "📝 Tus notas y memorias (${searchResults.size})",
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        items(searchResults, key = { "note_${it.entryId}" }) { hit ->
                            val viaAi = semanticSearch.viaNeedle && hit.entryId in semanticSearch.needleRanking
                            val icon = if (viaAi) Icons.Default.AutoAwesome else Icons.Default.Description
                            val tint = if (viaAi) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                contentPadding = 14.dp,
                                onClick = { onNavigateToDetail(hit.entryId) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tint.copy(alpha = 0.14f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hit.snippet.isNotBlank()) {
                                            Text(
                                                text = hit.snippet,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = hit.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (viaAi) NowBriefVitalsGreen else MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (deviceFiles.isNotEmpty()) {
                        item(key = "search_files_label") {
                            GlassLabel(
                                text = "📁 Archivos del teléfono (${deviceFiles.size})",
                                accent = NowBriefNewsBlue,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        items(deviceFiles.take(12), key = { "file_${it.id}_${it.dateModifiedMs}" }) { deviceFile ->
                            val isImage = deviceFile.mimeType.startsWith("image/")
                            val isAudio = deviceFile.mimeType.startsWith("audio/")
                            val icon = when {
                                isImage -> Icons.Default.Image
                                isAudio -> Icons.Default.AudioFile
                                else -> Icons.Default.Description
                            }
                            val tint = when {
                                isImage -> NowBriefNewsBlue
                                isAudio -> NowBriefVitalsGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                contentPadding = 14.dp,
                                onClick = { viewModel.openDeviceFile(deviceFile) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tint.copy(alpha = 0.14f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = deviceFile.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeKb = deviceFile.sizeBytes / 1024
                                        Text(
                                            text = "${deviceFile.mimeType.substringAfter('/')} · $sizeKb KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (deviceContacts.isNotEmpty()) {
                        item(key = "search_contacts_label") {
                            GlassLabel(
                                text = "👤 Contactos (${deviceContacts.size})",
                                accent = NowBriefVitalsGreen,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        items(deviceContacts.take(8), key = { "contact_${it.id}_${it.displayName}" }) { contact ->
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                contentPadding = 14.dp,
                                onClick = {
                                    contact.phoneNumber?.let { phone ->
                                        ContactsHelper.dialContact(context, phone)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = NowBriefVitalsGreen.copy(alpha = 0.14f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.displayName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = NowBriefVitalsGreen
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        contact.phoneNumber?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Weather Hero Card (Samsung base)
                    item(key = "weather_hero") {
                        WeatherHeroCard(
                            weather = weather,
                            forecast = weatherForecast,
                            selectedCity = selectedCity,
                            onChooseCity = { showCityPicker = true },
                            onOpenWeatherApp = { viewModel.openSystemWeatherApp() }
                        )
                    }

                    // Vitals Glass Card: Steps / Screen Time / Memories
                    item(key = "vitals_card") {
                        VitalsGlassCard(
                            steps = telemetry.todaySteps,
                            stepGoal = telemetry.stepGoal,
                            hasStepPermission = telemetry.hasActivityRecognitionPermission,
                            hasStepSensor = telemetry.isStepSensorAvailable,
                            screenTimeMinutes = telemetry.screenTimeMinutes,
                            memoriesCount = entries.size,
                            onRequestStepPermission = {
                                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                        )
                    }

                    // Today Glass Card: Calendar + Next Reminder + Unique Glance Alerts (Next Alarm & Low Battery)
                    item(key = "today_card") {
                        TodayGlassCard(
                            nextEvent = calendarEvents.firstOrNull(),
                            hasCalendarPermission = telemetry.hasCalendarPermission,
                            reminderTitle = nextReminder?.title,
                            nextAlarmText = nextAlarmText,
                            lowBatteryText = lowBatteryText,
                            onRequestCalendarPermission = {
                                calendarPermissionLauncher.launch(
                                    viewModel.calendarSyncManager.requiredCalendarPermissions()
                                )
                            },
                            onOpenCalendar = { viewModel.calendarSyncManager.openCalendarApp() },
                            onOpenReminders = {
                                viewModel.selectTab(MainNavTab.NOTIFICACIONES)
                            }
                        )
                    }

                    // Habit Engine Glass Card
                    item(key = "habit_card") {
                        HabitGlassCard(
                            digest = habitDigest,
                            context = context,
                            onOpenRoutines = onOpenRoutines
                        )
                    }

                    // Unified Feed Switcher: Noticias vs Memorias
                    item(key = "feed_switcher") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedFeedView == HomeFeedView.NOTICIAS,
                                    onClick = {
                                        exitSelection()
                                        selectedFeedView = HomeFeedView.NOTICIAS
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("Noticias") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NowBriefNewsBlue.copy(alpha = 0.22f),
                                        selectedLabelColor = NowBriefNewsBlue,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedFeedView == HomeFeedView.NOTICIAS,
                                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                                        selectedBorderColor = NowBriefNewsBlue.copy(alpha = 0.50f)
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
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedFeedView == HomeFeedView.MEMORIAS,
                                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                                    )
                                )
                            }

                            if (selectedFeedView == HomeFeedView.NOTICIAS) {
                                TextButton(onClick = onOpenNews) {
                                    Text(
                                        text = "Ver todas ›",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NowBriefNewsBlue
                                    )
                                }
                            } else {
                                TextButton(onClick = onNavigateToNewEntry) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Escribir",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedFeedView == HomeFeedView.NOTICIAS) {
                        if (rssArticles.isEmpty()) {
                            item(key = "news_empty") {
                                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = if (isRssLoading) {
                                            "Descargando titulares de tus fuentes…"
                                        } else {
                                            "No hay artículos en caché todavía. Desliza hacia abajo para actualizar."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val homeArticles = rssArticles.take(10)
                            items(homeArticles, key = { "news_${it.id}" }) { article ->
                                GlassDiscoverCard(
                                    article = article,
                                    onClick = { readerArticle = article },
                                    onSaveToJournal = { savedArticle ->
                                        viewModel.saveEntry(
                                            id = 0L,
                                            title = savedArticle.title,
                                            body = "${savedArticle.fullContent}\n\nFuente: ${savedArticle.link}",
                                            onComplete = {
                                                Toast.makeText(context, "Artículo guardado en tus notas", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                )
                            }
                            if (rssArticles.size > homeArticles.size) {
                                item(key = "news_see_all_footer") {
                                    LiquidGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 18.dp,
                                        contentPadding = 14.dp,
                                        onClick = onOpenNews
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Explorar las ${rssArticles.size} noticias",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = NowBriefNewsBlue
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = NowBriefNewsBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Memorias tab: Multi-select action bar + rich glass cards
                        if (selectionMode) {
                            item(key = "memories_selection_bar") {
                                LiquidGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 18.dp,
                                    contentPadding = 10.dp,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${selectedMemoryIds.size} seleccionadas",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 6.dp)
                                        )
                                        TextButton(onClick = {
                                            selectedMemoryIds =
                                                if (selectedMemoryIds.size == entries.size) emptySet()
                                                else entries.map { it.entry.id }.toSet()
                                        }) {
                                            Text(
                                                if (selectedMemoryIds.size == entries.size) "Ninguna" else "Todas",
                                                fontSize = 12.sp
                                            )
                                        }
                                        Button(
                                            onClick = deleteSelected,
                                            enabled = selectedMemoryIds.isNotEmpty(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Borrar", fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { exitSelection() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Salir de la selección")
                                        }
                                    }
                                }
                            }
                        }

                        if (entries.isEmpty()) {
                            item(key = "memories_empty") {
                                LiquidGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onNavigateToNewEntry
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Aún no tienes memorias escritas",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tus reflexiones, notas de voz y fotos aparecerán aquí. Toca para escribir tu primera nota.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(entries, key = { "mem_${it.entry.id}" }) { entryWithRel ->
                                GlassMemoryCard(
                                    entryWithRelations = entryWithRel,
                                    selected = selectionMode && entryWithRel.entry.id in selectedMemoryIds,
                                    onLongClick = {
                                        selectionMode = true
                                        selectedMemoryIds = setOf(entryWithRel.entry.id)
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            selectedMemoryIds =
                                                if (entryWithRel.entry.id in selectedMemoryIds) {
                                                    selectedMemoryIds - entryWithRel.entry.id
                                                } else {
                                                    selectedMemoryIds + entryWithRel.entry.id
                                                }
                                            if (selectedMemoryIds.isEmpty()) selectionMode = false
                                        } else {
                                            onNavigateToDetail(entryWithRel.entry.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating quick-capture expandable FAB (sits cleanly above the floating NowBriefGlassNavBar)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
        ) {
            if (fabExpanded) {
                GlassQuickAction(label = "Nueva memoria", icon = Icons.Default.Edit) {
                    fabExpanded = false
                    onNavigateToNewEntry()
                }
                GlassQuickAction(label = "Nota de voz", icon = Icons.Default.Mic) {
                    fabExpanded = false
                    showQuickDictateModal = true
                }
                GlassQuickAction(label = "Escanear con Lens", icon = Icons.Default.DocumentScanner) {
                    fabExpanded = false
                    onOpenLens()
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 10.dp,
                modifier = Modifier.size(56.dp)
            ) {
                IconButton(
                    onClick = { fabExpanded = !fabExpanded },
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("quick_capture_fab")
                ) {
                    Icon(
                        if (fabExpanded) Icons.Default.Clear else Icons.Default.Add,
                        contentDescription = "Captura rápida",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    // Smart Camera / Lens Choice Dialog (triggered from Search Pill camera icon)
    if (showPhotoChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoChoiceDialog = false },
            title = { Text("Cámara inteligente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showPhotoChoiceDialog = false
                            onOpenLens()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lens: texto, traducir, códigos", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoChoiceDialog = false
                            takePictureLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Foto rápida para una memoria", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoChoiceDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Elegir de la galería", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoChoiceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCityPicker) {
        CityPickerModal(
            viewModel = viewModel,
            onDismiss = { showCityPicker = false }
        )
    }

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

    if (showQuickDictateModal) {
        VoiceTranscriptionModal(
            title = "Dictado de Voz Rápido",
            viewModel = viewModel,
            onDismiss = { showQuickDictateModal = false },
            onSaveTranscript = { text ->
                if (text.isNotBlank()) {
                    viewModel.saveEntry(
                        id = 0L,
                        title = "Nota de Voz",
                        body = text,
                        onComplete = { newId -> onNavigateToDetail(newId) }
                    )
                }
                showQuickDictateModal = false
            }
        )
    }

    BackHandler(enabled = selectionMode) { exitSelection() }
}

/** Liquid-glass universal pill search bar with Mic (Assistant) and Camera (Lens / Photo). */
@Composable
private fun GlassUniversalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .testTag("google_pill_search_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar notas, archivos, contactos…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_files_input")
            )

            if (query.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .testTag("clear_search_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onVoiceClick)
                    .testTag("google_voice_search_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Asistente de voz",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = GoogleRed.copy(alpha = 0.14f),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCameraClick)
                    .testTag("google_lens_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Lens / Cámara",
                        tint = GoogleRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPermissionPill(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tint
            )
        }
    }
}

/** Rich RSS Discover card styled in Samsung liquid-glass aesthetics. */
@Composable
private fun GlassDiscoverCard(
    article: RssArticle,
    onSaveToJournal: (RssArticle) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("discover_card_${article.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RssFeed,
                        contentDescription = null,
                        tint = NowBriefNewsBlue.copy(alpha = 0.35f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NowBriefNewsBlue.copy(alpha = 0.16f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = article.sourceTitle.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NowBriefNewsBlue
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = article.sourceTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (article.pubDate.isNotBlank()) {
                            Text(
                                text = " • ${article.pubDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSaveToJournal(article) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Guardar en notas",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TITLE, article.title)
                                    putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir artículo"))
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rich Memory card with tags, audio badge, photo thumbnail, and long-press multi-select. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassMemoryCard(
    entryWithRelations: EntryWithRelations,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val entry = entryWithRelations.entry
    val hasAudio = entryWithRelations.audioRecords.isNotEmpty()
    val photoUri = entry.imageUri ?: entryWithRelations.mediaItems.firstOrNull()?.uri
    val dateFormatted = remember(entry.journalDate) {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(entry.journalDate))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("google_memory_card_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tagText = entryWithRelations.tags.firstOrNull()?.name ?: "Nota"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "#$tagText",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "• $dateFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasAudio) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoogleRed.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Audio",
                                    tint = GoogleRed,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Voz",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = GoogleRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entry.title.ifBlank { "Nota sin título" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val cleanSnippet = remember(entry.body) {
                    entry.body
                        .replace(Regex("#+\\s*"), "")
                        .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
                        .replace(Regex("[*_~`]"), "")
                        .replace("\n", " ")
                        .trim()
                }
                if (cleanSnippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanSnippet,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (photoUri != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

/** Small glass pill used by the expanded FAB quick actions. */
@Composable
private fun GlassQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WeatherHeroCard(
    weather: RealWeatherData,
    forecast: ForecastData?,
    selectedCity: WeatherCity?,
    onChooseCity: () -> Unit,
    onOpenWeatherApp: () -> Unit = {}
) {
    val hasWeather = weather.temperature != null
    val icon: ImageVector = when (weather.weatherCode) {
        0 -> Icons.Default.WbSunny
        1, 2 -> Icons.Default.WbCloudy
        3 -> Icons.Default.Cloud
        else -> Icons.Default.WbCloudy
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        GlassLabel(text = "🌤 Tu clima", accent = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(10.dp))
        if (hasWeather) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${weather.temperature}°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 54.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = weather.conditionText ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val freshness = weather.lastUpdated?.let {
                        val mins = ((System.currentTimeMillis() - it) / 60_000L).toInt()
                        when {
                            mins <= 1 -> "ahora"
                            mins < 60 -> "hace $mins min"
                            else -> "hace ${mins / 60} h"
                        }
                    }
                    Text(
                        text = listOfNotNull(weather.locationName, freshness).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (selectedCity != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCity.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Actualizando el clima… sin conexión se mostrará la última lectura",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Elige tu ciudad para ver el clima",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Funciona sin permisos de ubicación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onChooseCity) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Elegir ciudad")
                }
            }
        }

        if (hasWeather) {
            Spacer(modifier = Modifier.height(8.dp))

            if (forecast != null && forecast.buckets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    forecast.buckets.forEach { bucket ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = bucket.period,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${bucket.temperature}°",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (bucket.rainProbable) "🌧 ${bucket.condition}" else bucket.condition,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (forecast != null && forecast.daily.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    forecast.daily.drop(1).forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val dayName = SimpleDateFormat("EEE", Locale.getDefault())
                                .format(Date(day.dateEpochMs)).replaceFirstChar { it.uppercase() }
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${day.maxTemp}° ${day.minTemp}°",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (day.rainProbable) "🌧" else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pronóstico de la semana en caché — se actualiza con el ciclo del clima.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpenWeatherApp,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.WbCloudy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Abrir la app del clima", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun VitalsGlassCard(
    steps: Int,
    stepGoal: Int,
    hasStepPermission: Boolean,
    hasStepSensor: Boolean,
    screenTimeMinutes: Int?,
    memoriesCount: Int,
    onRequestStepPermission: () -> Unit
) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassLabel(text = "💚 Tus vitales", accent = NowBriefVitalsGreen)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RingStat(
                progress = if (stepGoal > 0) steps.toFloat() / stepGoal else 0f,
                valueText = if (hasStepPermission && hasStepSensor) "$steps" else "—",
                label = "pasos",
                color = NowBriefVitalsGreen
            )

            VerticalDividerSpacer()

            val screenText = screenTimeMinutes?.let {
                val h = it / 60
                val m = it % 60
                if (h > 0) "${h}h ${m}m" else "${m}m"
            } ?: "—"
            RingStat(
                progress = screenTimeMinutes?.let { (it % 60) / 60f } ?: 0f,
                valueText = screenText,
                label = "pantalla",
                color = NowBriefNewsBlue
            )

            VerticalDividerSpacer()

            RingStat(
                progress = (memoriesCount % 10) / 10f,
                valueText = "$memoriesCount",
                label = "memorias",
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (!hasStepPermission && hasStepSensor) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onRequestStepPermission) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Activar contador de pasos")
            }
        } else if (!hasStepSensor) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tu dispositivo no tiene sensor de pasos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RingStat(
    progress: Float,
    valueText: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            GlassRing(
                progress = progress,
                ringColor = color,
                modifier = Modifier.size(78.dp)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalDividerSpacer() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun TodayGlassCard(
    nextEvent: DeviceCalendarEvent?,
    hasCalendarPermission: Boolean,
    reminderTitle: String?,
    nextAlarmText: String?,
    lowBatteryText: String?,
    onRequestCalendarPermission: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenReminders: () -> Unit = {}
) {
    val eventTimeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (hasCalendarPermission) onOpenCalendar else null
    ) {
        GlassLabel(text = "📅 Hoy", accent = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(10.dp))

        if (!hasCalendarPermission) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Conecta tu calendario",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Verás tus próximos eventos aquí",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRequestCalendarPermission) {
                    Text("Permitir")
                }
            }
        } else if (nextEvent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = eventTimeFormat.format(Date(nextEvent.startMillis)),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "hora",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nextEvent.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    nextEvent.location?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Sin eventos próximos. Disfruta tu día ✨",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        reminderTitle?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenReminders)
                    .padding(vertical = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "recordatorio",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Unique device glance signals from AtAGlanceBar (system alarm + low battery)
        if (nextAlarmText != null || lowBatteryText != null) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nextAlarmText?.let { alarm ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = alarm,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                lowBatteryText?.let { batt ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GoogleRed.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = GoogleRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = batt,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = GoogleRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitGlassCard(
    digest: HabitMiners.HabitDigest?,
    context: Context,
    onOpenRoutines: () -> Unit
) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassLabel(text = "🧠 Tus rutinas", accent = NowBriefNewsBlue)
        Spacer(modifier = Modifier.height(10.dp))
        val lines = buildList {
            digest?.sleep?.let { s ->
                s.lastNight?.let { n ->
                    add(
                        "Anoche dormiste " + HabitMiners.formatDuration(
                            (n.endMillis - n.startMillis) / 60_000L
                        )
                    )
                }
                if (s.typicalBedMinuteOfDay != null && s.typicalWakeMinuteOfDay != null) {
                    add(
                        "Te acuestas ~" + HabitMiners.formatMinuteOfDay(s.typicalBedMinuteOfDay) +
                            " y despiertas ~" + HabitMiners.formatMinuteOfDay(s.typicalWakeMinuteOfDay)
                    )
                }
                s.sleepDebtMinutes?.takeIf { it > 15 }?.let {
                    add("Deuda de sueño: " + HabitMiners.formatDuration(it.toLong()) + " vs tu mediana")
                }
                s.bedtimeDriftMinutesPerNight?.let {
                    val dir = if (it > 0) "más tarde" else "más pronto"
                    add("Tu hora de dormir se mueve ~${abs(it).roundToInt()} min $dir cada noche")
                }
            }
            digest?.apps?.let { a ->
                a.predictedNow.firstOrNull()?.let { p ->
                    val conf = a.predictedNowConfidence
                    val label = HabitMiners.appLabel(context, p.pkg)
                    if (conf != null && conf >= 0.55f) {
                        add("A esta hora sueles abrir $label (${(conf * 100).roundToInt()}% de confianza)")
                    } else {
                        add("A esta hora sueles abrir $label")
                    }
                }
            }
            digest?.battery?.let { b ->
                b.projectedBedtimeLevel?.let {
                    val basis = if (b.drainConfidenceDays >= 3) "por tu ritmo reciente" else "por el ritmo de hoy"
                    add("Llegarás a dormir con ~$it% de batería ($basis)")
                }
            }
        }
        if (lines.isEmpty()) {
            Text(
                text = if (digest == null) {
                    "Aprendiendo de tu uso, sueño y lugares…"
                } else {
                    "Aprendiendo — necesito un par de días más de datos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lines.take(4).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenRoutines) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Ver rutinas aprendidas")
        }
    }
}
