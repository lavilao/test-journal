package com.example.data

import android.Manifest
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.contacts.DeviceContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceFileInfo(
    val id: Long,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val mimeType: String,
    val uri: Uri
)

data class RecentAppUsageInfo(
    val packageName: String,
    val appName: String,
    val lastTimeUsedMs: Long,
    val totalTimeInForegroundMs: Long
)

class DeviceSearchManager(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun hasUsageStatsPermission(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
        return !stats.isNullOrEmpty()
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    /**
     * Search real contacts on device by name or phone query.
     */
    suspend fun searchContacts(query: String): List<DeviceContactInfo> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission() || query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<DeviceContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val seenIds = mutableSetOf<String>()
                while (cursor.moveToNext() && results.size < 20) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else ""
                    if (!seenIds.add(id)) continue

                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Contacto" else "Contacto"
                    val phone = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                    results.add(DeviceContactInfo(id = id, displayName = name, phoneNumber = phone, photoUri = photo))
                }
            }
        } catch (_: Exception) {}

        results
    }

    /**
     * Search real device files (MediaStore) by filename.
     */
    suspend fun searchFiles(query: String): List<DeviceFileInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<DeviceFileInfo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit = 25)
            }
        } catch (_: Exception) {}

        results
    }

    /**
     * Get recent files modified or downloaded on device in the last days.
     */
    suspend fun getRecentDeviceFiles(limit: Int = 15): List<DeviceFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DeviceFileInfo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit)
            }
        } catch (_: Exception) {}

        results
    }

    private fun extractFilesFromCursor(cursor: Cursor, results: MutableList<DeviceFileInfo>, limit: Int) {
        val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        while (cursor.moveToNext() && results.size < limit) {
            val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Archivo" else "Archivo"
            val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
            val date = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else System.currentTimeMillis()
            val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "*/*" else "*/*"
            val uri = Uri.withAppendedPath(collection, id.toString())

            results.add(
                DeviceFileInfo(
                    id = id,
                    displayName = name,
                    path = uri.toString(),
                    sizeBytes = size,
                    dateModifiedMs = date,
                    mimeType = mime,
                    uri = uri
                )
            )
        }
    }

    /**
     * Get actual recently opened apps via UsageStatsManager.
     */
    suspend fun getRecentlyUsedApps(limit: Int = 10): List<RecentAppUsageInfo> = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val dayAgo = now - 1000L * 60 * 60 * 24 // Past 24 hours

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayAgo, now)
        if (stats.isNullOrEmpty()) return@withContext emptyList()

        val sorted = stats
            .filter { it.lastTimeUsed > dayAgo && it.packageName != context.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .take(limit)

        sorted.map { usage ->
            val appName = try {
                val appInfo = pm.getApplicationInfo(usage.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                usage.packageName.substringAfterLast('.')
            }

            RecentAppUsageInfo(
                packageName = usage.packageName,
                appName = appName,
                lastTimeUsedMs = usage.lastTimeUsed,
                totalTimeInForegroundMs = usage.totalTimeInForeground
            )
        }
    }

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun openFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(fallback) } catch (_: Exception) {}
        }
    }
}
