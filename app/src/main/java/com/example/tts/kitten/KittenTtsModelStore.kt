package com.example.tts.kitten

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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Storage, preferences and downloads for the KittenTTS 2 pico models.
 *
 * Nothing is bundled in the APK: the pico graph (int8, ~8 MB) and its voice
 * pack (~2 MB) are downloaded per language from Hugging Face only when the
 * user asks for them, exactly like the app does with the Cactus models.
 * eSpeak-NG's language data *is* bundled (assets/espeak-ng-data) because it is
 * required before any language can run.
 */
object KittenTtsModelStore {

    const val ENGINE_SYSTEM = "system"
    const val ENGINE_KITTEN = "kitten"

    private const val PREFS = "kitten_tts_prefs"
    private const val KEY_ENGINE = "engine"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_VOICE = "voice"
    private const val KEY_SPEED = "speed"

    private const val CONFIG_FILE = "config.json"
    private const val VOCAB_FILE = "vocab.json"

    data class DownloadUi(
        val language: String,
        val phase: String,
        val progress: Float,
        val receivedBytes: Long,
        val totalBytes: Long,
        val error: String? = null,
        val done: Boolean = false
    )

    private val downloadFlow = MutableStateFlow<DownloadUi?>(null)
    val downloadState: StateFlow<DownloadUi?> = downloadFlow

    private val mutex = Mutex()

