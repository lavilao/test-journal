package com.example.sync

import android.content.Context
import com.example.data.CalendarSyncManager
import com.example.data.DeviceCalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Process-wide hub that shares the REAL calendar state between the UI, the
 * widget and the background sync — previously each surface had its own
 * snapshot, which is why events went stale (and past events lingered).
 *
 * Invariant: [calendarEvents] only ever contains events that have NOT ended
 * yet. The filter is re-applied every minute by the ViewModel and at every
 * background sync, so a finished event disappears in real time.
 */
object SyncHub {

    private const val PREFS = "sync_prefs"
    private const val KEY_EVENTS_JSON = "events_json"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LAST_EVENTS_REFRESH = "last_events_refresh"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _calendarEvents = MutableStateFlow<List<DeviceCalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<DeviceCalendarEvent>> = _calendarEvents.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    @Volatile
    private var initialized = false

    /** Loads the persisted snapshot once per process (idempotent). */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            loadSnapshot(context.applicationContext)
        }
    }

    private fun loadSnapshot(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _lastSyncAt.value = prefs.getLong(KEY_LAST_SYNC, 0L)
            val json = prefs.getString(KEY_EVENTS_JSON, null) ?: return
            val array = JSONArray(json)
            val events = mutableListOf<DeviceCalendarEvent>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                events.add(
                    DeviceCalendarEvent(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        startMillis = o.getLong("start"),
                        endMillis = o.getLong("end"),
                        location = o.optString("location").ifBlank { null }
                    )
                )
            }
            _calendarEvents.value = futureOnly(events)
        } catch (_: Exception) {
        }
    }

    /** Only events that have not ended yet — the app and widget never show the past. */
    private fun futureOnly(events: List<DeviceCalendarEvent>): List<DeviceCalendarEvent> {
        val now = System.currentTimeMillis()
        return events.filter { it.endMillis > now }.sortedBy { it.startMillis }
    }

    /**
     * Re-applies the future filter without touching the calendar provider.
     * Cheap enough to run every minute while the UI is open.
     */
    fun refreshFilter() {
        _calendarEvents.value = futureOnly(_calendarEvents.value)
    }

    /** Fresh query against CalendarContract; persists + republishes the result. */
    suspend fun refreshCalendar(context: Context): List<DeviceCalendarEvent> =
        withContext(Dispatchers.IO) {
            ensureInitialized(context)
            val manager = CalendarSyncManager(context.applicationContext)
            val fresh = manager.getUpcomingEvents(limit = 8)
            val filtered = futureOnly(fresh)
            _calendarEvents.value = filtered
            persistSnapshot(context.applicationContext, fresh)
            val now = System.currentTimeMillis()
            _lastSyncAt.value = now
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, now)
                .putLong(KEY_LAST_EVENTS_REFRESH, now)
                .apply()
            filtered
        }

    private fun persistSnapshot(context: Context, events: List<DeviceCalendarEvent>) {
        try {
            val array = JSONArray()
            events.take(12).forEach { e ->
                array.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("title", e.title)
                        put("start", e.startMillis)
                        put("end", e.endMillis)
                        put("location", e.location ?: "")
                    }
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EVENTS_JSON, array.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Full background pass: calendar + due tasks + weather cache + widget,
     * then re-arm the per-reminder alarm. Used by the 5-minute tick and by
     * pull-to-refresh.
     */
    fun syncNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            refreshCalendar(app)
            try {
                ReminderNotifications.checkAndNotifyDue(app)
            } catch (_: Exception) {
            }
            try {
                // Location-based reminders (private app: coordinates never
                // leave the device).
                com.example.location.SmartPlaces.checkProximityAndNotify(app)
            } catch (_: Exception) {
            }
            try {
                // Honors the 20-minute staleness window internally, so this
                // only touches the network when the cached reading expires.
                com.example.telemetry.WeatherService(app).refreshWeather()
            } catch (_: Exception) {
            }
            try {
                // Habit engine: throttled telemetry harvest (~15 min) — the
                // collectors survive process death via UsageStats replays.
                com.example.habit.HabitEngine.collectTick(app)
            } catch (_: Exception) {
            }
            SyncScheduler.scheduleNextReminderAlarm(app)
            try {
                com.example.widget.AtAGlanceWidgetProvider.refreshAll(app)
            } catch (_: Exception) {
            }
        }
    }
}
