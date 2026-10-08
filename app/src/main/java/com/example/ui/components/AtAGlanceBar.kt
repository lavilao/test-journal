package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceCalendarEvent
import com.example.data.model.LocalReminder
import com.example.telemetry.LifeHubTelemetry
import com.example.telemetry.RealWeatherData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One rotating contextual line of the At a Glance bar. */
private data class GlanceChip(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val text: String,
    val onClick: (() -> Unit)? = null
)

@Composable
fun AtAGlanceBar(
    weather: RealWeatherData = RealWeatherData(),
    nextReminder: LocalReminder? = null,
    calendarEvent: DeviceCalendarEvent? = null,
    telemetry: LifeHubTelemetry? = null,
    onWeatherClick: () -> Unit = {},
    onChooseCityClick: () -> Unit = {},
    onReminderClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onActivateSteps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()) }
    val formattedDate = remember { dateFormat.format(Date()).replaceFirstChar { it.uppercase() } }

    val hasWeather = weather.temperature != null
    val weatherIcon: ImageVector = when (weather.weatherCode) {
        0 -> Icons.Default.WbSunny
        1, 2, 3 -> Icons.Default.WbCloudy
        else -> Icons.Default.Cloud
    }
    val weatherTint: Color = if (weather.weatherCode == 0) GoogleYellow else GoogleBlue

    // ------------------------------------------------------------------
    // Rotating contextual chips — everything REAL, in the spirit of the
    // Pixel At a Glance but sourced from THIS device only (calendar,
    // tasks, next alarm, low battery, steps) — no web data involved.
    // ------------------------------------------------------------------
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val nextAlarmText = remember {
        try {
            val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
            val t = am?.nextAlarmClock?.triggerTime
            if (t != null && t > System.currentTimeMillis()) {
                val f = SimpleDateFormat("EEE d · HH:mm", Locale.getDefault())
                "Próxima alarma: ${f.format(Date(t))}"
            } else null
        } catch (_: Exception) {
            null
        }
    }

    val lowBatteryText = remember {
        try {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val p = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (p in 1..20) "Batería baja: $p% — conecta el cargador" else null
        } catch (_: Exception) {
            null
        }
    }

    val chips = remember(calendarEvent, nextReminder, nextAlarmText, lowBatteryText, telemetry?.todaySteps, telemetry?.hasActivityRecognitionPermission) {
        buildList {
            calendarEvent?.let { ev ->
                add(
                    GlanceChip(
                        Icons.Default.CalendarToday,
                        GoogleBlue,
                        "${timeFormat.format(Date(ev.startMillis))} • ${ev.title}",
                        onCalendarClick
                    )
                )
            }
            nextReminder?.let { r ->
                add(GlanceChip(Icons.Default.CheckCircle, GoogleGreen, "Recordatorio: ${r.title}", onReminderClick))
            }
            nextAlarmText?.let {
                add(GlanceChip(Icons.Default.Alarm, GoogleRed, it, null))
            }
            lowBatteryText?.let {
                add(GlanceChip(Icons.Default.BatteryAlert, GoogleRed, it, null))
            }
            telemetry?.let { t ->
                if (t.hasActivityRecognitionPermission) {
                    add(GlanceChip(Icons.Default.DirectionsWalk, GoogleGreen, "${t.todaySteps} pasos hoy", null))
                } else {
                    add(GlanceChip(Icons.Default.DirectionsWalk, GoogleGreen.copy(alpha = 0.6f), "Toca para activar el contador de pasos", onActivateSteps))
                }
            }
            if (isEmpty()) {
                add(
                    GlanceChip(
                        Icons.Default.Cloud,
                        GoogleBlue,
                        if (hasWeather) "${weather.conditionText} • ${weather.locationName}" else "Toca para elegir tu ciudad y ver el clima",
                        if (hasWeather) onWeatherClick else onChooseCityClick
                    )
                )
            }
        }
    }

    var alertIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(chips.size) {
        if (chips.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            alertIndex = (alertIndex + 1) % chips.size
        }
    }

    // Pixel Style: Borderless, Transparent Background
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .testTag("pixel_at_a_glance_bar")
    ) {
        // Line 1: Date & Real Live Weather
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier
                    .clickable(onClick = if (hasWeather) onWeatherClick else onChooseCityClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasWeather) {
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = weather.conditionText,
                        tint = weatherTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${weather.temperature}°C",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddLocation,
                        contentDescription = "Elegir ciudad",
                        tint = GoogleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Elegir ciudad",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = GoogleBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Line 2: rotating contextual chips (calendar / task / alarm /
        // battery / steps) — the Pixel-style glance, minus the web.
        AnimatedContent(
            targetState = alertIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PixelAtAGlanceTransition"
        ) { index ->
            val chip = chips.getOrNull(index) ?: chips.firstOrNull()
            if (chip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (chip.onClick != null) Modifier.clickable(onClick = chip.onClick) else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = chip.icon,
                        contentDescription = null,
                        tint = chip.tint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = chip.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
