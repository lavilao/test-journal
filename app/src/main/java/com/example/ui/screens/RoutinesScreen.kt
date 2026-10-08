package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habit.HabitEngine
import com.example.habit.HabitMiners
import com.example.location.SmartPlaces
import com.example.ui.components.AppIconImage
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.components.GoogleRed
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Rutinas" — the habit engine's home. Shows what the phone has learned
 * about the user's routines (sleep, apps, places, battery, attention,
 * notifications), the sensor/permission status, the AI-generated daily
 * brief and the data controls (retention, delete).
 *
 * Everything here is derived from REAL on-device telemetry; cards stay
 * honest ("aprendiendo…") when there is not enough data yet.
 */
@Composable
fun RoutinesScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val digest by viewModel.habitDigest.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var eventCounts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var masterEnabled by remember { mutableStateOf(HabitEngine.isMasterEnabled(context)) }
    var retentionDays by remember { mutableStateOf(HabitEngine.retentionDays(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var briefText by remember { mutableStateOf<String?>(null) }
    var briefIsFromNeedle by remember { mutableStateOf(false) }
    var generatingBrief by remember { mutableStateOf(false) }
    var placesSaved by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }

    val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun refreshSensors() {
        refreshTick++
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHabitFacts()
        eventCounts = HabitEngine.eventCounts(context)
        val stored = HabitEngine.storedBrief(context)
        if (stored != null && stored.first == todayKey) {
            briefText = stored.second
            briefIsFromNeedle = stored.third
        }
        if (com.example.habit.ActivityTransitionsManager.hasPermission(context)) {
            com.example.habit.ActivityTransitionsManager.register(context)
        }
        loading = false
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshSensors() }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshSensors() }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // With the runtime permission granted, start the Transitions API
        // feed immediately (also re-armed on every sync tick).
        if (com.example.habit.ActivityTransitionsManager.hasPermission(context)) {
            com.example.habit.ActivityTransitionsManager.register(context)
        }
        refreshSensors()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ---------------- Header ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rutinas aprendidas",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Todo se aprende y se queda en tu teléfono",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                loading = true
                scope.launch {
                    viewModel.refreshHabitFacts()
                    eventCounts = HabitEngine.eventCounts(context)
                    loading = false
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }

        val days = digest?.daysObserved ?: 0
        Text(
            text = if (days >= 2) "$days días observados · ${digest?.eventCount ?: 0} eventos" else "Aprendiendo — necesito un par de días",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        if (loading && digest == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analizando tus patrones locales…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------------- Master switch ----------------
        SectionCard(title = "Motor de hábitos", testTag = "routines_master_card") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aprender de tu rutina",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "App usadas, sueño, lugares, batería y notificaciones (nunca el contenido).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { checked ->
                        masterEnabled = checked
                        HabitEngine.setMasterEnabled(context, checked)
                    },
                    modifier = Modifier.testTag("routines_master_switch")
                )
            }
        }

        // ---------------- Sensor status ----------------
        SectionCard(title = "Sensores y permisos") {
            SensorRow(
                title = "Acceso de uso",
                subtitle = "Apps abiertas, desbloqueos y pantalla (Android 15+)",
                granted = HabitEngine.hasUsageStatsPermission(context),
                actionLabel = "Activar",
                key = "usage_$refreshTick"
            ) {
                openSettings(context) { Settings.ACTION_USAGE_ACCESS_SETTINGS }
            }
            SensorRow(
                title = "Notificaciones",
                subtitle = "Qué apps te interrumpen y cuándo (solo emisor y hora)",
                granted = HabitEngine.hasNotificationListenerAccess(context),
                actionLabel = "Conceder",
                key = "notif_$refreshTick"
            ) {
                openSettings(context) { Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS }
            }
            if (Build.VERSION.SDK_INT >= 33 && !HabitEngine.hasNotificationListenerAccess(context)) {
                Text(
                    text = "Si Android lo bloquea por instalación manual: ${com.example.habit.HabitNotificationListener.adbUnlockCommand(context)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            SensorRow(
                title = "Ubicación",
                subtitle = "Wi-Fi de casa/trabajo y lugares frecuentes",
                granted = HabitEngine.hasLocationPermission(context),
                actionLabel = "Permitir",
                key = "loc_$refreshTick"
            ) {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SensorRow(
                    title = "Bluetooth",
                    subtitle = "Auriculares y otros dispositivos recurrentes",
                    granted = HabitEngine.hasBluetoothPermission(context),
                    actionLabel = "Permitir",
                    key = "bt_$refreshTick"
                ) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            val telemetry = viewModel.telemetry.collectAsState().value
            SensorRow(
                title = "Reconocimiento de actividad",
                subtitle = "Contador de pasos para tus tendencias",
                granted = telemetry.hasActivityRecognitionPermission,
                actionLabel = "Permitir",
                key = "ar_$refreshTick"
            ) {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        val d = digest

        // ---------------- Sleep ----------------
        SectionCard(title = "Sueño", icon = { Icon(Icons.Default.Bedtime, null, tint = GoogleBlue) }) {
            val sleep = d?.sleep
            if (sleep == null || sleep.nightsWithData < 2) {
                LearningText("Aprendiendo tu ventana de sueño — déjame un par de noches más.")
            } else {
                sleep.lastNight?.let { n ->
                    FactRow(
                        "Anoche",
                        "${HabitMiners.formatMinuteOfDay(HabitMiners.minuteOfDay(n.startMillis))} → " +
                            "${HabitMiners.formatMinuteOfDay(HabitMiners.minuteOfDay(n.endMillis))} · " +
                            HabitMiners.formatDuration((n.endMillis - n.startMillis) / 60_000L)
                    )
                }
                sleep.typicalBedMinuteOfDay?.let { bed ->
                    val wake = sleep.typicalWakeMinuteOfDay
                    if (wake != null) {
                        FactRow(
                            "Patrón típico",
                            "acostarse ~${HabitMiners.formatMinuteOfDay(bed)}, despertar ~${HabitMiners.formatMinuteOfDay(wake)}"
                        )
                    }
                }
                sleep.medianDurationMinutes?.let { FactRow("Duración mediana", HabitMiners.formatDuration(it.toLong())) }
                sleep.regularity?.let { r ->
                    FactRow("Regularidad", "${(r * 100).toInt()}%", progress = r)
                }
                Text(
                    text = "${sleep.nightsWithData} noches analizadas · silencio nocturno = sueño (heurística local)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---------------- Apps ----------------
        SectionCard(title = "Apps y predicciones", icon = { Icon(Icons.Default.Bolt, null, tint = GoogleBlue) }) {
            val apps = d?.apps
            if (apps == null || (apps.signature.isEmpty() && apps.predictedNow.isEmpty())) {
                LearningText("Activa el acceso de uso para aprender tus apps.")
            } else {
                if (apps.predictedNow.isNotEmpty()) {
                    Text(
                        text = "A esta hora sueles abrir:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    apps.predictedNow.take(3).forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            AppIconImage(packageName = p.pkg, sizeDp = 28)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = HabitMiners.appLabel(context, p.pkg),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${p.score.toInt()} veces",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (apps.signature.isNotEmpty()) {
                    Text(
                        text = "Tus apps firma (7 días):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    apps.signature.forEach { u ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            AppIconImage(packageName = u.pkg, sizeDp = 28)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = HabitMiners.appLabel(context, u.pkg),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${u.opens} aperturas · ${HabitMiners.formatDuration(u.minutes / 60_000L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---------------- Places ----------------
        SectionCard(title = "Lugares", icon = { Icon(Icons.Default.LocationOn, null, tint = GoogleGreen) }) {
            val places = d?.places
            if (places == null || (places.homeBssid == null && places.workBssid == null)) {
                LearningText("Con el permiso de ubicación aprenderé casa y trabajo del Wi-Fi nocturno/diurno.")
            } else {
                places.homeBssid?.let { bssid ->
                    FactRow(
                        "Casa",
                        "red Wi-Fi habitual detectada" + (places.homeConfidence?.let { " · ${(it * 100).toInt()}% de las noches" } ?: "")
                    )
                    places.homeCoords?.let { coords ->
                        if (placesSaved and 1 == 0) {
                            OutlinedButton(
                                onClick = {
                                    SmartPlaces.addPlace(
                                        context,
                                        "Casa (detectada)",
                                        coords.first,
                                        coords.second,
                                        "Aprendida del motor de hábitos"
                                    )
                                    placesSaved = placesSaved or 1
                                },
                                enabled = placesSaved and 1 == 0,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar como lugar inteligente")
                            }
                        } else {
                            Text(
                                text = "Guardada en tus lugares ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleGreen,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                places.workBssid?.let { bssid ->
                    Spacer(modifier = Modifier.height(6.dp))
                    FactRow(
                        "Trabajo",
                        "red Wi-Fi de diario detectada" + (places.workConfidence?.let { " · ${(it * 100).toInt()}% de los días" } ?: "")
                    )
                    places.workCoords?.let { coords ->
                        if (placesSaved and 2 == 0) {
                            OutlinedButton(
                                onClick = {
                                    SmartPlaces.addPlace(
                                        context,
                                        "Trabajo (detectado)",
                                        coords.first,
                                        coords.second,
                                        "Aprendido del motor de hábitos"
                                    )
                                    placesSaved = placesSaved or 2
                                },
                                enabled = placesSaved and 2 == 0,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar como lugar inteligente")
                            }
                        } else {
                            Text(
                                text = "Guardado en tus lugares ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleGreen,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${places.placesSeen} redes distintas vistas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---------------- Battery coach ----------------
        SectionCard(title = "Entrenador de batería", icon = { Icon(Icons.Default.BatteryStd, null, tint = GoogleGreen) }) {
            val battery = d?.battery
            if (battery == null || battery.drainPerHour == null) {
                LearningText("Necesito unas horas de uso para estimar tu consumo real.")
            } else {
                FactRow("Ahora", "${battery.levelNow}%")
                FactRow("Consumo hoy", "~${"%.1f".format(battery.drainPerHour)}%/h")
                battery.projectedBedtimeLevel?.let {
                    FactRow(
                        "Llegada a la hora de dormir",
                        "~$it%",
                        valueColor = if (it <= 15) GoogleRed else GoogleGreen
                    )
                }
                battery.typicalChargeMinuteOfDay?.let {
                    FactRow("Hora típica de carga", HabitMiners.formatMinuteOfDay(it))
                }
                FactRow("Ciclos", "~${battery.chargesPerWeek.toInt()} por semana · ${battery.overnightCharges} nocturnas")
            }
        }

        // ---------------- Attention ----------------
        SectionCard(title = "Atención y pantalla", icon = { Icon(Icons.Default.TrendingUp, null, tint = GoogleBlue) }) {
            val attention = d?.attention
            if (attention == null || attention.screenByDay.size < 2) {
                LearningText("Aún mido tu tiempo de pantalla y desbloqueos.")
            } else {
                FactRow("Pantalla hoy", HabitMiners.formatDuration(attention.screenMinutesToday.toLong()))
                attention.screenMinutes7dAvg?.let {
                    FactRow(
                        "Media 7 días",
                        HabitMiners.formatDuration(it.toInt().toLong()) +
                            (if (attention.screenMinutesToday > it * 1.3f) " · hoy por encima" else "")
                    )
                }
                attention.unlocksPerDayAvg?.let {
                    FactRow(
                        if (attention.unlocksAreDerived) "Desbloqueos (estimados)" else "Desbloqueos",
                        "~${it.toInt()} al día"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Minutos de pantalla (últimos ${attention.screenByDay.size} días):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Sparkline(
                    values = attention.screenByDay.map { it.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                if (attention.bestDay != null && attention.worstDay != null) {
                    FactRow("Días extremos", "más: ${attention.bestDay} · menos: ${attention.worstDay}")
                }
            }
        }

        // ---------------- Notifications ----------------
        SectionCard(title = "Interrupciones", icon = { Icon(Icons.Default.Notifications, null, tint = GoogleRed) }) {
            val notifs = d?.notifications
            if (notifs == null || notifs.topInterruptors.isEmpty()) {
                LearningText("Concede el acceso de notificaciones para ver quién te interrumpe.")
            } else {
                FactRow("Total", "~${notifs.totalPerDay.toInt()} al día")
                notifs.topInterruptors.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        AppIconImage(packageName = t.pkg, sizeDp = 24)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = HabitMiners.appLabel(context, t.pkg),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "~${t.perDay.toInt()}/día" +
                                (t.ignoreRate?.let { " · ignoras el ${(it * 100).toInt()}%" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if ((t.ignoreRate ?: 0f) > 0.6f) GoogleRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------------- Steps ----------------
        SectionCard(title = "Pasos", icon = { Icon(Icons.Default.DirectionsWalk, null, tint = GoogleGreen) }) {
            val steps = d?.steps
            if (steps == null || steps.byDay.size < 2) {
                LearningText("Activa el reconocimiento de actividad para tu tendencia de pasos.")
            } else {
                FactRow("Hoy", "${steps.today} pasos")
                FactRow("Media 7 días", "${steps.avgPerDay.toInt()} pasos")
                Sparkline(
                    values = steps.byDay.map { it.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }

        // ---------------- Routine change ----------------
        d?.rhythmChange?.let { rhythm ->
            SectionCard(title = "Cambio de rutina", icon = { Icon(Icons.Default.TrendingUp, null, tint = GoogleRed) }) {
                Text(
                    text = rhythm.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                rhythm.shifts.forEach {
                    Text(
                        text = "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // ---------------- Daily brief (local AI) ----------------
        SectionCard(
            title = "Brief del día · IA local",
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = GoogleBlue) },
            testTag = "routines_brief_card"
        ) {
            if (generatingBrief) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Redactando con Needle en tu teléfono…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (briefText != null) {
                Text(
                    text = briefText ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = if (briefIsFromNeedle) "Generado por la IA local (Cactus Needle 3)" else "Resumen local sin IA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { briefText = null }) {
                    Text("Regenerar")
                }
            } else {
                Text(
                    text = "Un resumen de tus patrones para empezar el día: sueño, apps, batería e interrupciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val currentDigest = digest ?: return@Button
                        generatingBrief = true
                        scope.launch {
                            val narrative = try {
                                HabitMiners.generateBriefNarrative(context, currentDigest)
                            } catch (_: Exception) {
                                null
                            }
                            val text = narrative ?: HabitMiners.templateBrief(currentDigest)
                            HabitEngine.saveBrief(context, text, fromNeedle = narrative != null)
                            briefIsFromNeedle = narrative != null
                            briefText = text
                            generatingBrief = false
                        }
                    },
                    enabled = digest != null,
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("routines_brief_generate_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generar resumen del día")
                }
            }
        }

        // ---------------- Brief notifications (learned wake time) ----------------
        SectionCard(title = "Notificaciones del brief", testTag = "routines_brief_notify_card") {
            var morningOn by remember { mutableStateOf(com.example.sync.BriefScheduler.isMorningEnabled(context)) }
            var weeklyOn by remember { mutableStateOf(com.example.sync.BriefScheduler.isWeeklyEnabled(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Brief matutino", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "A tu hora aprendida de despertar (mineral de sueño) + 15 min: " +
                                "clima, primer evento con viaje estimado, sueño y batería.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = morningOn,
                    onCheckedChange = { checked ->
                        morningOn = checked
                        scope.launch { com.example.sync.BriefScheduler.setMorningEnabled(context, checked) }
                    },
                    modifier = Modifier.testTag("morning_brief_switch")
                )
            }
            val nextMorning = remember(morningOn) { com.example.sync.BriefScheduler.nextMorningAt(context) }
            if (morningOn && nextMorning > 0) {
                Text(
                    text = "Próximo: ${SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(nextMorning))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Informe semanal", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Domingo 19:00: se guarda COMO NOTA en tu diario (con narrativa " +
                                "de Needle si está descargado) + notificación resumen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = weeklyOn,
                    onCheckedChange = { checked ->
                        weeklyOn = checked
                        scope.launch { com.example.sync.BriefScheduler.setWeeklyEnabled(context, checked) }
                    },
                    modifier = Modifier.testTag("weekly_report_switch")
                )
            }
            val nextWeekly = remember(weeklyOn) { com.example.sync.BriefScheduler.nextWeeklyAt(context) }
            if (weeklyOn && nextWeekly > 0) {
                Text(
                    text = "Próximo: ${SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(nextWeekly))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ---------------- Health Connect (real steps + sleep) ----------------
        SectionCard(title = "Health Connect — pasos y sueño reales", testTag = "routines_hc_card") {
            val hcLauncher = rememberLauncherForActivityResult(
                androidx.health.connect.client.PermissionController
                    .createRequestPermissionResultContract()
            ) { refreshSensors() }
            var hcPrefer by remember { mutableStateOf(com.example.health.HealthConnectManager.preferEnabled(context)) }
            var hcStatus by remember { mutableStateOf(-1) }
            var hcGranted by remember { mutableStateOf(false) }
            var hcSteps by remember { mutableStateOf<Long?>(null) }
            var hcSleep by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(refreshTick) {
                hcStatus = com.example.health.HealthConnectManager.sdkStatus(context)
                hcSteps = com.example.health.HealthConnectManager.todaySteps(context)
                hcSleep = com.example.health.HealthConnectManager.lastNightSleepMinutes(context)
                hcGranted = hcSteps != null
            }
            val available = hcStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
            Text(
                text = when {
                    available -> "Integrado en este sistema: pasos y sueño REALES alimentan el " +
                            "brief, el widget y el informe semanal (nada se envía fuera)."
                    hcStatus == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                        "Android 11: funciona instalando la app «Health Connect by Android» " +
                                "desde Play Store (integrado de serie en Android 14+). Sin ella, " +
                                "sigo usando el sensor de pasos del teléfono."
                    else -> "No disponible en este dispositivo; el sensor de pasos propio sigue " +
                            "usándose."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (available) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permisos de pasos y sueño", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (hcGranted) "Concedidos ✔" else "No concedidos todavía",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hcGranted) GoogleGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = {
                        hcLauncher.launch(com.example.health.HealthConnectManager.readPermissions)
                    }) {
                        Text("Conceder", fontSize = 12.sp)
                    }
                }
                hcSteps?.let {
                    Text("Hoy: $it pasos (Health Connect)", style = MaterialTheme.typography.labelMedium)
                }
                hcSleep?.let {
                    Text("Anoche: ${it / 60} h ${it % 60} min de sueño", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preferir Health Connect", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Si lo desactivas, vuelvo al sensor de pasos propio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hcPrefer,
                        onCheckedChange = { checked ->
                            hcPrefer = checked
                            com.example.health.HealthConnectManager.setPreferEnabled(context, checked)
                        }
                    )
                }
            } else if (hcStatus == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(
                            com.example.health.HealthConnectManager.installIntent(context)
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "Abre Play Store y busca «Health Connect»", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("Instalar Health Connect (Play Store)", fontSize = 12.sp)
                }
            }
        }

        // ---------------- Data & retention ----------------
        SectionCard(title = "Datos del motor") {
            if (eventCounts.isNotEmpty()) {
                Text(
                    text = eventCounts.joinToString("  ·  ") { "${it.second} ${labelForType(it.first)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Retención de datos:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf(15, 30, 45, 90).forEach { days ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = {
                            retentionDays = days
                            HabitEngine.setRetentionDays(context, days)
                        },
                        label = { Text("$days d") }
                    )
                }
            }
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("routines_delete_btn")
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = GoogleRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Borrar todos los datos de hábitos", color = GoogleRed)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar datos de hábitos?") },
            text = { Text("Se elimina todo el registro de eventos aprendidos. No afecta tus notas ni recordatorios.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        HabitEngine.deleteAllData(context)
                        viewModel.refreshHabitFacts()
                        eventCounts = emptyList()
                        briefText = null
                    }
                    showDeleteDialog = false
                }) {
                    Text("Borrar", color = GoogleRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ----------------------------------------------------------------------
// Building blocks
// ----------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .let { m -> if (testTag != null) m.testTag(testTag) else m }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SensorRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    key: String,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (granted) GoogleGreen else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        ) {}
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = "activo",
                style = MaterialTheme.typography.labelMedium,
                color = GoogleGreen
            )
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
    progress: Float? = null,
    valueColor: Color? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
        if (progress != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(GoogleBlue, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun LearningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val maxVal = values.max().coerceAtLeast(1f)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.height(48.dp)
    ) {
        values.forEach { v ->
            val fraction = (v / maxVal).coerceIn(0.06f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((fraction * 44).dp)
                    .background(
                        if (v == values.last()) GoogleBlue else GoogleBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
            )
        }
    }
}

private fun openSettings(context: Context, actionProvider: () -> String) {
    try {
        val intent = Intent(actionProvider()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun labelForType(type: String): String = when (type) {
    "APP_OPEN" -> "apps abiertas"
    "UNLOCK" -> "desbloqueos"
    "SCREEN_ON" -> "pantallas"
    "WIFI" -> "redes wi-fi"
    "NOTIF_POSTED" -> "notificaciones"
    "BATTERY" -> "batería"
    "CHARGE_START" -> "cargas"
    "STEPS" -> "pasos"
    "BT_CONNECT" -> "bluetooth"
    "LOCATION" -> "posiciones"
    else -> type.lowercase()
}
