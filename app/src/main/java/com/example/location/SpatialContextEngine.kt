package com.example.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.CalendarSyncManager
import com.example.data.DeviceCalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SPATIAL CONTEXT ENGINE — the calendar×location fusion.
 *
 * The calendar already knows WHERE each event happens (EVENT_LOCATION);
 * the app already knows where the user is. Combining the two gives
 * reminders a spatial dimension on top of the temporal one:
 *
 *  - "Time to leave": for the next located event, estimate the trip time
 *    from the current position (speed by detected activity — walking vs
 *    driving via the Transitions API) and notify exactly when the user
 *    must walk out the door: event.start − travel − buffer.
 *  - "You arrived": entering the event's place during the event window.
 *
 * Geocoding uses the platform Geocoder (on-device service, best-effort —
 * some ROMs route it through the network). Results are cached per location
 * string for 30 days. Unresolvable places are skipped honestly.
 */
object SpatialContextEngine {

    private const val PREFS = "spatial_prefs"
    private const val KEY_GEOCODE_CACHE = "geocode_cache_json"
    private const val KEY_ENABLED = "spatial_reminders_enabled"
    private const val KEY_LAST_LEAVE_NOTIFIED = "leave_notified_"
    private const val KEY_LAST_ARRIVE_NOTIFIED = "arrive_notified_"
    private const val NOTIF_CHANNEL = "spatial_reminders"
    private const val NOTIF_ID_BASE = 7100

    private const val CACHE_TTL_MS = 30L * 24 * 60 * 60_000L
    private const val EARTH_RADIUS_M = 6371000.0

