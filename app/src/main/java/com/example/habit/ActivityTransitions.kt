package com.example.habit

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Activity Recognition Transitions API (available from Android 10 — YES, on
 * the user's Android 11): real ENTER/EXIT events for IN_VEHICLE, ON_BICYCLE,
 * WALKING, RUNNING and STILL, delivered even when the app is in the
 * background, with far less battery than continuous location.
 *
 * Three consumers:
 *  1. The habit engine logs ACTIVITY_ENTER/ACTIVITY_EXIT events (commute
 *     mining, "you usually drive at this hour").
 *  2. The spatial engine estimates travel speed by current activity
 *     (walking vs driving → "time to leave" reminders).
 *  3. The weekly report describes movement patterns.
 */
object ActivityTransitionsManager {

    const val ACTION_TRANSITION = "com.example.habit.ACTIVITY_TRANSITION"
    private const val PREFS = "activity_prefs"
    private const val KEY_LAST_MODE = "last_mode"
    private const val KEY_LAST_MODE_AT = "last_mode_at"

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityTransitionsReceiver::class.java)
            .setAction(ACTION_TRANSITION)
        return PendingIntent.getBroadcast(
            context.applicationContext, 3001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Requests transition updates; returns false when unavailable. */
    fun register(context: Context): Boolean {
        if (!hasPermission(context)) return false
        return try {
            val transitions = mutableListOf<ActivityTransition>()
            listOf(
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.STILL
            ).forEach { type ->
                transitions.add(
                    ActivityTransition.Builder()
                        .setActivityType(type)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build()
                )
                transitions.add(
                    ActivityTransition.Builder()
                        .setActivityType(type)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build()
                )
            }
            val client = ActivityRecognition.getClient(context.applicationContext)
            // The gms Task reports failures asynchronously; a failed request
            // is visible simply because no events arrive.
            client.requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitions),
                pendingIntent(context)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unregister(context: Context) {
        try {
            ActivityRecognition.getClient(context.applicationContext)
                .removeActivityTransitionUpdates(pendingIntent(context))
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------
    // Event handling + state
    // ------------------------------------------------------------------

    fun handleIntent(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        try {
            val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents ?: return
            val app = context.applicationContext
            events.forEach { event ->
                val mode = activityName(event.activityType)
                val enter = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                // Persist the latest mode for travel-time estimation.
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_MODE, if (enter) mode else null)
                    .putLong(KEY_LAST_MODE_AT, System.currentTimeMillis())
                    .apply()
                // Feed the habit store (respects the master switch inside log()).
                kotlinx.coroutines.runBlocking {
                    HabitEngine.log(
                        app,
                        type = if (enter) "ACTIVITY_ENTER" else "ACTIVITY_EXIT",
                        key = mode
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Latest known activity mode, or null when stale/unknown. */
    fun currentMode(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_MODE)) return null
        val at = prefs.getLong(KEY_LAST_MODE_AT, 0L)
        // Older than an hour: stale, ignore.
        if (System.currentTimeMillis() - at > 60 * 60_000L) return null
        return prefs.getString(KEY_LAST_MODE, null)
    }

    /** Rough travel speed estimate (m/s) by current activity. */
    fun travelSpeedMps(context: Context): Double = when (currentMode(context)) {
        "IN_VEHICLE" -> 9.0        // ~32 km/h urban average
        "ON_BICYCLE" -> 4.5
        "WALKING" -> 1.35
        "RUNNING" -> 2.8
        else -> 1.35               // conservative: assume walking
    }

    fun activityName(type: Int): String = when (type) {
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.STILL -> "STILL"
        else -> "OTHER"
    }
}

/**
 * Manifest receiver for the transitions. Exported=false: only the system
 * (holding the PendingIntent) can deliver to it.
 */
class ActivityTransitionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ActivityTransitionsManager.handleIntent(context, intent)
    }
}
