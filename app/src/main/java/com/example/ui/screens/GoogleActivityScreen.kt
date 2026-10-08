package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.theme.ForestPrimary
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleActivityScreen(
    viewModel: JournalViewModel,
    onNavigateToGraph: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current

    val entries by viewModel.entries.collectAsState()
    val recentFiles by viewModel.recentDeviceFiles.collectAsState()
    val recentApps by viewModel.recentDeviceApps.collectAsState()

    var hasUsageAccess by remember { mutableStateOf(viewModel.deviceSearchManager.hasUsageStatsPermission()) }

    // Digital health report (screen time, top apps, steps, sleep hints) —
    // computed from THIS device's local usage data only.
    var healthInsights by remember { mutableStateOf<List<com.example.health.HealthInsight>>(emptyList()) }
    var healthLoading by remember { mutableStateOf(true) }

    LaunchedEffect(hasUsageAccess) {
        healthLoading = true
        healthInsights = HealthInsightsManager.buildTodayReport(context)
        healthLoading = false
    }

    // Re-check the permission when the user comes back from system settings —
    // this was the cause of the "activate usage access" button reappearing
    // even though the permission was already granted.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("google_activity_screen")
    ) {
        // Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Actividad del Dispositivo",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = {
                    viewModel.refreshRecentActivity()
                    hasUsageAccess = viewModel.deviceSearchManager.hasUsageStatsPermission()
                }) {
                    Icon(Icons.Default.History, contentDescription = "Refrescar", tint = GoogleBlue)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SECTION: Digital health report (local data only)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth().testTag("health_report_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    color = GoogleBlue
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
                                    "good" -> GoogleGreen
                                    "warn" -> com.example.ui.components.GoogleRed
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
                                            } else color
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
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // SECTION: Screen time today (parity with the Samsung vitals ring)
            item {
                val telemetry by viewModel.telemetry.collectAsState()
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!hasUsageAccess) {
                            TextButton(onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() }) {
                                Text("Activar", fontSize = 11.sp, color = GoogleBlue)
                            }
                        }
                    }
                }
            }

            // SECTION: Aplicaciones Usadas Recientemente (UsageStatsManager)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = GoogleBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Apps usadas recientemente",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (hasUsageAccess) {
                        TextButton(onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() }) {
                            Text("Ajustes de uso", fontSize = 11.sp, color = GoogleBlue)
                        }
                    }
                }
            }

            if (!hasUsageAccess) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Para mostrar las aplicaciones que has abierto recientemente en tu teléfono, Android requiere permiso de acceso al uso.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.deviceSearchManager.openUsageAccessSettings() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Activar Acceso de Uso", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (recentApps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sin registro de apps recientes en las últimas horas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(recentApps, key = { it.packageName }) { app ->
                    val mins = (app.totalTimeInForegroundMs / (1000 * 60)).coerceAtLeast(1)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.launchDeviceApp(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The app's REAL launcher icon (used to be a
                            // generic phone glyph — looked unfinished).
                            AppIconImage(
                                packageName = app.packageName,
                                sizeDp = 40
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = "Abierta hoy a las ${timeFormat.format(Date(app.lastTimeUsedMs))} • ~$mins min de uso",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.launchDeviceApp(app.packageName) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Abrir app", tint = GoogleBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // SECTION: Archivos Recientes del Dispositivo (MediaStore)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
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
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openDeviceFile(file) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Type-aware icon with image thumbnails (used to
                            // be the same generic file glyph for everything).
                            FileIconImage(file = file, sizeDp = 40)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "$sizeStr • ${dateFormat.format(Date(file.dateModifiedMs))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.openDeviceFile(file) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Abrir archivo", tint = GoogleBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // SECTION: Notas y Memorias Locales
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = GoogleGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Notas y escritos recientes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(onClick = onNavigateToGraph) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(14.dp), tint = ForestPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Grafo", fontSize = 11.sp, color = ForestPrimary)
                    }
                }
            }

            items(entries.take(5), key = { "entry_${it.entry.id}" }) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(item.entry.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(item.entry.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
