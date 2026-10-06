package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.telemetry.RealWeatherData
import com.example.ui.components.AuroraBackground
import com.example.ui.components.CityPickerModal
import com.example.ui.components.GlassLabel
import com.example.ui.components.GlassRing
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.NowBriefNewsBlue
import com.example.ui.theme.NowBriefPrimaryDark
import com.example.ui.theme.NowBriefPrimaryLight
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung "Now brief" style home: liquid-glass cards floating over an
 * animated aurora background — weather hero, vitals rings, today's agenda,
 * news digest and recent memories. Minimal text, glanceable data.
 */
@Composable
fun NowBriefHomeScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val weather by viewModel.realWeather.collectAsState()
    val rssArticles by viewModel.rssArticles.collectAsState()
    val isRssLoading by viewModel.isRssLoading.collectAsState()
    val calendarEvents by viewModel.upcomingCalendarEvents.collectAsState()
    val nextReminder by viewModel.nextActiveReminder.collectAsState()

    var showCityPicker by remember { mutableStateOf(false) }

    // Runtime permission launchers (contextual, asked inside the relevant card)
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshTelemetry() }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshCalendarEvents()
        viewModel.refreshTelemetry()
    }

    // Living clock, refreshed every 30 seconds.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val heroColor = if (isDark) NowBriefPrimaryDark else NowBriefPrimaryLight

    val greeting = remember(nowMillis) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "Buenas noches"
            hour < 12 -> "Buenos días"
            hour < 19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: big time + date + city + settings
            item {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = weather.locationName
                                    ?: viewModel.selectedWeatherCity.value?.name
                                    ?: "Elige tu ciudad",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.selectTab(com.example.viewmodel.MainNavTab.SETTINGS) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = {
                        viewModel.refreshRssFeeds()
                        viewModel.refreshWeather()
                        viewModel.refreshCalendarEvents()
                        viewModel.refreshTelemetry()
                    }) {
                        if (isRssLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Actualizar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Greeting hero
            item {
                Text(
                    text = "$greeting!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp
                    ),
                    color = heroColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Weather hero card
            item {
                WeatherHeroCard(
                    weather = weather,
                    onChooseCity = { showCityPicker = true }
                )
            }

            // Vitals card (steps / screen time / memories)
            item {
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

            // Today: calendar + reminder
            item {
                TodayGlassCard(
                    nextEvent = calendarEvents.firstOrNull(),
                    hasCalendarPermission = telemetry.hasCalendarPermission,
                    reminderTitle = nextReminder?.title,
                    onRequestCalendarPermission = {
                        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    onOpenCalendar = { viewModel.calendarSyncManager.openCalendarApp() }
                )
            }

            // News digest
            if (rssArticles.isNotEmpty()) {
                item {
                    NewsGlassCard(
                        articles = rssArticles.take(3),
                        accent = NowBriefNewsBlue
                    )
                }
            }

            // Recent memories
            item {
                MemoriesGlassCard(
                    entries = entries.take(3),
                    onWrite = onNavigateToNewEntry,
                    onOpen = onNavigateToDetail
                )
            }

            item { Spacer(modifier = Modifier.height(84.dp)) }
        }

        // Glass FAB for quick capture
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 108.dp)
        ) {
            IconButton(
                onClick = onNavigateToNewEntry,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Nueva memoria",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    if (showCityPicker) {
        CityPickerModal(
            viewModel = viewModel,
            onDismiss = { showCityPicker = false }
        )
    }
}

@Composable
private fun WeatherHeroCard(
    weather: RealWeatherData,
    onChooseCity: () -> Unit
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
                    Text(
                        text = weather.locationName ?: "",
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
            // Steps ring
            RingStat(
                progress = if (stepGoal > 0) steps.toFloat() / stepGoal else 0f,
                valueText = if (hasStepPermission && hasStepSensor) "$steps" else "—",
                label = "pasos",
                color = NowBriefVitalsGreen
            )

            VerticalDivider()

            // Screen time
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

            VerticalDivider()

            // Memories
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
private fun VerticalDivider() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun TodayGlassCard(
    nextEvent: com.example.data.DeviceCalendarEvent?,
    hasCalendarPermission: Boolean,
    reminderTitle: String?,
    onRequestCalendarPermission: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val eventTimeFormat = SimpleDateFormat("H:mm", Locale.getDefault())

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
    }
}

@Composable
private fun NewsGlassCard(
    articles: List<com.example.rss.RssArticle>,
    accent: Color
) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassLabel(text = "📰 Digest de noticias", accent = accent, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.RssFeed,
                contentDescription = null,
                tint = accent.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        articles.forEachIndexed { index, article ->
            if (index > 0) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
            Column {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = article.sourceTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoriesGlassCard(
    entries: List<com.example.data.model.EntryWithRelations>,
    onWrite: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            GlassLabel(
                text = "✍️ Tus memorias",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            ) {
                IconButton(onClick = onWrite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Escribir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Text(
                text = "Aún no hay memorias. Escribe tu primera nota →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        } else {
            entries.forEach { entryWithRel ->
                val entry = entryWithRel.entry
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry.id) }
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date(entry.journalDate)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(44.dp)
                    )
                    Text(
                        text = entry.title.ifBlank { entry.body.take(40) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
