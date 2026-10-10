package com.example.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
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

    /**
     * True when the app can read media + downloads from OTHER apps on this
     * device (what makes the device-wide file search actually useful).
     * Android 13+ uses the granular READ_MEDIA_* permissions; older devices
     * use READ_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** The exact runtime permissions to request for device-wide file search. */
    fun requiredStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Reliable usage-stats permission check via AppOps (the same source of
     * truth Android Settings uses). A queryUsageStats-based check returns
     * false when there simply was no usage in the window, which made the UI
     * ask for an already-granted permission.
     *
     * Some OEM ROMs report the op as MODE_DEFAULT even when the user flipped
     * the switch, so we add a second, empirical fallback: actually query the
     * usage stats — if the system hands us real rows, the permission IS
     * granted no matter what AppOps claims.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOpsSaysYes = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return fallbackUsageStatsCheck()
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
        if (appOpsSaysYes) return true
        return fallbackUsageStatsCheck()
    }

    /**
     * Empirical check: query real usage rows for the last 24 h. Getting any
     * row back proves the permission is granted (an unauthorized query
     * returns an empty list).
     */
    private fun fallbackUsageStatsCheck(): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            !stats.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    /**
     * Search real contacts by name or phone. CASE + ACCENT insensitive:
     * SQLite's LIKE only folds ASCII case, so «maria» would never find
     * «María». We scan the whole (bounded) contact list and match with a
     * de-accented, lowercased comparison.
     */
    suspend fun searchContacts(query: String): List<DeviceContactInfo> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission() || query.isBlank()) return@withContext emptyList()

        val qNorm = foldForSearch(query.trim())
        val qDigits = query.filter { it.isDigit() }
        val results = mutableListOf<DeviceContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
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

                    val nameHit = foldForSearch(name).contains(qNorm)
                    val phoneHit = qDigits.length >= 2 &&
                        (phone?.filter { it.isDigit() }?.contains(qDigits) == true)
                    if (nameHit || phoneHit) {
                        results.add(DeviceContactInfo(id = id, displayName = name, phoneNumber = phone, photoUri = photo))
                    }
                }
            }
        } catch (_: Exception) {}

        results
    }

    /** lowercase + accent-folded form for insensitive matching. */
    private fun foldForSearch(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")

    /**
     * Search real device files (MediaStore) by filename. Queries BOTH the
     * general Files collection and the Downloads collection — with the
     * storage permission granted, Downloads is where PDFs and documents
     * from other apps become visible on Android 11+.
     */
    suspend fun searchFiles(query: String): List<DeviceFileInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<DeviceFileInfo>()
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
            val filesCollection = mediaStoreCollection()
            context.contentResolver.query(
                filesCollection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit = 25, collection = filesCollection)
            }
        } catch (_: Exception) {}

        // Downloads (visible cross-app since Android 11 with READ permission)
        try {
            val downloadsCollection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                downloadsCollection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit = 25, collection = downloadsCollection)
            }
        } catch (_: Exception) {}

        // De-duplicate (a file can appear in both collections)
        results.distinctBy { it.path }.take(25)
    }

    /**
     * Get recent files modified or downloaded on device in the last days.
     */
    suspend fun getRecentDeviceFiles(limit: Int = 15): List<DeviceFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DeviceFileInfo>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        try {
            val collection = mediaStoreCollection()
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit, collection = collection)
            }
        } catch (_: Exception) {}

        try {
            val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                downloads,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                extractFilesFromCursor(cursor, results, limit, collection = downloads)
            }
        } catch (_: Exception) {}

        results.distinctBy { it.path }.sortedByDescending { it.dateModifiedMs }.take(limit)
    }

    private fun mediaStoreCollection(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private fun extractFilesFromCursor(
        cursor: Cursor,
        results: MutableList<DeviceFileInfo>,
        limit: Int,
        collection: Uri = mediaStoreCollection()
    ) {
        val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

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

    /** Launchable-app cache: a full PackageManager scan per keystroke was
     *  pure waste; 30 s of cache keeps «abre whatsapp» instant. */
    @Volatile
    private var launchableAppsCache: Pair<List<RecentAppUsageInfo>, Long>? = null

    /**
     * All launchable apps (name + package), for the assistant's "abrer <app>"
     * voice command and the app search experience.
     */
    suspend fun listLaunchableApps(forceRefresh: Boolean = false): List<RecentAppUsageInfo> = withContext(Dispatchers.IO) {
        val cached = launchableAppsCache
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - cached.second < 30_000L
        ) {
            return@withContext cached.first
        }
        val pm = context.packageManager
        val list = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { resolveInfo ->
                    RecentAppUsageInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(pm).toString(),
                        lastTimeUsedMs = 0L,
                        totalTimeInForegroundMs = 0L
                    )
                }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.appName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
        launchableAppsCache = list to System.currentTimeMillis()
        list
    }

    /** Find an installed app by fuzzy name ("whatsapp" -> com.whatsapp).
     *  Case + accent insensitive (foldForSearch on both sides). */
    suspend fun findAppByName(query: String): RecentAppUsageInfo? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        val apps = listLaunchableApps()
        val q = foldForSearch(query.trim()).removePrefix("la ").removePrefix("el ")
        apps.firstOrNull { foldForSearch(it.appName) == q }
            ?: apps.firstOrNull { foldForSearch(it.appName).contains(q) }
            ?: apps.firstOrNull {
                it.packageName.lowercase().contains(q.replace(" ", ""))
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