    // ------------------------------------------------------------------
    // Toggle
    // ------------------------------------------------------------------

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** The next event whose place resolves, with distance + travel minutes. */
    suspend fun nextLocatedEvent(context: Context): LocatedEvent? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!CalendarSyncManager(app).hasCalendarPermission()) return@withContext null
        val events = CalendarSyncManager(app).getUpcomingEvents(limit = 10)
        val now = System.currentTimeMillis()
        for (event in events) {
            val place = event.location?.takeIf { it.isNotBlank() } ?: continue
            val coords = resolvePlace(app, place) ?: continue
            val loc = SmartPlaces.lastKnownLocation(app) ?: break
            val distance = haversine(loc.latitude, loc.longitude, coords.first, coords.second)
            val speed = com.example.habit.ActivityTransitionsManager.travelSpeedMps(app)
            val travelMinutes = ((distance / speed) / 60.0).toInt().coerceIn(1, 240)
            return@withContext LocatedEvent(
                event = event,
                place = place,
                latitude = coords.first,
                longitude = coords.second,
                distanceMeters = distance,
                travelMinutes = travelMinutes,
                travelMode = travelModeLabel(app)
            )
        }
        null
    }

    /**
     * The 5-minute sync step: evaluates the next located events and posts
     * "time to leave" / arrival notifications, deduped per event instance.
     */
    suspend fun evaluate(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!isEnabled(app)) return@withContext
        if (!SmartPlaces.hasLocationPermission(app)) return@withContext
        if (!CalendarSyncManager(app).hasCalendarPermission()) return@withContext

        val events = CalendarSyncManager(app).getUpcomingEvents(limit = 8)
            .filter { it.location?.isNotBlank() == true }
            .filter { it.startMillis - System.currentTimeMillis() < 6 * 3600_000L }
        if (events.isEmpty()) return@withContext

        val loc = try {
            SmartPlaces.freshLocation(app, timeoutMs = 6_000)
        } catch (_: Exception) {
            null
        } ?: return@withContext

        val now = System.currentTimeMillis()
        val speed = com.example.habit.ActivityTransitionsManager.travelSpeedMps(app)

        for (event in events) {
            val place = event.location!!.trim()
            val coords = resolvePlace(app, place) ?: continue
            val distance = haversine(loc.latitude, loc.longitude, coords.first, coords.second)
            val travelMinutes = ((distance / speed) / 60.0).toInt().coerceIn(1, 240)
            val leaveAt = event.startMillis - travelMinutes * 60_000L - 5 * 60_000L // 5 min buffer

            // --- Arrival: inside 200 m while the event is live (±30 min). ---
            if (distance < 200 && now > event.startMillis - 30 * 60_000L &&
                now < event.endMillis + 30 * 60_000L
            ) {
                notifyOnce(
                    app, event, "arrive",
                    "Llegaste a ${short(place)}",
                    "«${event.title}» — ¿apunto algo en el diario?"
                )
                continue
            }

            // --- Time to leave: from 3 min before the computed departure
            //     until the event starts. ---
            if (now in (leaveAt - 3 * 60_000L)..(event.startMillis - 60_000L)) {
                notifyOnce(
                    app, event, "leave",
                    "Es hora de salir — ${short(place)}",
                    "«${event.title}» a las ${timeOf(event.startMillis)}: " +
                            "estás a ${humanDistance(distance)} (~$travelMinutes min " +
                            "${travelModeLabel(app)})"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Geocoding with a persistent cache
    // ------------------------------------------------------------------

    private data class CachedCoord(val lat: Double, val lon: Double, val at: Long)

    private fun geocodeCache(context: Context): MutableMap<String, CachedCoord> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GEOCODE_CACHE, null) ?: return mutableMapOf()
        return try {
            val root = JSONObject(raw)
            val out = mutableMapOf<String, CachedCoord>()
            root.keys().forEach { key ->
                val o = root.optJSONObject(key) ?: return@forEach
                out[key] = CachedCoord(o.getDouble("lat"), o.getDouble("lon"), o.getLong("at"))
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    /** Place string → coordinates, via SmartPlaces match or the Geocoder. */
    private suspend fun resolvePlace(context: Context, place: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val key = place.trim().lowercase(Locale.getDefault())

            // 1) A saved SmartPlace with a similar name wins (user-curated).
            SmartPlaces.places(app).firstOrNull { saved ->
                saved.name.lowercase(Locale.getDefault()).let {
                    it.contains(key) || key.contains(it)
                }
            }?.let { return@withContext it.latitude to it.longitude }

            // 2) Cached geocode result.
            val cache = geocodeCache(app)
            cache[key]?.let { cached ->
                if (System.currentTimeMillis() - cached.at < CACHE_TTL_MS) {
                    return@withContext cached.lat to cached.lon
                }
            }

            // 3) Platform Geocoder (best effort, may need network).
            val geocoded = try {
                if (Geocoder.isPresent()) {
                    @Suppress("DEPRECATION")
                    Geocoder(app).getFromLocationName(place, 1)
                        ?.firstOrNull()
                        ?.let { it.latitude to it.longitude }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

            if (geocoded != null) {
                cache[key] = CachedCoord(geocoded.first, geocoded.second, System.currentTimeMillis())
                persistCache(app, cache)
            }
            geocoded
        }

    private fun persistCache(context: Context, cache: Map<String, CachedCoord>) {
        try {
            val root = JSONObject()
            cache.entries.take(200).forEach { (k, v) ->
                root.put(k, JSONObject().put("lat", v.lat).put("lon", v.lon).put("at", v.at))
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_GEOCODE_CACHE, root.toString()).apply()
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private fun notifyOnce(
        context: Context,
        event: DeviceCalendarEvent,
        kind: String,
        title: String,
        text: String
    ) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dedupeKey = "${kind}_${event.id}_${event.startMillis}"
        if (prefs.getBoolean(dedupeKey, false)) return
        prefs.edit().putBoolean(dedupeKey, true).apply()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(app)
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(
            NOTIF_ID_BASE + (dedupeKey.hashCode() and 0xFFFF),
            NotificationCompat.Builder(app, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .build()
        )
    }

    private fun createChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL,
                "Recordatorios espaciales",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "«Es hora de salir» y llegadas, calculados con tu ubicación y los lugares del calendario"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun travelModeLabel(context: Context): String =
        when (com.example.habit.ActivityTransitionsManager.currentMode(context)) {
            "IN_VEHICLE" -> "en coche"
            "ON_BICYCLE" -> "en bici"
            "RUNNING" -> "corriendo"
            else -> "a pie"
        }

    private fun timeOf(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    private fun humanDistance(meters: Double): String = when {
        meters >= 1000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
        else -> "${meters.toInt()} m"
    }

    private fun short(place: String): String =
        place.substringBefore(',').trim().take(40).ifBlank { place.take(40) }

    /** Rich result for the UI preview card. */
    data class LocatedEvent(
        val event: DeviceCalendarEvent,
        val place: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMeters: Double,
        val travelMinutes: Int,
        val travelMode: String
    )
}
