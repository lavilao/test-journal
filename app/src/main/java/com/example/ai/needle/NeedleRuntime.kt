package com.example.ai.needle

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Thin Kotlin wrapper around the Cactus 4.1.4 unified native engine (JNI).
 *
 * Supports:
 *  - Needle 3 (35 MB) — on-device tool-calling router and text embeddings.
 *  - Whistle (17 MB) — local speech transcription (Spanish included).
 *  - Mirage (22 MB) — local vision-language model (scene description,
 *    object detection, OCR, and spatial relations in one pass).
 *
 * The engine is process-global and NOT thread-safe, so every call is
 * serialized here and dispatched to a single background thread.
 */
object NeedleRuntime {

    /** Engine bit: the text model (needle3.cact) is loaded. */
    const val MODEL_TEXT = 1

    /** Engine bit: the speech model (whistle.cact) is loaded. */
    const val MODEL_SPEECH = 2

    /** Engine bit: the vision model (mirage.cact) is loaded. */
    const val MODEL_VISION = 4

    @Volatile private var libraryLoaded = false
    @Volatile private var libraryError: String? = null
    @Volatile private var initialized = false
    @Volatile var lastErrorMessage: String? = null
        private set

    /**
     * Tokenized length of the static prefix (system prompt + declared
     * tools), as measured by needle_init. -1 = not initialized. Surfaced in
     * the tools menu so the user can see how much of the model's context
     * window the enabled tool catalogue is eating (context bloat).
     */
    @Volatile var staticPrefixTokens: Int = -1
        private set

    private val apiMutex = Mutex()

