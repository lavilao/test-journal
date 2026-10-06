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
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()) }
    val formattedDate = remember { dateFormat.format(Date()).replaceFirstChar { it.uppercase() } }

    var alertIndex by remember { mutableIntStateOf(0) }

    val activeAlertsCount = 2 + (if (calendarEvent != null) 1 else 0)

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            alertIndex = (alertIndex + 1) % activeAlertsCount
        }
    }

    val hasWeather = weather.temperature != null
    val weatherIcon: ImageVector = when (weather.weatherCode) {
        0 -> Icons.Default.WbSunny
        1, 2, 3 -> Icons.Default.WbCloudy
        else -> Icons.Default.Cloud
    }
    val weatherTint: Color = if (weather.weatherCode == 0) GoogleYellow else GoogleBlue

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

        // Line 2: Contextual Calendar / Reminder / Activity Status
        AnimatedContent(
            targetState = alertIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PixelAtAGlanceTransition"
        ) { index ->
            when (index) {
                0 -> {
                    if (calendarEvent != null) {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val timeStr = timeFormat.format(Date(calendarEvent.startMillis))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onCalendarClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = GoogleBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "$timeStr • ${calendarEvent.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (nextReminder != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onReminderClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoogleGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Recordatorio: ${nextReminder.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = if (hasWeather) onWeatherClick else onChooseCityClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasWeather) {
                                    "${weather.conditionText} • ${weather.locationName}"
                                } else {
                                    "Toca para elegir tu ciudad y ver el clima"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                1 -> {
                    if (nextReminder != null && calendarEvent != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onReminderClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoogleGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Recordatorio: ${nextReminder.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = if (hasWeather) onWeatherClick else onChooseCityClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasWeather) {
                                    "${weather.conditionText} en ${weather.locationName}"
                                } else {
                                    "Toca para elegir tu ciudad y ver el clima"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (telemetry != null) {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = GoogleGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "${telemetry.todaySteps} pasos hoy",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = if (hasWeather) {
                                    "${weather.conditionText} • ${weather.locationName}"
                                } else {
                                    "Toca para elegir tu ciudad y ver el clima"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
