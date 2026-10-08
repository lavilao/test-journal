package com.example.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.widget.AtAGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver behind the realtime sync:
 *  - 5-minute inexact tick (calendar / tasks / weather cache / widget),
 *  - one-shot per-reminder wake-up alarms,
 *  - boot + package-replaced (re-arm the cadence after a restart or an
 *    app update — this is what keeps "real time" surviving reboots).
 */
class RealtimeSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SyncScheduler.schedule(app)
                SyncHub.syncNow(app)
            }
            SyncScheduler.ACTION_REMINDER_DUE -> {
                val pending = goAsync()
                receiverScope.launch {
                    try {
                        ReminderNotifications.checkAndNotifyDue(app)
                        SyncScheduler.scheduleNextReminderAlarm(app)
                        AtAGlanceWidgetProvider.refreshAll(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> {
                // 5-minute tick: full pass (calendar is future-filtered).
                SyncHub.syncNow(app)
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
