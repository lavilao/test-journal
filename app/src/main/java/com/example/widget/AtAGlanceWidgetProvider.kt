package com.example.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.BatteryManager
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.LocalReminder
import com.example.telemetry.DeviceLifeHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pixel-style "At a Glance" widget with REAL data only, now PAGED and
 * CONFIGURABLE (v1.7 — Samsung Now Brief-style "choose your cards"):
 *
 *   Calendario  → próximo evento (solo futuro, con cuenta atrás)
 *   Pasos       → pasos de hoy (baseline diario persistido)
 *   Tareas      → próxima tarea pendiente
 *   Sistema     → batería + próxima alarma
 *   Noticias    → titular más reciente del caché RSS
 *
 * The user toggles modules in Ajustes → Widget. Standard app widgets
 * cannot capture horizontal swipes, so pages advance on tap of the
 * content area; the date row opens the app. It refreshes on the system
 * 30-minute tick, on every weather/step/calendar change the app observes,
 * and from the 5-minute background sync.
 */
class AtAGlanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            refreshWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PAGE_NEXT -> {
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                if (widgetId >= 0) {
                    advancePage(context, widgetId)
                    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
                    refreshWidget(context, manager, widgetId)
                }
            }
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_PAGE_NEXT = "com.example.widget.PAGE_NEXT"
        private const val ACTION_REFRESH = "com.example.widget.REFRESH"
        private const val EXTRA_WIDGET_ID = "widget_id"
        private const val PAGE_PREFS = "widget_pages"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** ------------------------------------------------------------------
         * Data gathering (all REAL, all honest — never a made-up number)
         * ------------------------------------------------------------------ */

        private data class WidgetData(
            val dateText: String,
            val weatherText: String,
            val eventTitle: String,
            val eventSub: String,
            val stepsTitle: String,
            val stepsSub: String,
            val taskTitle: String,
            val taskSub: String,
            val systemTitle: String,
            val systemSub: String,
            val newsTitle: String,
            val newsSub: String
        )

        private suspend fun gatherData(context: Context): WidgetData = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            val dateText = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }

            // --- Weather: same disk cache WeatherService persists ---
            val prefs = app.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val weatherText = readCachedWeatherText(prefs)

            // --- Next FUTURE calendar event (past events never show) ---
            val nextEvent = queryNextCalendarEvent(app)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val eventTitle: String
            val eventSub: String
            if (nextEvent != null) {
                eventTitle = "📅 ${timeFormat.format(Date(nextEvent.first))} · ${nextEvent.second}"
                eventSub = "En ${humanizeCountdown(nextEvent.first - System.currentTimeMillis())}" +
                    (nextEvent.third?.let { " · 📍 $it" } ?: "")
            } else {
                eventTitle = "Sin eventos próximos"
                eventSub = "El calendario está libre (o sin permiso)"
            }

            // --- Steps: the persisted daily baseline ---
            val hasStepPermission = ContextCompat.checkSelfPermission(
                app, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            val steps = DeviceLifeHubManager.cachedStepsToday(app)
            val stepsTitle: String
            val stepsSub: String
            if (hasStepPermission) {
                stepsTitle = "👟 ${formatNumber(steps)} pasos hoy"
                val goal = 8000
                val pct = (steps * 100 / goal).coerceAtMost(999)
                stepsSub = "Meta $goal · $pct%"
            } else {
                stepsTitle = "👟 Contador de pasos"
                stepsSub = "Ábrelo y activa el permiso de actividad física"
            }

            // --- Next pending task ---
            val nextTask: LocalReminder? = try {
                AppDatabase.getInstance(app).localReminderDao()
                    .getNextReminderAfter(System.currentTimeMillis())
            } catch (_: Exception) {
                null
            }
            val taskTitle: String
            val taskSub: String
            if (nextTask != null) {
                val tf = SimpleDateFormat("EEE d · HH:mm", Locale.getDefault())
                taskTitle = "✅ ${nextTask.title}"
                taskSub = "${tf.format(Date(nextTask.dueTimestamp))} · " +
                    nextTask.category.replaceFirstChar { it.uppercase() }
            } else {
                taskTitle = "Sin tareas pendientes"
                taskSub = "Todo al día — añade una desde la app"
            }

            // --- Battery + next alarm ---
            val battery = readBatteryPercent(app)
            val nextAlarm = (app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                ?.nextAlarmClock?.triggerTime
            val systemTitle = if (battery != null) "🔋 Batería al $battery%" else "🔋 Batería"
            val systemSub = if (nextAlarm != null && nextAlarm > System.currentTimeMillis()) {
                "Próxima alarma: ${
                    SimpleDateFormat("EEE d · HH:mm", Locale.getDefault()).format(Date(nextAlarm))
                }"
            } else {
                "Sin alarmas programadas"
            }

            // --- v1.7: top headline from the RSS disk cache (honest empty) ---
            val topNews = readTopHeadline(app)
            val newsTitle: String
            val newsSub: String
            if (topNews != null) {
                newsTitle = "📰 ${topNews.first.take(70)}"
                newsSub = topNews.second
            } else {
                newsTitle = "📰 Noticias"
                newsSub = "Abre la app y actualiza tus fuentes"
            }

            WidgetData(
                dateText, weatherText,
                eventTitle, eventSub,
                stepsTitle, stepsSub,
                taskTitle, taskSub,
                systemTitle, systemSub,
                newsTitle, newsSub
            )
        }

        /** ------------------------------------------------------------------
         * Rendering
         * ------------------------------------------------------------------ */

        internal fun refreshWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            widgetScope.launch {
                try {
                    val app = context.applicationContext
                    val data = gatherData(app)
                    val modules = WidgetConfig.enabledModules(app)

                    // Each enabled module contributes one (title, sub) page.
                    val pages: List<Pair<String, String>> = modules.map { id ->
                        when (id) {
                            "steps" -> data.stepsTitle to data.stepsSub
                            "task" -> data.taskTitle to data.taskSub
                            "system" -> data.systemTitle to data.systemSub
                            "news" -> data.newsTitle to data.newsSub
                            else -> data.eventTitle to data.eventSub
                        }
                    }
                    if (pages.isEmpty()) return@launch

                    val page = currentPage(app, appWidgetId).coerceIn(0, pages.size - 1)

                    val views = RemoteViews(app.packageName, R.layout.widget_at_a_glance)
                    views.setTextViewText(R.id.widget_date, data.dateText)
                    views.setTextViewText(
                        R.id.widget_weather,
                        if (WidgetConfig.weatherHeaderEnabled(app)) data.weatherText else ""
                    )

                    views.setTextViewText(R.id.widget_page_title, pages[page].first)
                    views.setTextViewText(R.id.widget_page_sub, pages[page].second)
                    views.setTextViewText(R.id.widget_dots, dotsFor(page, pages.size))

                    // Header (date + weather) → open the app.
                    val openIntent = Intent(app, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPi = PendingIntent.getActivity(
                        app, appWidgetId, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_header, openPi)

                    // Content area → cycle to the next page.
                    val pageIntent = Intent(app, AtAGlanceWidgetProvider::class.java).apply {
                        action = ACTION_PAGE_NEXT
                        putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    }
                    val pagePi = PendingIntent.getBroadcast(
                        app, 1000 + appWidgetId, pageIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_content, pagePi)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (_: Exception) {
                    // Widget refresh is best-effort; never crash the app for it.
                }
            }
        }

        private fun dotsFor(page: Int, count: Int): String =
            (0 until count).joinToString(" ") { if (it == page) "●" else "○" }

        private fun currentPage(context: Context, widgetId: Int): Int {
            return try {
                context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE)
                    .getInt("page_$widgetId", 0)
            } catch (_: Exception) {
                0
            }
        }

        private fun advancePage(context: Context, widgetId: Int) {
            try {
                val count = WidgetConfig.enabledModules(context).size.coerceAtLeast(1)
                val next = (currentPage(context, widgetId) + 1) % count
                context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE)
                    .edit().putInt("page_$widgetId", next).apply()
            } catch (_: Exception) {
            }
        }

        /** "2 h 15 min" style countdown for events in the near future. */
        private fun humanizeCountdown(millis: Long): String {
            if (millis <= 0) return "ahora"
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            return when {
                minutes < 60 -> "$minutes min"
                minutes < 60 * 24 -> {
                    val h = minutes / 60
                    val m = minutes % 60
                    if (m > 0) "$h h $m min" else "$h h"
                }
                else -> "${minutes / (60 * 24)} d"
            }
        }

        /**
         * v1.7: reads the newest cached headline (title + source) straight
         * from the RSS disk cache the app persists — no network, no fabric.
         */
        private fun readTopHeadline(context: Context): Pair<String, String>? = try {
            val f = java.io.File(context.filesDir, "rss_articles_cache.json")
            if (!f.exists()) null
            else {
                val arr = org.json.JSONArray(f.readText())
                if (arr.length() == 0) null
                else {
                    val top = arr.getJSONObject(0)
                    val title = top.optString("title", "")
                    val source = top.optString("sourceTitle", "")
                    if (title.isBlank()) null else title to source
                }
            }
        } catch (_: Exception) {
            null
        }

        private fun formatNumber(n: Int): String =
            String.format(Locale.getDefault(), "%,d", n)

        /**
         * Builds "17°" when a real cached reading exists, "" otherwise.
         * A missing reading leaves the slot blank instead of inventing a
         * temperature — the same honesty policy as the app UI.
         */
        private fun readCachedWeatherText(prefs: SharedPreferences): String {
            val temp = prefs.getInt("cache_temp", Int.MIN_VALUE)
            if (temp == Int.MIN_VALUE) return ""
            val location = prefs.getString("city_name", null)
            return if (location.isNullOrBlank()) "$temp°" else "$temp° · $location"
        }

        /** Next FUTURE event (triple of begin, title, location). */
        private fun queryNextCalendarEvent(context: Context): Triple<Long, String, String?>? {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            return try {
                val now = System.currentTimeMillis()
                val end = now + 36L * 60 * 60 * 1000 // next 36 hours
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, now)
                ContentUris.appendId(builder, end)
                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION
                )
                var cursor: Cursor? = null
                try {
                    cursor = context.contentResolver.query(
                        builder.build(), projection, null, null,
                        "${CalendarContract.Instances.BEGIN} ASC"
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            val begin = cursor.getLong(2)
                            val endMs = cursor.getLong(3)
                            // Only events that have not finished yet.
                            if (endMs > now) {
                                val title = cursor.getString(1) ?: "Evento"
                                val location = cursor.getString(4)
                                return Triple(begin, title, location)
                            }
                        } while (cursor.moveToNext())
                    }
                    null
                } finally {
                    cursor?.close()
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readBatteryPercent(context: Context): Int? {
            return try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    ?: return null
                val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (percent in 0..100) percent else null
            } catch (_: Exception) {
                null
            }
        }

        /** Refresh every instance of the widget (called after data updates). */
        fun refreshAll(context: Context) {
            try {
                val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, AtAGlanceWidgetProvider::class.java)
                )
                for (id in ids) refreshWidget(context, manager, id)
            } catch (_: Exception) {
                // Widget refresh is best-effort; never crash the app for it.
            }
        }
    }
}
