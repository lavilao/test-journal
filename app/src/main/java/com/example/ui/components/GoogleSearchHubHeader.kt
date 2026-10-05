package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceCalendarEvent
import com.example.data.model.LocalReminder
import com.example.telemetry.LifeHubTelemetry
import com.example.telemetry.RealWeatherData

val GoogleBlue = Color(0xFF4285F4)
val GoogleRed = Color(0xFFEA4335)
val GoogleYellow = Color(0xFFFBBC05)
val GoogleGreen = Color(0xFF34A853)

@Composable
fun GoogleSearchHubHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    telemetry: LifeHubTelemetry,
    nextReminder: LocalReminder?,
    weather: RealWeatherData = RealWeatherData(),
    calendarEvent: DeviceCalendarEvent? = null,
    onVoiceClick: () -> Unit,
    onCameraClick: () -> Unit,
    onWeatherClick: () -> Unit = {},
    onReminderClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_search_hub_header")
    ) {
        // Top Bar: Clean, minimal header with a single Settings avatar on the right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSettingsClick)
                    .testTag("top_settings_avatar")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Pixel-Style "At a Glance" Widget (Transparent & Borderless)
        AtAGlanceBar(
            weather = weather,
            nextReminder = nextReminder,
            calendarEvent = calendarEvent,
            telemetry = telemetry,
            onWeatherClick = onWeatherClick,
            onReminderClick = onReminderClick,
            onCalendarClick = onCalendarClick,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Signature Google Pill Search Bar with clipped rounded shadow
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .testTag("google_pill_search_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Buscar archivos o notas...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_files_input")
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.testTag("google_voice_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictado de voz",
                        tint = GoogleBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier.testTag("google_lens_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Tomar foto / Cámara",
                        tint = GoogleRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
