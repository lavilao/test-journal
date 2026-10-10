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
import com.example.widget.AtAGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    init {
        // Restore the persisted day-baseline so a process restart does not
        // reset the day's steps back to 0 (the old in-memory baseline died
        // with the process).
        val cached = cachedStepsToday(context)
        if (cached > 0) {
            _telemetry.value = _telemetry.value.copy(todaySteps = cached)
        }
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
            handleStepReading(totalSteps)
        }
    }

    /**
     * TYPE_STEP_COUNTER returns the steps since the last BOOT, as a
     * monotonically growing total. The day's steps are computed against a
     * baseline captured at the first reading of each calendar day and
     * PERSISTED, so restarts and widget reads all agree.
     */
    private fun handleStepReading(totalSteps: Int) {
        val prefs = context.getSharedPreferences(STEP_PREFS, Context.MODE_PRIVATE)
        val today = todayKey()
        val storedDay = prefs.getString(KEY_DAY, null)

        // Health Connect is the preferred source when the user says so: the
        // hardware sensor stands down for the day instead of stomping the
        // HC number back with its own baseline math.
        if (storedDay == today &&
            prefs.getString(KEY_SOURCE, "sensor") == "health_connect"
        ) {
            prefs.edit().putInt(KEY_TOTAL_LAST, totalSteps).apply()
            return
        }

        var baseline = prefs.getInt(KEY_BASELINE, Int.MIN_VALUE)

        if (baseline == Int.MIN_VALUE || storedDay != today) {
            // First reading of the day (or first ever): anchor the baseline.
            baseline = totalSteps
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_BASELINE, baseline)
                .putInt(KEY_STEPS, 0)
                .putString(KEY_SOURCE, "sensor")
                .apply()
        }

        val stepsToday = (totalSteps - baseline).coerceAtLeast(0)
        prefs.edit()
            .putInt(KEY_STEPS, stepsToday)
            .putInt(KEY_TOTAL_LAST, totalSteps)
            .putString(KEY_SOURCE, "sensor")
            .apply()

        _telemetry.value = _telemetry.value.copy(todaySteps = stepsToday)
        maybeRefreshWidget(stepsToday)
    }

    /** Throttled widget refresh: at most once every 20 s (per step would be wasteful). */
    private fun maybeRefreshWidget(steps: Int) {
        val now = System.currentTimeMillis()
        if (now - lastWidgetStepRefresh < 20_000L && steps % 50 != 0) return
        lastWidgetStepRefresh = now
        try {
            AtAGlanceWidgetProvider.refreshAll(context.applicationContext)
        } catch (_: Exception) {
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
     *  may legitimately return nothing even when permission is granted.
     *  Falls back to an empirical query: if the system returns usage rows,
     *  the permission is granted regardless of what AppOps reports (some
     *  OEM ROMs misreport the op mode). */
    fun checkUsageStatsPermission(): Boolean {
        val appOpsSaysYes = try {
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
        if (appOpsSaysYes) return true
        // Empirical fallback: real usage rows prove the permission is granted.
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            !stats.isNullOrEmpty()
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

    companion object {
        private const val STEP_PREFS = "step_prefs"
        private const val KEY_DAY = "day_key"
        private const val KEY_BASELINE = "baseline"
        private const val KEY_STEPS = "steps_today"
        private const val KEY_TOTAL_LAST = "total_last"
        private const val KEY_SOURCE = "steps_source"

        @Volatile
        private var lastWidgetStepRefresh = 0L

        private fun todayKey(): String =
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        /**
         * The persisted steps for TODAY (0 when the day rolled over or no
         * reading exists yet). Used by the widget so it can never show a
         * stale number from a previous session.
         */
        fun cachedStepsToday(context: Context): Int {
            return try {
                val prefs = context.getSharedPreferences(STEP_PREFS, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_DAY, null) == todayKey()) {
                    prefs.getInt(KEY_STEPS, 0)
                } else {
                    0
                }
            } catch (_: Exception) {
                0
            }
        }
    }
}
