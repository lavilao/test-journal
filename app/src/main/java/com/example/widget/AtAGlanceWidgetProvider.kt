package com.example.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pixel-style "At a Glance" widget with REAL data only:
 *  - Date (always true).
 *  - Weather, read from the same disk cache WeatherService persists after
 *    every successful Open-Meteo fetch (never a made-up temperature).
 *  - Next calendar event, read straight from the CalendarProvider when the
 *    READ_CALENDAR permission is granted.
 *  - Battery level from BatteryManager as the honest fallback line.
 *
 * It refreshes on the system's 30-minute widget tick AND right after every
 * successful weather refresh (see [WeatherService] -> [refreshAll]).
 */
class AtAGlanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_at_a_glance)

            // Format current date e.g. "Sábado, 3 de octubre"
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            val formattedDate = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_date, formattedDate)

            // --- REAL weather from the WeatherService disk cache ---
            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val weatherText = readCachedWeatherText(prefs)
            views.setTextViewText(R.id.widget_weather, weatherText)

            // --- REAL contextual line: next calendar event, else battery ---
            views.setTextViewText(R.id.widget_event_text, buildContextLine(context))

            // Click Intent to open App
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Builds "17°C" when a real cached reading exists, "" otherwise.
         * A missing reading leaves the slot blank instead of inventing a
         * temperature — the same honesty policy as the app UI.
         */
        private fun readCachedWeatherText(prefs: SharedPreferences): String {
            val temp = prefs.getInt("cache_temp", Int.MIN_VALUE)
            if (temp == Int.MIN_VALUE) return ""
            val location = prefs.getString("city_name", null)
            return if (location.isNullOrBlank()) "$temp°" else "$temp° · $location"
        }

        /**
         * Priority: next calendar event (real, from CalendarProvider) →
         * battery level (real, from BatteryManager) → neutral date line.
         */
        private fun buildContextLine(context: Context): String {
            val nextEvent = queryNextCalendarEvent(context)
            if (nextEvent != null) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                return "📅 ${timeFormat.format(Date(nextEvent.first))} · ${nextEvent.second}"
            }
            val battery = readBatteryPercent(context)
            if (battery != null) {
                return "🔋 Batería al $battery%"
            }
            return ""
        }

        private fun queryNextCalendarEvent(context: Context): Pair<Long, String>? {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            return try {
                val start = System.currentTimeMillis()
                val end = start + 36L * 60 * 60 * 1000 // next 36 hours
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, start)
                ContentUris.appendId(builder, end)
                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN
                )
                context.contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val title = cursor.getString(1) ?: "Evento"
                        val begin = cursor.getLong(2)
                        begin to title
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readBatteryPercent(context: Context): Int? {
            return try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
                val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (percent in 0..100) percent else null
            } catch (_: Exception) {
                null
            }
        }

        /** Refresh every instance of the widget (called after weather updates). */
        fun refreshAll(context: Context) {
            try {
                val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, AtAGlanceWidgetProvider::class.java)
                )
                for (id in ids) updateAppWidget(context, manager, id)
            } catch (_: Exception) {
                // Widget refresh is best-effort; never crash the app for it.
            }
        }
    }
}
