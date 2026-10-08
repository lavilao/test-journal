package com.example.sync

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.needle.NeedleTools
import com.example.data.local.AppDatabase
import com.example.data.model.JournalEntry
import com.example.habit.HabitMiners
import com.example.health.HealthConnectManager
import com.example.repository.JournalRepository
import com.example.telemetry.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * MORNING BRIEF + WEEKLY REPORT.
 *
 * The morning notification fires at the user's LEARNED wake time (the sleep
 * miner's typical wake minute, +15 min) — not a fixed alarm — and carries
 * weather, the first event of the day with its SPATIAL travel estimate,
 * sleep and battery. The weekly report (Sunday 19:00) aggregates the week,
 * writes itself into the journal as a NOTE ( Needle narrative when the
 * local model is available, honest template otherwise) and posts a digest
 * notification.
 */
object BriefScheduler {

    const val ACTION_MORNING = "com.example.sync.MORNING_BRIEF"
    const val ACTION_WEEKLY = "com.example.sync.WEEKLY_REPORT"

    private const val PREFS = "brief_prefs"
    private const val KEY_MORNING_ENABLED = "morning_enabled"
    private const val KEY_WEEKLY_ENABLED = "weekly_enabled"
    private const val KEY_NEXT_MORNING_AT = "next_morning_at"
    private const val KEY_NEXT_WEEKLY_AT = "next_weekly_at"
    private const val KEY_LAST_MORNING_DAY = "last_morning_day"
    private const val KEY_LAST_WEEKLY_KEY = "last_weekly_key"

    private const val MORNING_NOTIF_ID = 5001
    private const val WEEKLY_NOTIF_ID = 5002
    private const val CHANNEL_MORNING = "brief_matutino"
    private const val CHANNEL_WEEKLY = "informe_semanal"

    private const val DAY_MS = 24 * 60 * 60_000L

    // ------------------------------------------------------------------
    // Toggles (the UI reads/writes these)
    // ------------------------------------------------------------------

