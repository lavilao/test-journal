package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GoogleBlue
import com.example.ui.components.GoogleGreen
import com.example.ui.components.GoogleRed
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GoogleNotificationsScreen(
    viewModel: JournalViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reminders by viewModel.allReminders.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val calendarEvents by viewModel.upcomingCalendarEvents.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // "Sincronizar" used to silently return nothing because the calendar
    // permission was never requested from this screen. Now it asks first.
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.refreshCalendarEvents()
        }
        viewModel.refreshTelemetry()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("google_notifications_screen")
    ) {
        // Top Header
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
                    text = "Tareas & Recordatorios",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva tarea", fontSize = 12.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Android System Calendar Events Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Eventos del Calendario",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(onClick = { viewModel.calendarSyncManager.openCalendarApp() }) {
                        Text("Abrir Calendario", fontSize = 12.sp, color = GoogleBlue)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp), tint = GoogleBlue)
                    }
                }
            }

            if (calendarEvents.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (telemetry.hasCalendarPermission) {
                                Text(
                                    text = "Sin eventos próximos en el calendario de este dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { viewModel.refreshCalendarEvents() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Sincronizar eventos ahora", fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    text = "Conecta tu calendario para ver aquí tus próximos eventos reales.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        calendarPermissionLauncher.launch(
                                            viewModel.calendarSyncManager.requiredCalendarPermissions()
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Permitir acceso al calendario", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(calendarEvents, key = { "cal_${it.id}" }) { event ->
                    val timeFormat = remember { SimpleDateFormat("EEEE d, HH:mm", Locale.getDefault()) }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.calendarSyncManager.openCalendarApp() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GoogleBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = GoogleBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = timeFormat.format(Date(event.startMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!event.location.isNullOrBlank()) {
                                    Text(
                                        text = "📍 ${event.location}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Local Tasks & Reminders Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis tareas (${reminders.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (reminders.isEmpty()) {
                item {
                    Text(
                        text = "No tienes tareas pendientes. Pulsa 'Nueva tarea' para agregar una con fecha y hora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = reminder.isCompleted,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleReminder(reminder.id, isChecked)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = GoogleBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reminder.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (reminder.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = reminder.category.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (reminder.dueTimestamp > 0) {
                                        val timeFormat = SimpleDateFormat(" • d MMM, HH:mm", Locale.getDefault())
                                        Text(
                                            text = timeFormat.format(Date(reminder.dueTimestamp)),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = GoogleBlue
                                        )
                                    }
                                }
                            }

                            // Delete Task Button
                            IconButton(
                                onClick = {
                                    viewModel.deleteReminder(reminder.id)
                                    Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar tarea",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var titleText by remember { mutableStateOf("") }
        var categoryText by remember { mutableStateOf("Personal") }
        var syncWithCalendar by remember { mutableStateOf(true) }

        val calendar = remember { Calendar.getInstance() }
        var selectedCalendar by remember { mutableStateOf(calendar) }
        var dateFormatted by remember {
            mutableStateOf(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time))
        }
        var timeFormatted by remember {
            mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time))
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nueva Tarea con Hora") },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Título de la tarea") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Categoría (Personal, Trabajo, Salud)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Fecha y hora de recordatorio:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val dp = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedCalendar.set(Calendar.YEAR, year)
                                        selectedCalendar.set(Calendar.MONTH, month)
                                        selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        dateFormatted = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(selectedCalendar.time)
                                    },
                                    selectedCalendar.get(Calendar.YEAR),
                                    selectedCalendar.get(Calendar.MONTH),
                                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
                                )
                                dp.show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dateFormatted, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val tp = TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        selectedCalendar.set(Calendar.MINUTE, minute)
                                        timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedCalendar.time)
                                    },
                                    selectedCalendar.get(Calendar.HOUR_OF_DAY),
                                    selectedCalendar.get(Calendar.MINUTE),
                                    true
                                )
                                tp.show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(timeFormatted, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { syncWithCalendar = !syncWithCalendar },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Añadir al Calendario de Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = syncWithCalendar,
                            onCheckedChange = { syncWithCalendar = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleText.isNotBlank()) {
                            val scheduledTime = selectedCalendar.timeInMillis
                            viewModel.addReminder(titleText, categoryText, scheduledTime)

                            if (syncWithCalendar) {
                                scope.launch {
                                    val uri = viewModel.calendarSyncManager.addEventToCalendar(
                                        title = titleText,
                                        description = "Categoría: $categoryText",
                                        startMillis = scheduledTime,
                                        durationMinutes = 60
                                    )
                                    if (uri != null) {
                                        viewModel.refreshCalendarEvents()
                                        Toast.makeText(context, "Sincronizado con el Calendario de Android", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Text("Guardar Tarea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
