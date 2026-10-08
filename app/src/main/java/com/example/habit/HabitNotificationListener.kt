package com.example.habit

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Opt-in system notification telemetry for the habit engine.
 *
 * PRIVACY: logs ONLY package name + timestamp + category/ongoing flags.
 * Notification titles and text are NEVER read or stored.
 *
 * Requires the special "Notification access" grant (Settings link is shown
 * in the Routines screen). On Android 13+ sideloaded apps may hit
 * "Restricted settings" — the adb command below unlocks it:
 *   adb shell cmd notification allow_listener <applicationId>/com.example.habit.HabitNotificationListener
 * (the Routines screen builds the exact command at runtime)
 */
class HabitNotificationListener : NotificationListenerService() {

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun adbUnlockCommand(context: android.content.Context): String =
            "adb shell cmd notification allow_listener ${context.packageName}/${HabitNotificationListener::class.java.name}"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!HabitEngine.isMasterEnabled(this)) return
        if (!HabitEngine.isNotificationCollectorEnabled(this)) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val n = sbn.notification
        val isGroupSummary = n != null && (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return // count real notifications, not group headers

        val ongoing = n?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0
        val meta = buildString {
            n?.category?.takeIf { it.isNotBlank() }?.let { append("cat=").append(it).append("|") }
            if (ongoing) append("ongoing=1")
        }.ifBlank { null }

        scope.launch {
            HabitEngine.log(
                this@HabitNotificationListener,
                HabitEventType.NOTIF_POSTED,
                key = pkg,
                meta = meta
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!HabitEngine.isMasterEnabled(this)) return
        if (!HabitEngine.isNotificationCollectorEnabled(this)) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return
        scope.launch {
            HabitEngine.log(
                this@HabitNotificationListener,
                HabitEventType.NOTIF_REMOVED,
                key = pkg
            )
        }
    }
}
