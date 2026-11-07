package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.health.DayHealthTrend
import com.example.health.WeeklyHealthTrends

enum class HealthMetricType {
    STEPS,
    SLEEP,
    ACTIVITY
}

val HealthStepsColor = Color(0xFF34A853) // Android Green
val HealthSleepColor = Color(0xFF673AB7) // Deep Violet
val HealthActivityColor = Color(0xFFFF6F00) // Energetic Orange / Amber

@Composable
fun WeeklyHealthTrendsCard(
    trends: WeeklyHealthTrends,
    modifier: Modifier = Modifier,
    onConnectHealthClick: (() -> Unit)? = null
) {
    var selectedMetric by remember { mutableStateOf(HealthMetricType.STEPS) }
    var selectedDayIndex by remember { mutableIntStateOf(trends.days.size - 1) }

    val activeColor = when (selectedMetric) {
        HealthMetricType.STEPS -> HealthStepsColor
        HealthMetricType.SLEEP -> HealthSleepColor
        HealthMetricType.ACTIVITY -> HealthActivityColor
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_health_trends_card")
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            // Header: Title + Health Connect / Sensor status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = activeColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (selectedMetric) {
                                    HealthMetricType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
                                    HealthMetricType.SLEEP -> Icons.Default.Bedtime
                                    HealthMetricType.ACTIVITY -> Icons.Default.FitnessCenter
                                },
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tendencias Semanales",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Activity Recognition & Health Connect",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Source indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (trends.isHealthConnectActive) HealthStepsColor else GoogleBlue).copy(alpha = 0.10f),
                    modifier = Modifier.then(
                        if (onConnectHealthClick != null) Modifier.clickable(onClick = onConnectHealthClick) else Modifier
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (trends.isHealthConnectActive) HealthStepsColor else GoogleBlue)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (trends.isHealthConnectActive) "Health Connect" else "Sensor local",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = if (trends.isHealthConnectActive) HealthStepsColor else GoogleBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMetric == HealthMetricType.STEPS,
                    onClick = { selectedMetric = HealthMetricType.STEPS },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Pasos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HealthStepsColor.copy(alpha = 0.15f),
                        selectedLabelColor = HealthStepsColor
                    )
                )

                FilterChip(
                    selected = selectedMetric == HealthMetricType.SLEEP,
                    onClick = { selectedMetric = HealthMetricType.SLEEP },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Sueño") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HealthSleepColor.copy(alpha = 0.15f),
                        selectedLabelColor = HealthSleepColor
                    )
                )

                FilterChip(
                    selected = selectedMetric == HealthMetricType.ACTIVITY,
                    onClick = { selectedMetric = HealthMetricType.ACTIVITY },
                    leadingIcon = {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Actividad") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HealthActivityColor.copy(alpha = 0.15f),
                        selectedLabelColor = HealthActivityColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Key Stat of the Selected Day
            val selectedDay = trends.days.getOrNull(selectedDayIndex.coerceIn(0, trends.days.size - 1))
            val (statValue, statUnit, statSubtitle) = when (selectedMetric) {
                HealthMetricType.STEPS -> {
                    val steps = selectedDay?.steps ?: 0
                    val goalPercent = if (trends.stepGoal > 0) (steps * 100) / trends.stepGoal else 0
                    Triple("$steps", "pasos", "$goalPercent% de la meta diaria (8.000)")
                }
                HealthMetricType.SLEEP -> {
                    val mins = selectedDay?.sleepMinutes ?: 0
                    val h = mins / 60
                    val m = mins % 60
                    val formatted = if (h > 0) "${h}h ${m}m" else "${m}m"
                    val desc = when {
                        mins >= 420 -> "Descanso reparador (óptimo)"
                        mins >= 360 -> "Sueño ligero"
                        else -> "Sueño corto"
                    }
                    Triple(formatted, "duración", desc)
                }
                HealthMetricType.ACTIVITY -> {
                    val actMin = selectedDay?.activeMinutes ?: 0
                    val cal = (actMin * 4.2).toInt() // Rough kcal estimate
                    Triple("$actMin", "minutos activos", "~$cal kcal quemadas en movimiento")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = statValue,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = activeColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statUnit,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        text = statSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                selectedDay?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "${it.dayLabel} · ${it.date.dayOfMonth}/${it.date.monthValue}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom High-Performance Chart (Canvas Bar Visualizer with interaction)
            WeeklyHealthBarChart(
                days = trends.days,
                metric = selectedMetric,
                barColor = activeColor,
                selectedIndex = selectedDayIndex,
                onSelectIndex = { selectedDayIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Weekly Summary Bottom Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryPill(
                    label = "Promedio pasos",
                    value = "${trends.averageDailySteps}/día",
                    color = HealthStepsColor
                )
                SummaryPill(
                    label = "Promedio sueño",
                    value = "${trends.averageSleepMinutes / 60}h ${trends.averageSleepMinutes % 60}m",
                    color = HealthSleepColor
                )
                SummaryPill(
                    label = "Total activo",
                    value = "${trends.totalActiveMinutes} min",
                    color = HealthActivityColor
                )
            }
        }
    }
}

@Composable
private fun WeeklyHealthBarChart(
    days: List<DayHealthTrend>,
    metric: HealthMetricType,
    barColor: Color,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return

    val values = days.map { day ->
        when (metric) {
            HealthMetricType.STEPS -> day.steps.toFloat()
            HealthMetricType.SLEEP -> day.sleepMinutes.toFloat()
            HealthMetricType.ACTIVITY -> day.activeMinutes.toFloat()
        }
    }

    val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable {
                    // Interaction handled via tapped day columns below
                }
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barCount = days.size
            val slotWidth = totalWidth / barCount
            val barWidth = slotWidth * 0.45f
            val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

            // Draw faint reference baseline grid
            drawLine(
                color = outlineColor,
                start = Offset(0f, totalHeight * 0.25f),
                end = Offset(totalWidth, totalHeight * 0.25f),
                strokeWidth = 1f
            )
            drawLine(
                color = outlineColor,
                start = Offset(0f, totalHeight * 0.75f),
                end = Offset(totalWidth, totalHeight * 0.75f),
                strokeWidth = 1f
            )

            // Draw bars
            days.forEachIndexed { i, _ ->
                val v = values[i]
                val normalizedHeight = (v / maxValue).coerceIn(0.06f, 1f) * (totalHeight - 10f)
                val isSelected = i == selectedIndex

                val barLeft = i * slotWidth + (slotWidth - barWidth) / 2
                val barTop = totalHeight - normalizedHeight

                // Background track
                drawRoundRect(
                    color = surfaceVariant.copy(alpha = 0.35f),
                    topLeft = Offset(barLeft, 0f),
                    size = Size(barWidth, totalHeight),
                    cornerRadius = cornerRadius
                )

                // Filled Bar (solid or gradient)
                val brush = if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.75f)),
                        startY = barTop,
                        endY = totalHeight
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(barColor.copy(alpha = 0.45f), barColor.copy(alpha = 0.20f)),
                        startY = barTop,
                        endY = totalHeight
                    )
                }

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, normalizedHeight),
                    cornerRadius = cornerRadius
                )

                // Selection dot on top
                if (isSelected) {
                    drawCircle(
                        color = barColor,
                        radius = 3.5f.dp.toPx(),
                        center = Offset(barLeft + barWidth / 2, (barTop - 7f.dp.toPx()).coerceAtLeast(4f.dp.toPx()))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Day Labels Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            days.forEachIndexed { i, day ->
                val isSelected = i == selectedIndex
                Text(
                    text = day.dayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    color = if (isSelected) barColor else textPaintColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectIndex(i) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
