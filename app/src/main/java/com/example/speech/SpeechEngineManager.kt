package com.example.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * One recognition engine option shown to the user.
 *
 * [id] is persisted in preferences and is one of:
 *  - "auto": let this manager decide (on-device recognizer first, then a
 *    Google-owned recognition service if present, then the system default).
 *  - "ondevice": the dedicated on-device recognizer (API 31+).
 *  - "<pkg>/<cls>": an explicit recognition service component.
 */
data class RecognitionEngineInfo(
    val id: String,
    val label: String,
    val sublabel: String,
    val component: ComponentName?,
    val isOnDevice: Boolean = false
)

/** Result of SpeechRecognizer#checkRecognitionSupport for one locale (API 33+). */
data class OfflineSpeechSupport(
    val installed: List<String>,
    val pending: List<String>,
    val downloadable: List<String>
)

/**
 * Central speech-engine management.
 *
 * WHY THIS EXISTS: `SpeechRecognizer.createSpeechRecognizer(context)` binds to
 * the DEFAULT recognition service chosen at system level. On Samsung phones
 * that default is "Samsung Voice Input" (com.samsung.android.svoiceime) — the
 * very same engine Gboard's voice typing uses — which is why dictation in the
 * app "sounds like Gboard". By letting the user pick the component explicitly
 * (Speech Services by Google vs Samsung vs on-device), dictation always uses
 * the intended engine.
 *
 * It also wraps the offline-model APIs added in Android 13:
 *  - [SpeechRecognizer.checkRecognitionSupport] — which offline languages are
 *    installed / pending / available for download.
 *  - [SpeechRecognizer.triggerModelDownload] — ask the recognition service to
 *    schedule the download of the offline model for a locale. On Android 12
 *    and below this is not possible from a third-party app and the only path
 *    is the system settings screen ([openOfflineModelsSettings]).
 */
class SpeechEngineManager(private val context: Context) {

