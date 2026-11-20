package com.example.ai.needle

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Optional-download manager for the local Cactus 4.1.4 models:
 *  - needle3.cact (35 MB) — the Needle 3 tool-calling router.
 *  - whistle.cact (17 MB) — the Whistle speech model (Spanish included).
 *  - mirage.cact  (22 MB) — the Mirage VLM + OCR + detection model.
 *
 * Nothing is bundled in the APK and nothing downloads automatically: the user
 * presses a button, watches the progress and can delete any model at any time.
 */
object NeedleModelManager {

    const val NEEDLE_FILE = "needle3.cact"
    const val WHISTLE_FILE = "whistle.cact"
    const val MIRAGE_FILE = "mirage.cact"

    /** Live download progress shown in the settings UI (null when idle). */
    data class DownloadUi(
        val model: String,
        val progress: Float,
        val receivedBytes: Long,
        val totalBytes: Long,
        val error: String? = null,
        val done: Boolean = false
    )

    private const val PREFS = "needle_prefs"
    private const val KEY_ASSISTANT_ENABLED = "assistant_enabled"
    private const val KEY_WHISTLE_DICTATION = "whistle_dictation_enabled"
    private const val KEY_MIRAGE_VISION = "mirage_vision_enabled"

    private val downloadFlow = MutableStateFlow<DownloadUi?>(null)
    val downloadState: StateFlow<DownloadUi?> = downloadFlow

