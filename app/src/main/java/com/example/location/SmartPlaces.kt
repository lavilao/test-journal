package com.example.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A user-defined place for location-based reminders (home, work, the
 * market…). Coordinates stay on the device.
 */
data class SmartPlace(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val note: String,
    val radiusMeters: Int = 200
)

/**
 * Private-app location layer: named places + proximity reminders.
 *
 * This is a "geofence-lite" implementation on purpose: it piggybacks on
 * the 5-minute background sync (and any app open) using the LAST KNOWN
 * location — no persistent GPS tracking, no new heavy services. The app
 * is private and single-user, and every coordinate lives in this device's
 * own storage, which the user explicitly approved.
 */
object SmartPlaces {

    private const val PREFS = "smart_places"
    private const val KEY_PLACES = "places_json"
    private const val KEY_LAST_NOTIFIED = "last_notified_"
    private const val CHANNEL_ID = "mnemosyne_places"

    /** Minutes before re-notifying about the same place. */
    private const val RE_NOTIFY_MINUTES = 45

    // ------------------------------------------------------------------
    // Place storage
    // ------------------------------------------------------------------

    fun places(context: Context): List<SmartPlace> {
        return try {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PLACES, null) ?: return emptyList()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SmartPlace(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    note = o.optString("note"),
                    radiusMeters = o.optInt("radius", 200)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addPlace(context: Context, name: String, lat: Double, lon: Double, note: String): SmartPlace {
        val list = places(context).toMutableList()
        val place = SmartPlace(
            id = System.currentTimeMillis(),
            name = name.trim().ifBlank { "Lugar" },
            latitude = lat,
            longitude = lon,
            note = note.trim()
        )
        list.add(place)
        persist(context, list)
        return place
    }

    fun removePlace(context: Context, id: Long) {
        val list = places(context).filter { it.id != id }
        persist(context, list)
    }

    private fun persist(context: Context, list: List<SmartPlace>) {
        try {
            val array = JSONArray()
            list.forEach { p ->
                array.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("lat", p.latitude)
                        .put("lon", p.longitude)
                        .put("note", p.note)
                        .put("radius", p.radiusMeters)
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PLACES, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Location
    // ------------------------------------------------------------------

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Best last-known location (no new fix requested — battery friendly). */
    fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val candidates = mutableListOf<Location>()
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { candidates.add(it) }
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { candidates.add(it) }
            lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let { candidates.add(it) }
            candidates.filter { it.time > 0 }.maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Requests ONE fresh location fix (used when saving the current place);
     * falls back to the last known one after a short timeout.
     */
    suspend fun freshLocation(context: Context, timeoutMs: Long = 8_000): Location? =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission(context)) return@withContext null
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null
            try {
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> return@withContext lastKnownLocation(context)
                }
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine<Location> { cont ->
                        val listener = LocationListener { location ->
                            if (cont.isActive) {
                                cont.resumeWith(Result.success(location))
                            }
                        }
                        cont.invokeOnCancellation {
                            try {
                                lm.removeUpdates(listener)
                            } catch (_: Exception) {
                            }
                        }
                        try {
                            lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWith(Result.failure(e))
                        }
                    }
                } ?: lastKnownLocation(context)
            } catch (_: Exception) {
                lastKnownLocation(context)
            }
        }

    // ------------------------------------------------------------------
    // Distance + proximity reminders
    // ------------------------------------------------------------------

    fun distanceMeters(a: Location, b: SmartPlace): Float {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return (earthRadius * c).toFloat()
    }

    /** Nearest saved place within its radius, or null. */
    fun nearestWithinRadius(context: Context): Pair<SmartPlace, Float>? {
        val loc = lastKnownLocation(context) ?: return null
        var best: Pair<SmartPlace, Float>? = null
        places(context).forEach { place ->
            val d = distanceMeters(loc, place)
            if (d <= place.radiusMeters && (best == null || d < best!!.second)) {
                best = place to d
            }
        }
        return best
    }

    /**
     * Proximity pass, called from the 5-minute sync: notifies when the user
     * is inside one of their places, at most once per place per 45 minutes.
     */
    fun checkProximityAndNotify(context: Context) {
        val app = context.applicationContext
        if (!hasLocationPermission(app)) return
        val hit = nearestWithinRadius(app) ?: return
        val (place, distance) = hit

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_NOTIFIED + place.id, 0L)
        val now = System.currentTimeMillis()
        if (now - last < RE_NOTIFY_MINUTES * 60_000L) return
        prefs.edit().putLong(KEY_LAST_NOTIFIED + place.id, now).apply()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Recordatorios por ubicación",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val openIntent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                app, 3000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (place.note.isNotBlank()) {
                "Estás a ${distance.toInt()} m de ${place.name} — ${place.note}"
            } else {
                "Estás a ${distance.toInt()} m de ${place.name}"
            }
            nm.notify(
                3000 + (place.id % 10000).toInt(),
                NotificationCompat.Builder(app, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Cerca de ${place.name}")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
            )
        } catch (_: Exception) {
        }
    }
}
