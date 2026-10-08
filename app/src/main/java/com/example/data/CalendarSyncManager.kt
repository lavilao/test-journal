package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String? = null
)

class CalendarSyncManager(private val context: Context) {

    /**
     * We only READ the calendar, so READ is the permission that matters.
     * (The old check also required WRITE, which is requested separately —
     * after granting only READ the calendar stayed "broken" forever.)
     */
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    /** Both calendar permissions, to request together in one dialog. */
    fun requiredCalendarPermissions(): Array<String> {
        return arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }

    suspend fun getUpcomingEvents(limit: Int = 5): List<DeviceCalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext emptyList()

        val events = mutableListOf<DeviceCalendarEvent>()
        val now = System.currentTimeMillis()
        val endOfDay = now + 86400000L * 3 // Next 3 days

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, endOfDay)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)

                while (cursor.moveToNext() && events.size < limit) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "Evento" else "Evento"
                    val start = if (startIdx >= 0) cursor.getLong(startIdx) else now
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else now + 3600000L
                    val loc = if (locIdx >= 0) cursor.getString(locIdx) else null

                    // FUTURE ONLY: an instance that overlaps the window but
                    // already finished (e.g. today's all-day event late at
                    // night) must never surface as "upcoming".
                    if (end <= now) continue

                    events.add(DeviceCalendarEvent(id, title, start, end, loc))
                }
            }
        } catch (_: Exception) {}

        events
    }

    suspend fun addEventToCalendar(
        title: String,
        description: String,
        startMillis: Long,
        durationMinutes: Int = 60
    ): Uri? = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext null

        try {
            val primaryCalendarId = getPrimaryCalendarId() ?: 1L
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, startMillis + durationMinutes * 60 * 1000L)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, primaryCalendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            // Add 10-minute reminder alarm
            if (uri != null) {
                val eventId = uri.lastPathSegment?.toLongOrNull()
                if (eventId != null) {
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.MINUTES, 10)
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                var fallbackId: Long? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    if (fallbackId == null) fallbackId = id
                    if (primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1) {
                        return id
                    }
                }
                return fallbackId
            }
        } catch (_: Exception) {}
        return null
    }

    fun openCalendarApp() {
        try {
            val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = builder.build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }
}
