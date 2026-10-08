package com.example.habit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only log of behavioral events collected on-device. Everything the
 * habit engine learns comes from this single table — nothing leaves the
 * phone, and rows are pruned automatically to the retention window the user
 * picks in Settings.
 */
@Entity(
    tableName = "habit_events",
    indices = [Index("timestamp"), Index("type")]
)
data class HabitEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val key: String? = null,
    val value: Double? = null,
    val meta: String? = null
)

/**
 * Event vocabulary — small, explicit and stable. New types can be appended;
 * miners must tolerate unknown types gracefully.
 */
object HabitEventType {
    /** App came to the foreground (UsageEvents.ACTIVITY_RESUMED). key = package. */
    const val APP_OPEN = "APP_OPEN"
    /** App left the foreground (ACTIVITY_PAUSED/STOPPED). key = package. */
    const val APP_CLOSE = "APP_CLOSE"
    /** Keyguard dismissed = user unlocked (UsageEvents.KEYGUARD_HIDDEN, Android 15+). */
    const val UNLOCK = "UNLOCK"
    /** Screen became interactive (UsageEvents.SCREEN_INTERACTIVE, Android 15+). */
    const val SCREEN_ON = "SCREEN_ON"
    /** Charger plugged. value = battery % at plug-in. */
    const val CHARGE_START = "CHARGE_START"
    /** Charger unplugged. value = battery % at unplug. */
    const val CHARGE_END = "CHARGE_END"
    /** Battery snapshot (every harvest). value = %, meta = charging|full|discharging|other. */
    const val BATTERY = "BATTERY"
    /** Steps-so-far snapshot. value = steps today (from the step baseline). */
    const val STEPS = "STEPS"
    /** Next system alarm changed. value = trigger time millis. */
    const val ALARM_SET = "ALARM_SET"
    /** Wi-Fi cell seen. key = BSSID, meta = SSID (only when the network changed). */
    const val WIFI = "WIFI"
    /** Bluetooth device connected. key = address, meta = friendly name. */
    const val BT_CONNECT = "BT_CONNECT"
    /** Bluetooth device disconnected. key = address. */
    const val BT_DISCONNECT = "BT_DISCONNECT"
    /** Ringer mode changed. value = 0 silent / 1 vibrate / 2 normal. */
    const val RINGER = "RINGER"
    /** Do-not-disturb filter changed. value = interruption filter id. */
    const val DND = "DND"
    /** Notification posted by another app (NEVER its content). key = package,
     *  meta = "cat=<category>|ongoing=1" fragments. */
    const val NOTIF_POSTED = "NOTIF_POSTED"
    /** Notification removed. key = package. */
    const val NOTIF_REMOVED = "NOTIF_REMOVED"
    /** Passive location fix (private app, stays local). value = latitude, meta = longitude. */
    const val LOCATION = "LOCATION"
    /** Device booted. */
    const val BOOT = "BOOT"
    /** Device shut down. */
    const val SHUTDOWN = "SHUTDOWN"
    /** Timezone changed (travel signal). meta = timezone id. */
    const val TIMEZONE = "TIMEZONE"
    /** App updated in place (MY_PACKAGE_REPLACED). */
    const val UPDATE = "UPDATE"
}
