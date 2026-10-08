package com.example.habit

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manifest-registered realtime collectors. Every action here is on the
 * implicit-broadcast exemption list, so they fire even when the app process
 * is asleep — no foreground service, no battery cost, no fragile process.
 *
 * Unlock/screen events do NOT need a receiver: on Android 15+ they arrive
 * through UsageStats (KEYGUARD_HIDDEN / SCREEN_INTERACTIVE), and on older
 * devices the miners infer them from app-open quiet gaps.
 */
class HabitEventReceiver : BroadcastReceiver() {

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val action = intent?.action ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> HabitEngine.logPowerAsync(app, charging = true)
                    Intent.ACTION_POWER_DISCONNECTED -> HabitEngine.logPowerAsync(app, charging = false)
                    Intent.ACTION_BOOT_COMPLETED ->
                        HabitEngine.log(app, HabitEventType.BOOT)
                    Intent.ACTION_MY_PACKAGE_REPLACED ->
                        HabitEngine.log(app, HabitEventType.UPDATE)
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        val tz = if (android.os.Build.VERSION.SDK_INT >= 24) {
                            intent.getStringExtra(Intent.EXTRA_TIMEZONE)
                        } else {
                            null
                        }
                        HabitEngine.log(app, HabitEventType.TIMEZONE, meta = tz)
                    }
                    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ->
                        HabitEngine.logAlarmChangeAsync(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