    private val mutex = Mutex()
    @Volatile private var cancelRequested = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.MINUTES)
            .build()
    }

    // ------------------------------------------------------------------
    // File state
    // ------------------------------------------------------------------

    fun needleFile(context: Context): File = File(NeedleRuntime.modelsDir(context), NEEDLE_FILE)
    fun whistleFile(context: Context): File = File(NeedleRuntime.modelsDir(context), WHISTLE_FILE)
    fun mirageFile(context: Context): File = File(NeedleRuntime.modelsDir(context), MIRAGE_FILE)

    fun isNeedleDownloaded(context: Context): Boolean = needleFile(context).exists()
    fun isWhistleDownloaded(context: Context): Boolean = whistleFile(context).exists()
    fun isMirageDownloaded(context: Context): Boolean = mirageFile(context).exists()

    fun needleFileBytes(context: Context): Long = needleFile(context).takeIf { it.exists() }?.length() ?: 0L
    fun whistleFileBytes(context: Context): Long = whistleFile(context).takeIf { it.exists() }?.length() ?: 0L
    fun mirageFileBytes(context: Context): Long = mirageFile(context).takeIf { it.exists() }?.length() ?: 0L

    // ------------------------------------------------------------------
    // Preference switches
    // ------------------------------------------------------------------

    fun isAssistantEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASSISTANT_ENABLED, true)

    fun setAssistantEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ASSISTANT_ENABLED, enabled).apply()
    }

    fun isWhistleDictationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WHISTLE_DICTATION, true)

    fun setWhistleDictationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WHISTLE_DICTATION, enabled).apply()
    }

    fun isMirageVisionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MIRAGE_VISION, true)

    fun setMirageVisionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MIRAGE_VISION, enabled).apply()
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Loads the models from disk when present (idempotent). */
    suspend fun ensureLoaded(context: Context) {
        if (isNeedleDownloaded(context) || isWhistleDownloaded(context) || isMirageDownloaded(context)) {
            NeedleRuntime.loadFromDisk(context)
        }
    }

    // ------------------------------------------------------------------
    // Download / delete
    // ------------------------------------------------------------------

    fun cancelDownload() {
        cancelRequested = true
    }

    /**
     * Downloads one of the models to app storage with progress reporting.
     * Writes to a .tmp file first and renames atomically, so a partial
     * download never masquerades as a usable model.
     */
    suspend fun download(context: Context, model: String): Boolean = mutex.withLock {
        cancelRequested = false
        val url = when (model) {
            NEEDLE_FILE -> "https://huggingface.co/Cactus-Compute/needle3/resolve/main/needle3.cact"
            WHISTLE_FILE -> "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact"
            MIRAGE_FILE -> "https://huggingface.co/Cactus-Compute/mirage/resolve/main/mirage.cact"
            else -> return@withLock false
        }
        val target = when (model) {
            NEEDLE_FILE -> needleFile(context)
            WHISTLE_FILE -> whistleFile(context)
            else -> mirageFile(context)
        }
        val tmp = File(target.parentFile, "${target.name}.tmp")

        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        downloadFlow.value = DownloadUi(
                            model = model, progress = 0f, receivedBytes = 0, totalBytes = 0,
                            error = "HTTP ${resp.code}"
                        )
                        return@withContext false
                    }
                    val body = resp.body ?: return@withContext false
                    val total = body.contentLength()
                    var received = 0L
                    val input = body.byteStream()
                    val output = FileOutputStream(tmp)
                    val buffer = ByteArray(64 * 1024)
                    output.use { fos ->
                        input.use { ins ->
                            while (true) {
                                if (cancelRequested) {
                                    tmp.delete()
                                    downloadFlow.value = null
                                    return@withContext false
                                }
                                val read = ins.read(buffer)
                                if (read < 0) break
                                fos.write(buffer, 0, read)
                                received += read
                                downloadFlow.value = DownloadUi(
                                    model = model,
                                    progress = if (total > 0) received.toFloat() / total else 0f,
                                    receivedBytes = received,
                                    totalBytes = total
                                )
                            }
                        }
                    }
                    if (total > 0 && received < total) {
                        tmp.delete()
                        downloadFlow.value = DownloadUi(
                            model = model, progress = 0f, receivedBytes = received,
                            totalBytes = total, error = "descarga incompleta"
                        )
                        return@withContext false
                    }
                    if (tmp.exists() && tmp.length() > 0) {
                        if (target.exists()) target.delete()
                        if (!tmp.renameTo(target)) {
                            tmp.delete()
                            downloadFlow.value = DownloadUi(
                                model = model, progress = 1f, receivedBytes = received,
                                totalBytes = total, error = "no se pudo guardar el archivo"
                            )
                            return@withContext false
                        }
                    } else {
                        downloadFlow.value = DownloadUi(
                            model = model, progress = 0f, receivedBytes = 0,
                            totalBytes = total, error = "descarga vacía"
                        )
                        return@withContext false
                    }
                }
                // Load straight away so the feature is usable without a restart.
                NeedleRuntime.loadFromDisk(context)
                downloadFlow.value = DownloadUi(
                    model = model, progress = 1f, receivedBytes = target.length(),
                    totalBytes = target.length(), done = true
                )
                true
            } catch (t: Throwable) {
                tmp.delete()
                downloadFlow.value = DownloadUi(
                    model = model, progress = 0f, receivedBytes = 0, totalBytes = 0,
                    error = t.message ?: t.javaClass.simpleName
                )
                false
            }
        }
    }

    /** Deletes one (or all, when model is null) model files and unloads. */
    suspend fun delete(context: Context, model: String?) = mutex.withLock {
        val dir = NeedleRuntime.modelsDir(context)
        if (model == null || model == NEEDLE_FILE) File(dir, NEEDLE_FILE).delete()
        if (model == null || model == WHISTLE_FILE) File(dir, WHISTLE_FILE).delete()
        if (model == null || model == MIRAGE_FILE) File(dir, MIRAGE_FILE).delete()
        File(dir, "$NEEDLE_FILE.tmp").delete()
        File(dir, "$WHISTLE_FILE.tmp").delete()
        File(dir, "$MIRAGE_FILE.tmp").delete()
        downloadFlow.value = null
        NeedleRuntime.reset()
        // Reload whatever is still on disk.
        NeedleRuntime.loadFromDisk(context)
    }

    /** Human-readable size, e.g. "21,6 MB". */
    fun formatBytes(bytes: Long): String =
        String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
