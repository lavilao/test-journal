package com.example.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Fires the morning brief and the weekly report, and re-arms both after
 * boot / app update so the learned-wake-time schedule survives restarts.
 */
class BriefReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBriefAction = action == BriefScheduler.ACTION_MORNING ||
                action == BriefScheduler.ACTION_WEEKLY
        val isMaintenance = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBriefAction && !isMaintenance) return

        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                if (action == BriefScheduler.ACTION_MORNING) {
                    runBlocking { BriefScheduler.buildAndPostMorning(app) }
                } else if (action == BriefScheduler.ACTION_WEEKLY) {
                    runBlocking { BriefScheduler.buildAndPostWeekly(app) }
                }
                runBlocking { BriefScheduler.rearm(app) }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.apply { name = "brief-receiver" }.start()
    }
}