    fun isMorningEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MORNING_ENABLED, true)

    suspend fun setMorningEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply()
        rearm(context)
    }

    fun isWeeklyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEEKLY_ENABLED, true)

    suspend fun setWeeklyEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WEEKLY_ENABLED, enabled).apply()
        rearm(context)
    }

    fun nextMorningAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_MORNING_AT, 0L)

    fun nextWeeklyAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_WEEKLY_AT, 0L)

    // ------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------

    /**
     * (Re)arms both alarms. Cheap when the next triggers are already in the
     * future: the expensive wake-estimate only runs when a NEW day's alarm
     * must be computed.
     */
    suspend fun rearm(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // ---- Morning ----
        if (isMorningEnabled(app)) {
            val next = prefs.getLong(KEY_NEXT_MORNING_AT, 0L)
            val needsReschedule = next < System.currentTimeMillis() + 30 * 60_000L
            if (needsReschedule) {
                val wakeMinute = wakeEstimateMinute(app) ?: (7 * 60 + 30)
                val trigger = nextOccurrenceOf(wakeMinute + 15)
                setAlarm(am, app, ACTION_MORNING, trigger, 501)
                prefs.edit().putLong(KEY_NEXT_MORNING_AT, trigger).apply()
            }
        } else {
            cancelAlarm(am, app, ACTION_MORNING, 501)
            prefs.edit().putLong(KEY_NEXT_MORNING_AT, 0L).apply()
        }

        // ---- Weekly (Sunday 19:00) ----
        if (isWeeklyEnabled(app)) {
            val next = prefs.getLong(KEY_NEXT_WEEKLY_AT, 0L)
            if (next < System.currentTimeMillis() + 30 * 60_000L) {
                val trigger = nextSundayAt(19, 0)
                setAlarm(am, app, ACTION_WEEKLY, trigger, 502)
                prefs.edit().putLong(KEY_NEXT_WEEKLY_AT, trigger).apply()
            }
        } else {
            cancelAlarm(am, app, ACTION_WEEKLY, 502)
            prefs.edit().putLong(KEY_NEXT_WEEKLY_AT, 0L).apply()
        }
    }

    /** The learned wake minute (sleep miner) or null. */
    private suspend fun wakeEstimateMinute(context: Context): Int? = try {
        val digest = HabitMiners.buildDigest(context)
        digest.sleep?.typicalWakeMinuteOfDay
    } catch (_: Exception) {
        null
    }

    private fun nextOccurrenceOf(minuteOfDay: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60).coerceIn(0, 23))
        cal.set(Calendar.MINUTE, (minuteOfDay % 60).coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 5 * 60_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun nextSundayAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        var daysUntilSunday = (Calendar.SUNDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (daysUntilSunday == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
            daysUntilSunday = 7
        }
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        return cal.timeInMillis
    }

    private fun setAlarm(am: AlarmManager?, context: Context, action: String, triggerAt: Long, requestCode: Int) {
        if (am == null) return
        val pi = pendingIntent(context, action, requestCode)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: Exception) {
            try { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) } catch (_: Exception) {}
        }
    }

    private fun cancelAlarm(am: AlarmManager?, context: Context, action: String, requestCode: Int) {
        if (am == null) return
        try { am.cancel(pendingIntent(context, action, requestCode)) } catch (_: Exception) {}
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context.applicationContext, BriefReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context.applicationContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ------------------------------------------------------------------
    // Morning brief
    // ------------------------------------------------------------------

    suspend fun buildAndPostMorning(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayKey = dayKey(System.currentTimeMillis())
        if (prefs.getString(KEY_LAST_MORNING_DAY, null) == todayKey) return@withContext
        prefs.edit().putString(KEY_LAST_MORNING_DAY, todayKey).apply()

        val digest = try { HabitMiners.buildDigest(app) } catch (_: Exception) { null }

        val sb = StringBuilder()
        sb.append(greeting()).append('.')

        // Weather
        try {
            val weather = WeatherService(app).refreshWeather(force = false)
            weather.temperature?.let { temp ->
                sb.append(" ").append(weather.conditionText?.lowercase(Locale.getDefault())?.let { "$it, " } ?: "")
                    .append(temp).append(" grados")
                weather.locationName?.let { sb.append(" en ").append(it) }
                sb.append(".")
            }
        } catch (_: Exception) {}

        // First event + spatial travel estimate
        try {
            val events = com.example.sync.SyncHub.refreshCalendar(app)
            val next = events.firstOrNull()
            if (next != null) {
                sb.append(" Hoy: «").append(next.title).append("» a las ")
                    .append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.startMillis)))
                    .append('.')
                try {
                    val located = com.example.location.SpatialContextEngine.nextLocatedEvent(app)
                    if (located != null && located.event.id == next.id) {
                        sb.append(" Estás a ~").append(located.travelMinutes)
                            .append(" min ").append(located.travelMode)
                            .append(" de ").append(located.place.substringBefore(',').trim())
                            .append(" — sal a tiempo.")
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Sleep (Health Connect first, miner estimate as fallback)
        try {
            val hcSleep = HealthConnectManager.lastNightSleepMinutes(app)
            if (hcSleep != null) {
                sb.append(" Dormiste ").append(hcSleep / 60).append(" h ").append(hcSleep % 60).append(" min.")
            } else {
                digest?.sleep?.lastNight?.let { night ->
                    val minutes = (night.endMillis - night.startMillis) / 60_000L
                    if (minutes in 60..14 * 60) {
                        sb.append(" Dormiste ~").append(minutes / 60).append(" h ").append(minutes % 60).append(" min.")
                    }
                }
            }
        } catch (_: Exception) {}

        // Local AI narrative for the habits digest
        if (digest != null) {
            val narrative = try {
                withTimeoutOrNull(20_000) { HabitMiners.generateBriefNarrative(app, digest) }
            } catch (_: Exception) {
                null
            }
            if (!narrative.isNullOrBlank()) {
                sb.append("\n\n").append(narrative.trim())
            } else {
                try {
                    sb.append("\n\n").append(HabitMiners.templateBrief(digest).trim())
                } catch (_: Exception) {}
            }
        }

        postNotification(
            app, MORNING_NOTIF_ID, CHANNEL_MORNING, "Brief matutino",
            "Tu resumen para empezar el día", sb.toString().trim()
        )
    }

    // ------------------------------------------------------------------
    // Weekly report
    // ------------------------------------------------------------------

    suspend fun buildAndPostWeekly(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val weekKey = weekKey(System.currentTimeMillis())
        if (prefs.getString(KEY_LAST_WEEKLY_KEY, null) == weekKey) return@withContext
        prefs.edit().putString(KEY_LAST_WEEKLY_KEY, weekKey).apply()

        val digest = try { HabitMiners.buildDigest(app) } catch (_: Exception) { null }
        val now = System.currentTimeMillis()

        // Journal stats for the week
        val entries = try {
            AppDatabase.getInstance(app).journalDao().getAllEntriesSnapshot()
                .filter { it.createdAt >= now - 7 * DAY_MS }
        } catch (_: Exception) {
            emptyList()
        }
        val words = entries.sumOf { it.wordCount }
        val topMoods = entries.mapNotNull { it.mood }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
            .joinToString { "${it.key} (${it.value})" }

        // Steps for the week (Health Connect when available)
        val steps7 = try {
            HealthConnectManager.stepsLastDays(app, 7)
        } catch (_: Exception) {
            null
        }
        val stepsAvg = steps7?.takeIf { it.isNotEmpty() }
            ?.let { list -> list.sumOf { it.second } / list.size }
        val stepsLine = when {
            stepsAvg != null && stepsAvg > 0 ->
                "- Pasos promedio/día: $stepsAvg (Health Connect)\n"
            digest?.steps?.avgPerDay != null && digest.steps.avgPerDay > 0f ->
                "- Pasos promedio/día: ${digest.steps.avgPerDay.toInt()}\n"
            else -> ""
        }

        val statsTurn = buildString {
            append("Estadísticas del usuario para el INFORME SEMANAL (últimos 7 días):\n")
            append("- Entradas escritas en el diario: ${entries.size}")
            if (words > 0) append(" ($words palabras)")
            append('\n')
            if (topMoods.isNotBlank()) append("- Ánimos más frecuentes: $topMoods\n")
            digest?.sleep?.let { s ->
                s.medianDurationMinutes?.let { append("- Sueño típico: ${it / 60} h ${it % 60} min\n") }
                s.regularity?.let { append("- Regularidad del sueño: ${(it * 100).toInt()}%\n") }
            }
            digest?.apps?.let { a ->
                a.signature.take(3).forEachIndexed { i, u ->
                    append("- App #${i + 1} más usada: ${u.pkg} (${u.minutes} min/día)\n")
                }
            }
            append(stepsLine)
            digest?.attention?.screenMinutes7dAvg?.let {
                append("- Pantalla promedio/día: ${it.toInt()} min\n")
            }
            digest?.notifications?.totalPerDay?.let {
                append("- Notificaciones/día: ${it.toInt()}\n")
            }
            digest?.battery?.let { b ->
                append("- Batería ahora: ${b.levelNow}%; ")
                b.drainPerHour?.let { append("consumo ${"%.1f".format(it)}%/h\n") }
            }
            append("Redacta el informe con resumen_semanal: útil, cercano, con un consejo accionable.")
        }

        val narrative = try {
            if (com.example.ai.needle.NeedleModelManager.isNeedleDownloaded(app)) {
                com.example.ai.needle.NeedleModelManager.ensureLoaded(app)
                withTimeoutOrNull(30_000) { NeedleTools.weeklyNarrative(statsTurn) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        val range = "${dayOfMonth(now - 6 * DAY_MS)}–${dayOfMonth(now)} " +
                SimpleDateFormat("MMM yyyy", Locale("es")).format(Date(now))
        val title = "Informe semanal · $range"

        val body = buildString {
            if (!narrative.isNullOrBlank()) {
                append(narrative.trim())
                append("\n\n—\n")
            }
            append("Datos de la semana:\n")
            append("- Entradas: ${entries.size}")
            if (words > 0) append(" · $words palabras")
            append('\n')
            if (topMoods.isNotBlank()) append("- Ánimos: $topMoods\n")
            stepsAvg?.takeIf { it > 0 }?.let { append("- Pasos promedio: $it/día (Health Connect)\n") }
                ?: digest?.steps?.takeIf { it.avgPerDay > 0f }?.let {
                    append("- Pasos promedio: ${it.avgPerDay.toInt()}/día\n")
                }
            digest?.attention?.screenMinutes7dAvg?.let { append("- Pantalla: ${it.toInt()} min/día\n") }
            digest?.sleep?.medianDurationMinutes?.let { append("- Sueño: ${it / 60} h ${it % 60} min\n") }
        }.trim()

        // 1) The report becomes a real journal NOTE.
        try {
            JournalRepository(app).saveEntry(
                JournalEntry(
                    title = title,
                    body = body,
                    journalDate = now,
                    mood = null
                )
            )
        } catch (_: Exception) {}

        // 2) Digest notification.
        postNotification(
            app, WEEKLY_NOTIF_ID, CHANNEL_WEEKLY, title,
            "Informe semanal guardado en tu diario",
            (narrative ?: body).take(400)
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun postNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        text: String,
        bigText: String
    ) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(app, channelId)
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(
            id,
            NotificationCompat.Builder(app, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(open)
                .build()
        )
    }

    private fun createChannel(context: Context, id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                id,
                if (id == CHANNEL_MORNING) "Brief matutino" else "Informe semanal",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (id == CHANNEL_MORNING) {
                    "El resumen de la mañana a tu hora aprendida de despertar"
                } else {
                    "El informe de la semana, guardado también como nota del diario"
                }
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 -> "Buenas noches (aún)"
            hour < 13 -> "Buenos días"
            hour < 20 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    private fun dayKey(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun weekKey(millis: Long): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return "%04d-W%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR))
    }

    private fun dayOfMonth(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.DAY_OF_MONTH)} " +
                SimpleDateFormat("MMM", Locale("es")).format(Date(millis))
    }
}
