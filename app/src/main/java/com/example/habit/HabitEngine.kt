package com.example.habit

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.location.SmartPlaces
import com.example.telemetry.DeviceLifeHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The habit engine's collector side: turns Android's internal telemetry into
 * a single append-only [HabitEvent] log.
 *
 * Design principles:
 *  - **No fragile processes.** Realtime unlock/screen events come from
 *    UsageStats (KEYGUARD/SCREEN event types on Android 15+) and manifest
 *    receivers for the exempted broadcasts (power/alarm/timezone/boot), so
 *    collection survives process death; the 5-minute sync tick calls
 *    [collectTick] which throttles to ~15 minutes.
 *  - **Bootstrap.** The first harvest pulls 7 days of usage history, so the
 *    app has something to learn from immediately.
 *  - **Opt-in per source, honest without permissions.** Each collector can
 *    be switched off in Settings; missing permissions simply mean fewer
 *    events, never invented data.
 *  - **Privacy.** Everything stays in the local Room DB, pruned to the
 *    retention window. Notification events store package + time only —
 *    never content.
 */
object HabitEngine {

    private const val PREFS = "habit_prefs"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_COLLECT_USAGE = "collect_usage"
    private const val KEY_COLLECT_NOTIF = "collect_notifications"
    private const val KEY_COLLECT_LOCATION = "collect_location"
    private const val KEY_COLLECT_BLUETOOTH = "collect_bluetooth"
    private const val KEY_RETENTION = "retention_days"
    private const val KEY_LAST_HARVEST = "last_harvest_ts"
    private const val KEY_LAST_EVENT_TS = "last_usage_event_ts"
    private const val KEY_LAST_ALARM = "last_alarm_ts"
    private const val KEY_LAST_CHARGING = "last_charging"
    private const val KEY_LAST_BSSID = "last_bssid"
    private const val KEY_LAST_WIFI_LOG = "last_wifi_log_ts"
    private const val KEY_LAST_BT = "last_bt_set"
    private const val KEY_LAST_RINGER = "last_ringer"
    private const val KEY_LAST_DND = "last_dnd"
    private const val KEY_LAST_PRUNE_DAY = "last_prune_day"
    private const val KEY_LAST_LOCATION = "last_location"
    private const val KEY_BRIEF_DAY = "brief_day"
    private const val KEY_BRIEF_TEXT = "brief_text"
    private const val KEY_BRIEF_AI = "brief_ai"