    // Single-threaded dispatcher: the engine is not thread-safe and inference
    // takes seconds on a phone — callers queue here instead of racing.
    private val needleDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable).apply {
            name = "needle-engine"
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    init {
        try {
            System.loadLibrary("needlejni")
            libraryLoaded = true
        } catch (t: Throwable) {
            libraryLoaded = false
            libraryError = t.message ?: t.javaClass.simpleName
        }
    }

    // ------------------------------------------------------------------
    // Native bridge
    // ------------------------------------------------------------------

    private external fun nativeAvailable(): Boolean
    private external fun nativeLoadModel(path: String): Int
    private external fun nativeModels(): Int
    private external fun nativeInit(systemPrompt: String, toolsJson: String): Int
    private external fun nativeCompleteText(input: String, maxNewTokens: Int): ByteArray?
    private external fun nativeCompleteAudio(
        pcm: FloatArray,
        samples: Int,
        language: String?,
        keywords: String?,
        maxNewTokens: Int
    ): ByteArray?
    private external fun nativeTranscribe(
        pcm: FloatArray,
        samples: Int,
        language: String?,
        keywords: String?
    ): ByteArray?
    private external fun nativeEmbedText(input: String): FloatArray?
    private external fun nativeStreamProcess(
        pcm: FloatArray,
        samples: Int,
        language: String?,
        keywords: String?
    ): ByteArray?
    private external fun nativeStreamStop(): ByteArray?
    private external fun nativeDescribeImage(
        rgb: ByteArray,
        width: Int,
        height: Int,
        optionsJson: String?,
        language: String?,
        keywords: String?
    ): ByteArray?
    private external fun nativeSetThreads(threads: Int)
    private external fun nativeLastError(): ByteArray?
    private external fun nativeReset()

    // ------------------------------------------------------------------
    // Public state
    // ------------------------------------------------------------------

    /** True when the native library exists AND this ABI has the real engine. */
    fun isSupported(): Boolean = libraryLoaded && nativeAvailable()

    /** Why the native library failed to load, when it did. */
    fun supportError(): String? = if (libraryLoaded) null else libraryError

    /** Bitmask of loaded model kinds (MODEL_TEXT / MODEL_SPEECH / MODEL_VISION). */
    fun loadedModels(): Int = if (libraryLoaded) nativeModels() else 0

    fun isTextModelLoaded(): Boolean = (loadedModels() and MODEL_TEXT) != 0
    fun isSpeechModelLoaded(): Boolean = (loadedModels() and MODEL_SPEECH) != 0
    fun isVisionModelLoaded(): Boolean = (loadedModels() and MODEL_VISION) != 0

    /** True when the text model is loaded AND the tool catalogue is declared. */
    fun isReady(): Boolean = isTextModelLoaded() && initialized

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Loads the downloaded models (when present) and declares the tool
     * catalogue. Safe to call repeatedly; returns the loaded model bitmask.
     */
    suspend fun loadFromDisk(context: Context): Int = withContext(needleDispatcher) {
        if (!isSupported()) return@withContext 0
        val dir = modelsDir(context)
        val textFile = File(dir, NeedleModelManager.NEEDLE_FILE)
        val speechFile = File(dir, NeedleModelManager.WHISTLE_FILE)
        val visionFile = File(dir, NeedleModelManager.MIRAGE_FILE)
        if (textFile.exists() && !isTextModelLoaded()) {
            val rc = nativeLoadModel(textFile.absolutePath)
            if (rc < 0) {
                lastErrorMessage = lastError() ?: "no se pudo cargar ${textFile.name}"
            }
        }
        if (speechFile.exists() && !isSpeechModelLoaded()) {
            val rc = nativeLoadModel(speechFile.absolutePath)
            if (rc < 0) {
                lastErrorMessage = lastError() ?: "no se pudo cargar ${speechFile.name}"
            }
        }
        if (visionFile.exists() && !isVisionModelLoaded()) {
            val rc = nativeLoadModel(visionFile.absolutePath)
            if (rc < 0) {
                lastErrorMessage = lastError() ?: "no se pudo cargar ${visionFile.name}"
            }
        }
        if (isTextModelLoaded()) initialize(context)
        loadedModels()
    }

    /**
     * Probes a candidate `.cact` file on disk using the native Cactus engine
     * and returns its model bitmask (MODEL_TEXT=1, MODEL_SPEECH=2, MODEL_VISION=4),
     * or 0 if invalid/incompatible. Resets the transient state afterwards so the
     * caller can reload the canonical model set cleanly.
     */
    suspend fun probeModelKind(file: File): Int = apiMutex.withLock {
        if (!isSupported() || !file.exists() || file.length() <= 0L) return@withLock 0
        withContext(needleDispatcher) {
            nativeReset()
            initialized = false
            lastErrorMessage = null
            val rc = nativeLoadModel(file.absolutePath)
            val detected = if (rc > 0) rc else 0
            if (detected == 0) {
                lastErrorMessage = lastError() ?: "formato .cact incompatible o dañado"
            }
            nativeReset()
            detected
        }
    }

    /** Declares the system prompt + tools. Must run after the text model. */
    private suspend fun initialize(context: Context): Boolean = apiMutex.withLock {
        if (initialized) return@withLock true
        declareTools(context)
    }

    /**
     * (Re)declares the tool catalogue from the CURRENT gating switches —
     * used by the tools menu. Returns true when needle_init accepted the
     * prompt. When the enabled set exceeds the context window the engine
     * reports the measured token count and we fall back to the compact core
     * set, so the assistant never ends up without tools.
     */
    suspend fun applyToolGating(context: Context): Boolean = apiMutex.withLock {
        WhistleKeywordBuilder.invalidate()
        initialized = false
        declareTools(context)
    }

    private suspend fun declareTools(context: Context): Boolean =
        withContext(needleDispatcher) {
            var rc = nativeInit(
                NeedleTools.buildSystemPrompt(),
                NeedleTools.buildToolsJson(context)
            )
            if (rc < 0) {
                // Full catalogue too big for the context window (context
                // bloat): retry with the compact core set, honestly degraded.
                val fullError = lastError()
                rc = nativeInit(
                    NeedleTools.buildSystemPrompt(),
                    NeedleTools.buildCoreToolsJson()
                )
                if (rc >= 0) {
                    lastErrorMessage = "Conjunto de herramientas reducido (el catálogo " +
                            "completo no cabía en la ventana de contexto). $fullError"
                }
            }
            staticPrefixTokens = if (rc >= 0) rc else -1
            initialized = rc >= 0
            if (rc < 0 && lastErrorMessage == null) {
                lastErrorMessage = lastError() ?: "needle_init devolvió $rc"
            }
            initialized
        }

    // ------------------------------------------------------------------
    // Inference
    // ------------------------------------------------------------------

    /**
     * Routes a text utterance through Needle. Returns the raw JSON response
     * (parsed by [NeedleTools]) or null when unavailable / on error.
     */
    suspend fun completeText(utterance: String, maxNewTokens: Int = 320): String? =
        apiMutex.withLock {
            if (!isReady()) return@withLock null
            withContext(needleDispatcher) {
                val bytes = nativeCompleteText(utterance, maxNewTokens)
                bytes?.toString(Charsets.UTF_8)
            }
        }

    /**
     * Audio in, tool calls out: 16 kHz mono float PCM. Requires the speech
     * model. Returns the raw JSON response (speech fields under audio_*) or
     * null when unavailable / on error.
     */
    suspend fun completeAudio(
        pcm: FloatArray,
        language: String? = null,
        maxNewTokens: Int = 320,
        keywords: String? = null
    ): String? = apiMutex.withLock {
        if (!isReady() || !isSpeechModelLoaded() || pcm.isEmpty()) return@withLock null
        withContext(needleDispatcher) {
            val bytes = nativeCompleteAudio(pcm, pcm.size, language, keywords, maxNewTokens)
            bytes?.toString(Charsets.UTF_8)
        }
    }

    /** Local transcription of 16 kHz mono float PCM via Whistle (single <=30s window). */
    suspend fun transcribe(
        pcm: FloatArray,
        language: String? = null,
        keywords: String? = null
    ): String? = apiMutex.withLock {
        if (!isSpeechModelLoaded() || pcm.isEmpty()) return@withLock null
        withContext(needleDispatcher) {
            val bytes = nativeTranscribe(pcm, pcm.size, language, keywords)
            bytes?.toString(Charsets.UTF_8)
        }
    }

    /**
     * Transcribes audio of any practical length by splitting recordings longer
     * than 28 seconds into overlapping 26-second windows (2-second overlap) and
     * deduplicating boundary words cleanly.
     */
    suspend fun transcribeLongAudio(
        pcm: FloatArray,
        language: String? = null,
        keywords: String? = null,
        onChunkProgress: ((currentChunk: Int, totalChunks: Int) -> Unit)? = null
    ): WhistleResult? {
        if (!isSpeechModelLoaded() || pcm.isEmpty()) return null
        val sampleRate = 16_000
        val singleCallThreshold = 28 * sampleRate
        if (pcm.size <= singleCallThreshold) {
            onChunkProgress?.invoke(1, 1)
            val raw = transcribe(pcm, language, keywords)
            return WhistleResult.parse(raw)
        }

        val windowSamples = 26 * sampleRate
        val stepSamples = 24 * sampleRate
        val starts = ArrayList<Int>()
        var offset = 0
        while (offset < pcm.size) {
            starts.add(offset)
            if (offset + windowSamples >= pcm.size) break
            offset += stepSamples
        }

        var mergedText = ""
        var detectedLang: String? = null
        val ttftValues = ArrayList<Double>()
        val tpsValues = ArrayList<Double>()

        for ((index, start) in starts.withIndex()) {
            onChunkProgress?.invoke(index + 1, starts.size)
            val end = minOf(pcm.size, start + windowSamples)
            val slice = pcm.copyOfRange(start, end)
            val raw = transcribe(slice, language, keywords)
            val parsed = WhistleResult.parse(raw) ?: continue
            if (parsed.text.isNotBlank()) {
                mergedText = WhistleKeywordBuilder.mergeChunkTranscripts(mergedText, parsed.text)
            }
            if (detectedLang == null && !parsed.language.isNullOrBlank()) {
                detectedLang = parsed.language
            }
            parsed.ttftMs?.let { ttftValues.add(it) }
            parsed.decodeTps?.let { tpsValues.add(it) }
        }

        if (mergedText.isBlank()) return null
        return WhistleResult(
            text = mergedText,
            language = detectedLang ?: language,
            ttftMs = ttftValues.takeIf { it.isNotEmpty() }?.average(),
            decodeTps = tpsValues.takeIf { it.isNotEmpty() }?.average()
        )
    }

    /**
     * Vision understanding via Mirage (mirage.cact): analyzes a Bitmap locally
     * in a single pass and returns scene description, objects, OCR text, and relations.
     */
    suspend fun describeImage(
        bitmap: Bitmap,
        language: String? = "es",
        keywords: String? = null,
        optionsJson: String = MirageVision.DEFAULT_OPTIONS_JSON
    ): MirageResult? = apiMutex.withLock {
        if (!isVisionModelLoaded()) return@withLock null
        withContext(needleDispatcher) {
            try {
                val (rgb, width, height) = MirageVision.bitmapToPackedRgb(bitmap) ?: return@withContext null
                val bytes = nativeDescribeImage(rgb, width, height, optionsJson, language, keywords)
                if (bytes == null) {
                    lastErrorMessage = lastError() ?: "cactus_describe falló"
                    return@withContext null
                }
                MirageVision.parseJson(bytes.toString(Charsets.UTF_8))
            } catch (t: Throwable) {
                lastErrorMessage = t.message
                null
            }
        }
    }

    /** Loads an image from a content/file URI, decodes scaled RGB, and runs Mirage locally. */
    suspend fun describeImageFromUri(
        context: Context,
        uri: Uri,
        language: String? = "es",
        keywords: String? = null,
        optionsJson: String = MirageVision.DEFAULT_OPTIONS_JSON
    ): MirageResult? = apiMutex.withLock {
        if (!isVisionModelLoaded()) return@withLock null
        withContext(needleDispatcher) {
            try {
                val (rgb, width, height) = MirageVision.decodePackedRgbFromUri(context, uri)
                    ?: return@withContext null
                val bytes = nativeDescribeImage(rgb, width, height, optionsJson, language, keywords)
                if (bytes == null) {
                    lastErrorMessage = lastError() ?: "cactus_describe falló"
                    return@withContext null
                }
                MirageVision.parseJson(bytes.toString(Charsets.UTF_8))
            } catch (t: Throwable) {
                lastErrorMessage = t.message
                null
            }
        }
    }

    /**
     * TRUE text embedding with the loaded Needle model: a vector for a
     * sentence, the basis of the on-device semantic search and RAG.
     */
    suspend fun embedText(text: String): FloatArray? = apiMutex.withLock {
        if (!isTextModelLoaded() || text.isBlank()) return@withLock null
        withContext(needleDispatcher) {
            try {
                nativeEmbedText(text.take(1600))
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * LIVE transcription pass: appends ~1 s of 16 kHz mono float PCM to the
     * stream and returns the JSON with the words this pass committed plus
     * the unconfirmed tail. Only for the speech model.
     */
    suspend fun streamProcess(
        pcm: FloatArray,
        language: String? = null,
        keywords: String? = null
    ): String? = apiMutex.withLock {
        if (!isSpeechModelLoaded() || pcm.isEmpty()) return@withLock null
        withContext(needleDispatcher) {
            try {
                val bytes = nativeStreamProcess(pcm, pcm.size, language, keywords)
                bytes?.toString(Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Ends the live stream; JSON with the final committed words. */
    suspend fun streamStop(): String? = apiMutex.withLock {
        if (!isSpeechModelLoaded()) return@withLock null
        withContext(needleDispatcher) {
            try {
                val bytes = nativeStreamStop()
                bytes?.toString(Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Unloads everything (used when the user deletes the models). */
    suspend fun reset() = apiMutex.withLock {
        withContext(needleDispatcher) {
            if (libraryLoaded) nativeReset()
            initialized = false
            lastErrorMessage = null
        }
    }
    private fun lastError(): String? = nativeLastError()?.toString(Charsets.UTF_8)

    fun modelsDir(context: Context): File =
        File(context.filesDir, "needle").apply { mkdirs() }
}
