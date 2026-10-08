package com.example.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Health Connect integration — REAL step and sleep data when the platform
 * has it, with honest degradation everywhere else.
 *
 * AVAILABILITY (verified at runtime, this is the source of truth):
 *  - Android 14+: Health Connect is built into the system.
 *  - Android 9-13 (includes the user's Android 11): fully usable after
 *    installing the "Health Connect by Android" app from the Play Store —
 *    this manager offers exactly that install when the provider is missing.
 *  - Without it, the app keeps using its own step sensor baseline, so
 *    nothing else breaks.
 */
object HealthConnectManager {

    private const val PREFS = "health_prefs"
    private const val KEY_PREFER = "prefer_health_connect"

    /** User toggle: prefer Health Connect numbers when available. */
    fun preferEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFER, true)

    fun setPreferEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREFER, enabled).apply()
    }

    // ------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------

    /** One of HealthConnectClient.SDK_* codes, or -1 when the library can't answer. */
    fun sdkStatus(context: Context): Int = try {
        HealthConnectClient.sdkStatus(context.applicationContext)
    } catch (_: Exception) {
        -1
    }

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Needs a Play Store install of the Health Connect provider. */
    fun needsProviderInstall(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private fun client(context: Context): HealthConnectClient? {
        if (!isAvailable(context)) return null
        return try {
            HealthConnectClient.getOrCreate(context.applicationContext)
        } catch (_: Exception) {
            null
        }
    }

    /** Play Store deep link for the Health Connect by Android app. */
    fun installIntent(context: Context): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /** Steps walked today (from local midnight), or null when unavailable. */
    suspend fun todaySteps(context: Context): Long? = withContext(Dispatchers.IO) {
        if (!preferEnabled(context)) return@withContext null
        val hc = client(context) ?: return@withContext null
        if (!hasReadStepsPermission(hc)) return@withContext null
        try {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val end = Instant.now()
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.result[StepsRecord.COUNT_TOTAL]?.let { it }
        } catch (_: Exception) {
            null
        }
    }

    /** Sleep of the last night in minutes (longest session window), or null. */
    suspend fun lastNightSleepMinutes(context: Context): Long? = withContext(Dispatchers.IO) {
        if (!preferEnabled(context)) return@withContext null
        val hc = client(context) ?: return@withContext null
        try {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
            val end = LocalDateTime.now(zone).toInstant()
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val total = response.records.sumOf { record ->
                Duration.between(record.startTime, record.endTime).toMinutes()
            }
            if (total > 0) total else null
        } catch (_: Exception) {
            null
        }
    }

    /** Steps per day for the last [days] days (oldest first), or null. */
    suspend fun stepsLastDays(context: Context, days: Int = 7): List<Pair<LocalDate, Long>>? =
        withContext(Dispatchers.IO) {
            if (!preferEnabled(context)) return@withContext null
            dayTotals(context, days.coerceIn(1, 14))
        }

    /** Real per-day step counts via one bounded query per day. */
    private suspend fun dayTotals(context: Context, days: Int): List<Pair<LocalDate, Long>>? {
        val hc = client(context) ?: return null
        if (!hasReadStepsPermission(hc)) return null
        val zone = ZoneId.systemDefault()
        val out = mutableListOf<Pair<LocalDate, Long>>()
        var day = LocalDate.now(zone)
        repeat(days) {
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            try {
                val response = hc.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )
                out.add(0, day to (response.result[StepsRecord.COUNT_TOTAL] ?: 0L))
            } catch (_: Exception) {
                out.add(0, day to 0L)
            }
            day = day.minusDays(1)
        }
        return out
    }

    // ------------------------------------------------------------------
    // Steps cache bridge (feeds the existing step pipeline)
    // ------------------------------------------------------------------

    /**
     * When Health Connect is available AND preferred AND the permission is
     * granted, overwrites the app's step cache with the real daily count so
     * every surface (widget, At a Glance, habit engine) upgrades at once.
     */
    suspend fun refreshStepsIntoCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val steps = todaySteps(app) ?: return@withContext false
        val prefs = app.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        val today = todayKey()
        if (prefs.getString("day_key", null) != today) {
            prefs.edit().putString("day_key", today).apply()
        }
        prefs.edit()
            .putInt("steps_today", steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .apply()
        true
    }

    private fun todayKey(): String {
        val now = java.time.LocalDateTime.now()
        return "%04d-%02d-%02d".format(now.year, now.monthValue, now.dayOfMonth)
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * The permission set this app declares in the manifest
     * (android.permission.health.READ_STEPS / READ_SLEEP) — used by the
     * request contract in the UI.
     */
    val readPermissions: Set<String> = setOf(
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP"
    )

    suspend fun hasReadStepsPermission(hc: HealthConnectClient): Boolean = try {
        hc.permissionController().getGrantedPermissions().contains(
            "android.permission.health.READ_STEPS"
        )
    } catch (_: Exception) {
        false
    }
}