    private const val DAY_MS = 24 * 60 * 60_000L
    private const val BOOTSTRAP_MS = 7 * DAY_MS
    private const val MIN_HARVEST_INTERVAL_MS = 15 * 60_000L
    private const val DEFAULT_RETENTION_DAYS = 45

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------
    // Toggles (all default ON — the user asked for an app that learns; every
    // source can be switched off individually in Settings)
    // ------------------------------------------------------------------

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER, true)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply()
    }

    fun isUsageCollectorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLECT_USAGE, true)

    fun setUsageCollectorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLECT_USAGE, enabled).apply()
    }

    fun isNotificationCollectorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLECT_NOTIF, true)

    fun setNotificationCollectorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLECT_NOTIF, enabled).apply()
    }

    fun isLocationCollectorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLECT_LOCATION, true)

    fun setLocationCollectorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLECT_LOCATION, enabled).apply()
    }

    fun isBluetoothCollectorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLECT_BLUETOOTH, true)

    fun setBluetoothCollectorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLECT_BLUETOOTH, enabled).apply()
    }

    fun retentionDays(context: Context): Int =
        prefs(context).getInt(KEY_RETENTION, DEFAULT_RETENTION_DAYS)

    fun setRetentionDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_RETENTION, days).apply()
    }

    // ------------------------------------------------------------------
    // Event logging
    // ------------------------------------------------------------------

    /** Appends one event (respects the master switch). */
    suspend fun log(
        context: Context,
        type: String,
        key: String? = null,
        value: Double? = null,
        meta: String? = null
    ) {
        if (!isMasterEnabled(context)) return
        withContext(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(context.applicationContext).habitEventDao().insert(
                    HabitEvent(
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        key = key,
                        value = value,
                        meta = meta
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    /** Fire-and-forget variant for broadcast receivers. */
    fun logAsync(
        context: Context,
        type: String,
        key: String? = null,
        value: Double? = null,
        meta: String? = null
    ) {
        scope.launch { log(context, type, key, value, meta) }
    }

    /** Charger plug/unplug — realtime path from the manifest receiver. */
    fun logPowerAsync(context: Context, charging: Boolean) {
        val app = context.applicationContext
        scope.launch {
            if (!isMasterEnabled(app)) return@launch
            // keep the harvest's dedupe state in sync
            prefs(app).edit().putBoolean(KEY_LAST_CHARGING, charging).apply()
            val level = currentBatteryPercent(app)
            log(app, if (charging) HabitEventType.CHARGE_START else HabitEventType.CHARGE_END, value = level?.toDouble())
        }
    }

    /** Next system alarm changed — realtime path from the manifest receiver. */
    fun logAlarmChangeAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!isMasterEnabled(app)) return@launch
            try {
                val am = app.getSystemService(AlarmManager::class.java) ?: return@launch
                val next = am.nextAlarmClock?.triggerTime ?: 0L
                val last = prefs(app).getLong(KEY_LAST_ALARM, -1L)
                if (next > 0 && next != last) {
                    log(app, HabitEventType.ALARM_SET, value = next.toDouble())
                    prefs(app).edit().putLong(KEY_LAST_ALARM, next).apply()
                }
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------
    // The harvest: one collector pass over the phone's internal state
    // ------------------------------------------------------------------

    /**
     * Collects everything available since the last pass (or a 7-day
     * bootstrap on first run) and appends it to the event log. Returns the
     * number of events inserted.
     */
    suspend fun harvest(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!isMasterEnabled(app)) return@withContext 0
        val p = prefs(app)
        val now = System.currentTimeMillis()
        val events = mutableListOf<HabitEvent>()

        // 1. Usage events: app switches, unlocks, screen-on, boots.
        if (isUsageCollectorEnabled(app) && hasUsageStatsPermission(app)) {
            try {
                val bootstrapped = p.getLong(KEY_LAST_EVENT_TS, 0L)
                val since = if (bootstrapped == 0L) now - BOOTSTRAP_MS else bootstrapped
                val usm = app.getSystemService(UsageStatsManager::class.java)
                if (usm != null) {
                    val usage = usm.queryEvents(since, now)
                    val excluded = excludedPackages(app)
                    val ev = UsageEvents.Event()
                    var maxTs = since
                    while (usage.hasNextEvent()) {
                        usage.getNextEvent(ev)
                        val pkg = ev.packageName ?: continue
                        if (pkg in excluded) continue
                        val ts = ev.timeStamp
                        if (ts > maxTs) maxTs = ts
                        when (ev.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.APP_OPEN, key = pkg))
                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.APP_CLOSE, key = pkg))
                            UsageEvents.Event.KEYGUARD_HIDDEN ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.UNLOCK))
                            UsageEvents.Event.SCREEN_INTERACTIVE ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.SCREEN_ON))
                            UsageEvents.Event.DEVICE_STARTUP ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.BOOT))
                            UsageEvents.Event.DEVICE_SHUTDOWN ->
                                events.add(HabitEvent(timestamp = ts, type = HabitEventType.SHUTDOWN))
                        }
                    }
                    p.edit().putLong(KEY_LAST_EVENT_TS, maxTs + 1L).apply()
                }
            } catch (_: Exception) {
            }
        }

        // 2. Battery snapshot + charging transition (deduped with the receiver).
        try {
            val sticky = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val state = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    else -> "other"
                }
                val lastCharging = p.getBoolean(KEY_LAST_CHARGING, false)
                if (charging != lastCharging) {
                    events.add(
                        HabitEvent(
                            timestamp = now,
                            type = if (charging) HabitEventType.CHARGE_START else HabitEventType.CHARGE_END,
                            value = if (pct >= 0) pct.toDouble() else null
                        )
                    )
                    p.edit().putBoolean(KEY_LAST_CHARGING, charging).apply()
                }
                events.add(
                    HabitEvent(
                        timestamp = now,
                        type = HabitEventType.BATTERY,
                        value = if (pct >= 0) pct.toDouble() else null,
                        meta = state
                    )
                )
            }
        } catch (_: Exception) {
        }

        // 3. Steps today (from the existing daily baseline).
        try {
            val steps = DeviceLifeHubManager.cachedStepsToday(app)
            events.add(HabitEvent(timestamp = now, type = HabitEventType.STEPS, value = steps.toDouble()))
        } catch (_: Exception) {
        }

        // 4. Next alarm (fallback path if the broadcast was missed).
        try {
            val am = app.getSystemService(AlarmManager::class.java)
            val next = am?.nextAlarmClock?.triggerTime ?: 0L
            val last = p.getLong(KEY_LAST_ALARM, -1L)
            if (next > 0 && next != last) {
                events.add(HabitEvent(timestamp = now, type = HabitEventType.ALARM_SET, value = next.toDouble()))
                p.edit().putLong(KEY_LAST_ALARM, next).apply()
            }
        } catch (_: Exception) {
        }

        // 5. Wi-Fi cell + passive location (both need location permission).
        if (isLocationCollectorEnabled(app) && SmartPlaces.hasLocationPermission(app)) {
            try {
                val wifi = currentWifi(app)
                val lastBssid = p.getString(KEY_LAST_BSSID, null)
                val lastWifiLog = p.getLong(KEY_LAST_WIFI_LOG, 0L)
                if (wifi != null && (wifi.first != lastBssid || now - lastWifiLog > 2 * 60 * 60_000L)) {
                    events.add(
                        HabitEvent(
                            timestamp = now,
                            type = HabitEventType.WIFI,
                            key = wifi.first,
                            meta = if (wifi.first != lastBssid) wifi.second else null
                        )
                    )
                }
                if (wifi != null) {
                    p.edit()
                        .putString(KEY_LAST_BSSID, wifi.first)
                        .putLong(KEY_LAST_WIFI_LOG, now)
                        .apply()
                }
            } catch (_: Exception) {
            }
            try {
                val loc = SmartPlaces.lastKnownLocation(app)
                if (loc != null) {
                    val lastLoc = p.getString(KEY_LAST_LOCATION, null)
                    val parts = lastLoc?.split(",")
                    val moved = if (parts != null && parts.size >= 2) {
                        haversineMeters(loc.latitude, loc.longitude, parts[0].toDouble(), parts[1].toDouble()) > 150.0
                    } else {
                        true
                    }
                    val lastTs = parts?.getOrNull(2)?.toLongOrNull() ?: 0L
                    val stale = now - lastTs > 6 * 60 * 60_000L
                    if (moved || stale) {
                        events.add(
                            HabitEvent(
                                timestamp = now,
                                type = HabitEventType.LOCATION,
                                value = loc.latitude,
                                meta = loc.longitude.toString()
                            )
                        )
                        p.edit().putString(KEY_LAST_LOCATION, "${loc.latitude},${loc.longitude},$now").apply()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 6. Bluetooth connections (deduped as transitions).
        if (isBluetoothCollectorEnabled(app)) {
            try {
                val bt = connectedBluetooth(app)
                if (bt != null) {
                    val lastSet = p.getString(KEY_LAST_BT, "") ?: ""
                    val currentSet = bt.map { it.first }.sorted().joinToString(",")
                    if (currentSet != lastSet) {
                        val lastDevs = lastSet.split(",").filter { it.isNotBlank() }.toSet()
                        for ((addr, name) in bt) {
                            if (addr !in lastDevs) {
                                events.add(
                                    HabitEvent(timestamp = now, type = HabitEventType.BT_CONNECT, key = addr, meta = name)
                                )
                            }
                        }
                        for (addr in lastDevs) {
                            if (bt.none { it.first == addr }) {
                                events.add(HabitEvent(timestamp = now, type = HabitEventType.BT_DISCONNECT, key = addr))
                            }
                        }
                        p.edit().putString(KEY_LAST_BT, currentSet).apply()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 7. Ringer / DND state changes (harvest-resolution fallback).
        try {
            val ringer = app.getSystemService(AudioManager::class.java)?.ringerMode ?: -1
            val lastRinger = p.getInt(KEY_LAST_RINGER, -1)
            if (ringer >= 0 && ringer != lastRinger) {
                events.add(HabitEvent(timestamp = now, type = HabitEventType.RINGER, value = ringer.toDouble()))
                p.edit().putInt(KEY_LAST_RINGER, ringer).apply()
            }
        } catch (_: Exception) {
        }
        try {
            val filter = app.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: -1
            val lastDnd = p.getInt(KEY_LAST_DND, -1)
            if (filter >= 0 && filter != lastDnd) {
                events.add(HabitEvent(timestamp = now, type = HabitEventType.DND, value = filter.toDouble()))
                p.edit().putInt(KEY_LAST_DND, filter).apply()
            }
        } catch (_: Exception) {
        }

        // Persist + bookkeeping.
        var inserted = 0
        if (events.isNotEmpty()) {
            try {
                AppDatabase.getInstance(app).habitEventDao().insertAll(events)
                inserted = events.size
            } catch (_: Exception) {
            }
        }
        p.edit().putLong(KEY_LAST_HARVEST, now).apply()

        // Prune once per day to the retention window.
        val today = dayKey(now)
        if (p.getString(KEY_LAST_PRUNE_DAY, null) != today) {
            try {
                AppDatabase.getInstance(app).habitEventDao().prune(now - retentionDays(app) * DAY_MS)
                p.edit().putString(KEY_LAST_PRUNE_DAY, today).apply()
            } catch (_: Exception) {
            }
        }
        inserted
    }

    /**
     * Called from the 5-minute sync tick (and app open). Internal throttle:
     * an actual harvest runs at most every 15 minutes.
     */
    suspend fun collectTick(context: Context) {
        val app = context.applicationContext
        if (!isMasterEnabled(app)) return
        val last = prefs(app).getLong(KEY_LAST_HARVEST, 0L)
        if (System.currentTimeMillis() - last >= MIN_HARVEST_INTERVAL_MS) {
            harvest(app)
        }
    }

    suspend fun deleteAllData(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(context.applicationContext).habitEventDao().clearAll()
            } catch (_: Exception) {
            }
        }
    }

    /** Per-type event counts for the data card in the Routines screen. */
    suspend fun eventCounts(context: Context): List<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context.applicationContext).habitEventDao()
                listOf(
                    HabitEventType.APP_OPEN,
                    HabitEventType.UNLOCK,
                    HabitEventType.SCREEN_ON,
                    HabitEventType.WIFI,
                    HabitEventType.NOTIF_POSTED,
                    HabitEventType.BATTERY,
                    HabitEventType.CHARGE_START,
                    HabitEventType.STEPS,
                    HabitEventType.BT_CONNECT,
                    HabitEventType.LOCATION
                ).mapNotNull { type ->
                    val c = dao.countType(type)
                    if (c > 0) type to c else null
                }.sortedByDescending { it.second }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ------------------------------------------------------------------
    // Daily brief (generated on-device; Needle narrative when available)
    // ------------------------------------------------------------------

    fun storedBrief(context: Context): Triple<String, String, Boolean>? {
        val p = prefs(context)
        val day = p.getString(KEY_BRIEF_DAY, null) ?: return null
        val text = p.getString(KEY_BRIEF_TEXT, null) ?: return null
        return Triple(day, text, p.getBoolean(KEY_BRIEF_AI, false))
    }

    fun saveBrief(context: Context, text: String, fromNeedle: Boolean = false) {
        prefs(context).edit()
            .putString(KEY_BRIEF_DAY, dayKey(System.currentTimeMillis()))
            .putString(KEY_BRIEF_TEXT, text)
            .putBoolean(KEY_BRIEF_AI, fromNeedle)
            .apply()
    }

    // ------------------------------------------------------------------
    // Permission / availability checks for the sensor status card
    // ------------------------------------------------------------------

    fun hasUsageStatsPermission(context: Context): Boolean {
        val app = context.applicationContext
        try {
            val ops = app.getSystemService(android.app.AppOpsManager::class.java)
            val mode = ops?.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                app.packageName
            )
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
        } catch (_: Exception) {
        }
        // Empirical fallback: some OEM ROMs report "denied" via AppOps while
        // actually allowing queries — same trick as DeviceLifeHubManager.
        return try {
            val usm = app.getSystemService(UsageStatsManager::class.java) ?: return false
            val since = System.currentTimeMillis() - 24 * 60 * 60_000L
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, System.currentTimeMillis())
                ?.any { it.totalTimeInForeground > 0 } == true
        } catch (_: Exception) {
            false
        }
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
        return try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            raw.split(":").map { it.trim() }.any {
                it.equals(ComponentName(context, HabitNotificationListener::class.java).flattenToString(), ignoreCase = true) ||
                    it.equals("${context.packageName}/${HabitNotificationListener::class.java.name}", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasLocationPermission(context: Context): Boolean = SmartPlaces.hasLocationPermission(context)

    fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationListenerConnected(context: Context): Boolean =
        hasNotificationListenerAccess(context)

    // ------------------------------------------------------------------
    // Low-level readers
    // ------------------------------------------------------------------

    private fun currentBatteryPercent(context: Context): Int? {
        return try {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return null
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) level * 100 / scale else null
        } catch (_: Exception) {
            null
        }
    }

    /** Current (BSSID, SSID) if connected to Wi-Fi and location is granted. */
    private fun currentWifi(context: Context): Pair<String, String?>? {
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
                if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return null
                val info = caps.transportInfo as? WifiInfo ?: return null
                val bssid = info.bssid ?: return null
                bssid to cleanSsid(info.ssid)
            } else {
                @Suppress("DEPRECATION")
                val wm = context.getSystemService(WifiManager::class.java) ?: return null
                val info = wm.connectionInfo ?: return null
                val bssid = info.bssid ?: return null
                bssid to cleanSsid(info.ssid)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanSsid(ssid: String?): String? {
        val s = ssid?.removeSurrounding("\"") ?: return null
        return if (s.isBlank() || s == "<unknown ssid>") null else s
    }

    /** Currently connected Bluetooth devices (address to name), or null when
     *  the permission is missing / Bluetooth is off. */
    private fun connectedBluetooth(context: Context): Set<Pair<String, String>>? {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission(context)) return null
        return try {
            val bm = context.getSystemService(BluetoothManager::class.java) ?: return null
            val devices = bm.getConnectedDevices(BluetoothProfile.HEADSET)
            devices.mapNotNull { d ->
                try {
                    d.address to (d.name ?: d.address)
                } catch (_: SecurityException) {
                    d.address to d.address
                }
            }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    /** Launchers + SystemUI + ourselves: not interesting as "apps". */
    @Volatile
    private var excludedCache: Set<String>? = null

    fun excludedPackages(context: Context): Set<String> {
        excludedCache?.let { return it }
        val set = mutableSetOf(context.packageName, "com.android.systemui", "com.android.keyguard")
        try {
            val pm = context.packageManager
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(home, 0).forEach {
                it.activityInfo?.packageName?.let { pkg -> set.add(pkg) }
            }
        } catch (_: Exception) {
        }
        excludedCache = set
        return set
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dayKey(ts: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date(ts))
}