    companion object {
        private const val PREFS = "speech_engine_prefs"
        private const val KEY_SELECTION = "selected_engine"

        /** Packages that belong to Google's "Speech Services by Google". */
        private val GOOGLE_PACKAGES = setOf(
            "com.google.android.tts",
            "com.google.android.apps.speechservices"
        )
        private const val SAMSUNG_VOICE_INPUT = "com.samsung.android.svoiceime"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Last honest failure, surfaced in the settings UI. */
    val lastError = MutableStateFlow<String?>(null)

    // -----------------------------------------------------------------
    // Engine discovery
    // -----------------------------------------------------------------

    /** All recognition services installed on the device (plus the on-device one). */
    fun availableEngines(): List<RecognitionEngineInfo> {
        val result = mutableListOf<RecognitionEngineInfo>()
        result.add(
            RecognitionEngineInfo(
                id = "auto",
                label = "Automático",
                sublabel = resolvedEngineInfoNoRecurse().label,
                component = null
            )
        )

        if (isOnDeviceAvailable()) {
            result.add(
                RecognitionEngineInfo(
                    id = "ondevice",
                    label = "Reconocedor en el dispositivo",
                    sublabel = "Motor nuevo de Google sin conexión (API 31+)",
                    component = null,
                    isOnDevice = true
                )
            )
        }

        val pm = context.packageManager
        val query = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(query, PackageManager.MATCH_ALL)
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(query, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        services
            .filter { it.serviceInfo != null }
            .map { ri ->
                val si = ri.serviceInfo
                val cn = ComponentName(si.packageName, si.name)
                val label = try {
                    ri.loadLabel(pm).toString()
                } catch (_: Exception) {
                    si.packageName
                }
                val vendor = when (si.packageName) {
                    in GOOGLE_PACKAGES -> "Google · Speech Services"
                    SAMSUNG_VOICE_INPUT -> "Samsung · voz del teclado (la que usa Gboard)"
                    else -> si.packageName
                }
                RecognitionEngineInfo(
                    id = "${si.packageName}/${si.name}",
                    label = label,
                    sublabel = vendor,
                    component = cn
                )
            }
            .sortedBy { if (it.component?.packageName in GOOGLE_PACKAGES) 0 else 1 }
            .forEach { result.add(it) }

        return result
    }

    /** True when the dedicated on-device recognizer exists (API 31+). */
    fun isOnDeviceAvailable(): Boolean = try {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }

    /** The engine that would be used right now. */
    fun resolvedEngineInfo(): RecognitionEngineInfo {
        val selection = prefs.getString(KEY_SELECTION, "auto") ?: "auto"

        if (selection == "ondevice" && isOnDeviceAvailable()) {
            return RecognitionEngineInfo(
                "ondevice", "Reconocedor en el dispositivo",
                "Motor nuevo de Google sin conexión", null, isOnDevice = true
            )
        }
        if (selection != "auto") {
            val engines = availableEngines()
            engines.firstOrNull { it.id == selection }?.let { return it }
        }
        return resolvedEngineInfoNoRecurse()
    }

    /** "auto" resolution without re-listing engines (used while listing). */
    private fun resolvedEngineInfoNoRecurse(): RecognitionEngineInfo {
        if (isOnDeviceAvailable()) {
            return RecognitionEngineInfo(
                "ondevice", "Reconocedor en el dispositivo",
                "Motor nuevo de Google sin conexión", null, isOnDevice = true
            )
        }
        val pm = context.packageManager
        val query = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(query, PackageManager.MATCH_ALL)
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(query, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }
        services
            .firstOrNull { it.serviceInfo != null && it.serviceInfo.packageName in GOOGLE_PACKAGES }
            ?.let { ri ->
                return RecognitionEngineInfo(
                    "${ri.serviceInfo.packageName}/${ri.serviceInfo.name}",
                    "Speech Services by Google",
                    "Google · servicio explícito", null
                )
            }
        return RecognitionEngineInfo(
            "auto", "Servicio predeterminado del sistema",
            "El que elijas en Ajustes → Entrada de voz", null
        )
    }

    /** Persist the user's engine choice. */
    fun selectEngine(id: String) {
        prefs.edit().putString(KEY_SELECTION, id).apply()
    }

    fun currentSelection(): String = prefs.getString(KEY_SELECTION, "auto") ?: "auto"

    // -----------------------------------------------------------------
    // Recognizer factory
    // -----------------------------------------------------------------

    /**
     * Creates a SpeechRecognizer bound to the selected engine, falling back
     * to the plain system recognizer when the explicit binding fails.
     */
    fun createRecognizer(): SpeechRecognizer {
        val engine = resolvedEngineInfo()
        return try {
            when {
                engine.isOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

                engine.component != null ->
                    SpeechRecognizer.createSpeechRecognizer(context, engine.component)

                else ->
                    SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            lastError.value =
                "El motor «${engine.label}» falló al iniciar (${e.message}); uso el predeterminado."
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    /** Honest one-line description for settings/dictation UIs. */
    fun engineDescription(): String {
        val engine = resolvedEngineInfo()
        val offline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "puede consultar y descargar modelos sin conexión desde esta app"
        } else {
            "los modelos sin conexión se gestionan en Ajustes del sistema (en Android 13+ se pueden descargar desde esta app)"
        }
        return "Motor activo: ${engine.label} — $offline."
    }

    // -----------------------------------------------------------------
    // Offline model support / download (API 33+)
    // -----------------------------------------------------------------

    /**
     * Asks the active engine which offline models exist for [locale].
     * Returns null on Android < 13 or when the query fails.
     */
    fun checkOfflineSupport(
        locale: Locale = Locale.getDefault(),
        onResult: (OfflineSpeechSupport?) -> Unit,
        onError: (Int) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(null)
            return
        }
        val recognizer = try {
            createRecognizer()
        } catch (_: Exception) {
            onResult(null)
            return
        }
        val intent = offlineIntent(locale)
        val main = ContextCompat.getMainExecutor(context)
        try {
            recognizer.checkRecognitionSupport(intent, main, object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    val support = OfflineSpeechSupport(
                        installed = recognitionSupport.installedOnDeviceLanguages.orEmpty(),
                        pending = recognitionSupport.pendingOnDeviceLanguages.orEmpty(),
                        downloadable = recognitionSupport.supportedOnDeviceLanguages.orEmpty()
                    )
                    try { recognizer.destroy() } catch (_: Exception) {}
                    onResult(support)
                }

                override fun onError(error: Int) {
                    try { recognizer.destroy() } catch (_: Exception) {}
                    onError(error)
                }
            })
        } catch (e: Exception) {
            try { recognizer.destroy() } catch (_: Exception) {}
            lastError.value = "No pude consultar los modelos: ${e.message}"
            onResult(null)
        }
    }

    /** Reports whether the offline model for [locale] is already installed. */
    fun isOfflineModelInstalled(
        locale: Locale = Locale.getDefault(),
        onResult: (Boolean) -> Unit
    ) {
        val tag = locale.toLanguageTag()
        checkOfflineSupport(locale, onResult = { support ->
            val installed = support?.installed ?: emptyList()
            val languagePart = tag.substringBefore('-')
            onResult(installed.any { it == tag || it.startsWith(languagePart) })
        })
    }

    private var downloadRecognizer: SpeechRecognizer? = null

    /**
     * Schedules the download of the offline model for [locale] through the
     * active recognition service (Android 13+). The download itself runs in
     * Speech Services by Google — with this API the app cannot follow its
     * progress, so re-check [checkOfflineSupport] afterwards.
     */
    fun triggerOfflineModelDownload(
        locale: Locale = Locale.getDefault(),
        onScheduled: () -> Unit = {},
        onUnavailable: () -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onUnavailable()
            return
        }
        downloadRecognizer?.let { try { it.destroy() } catch (_: Exception) {} }
        downloadRecognizer = try {
            createRecognizer()
        } catch (_: Exception) {
            null
        }
        if (downloadRecognizer == null) {
            onUnavailable()
            return
        }
        try {
            downloadRecognizer?.triggerModelDownload(offlineIntent(locale))
            onScheduled()
            // Release the client after the request had time to reach the service.
            Handler(Looper.getMainLooper()).postDelayed({
                downloadRecognizer?.let { try { it.destroy() } catch (_: Exception) {} }
                downloadRecognizer = null
            }, 60_000L)
        } catch (e: Exception) {
            lastError.value = "La descarga no se pudo programar: ${e.message}"
            try { downloadRecognizer?.destroy() } catch (_: Exception) {}
            downloadRecognizer = null
            onUnavailable()
        }
    }

    private fun offlineIntent(locale: Locale): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    // -----------------------------------------------------------------
    // System settings deep links
    // -----------------------------------------------------------------

    /**
     * Opens the offline voice-model management screen with graceful
     * fallbacks, since ROMs expose it differently.
     */
    fun openOfflineModelsSettings(): Boolean {
        // 1) Voice input settings (lists recognition services; on most
        //    Android 12+/13+ builds, tapping the active service leads to its
        //    offline languages screen).
        val voiceInput = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(voiceInput)) return true

        // 2) Samsung One UI keeps offline voice inside the Samsung Voice
        //    Input app details.
        val samsungDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$SAMSUNG_VOICE_INPUT"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(samsungDetails)) return true

        // 3) Speech Services by Google app details.
        for (pkg in GOOGLE_PACKAGES) {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(details)) return true
        }
        return false
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
