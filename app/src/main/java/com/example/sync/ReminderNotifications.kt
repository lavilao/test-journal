package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.LocalReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * REAL task notifications.
 *
 * The task list used to live only inside the app: a reminder could become
 * due and nothing would ever fire. This object is called from the 5-minute
 * sync tick and from the per-reminder alarm, and posts one notification per
 * due (not completed) task — with deduplication so a task is announced once.
 */
object ReminderNotifications {

    private const val CHANNEL_ID = "mnemosyne_tasks"
    private const val PREFS = "reminder_notified"
    private const val KEY_NOTIFIED_IDS = "notified_ids"

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tareas y recordatorios",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de tareas que llegan a su hora"
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Notifies every active reminder whose time has come (once), and prunes
     * the dedupe set of ids that are gone/completed.
     */
    suspend fun checkAndNotifyDue(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val app = context.applicationContext
        val now = System.currentTimeMillis()

        val due = withContext(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(app).localReminderDao().getDueReminders(now)
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (due.isEmpty()) {
            clearNotified(app)
            return
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alreadyNotified = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet())?.toMutableSet()
            ?: mutableSetOf()

        due.forEach { reminder ->
            if (reminder.id.toString() !in alreadyNotified) {
                notifyOne(app, reminder)
                alreadyNotified.add(reminder.id.toString())
            }
        }

        // Keep only ids still in the due list.
        val validIds = due.map { it.id.toString() }.toSet()
        prefs.edit()
            .putStringSet(KEY_NOTIFIED_IDS, alreadyNotified.filter { it in validIds }.toSet())
            .apply()
    }

    private fun clearNotified(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_NOTIFIED_IDS).apply()
    }

    private fun notifyOne(context: Context, reminder: LocalReminder) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_NOTIFICATIONS)
        }
        val pending = PendingIntent.getActivity(
            context,
            (reminder.id % 100000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tarea: ${reminder.title}")
            .setContentText(
                "Eran las ${timeFormat.format(Date(reminder.dueTimestamp))} · " +
                    reminder.category.replaceFirstChar { it.uppercase() }
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            nm.notify(2000 + (reminder.id % 100000).toInt(), notification)
        } catch (_: Exception) {
        }
    }
}
