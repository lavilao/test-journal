package com.example.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.local.AppDatabase
import com.example.data.model.LocalReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Two complementary alarm channels, tuned for the "sync at most 5 minutes
 * apart" requirement without melting the battery:
 *
 *  1. A 5-minute INEXACT repeating tick (ELAPSED_REALTIME, no wake-ups):
 *     refreshes calendar events, weather cache, due-task checks and the
 *     widget while the device is awake.
 *  2. A per-reminder one-shot alarm (RTC_WAKEUP + setAndAllowWhileIdle, no
 *     exact-alarm permission needed) so a task still announces itself on
 *     time even when the phone is dozing.
 */
object SyncScheduler {

    const val ACTION_TICK = "com.example.SYNC_TICK"
    const val ACTION_REMINDER_DUE = "com.example.REMINDER_DUE"

    private const val TICK_PERIOD_MS = 5 * 60_000L
    private const val TICK_REQUEST_CODE = 4001
    private const val REMINDER_REQUEST_CODE = 4002

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts / restarts the 5-minute sync cadence (idempotent). */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = tickPendingIntent(context)
        am.cancel(pi)
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 45_000L,
            TICK_PERIOD_MS,
            pi
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(tickPendingIntent(context))
    }

    /**
     * Arms the one-shot wake-up alarm for the next pending reminder. Called
     * after every reminder change and after each sync pass.
     */
    fun scheduleNextReminderAlarm(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val next: LocalReminder? = try {
                AppDatabase.getInstance(app).localReminderDao()
                    .getNextReminderAfter(System.currentTimeMillis())
            } catch (_: Exception) {
                null
            }
            val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
            val pi = reminderPendingIntent(app)
            am.cancel(pi)
            if (next != null && next.dueTimestamp > System.currentTimeMillis()) {
                try {
                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next.dueTimestamp,
                        pi
                    )
                } catch (_: Exception) {
                    // Very aggressive OEM battery managers can throw here; the
                    // 5-minute tick still covers the notification.
                }
            }
        }
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RealtimeSyncReceiver::class.java).apply {
            action = ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            TICK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RealtimeSyncReceiver::class.java).apply {
            action = ACTION_REMINDER_DUE
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