    @Volatile
    private var cancelRequested = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.MINUTES)
            .build()
    }

    // ------------------------------------------------------------------
    // Paths
    // ------------------------------------------------------------------

    fun modelDir(context: Context): File = File(context.filesDir, "kitten_tts")

    /** Parent of `espeak-ng-data`; eSpeak-NG appends the leaf itself. */
    fun espeakParentDir(context: Context): File = context.filesDir

    fun espeakDataDir(context: Context): File = File(context.filesDir, "espeak-ng-data")

    fun modelFile(context: Context, language: String): File =
        File(modelDir(context), KittenTtsCatalog.modelFile(language))

    fun voicesFile(context: Context, language: String): File =
        File(modelDir(context), KittenTtsCatalog.voicesFile(language))

    fun vocabFile(context: Context): File = File(modelDir(context), VOCAB_FILE)

    fun configFile(context: Context): File = File(modelDir(context), CONFIG_FILE)

    fun isDownloaded(context: Context, language: String): Boolean =
        modelFile(context, language).length() > 1_000L &&
            voicesFile(context, language).length() > 1_000L &&
            vocabFile(context).length() > 0L &&
            configFile(context).length() > 0L

    fun downloadedBytes(context: Context, language: String): Long {
        if (!isDownloaded(context, language)) return 0L
        return modelFile(context, language).length() + voicesFile(context, language).length()
    }

    // ------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun engine(context: Context): String =
        prefs(context).getString(KEY_ENGINE, ENGINE_SYSTEM) ?: ENGINE_SYSTEM

    fun setEngine(context: Context, engine: String) {
        prefs(context).edit().putString(KEY_ENGINE, engine).apply()
    }

    fun isKittenSelected(context: Context): Boolean = engine(context) == ENGINE_KITTEN

    fun language(context: Context): String {
        val stored = prefs(context).getString(KEY_LANGUAGE, KittenTtsCatalog.DEFAULT_LANGUAGE)
        return if (stored != null && KittenTtsCatalog.isSupportedLanguage(stored)) {
            stored
        } else {
            KittenTtsCatalog.DEFAULT_LANGUAGE
        }
    }

    /** Picking a language resets the voice to that language's first voice. */
    fun setLanguage(context: Context, code: String) {
        if (!KittenTtsCatalog.isSupportedLanguage(code)) return
        val firstVoice = KittenTtsCatalog.voices(code).first()
        prefs(context).edit()
            .putString(KEY_LANGUAGE, code)
            .putString(KEY_VOICE, firstVoice.key)
            .apply()
    }

    fun voice(context: Context): String {
        val stored = prefs(context).getString(KEY_VOICE, null)
        val currentLanguage = language(context)
        if (stored != null && KittenTtsCatalog.languageOfVoice(stored) == currentLanguage) {
            return stored
        }
        return KittenTtsCatalog.voices(currentLanguage).first().key
    }

    /** The voice carries its language, so picking a voice also picks the model. */
    fun setVoice(context: Context, voiceKey: String) {
        val voiceLanguage = KittenTtsCatalog.languageOfVoice(voiceKey) ?: return
        prefs(context).edit()
            .putString(KEY_LANGUAGE, voiceLanguage)
            .putString(KEY_VOICE, voiceKey)
            .apply()
    }

    fun speed(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED, 1.0f).coerceIn(0.6f, 1.6f)

    fun setSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, speed.coerceIn(0.6f, 1.6f)).apply()
    }

    // ------------------------------------------------------------------
    // eSpeak-NG data (bundled assets -> filesDir)
    // ------------------------------------------------------------------

    /**
     * Copies assets/espeak-ng-data to filesDir on first use. eSpeak-NG reads
     * its compiled tables with plain file I/O, so it cannot read them from the
     * APK directly.
     */
    suspend fun ensureEspeakData(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = espeakDataDir(context)
        if (File(target, "phontab").exists() && File(target, "phondata").exists()) return@withContext true
        try {
            target.mkdirs()
            copyAssetDir(context, "espeak-ng-data", target)
            File(target, "phontab").exists()
        } catch (_: Throwable) {
            false
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // It is a file.
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssetDir(context, "$assetPath/$child", File(target, child))
        }
    }

    // ------------------------------------------------------------------
    // Download / delete
    // ------------------------------------------------------------------

    fun cancelDownload() {
        cancelRequested = true
    }

    /**
     * Downloads config.json, vocab.json and the selected language's ONNX graph
     * plus voice pack. Each file is written to `.tmp` and renamed, so a partial
     * download never looks usable.
     */
    suspend fun download(context: Context, language: String): Boolean = mutex.withLock {
        if (!KittenTtsCatalog.isSupportedLanguage(language)) return@withLock false
        cancelRequested = false
        downloadFlow.value = DownloadUi(language, "Preparando…", 0f, 0, 0)

        val dir = modelDir(context).apply { mkdirs() }
        val files = listOf(
            CONFIG_FILE to "$CONFIG_FILE",
            VOCAB_FILE to "$VOCAB_FILE",
            KittenTtsCatalog.modelFile(language) to KittenTtsCatalog.modelFile(language),
            KittenTtsCatalog.voicesFile(language) to KittenTtsCatalog.voicesFile(language)
        )

        withContext(Dispatchers.IO) {
            var completedBytes = 0L
            try {
                for ((name, remoteName) in files) {
                    if (cancelRequested) {
                        downloadFlow.value = null
                        return@withContext false
                    }
                    val target = File(dir, name)
                    val ok = downloadFile(
                        language = language,
                        url = "${KittenTtsCatalog.REPO_BASE}/$remoteName",
                        target = target,
                        baseBytes = completedBytes
                    )
                    if (!ok) return@withContext false
                    completedBytes += target.length()
                }
                downloadFlow.value = DownloadUi(
                    language = language,
                    phase = "Listo",
                    progress = 1f,
                    receivedBytes = completedBytes,
                    totalBytes = completedBytes,
                    done = true
                )
                true
            } catch (t: Throwable) {
                downloadFlow.value = DownloadUi(
                    language = language,
                    phase = "Error",
                    progress = 0f,
                    receivedBytes = 0,
                    totalBytes = 0,
                    error = t.message ?: t.javaClass.simpleName
                )
                false
            }
        }
    }

    private fun downloadFile(
        language: String,
        url: String,
        target: File,
        baseBytes: Long
    ): Boolean {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.delete()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                downloadFlow.value = DownloadUi(
                    language = language,
                    phase = "Error",
                    progress = 0f,
                    receivedBytes = baseBytes,
                    totalBytes = 0,
                    error = "HTTP ${resp.code}"
                )
                return false
            }
            val body = resp.body ?: return false
            val expected = body.contentLength()
            var received = 0L
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelRequested) {
                            tmp.delete()
                            downloadFlow.value = null
                            return false
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        downloadFlow.value = DownloadUi(
                            language = language,
                            phase = "Descargando ${target.name}",
                            progress = if (expected > 0) received.toFloat() / expected else 0f,
                            receivedBytes = baseBytes + received,
                            totalBytes = if (expected > 0) baseBytes + expected else 0
                        )
                    }
                }
            }
            if (expected > 0 && received < expected) {
                tmp.delete()
                downloadFlow.value = DownloadUi(
                    language = language,
                    phase = "Error",
                    progress = 0f,
                    receivedBytes = baseBytes + received,
                    totalBytes = baseBytes + expected,
                    error = "descarga incompleta"
                )
                return false
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                downloadFlow.value = DownloadUi(
                    language = language,
                    phase = "Error",
                    progress = 0f,
                    receivedBytes = baseBytes,
                    totalBytes = 0,
                    error = "no se pudo guardar ${target.name}"
                )
                return false
            }
            return true
        }
    }

    suspend fun deleteLanguage(context: Context, language: String) = withContext(Dispatchers.IO) {
        modelFile(context, language).delete()
        voicesFile(context, language).delete()
        // The shared config/vocab stay only while another language is installed.
        val anyLeft = KittenTtsCatalog.languages.any { it.code != language && isDownloaded(context, it.code) }
        if (!anyLeft) {
            configFile(context).delete()
            vocabFile(context).delete()
        }
        downloadFlow.value = null
    }

    suspend fun deleteAll(context: Context) = withContext(Dispatchers.IO) {
        modelDir(context).deleteRecursively()
        downloadFlow.value = null
    }

    fun formatMb(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
