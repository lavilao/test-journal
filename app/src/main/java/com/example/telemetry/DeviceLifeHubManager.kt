package com.example.telemetry

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.model.LocalReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarEventInfo(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String?
)

data class LifeHubTelemetry(
    val todaySteps: Int = 0,
    val stepGoal: Int = 8000,
    /**
     * Screen time in minutes for today. Null when the usage-stats permission
     * is not granted or no data is available — we never invent a number.
     */
    val screenTimeMinutes: Int? = null,
    val hasUsageStatsPermission: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val hasActivityRecognitionPermission: Boolean = false,
    val isStepSensorAvailable: Boolean = false,
    val nextCalendarEvent: CalendarEventInfo? = null,
    val activeRemindersCount: Int = 0
)

class DeviceLifeHubManager(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _telemetry = MutableStateFlow(
        LifeHubTelemetry(isStepSensorAvailable = stepSensor != null)
    )
    val telemetry: StateFlow<LifeHubTelemetry> = _telemetry.asStateFlow()

    private var initialSteps = -1

    init {
        registerStepSensor()
        refreshTelemetry()
    }

    fun registerStepSensor() {
        if (stepSensor != null && hasActivityRecognitionPermission()) {
            try {
                sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            } catch (_: Exception) {}
        }
    }

    fun unregisterStepSensor() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Q, the step counter did not need this permission.
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values.firstOrNull()?.toInt() ?: return
            if (initialSteps < 0) {
                initialSteps = totalSteps
            }
            val stepsToday = (totalSteps - initialSteps).coerceAtLeast(0)
            _telemetry.value = _telemetry.value.copy(todaySteps = stepsToday)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun refreshTelemetry() {
        scope.launch(Dispatchers.IO) {
            val hasActivity = hasActivityRecognitionPermission()
            val hasUsage = checkUsageStatsPermission()
            // Real value or null. No invented baselines.
            val screenTime = if (hasUsage) queryTodayScreenTime() else null
            val hasCal = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            val nextEvent = if (hasCal) queryNextCalendarEvent() else null

            _telemetry.value = _telemetry.value.copy(
                screenTimeMinutes = screenTime,
                hasUsageStatsPermission = hasUsage,
                hasCalendarPermission = hasCal,
                hasActivityRecognitionPermission = hasActivity,
                isStepSensorAvailable = stepSensor != null,
                nextCalendarEvent = nextEvent
            )
        }
    }

    /** Uses AppOps (the real source of truth) instead of a usage query that
     *  may legitimately return nothing even when permission is granted. */
    fun checkUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun queryTodayScreenTime(): Int? {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = cal.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (stats.isNullOrEmpty()) return null
            var totalTimeMs = 0L
            stats.forEach {
                totalTimeMs += it.totalTimeInForeground
            }
            if (totalTimeMs <= 0L) return null
            (totalTimeMs / (1000 * 60)).toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun queryNextCalendarEvent(): CalendarEventInfo? {
        return try {
            val startMillis = System.currentTimeMillis()
            val endMillis = startMillis + (24 * 60 * 60 * 1000L) // Next 24 hours

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION
            )

            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val eventId = it.getLong(0)
                    val title = it.getString(1) ?: "Calendar Event"
                    val begin = it.getLong(2)
                    val end = it.getLong(3)
                    val allDay = it.getInt(4) == 1
                    val location = it.getString(5)
                    return CalendarEventInfo(
                        id = eventId,
                        title = title,
                        startMillis = begin,
                        endMillis = end,
                        isAllDay = allDay,
                        location = location
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
