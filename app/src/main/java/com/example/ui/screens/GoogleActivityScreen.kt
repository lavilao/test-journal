package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.health.HealthInsightsManager
import com.example.ui.components.AppIconImage
import com.example.ui.components.FileIconImage
import com.example.ui.components.GlassTopBar
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.WeeklyHealthTrendsCard
import com.example.ui.theme.NowBriefVitalsGreen
import com.example.ui.theme.tabular
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleActivityScreen(
    viewModel: JournalViewModel,
    onNavigateToGraph: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onOpenRoutines: () -> Unit = {}
) {
    val context = LocalContext.current

    val entries by viewModel.entries.collectAsState()
    val recentFiles by viewModel.recentDeviceFiles.collectAsState()
    val recentApps by viewModel.recentDeviceApps.collectAsState()
    val habitDigest by viewModel.habitDigest.collectAsState()
    val weeklyTrends by viewModel.weeklyHealthTrends.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    val activityPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshTelemetry()
            viewModel.refreshWeeklyHealthTrends()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWeeklyHealthTrends()
    }

    var hasUsageAccess by remember { mutableStateOf(viewModel.deviceSearchManager.hasUsageStatsPermission()) }
    var healthInsights by remember { mutableStateOf<List<com.example.health.HealthInsight>>(emptyList()) }
    var healthLoading by remember { mutableStateOf(true) }

    LaunchedEffect(hasUsageAccess) {
        healthLoading = true
        healthInsights = HealthInsightsManager.buildTodayReport(context)
        healthLoading = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = viewModel.deviceSearchManager.hasUsageStatsPermission()
                viewModel.refreshRecentActivity()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRecentActivity()
        hasUsageAccess = viewModel.deviceSearchManager.hasUsageStatsPermission()
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val glassBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("google_activity_screen")
    ) {
        GlassTopBar(
            title = "Actividad del Dispositivo",
            subtitle = "Salud digital, rutinas y registros locales",
            actions = {
                IconButton(onClick = {
                    viewModel.refreshRecentActivity()
                    hasUsageAccess = viewModel.deviceSearchManager.hasUsageStatsPermission()
                }) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "Refrescar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = navBottom + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            weeklyTrends?.let { trends ->
                item {
                    WeeklyHealthTrendsCard(
                        trends = trends,
                        onConnectHealthClick = {
                            activityPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    )
                }
            }

            // Digital health report (local data only)
            item {
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    contentPadding = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_report_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Informe de salud digital",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "hoy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (healthLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Analizando el uso local de hoy…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        healthInsights.forEach { insight ->
                            val color = when (insight.severity) {
                                "good" -> NowBriefVitalsGreen
                                "warn" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = insight.emoji,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = insight.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (insight.severity == "info") {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            color
                                        }
                                    )
                                    Text(
                                        text = insight.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Todo viene del registro de uso del propio " +
                                "teléfono (UsageStats local) y del sensor de pasos; " +
                                "nada sale del dispositivo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Learned routines (habit engine digest)
            item {
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    contentPadding = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routines_summary_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rutinas aprendidas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${habitDigest?.daysObserved ?: 0} días",
                            style = MaterialTheme.typography.labelSmall.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val routineLines = buildList {
                        habitDigest?.sleep?.lastNight?.let { n ->
                            add(
                                "Anoche dormiste " + com.example.habit.HabitMiners.formatDuration(
                                    (n.endMillis - n.startMillis) / 60_000L
                                )
                            )
                        }
                        habitDigest?.apps?.predictedNow?.firstOrNull()?.let {
                            add("A esta hora sueles abrir " + com.example.habit.HabitMiners.appLabel(context, it.pkg))
                        }
                        habitDigest?.battery?.projectedBedtimeLevel?.let {
                            add("Batería a la hora de dormir: ~$it%")
                        }
                        habitDigest?.notifications?.topInterruptors?.firstOrNull()?.let { t ->
                            t.ignoreRate?.let { r ->
                                add(
                                    "Ignoras el " + (r * 100).toInt() + "% de los avisos de " +
                                        com.example.habit.HabitMiners.appLabel(context, t.pkg)
                                )
                            }
                        }
                    }
                    if (routineLines.isEmpty()) {
                        Text(
                            text = "Aprendiendo de tu uso, sueño, lugares y batería — todo local.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        routineLines.take(3).forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenRoutines,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver rutinas y brief del día")
                    }
                }
            }

            // Screen time today
            item {
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tiempo de pantalla hoy",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val minutes = telemetry.screenTimeMinutes
                            Text(
                                text = if (minutes == null) {
                                    "Activa el acceso de uso para verlo"
                                } else {
                                    val h = minutes / 60
                                    val m = minutes % 60
                                    if (h > 0) "${h} h ${m} min" else "${m} min"
                                },
                                style = MaterialTheme.typography.bodySmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!hasUsageAccess) {
                            TextButton(onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() }) {
                                Text("Activar", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Apps usadas recientemente
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Apps usadas recientemente",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (hasUsageAccess) {
                        TextButton(onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() }) {
                            Text("Ajustes de uso", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (!hasUsageAccess) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        contentPadding = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Para mostrar las aplicaciones que has abierto recientemente en tu teléfono, Android requiere permiso de acceso al uso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Activar Acceso de Uso", fontSize = 12.sp)
                        }
                    }
                }
            } else if (recentApps.isEmpty()) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        contentPadding = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sin registro de apps recientes en las últimas horas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recentApps, key = { it.packageName }) { app ->
                    val mins = (app.totalTimeInForegroundMs / (1000 * 60)).coerceAtLeast(1)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = glassSurface,
                        border = BorderStroke(1.dp, glassBorder),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.launchDeviceApp(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                sizeDp = 40
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = "Abierta hoy a las ${timeFormat.format(Date(app.lastTimeUsedMs))} • ~$mins min de uso",
                                    style = MaterialTheme.typography.labelSmall.tabular,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.launchDeviceApp(app.packageName) }) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Abrir app",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Archivos Recientes del Dispositivo (MediaStore)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Archivos recientes del teléfono",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (recentFiles.isEmpty()) {
                item {
                    Text(
                        text = "No se encontraron archivos recientes en el almacenamiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(recentFiles, key = { "rec_file_${it.id}" }) { file ->
                    val kb = file.sizeBytes / 1024
                    val sizeStr = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
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
                            FileIconImage(file = file, sizeDp = 40)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$sizeStr • ${dateFormat.format(Date(file.dateModifiedMs))}",
                                    style = MaterialTheme.typography.labelSmall.tabular,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.openDeviceFile(file) }) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Abrir archivo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Notas y Memorias Locales
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = NowBriefVitalsGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Notas y escritos recientes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(onClick = onNavigateToGraph) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Grafo", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            items(entries.take(5), key = { "entry_${it.entry.id}" }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = glassSurface,
                    border = BorderStroke(1.dp, glassBorder),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(item.entry.id) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            item.entry.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            item.entry.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

