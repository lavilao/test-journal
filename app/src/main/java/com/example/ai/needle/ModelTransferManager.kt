package com.example.ai.needle

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.speech.voiceprint.VoicePrintStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Backup / restore and offline local import for the LOCAL models:
 *  - needle3.cact, whistle.cact, mirage.cact (Cactus 4.1.4) — exported or
 *    imported directly via Storage Access Framework without any internet.
 *  - The voice print (Voice Match) — exported/imported as JSON.
 */
object ModelTransferManager {

    data class TransferResult(val ok: Boolean, val message: String)

    const val AUTO_MODEL = "auto"
    private const val MIN_MODEL_BYTES = 1024L * 512 // 512 KB sanity floor

    // ------------------------------------------------------------------
    // Cactus model files
    // ------------------------------------------------------------------

    /** Copies a downloaded or imported model to a user-picked SAF destination. */
    suspend fun exportModel(context: Context, model: String, dest: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val source: File = when (model) {
                    NeedleModelManager.NEEDLE_FILE -> NeedleModelManager.needleFile(context)
                    NeedleModelManager.WHISTLE_FILE -> NeedleModelManager.whistleFile(context)
                    NeedleModelManager.MIRAGE_FILE -> NeedleModelManager.mirageFile(context)
                    else -> return@withContext TransferResult(false, "Modelo desconocido")
                }
                if (!source.exists()) {
                    return@withContext TransferResult(false, "El modelo no está disponible en el dispositivo")
                }
                context.contentResolver.openOutputStream(dest, "w")?.use { out ->
                    source.inputStream().use { ins -> ins.copyTo(out, 64 * 1024) }
                } ?: return@withContext TransferResult(false, "No pude abrir el destino")
                TransferResult(true, "Exportados ${NeedleModelManager.formatBytes(source.length())} de ${source.name}")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    /**
     * Imports a local `.cact` model from a user-picked SAF source without internet.
     * Supports explicit slot (`needle3.cact`, `whistle.cact`, `mirage.cact`) or
     * [AUTO_MODEL] auto-detection via Cactus native model probing / filename.
     */
    suspend fun importModel(context: Context, model: String, src: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            val modelsDir = NeedleRuntime.modelsDir(context)
            val tmp = File(modelsDir, "import_candidate.cact.tmp")
            try {
                var bytes = 0L
                context.contentResolver.openInputStream(src)?.use { ins ->
                    FileOutputStream(tmp).use { out ->
                        bytes = ins.copyTo(out, 64 * 1024)
                    }
                } ?: run {
                    tmp.delete()
                    return@withContext TransferResult(false, "No pude abrir el archivo seleccionado")
                }

                if (bytes < MIN_MODEL_BYTES) {
                    tmp.delete()
                    return@withContext TransferResult(
                        false,
                        "El archivo pesa ${bytes / 1024} KB: no es un archivo .cact válido"
                    )
                }

                val displayName = readDisplayName(context, src)
                val resolvedModelName: String = if (NeedleRuntime.isSupported()) {
                    val detectedKind = NeedleRuntime.probeModelKind(tmp)
                    if (detectedKind == 0) {
                        tmp.delete()
                        val detail = NeedleRuntime.lastErrorMessage?.let { " ($it)" }.orEmpty()
                        return@withContext TransferResult(
                            false,
                            "Archivo .cact no reconocido o incompatible con Cactus 4.1.4$detail"
                        )
                    }
                    when {
                        (detectedKind and NeedleRuntime.MODEL_VISION) != 0 -> NeedleModelManager.MIRAGE_FILE
                        (detectedKind and NeedleRuntime.MODEL_SPEECH) != 0 -> NeedleModelManager.WHISTLE_FILE
                        (detectedKind and NeedleRuntime.MODEL_TEXT) != 0 -> NeedleModelManager.NEEDLE_FILE
                        else -> {
                            tmp.delete()
                            return@withContext TransferResult(false, "Tipo de modelo desconocido ($detectedKind)")
                        }
                    }
                } else {
                    resolveModelFromHint(model, displayName) ?: run {
                        tmp.delete()
                        return@withContext TransferResult(
                            false,
                            "No se pudo identificar el tipo de modelo por el nombre ($displayName); usa el botón específico."
                        )
                    }
                }

                val target = when (resolvedModelName) {
                    NeedleModelManager.NEEDLE_FILE -> NeedleModelManager.needleFile(context)
                    NeedleModelManager.WHISTLE_FILE -> NeedleModelManager.whistleFile(context)
                    NeedleModelManager.MIRAGE_FILE -> NeedleModelManager.mirageFile(context)
                    else -> {
                        tmp.delete()
                        return@withContext TransferResult(false, "Modelo desconocido")
                    }
                }

                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@withContext TransferResult(false, "No pude guardar el modelo en almacenamiento interno")
                }

                // Enable the matching local feature automatically on offline import.
                when (resolvedModelName) {
                    NeedleModelManager.NEEDLE_FILE -> NeedleModelManager.setAssistantEnabled(context, true)
                    NeedleModelManager.WHISTLE_FILE -> NeedleModelManager.setWhistleDictationEnabled(context, true)
                    NeedleModelManager.MIRAGE_FILE -> NeedleModelManager.setMirageVisionEnabled(context, true)
                }

                // Reload all present models immediately (no internet or restart required).
                NeedleRuntime.reset()
                NeedleRuntime.loadFromDisk(context)

                val friendlyName = when (resolvedModelName) {
                    NeedleModelManager.NEEDLE_FILE -> "Needle 3"
                    NeedleModelManager.WHISTLE_FILE -> "Whistle"
                    NeedleModelManager.MIRAGE_FILE -> "Mirage"
                    else -> resolvedModelName
                }
                TransferResult(
                    true,
                    "$friendlyName (${target.name}) importado y listo (${NeedleModelManager.formatBytes(bytes)})"
                )
            } catch (t: Throwable) {
                tmp.delete()
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    internal fun resolveModelFromHint(explicitModel: String, displayName: String?): String? {
        if (explicitModel == NeedleModelManager.NEEDLE_FILE ||
            explicitModel == NeedleModelManager.WHISTLE_FILE ||
            explicitModel == NeedleModelManager.MIRAGE_FILE
        ) {
            return explicitModel
        }
        val lower = displayName?.lowercase().orEmpty()
        return when {
            "mirage" in lower -> NeedleModelManager.MIRAGE_FILE
            "whistle" in lower -> NeedleModelManager.WHISTLE_FILE
            "needle" in lower -> NeedleModelManager.NEEDLE_FILE
            else -> null
        }
    }

    private fun readDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    } catch (_: Throwable) {
        uri.lastPathSegment
    }

    // ------------------------------------------------------------------
    // Voice Match print
    // ------------------------------------------------------------------

    /** Exports the enrolled voice print as a small JSON file. */
    suspend fun exportVoicePrint(context: Context, dest: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val store = VoicePrintStore(context)
                if (!store.isEnrolled()) {
                    return@withContext TransferResult(false, "No hay huella de voz inscrita")
                }
                val prefs = context.getSharedPreferences("voice_print_prefs", Context.MODE_PRIVATE)
                val json = JSONObject()
                    .put("type", "mnemosyne_voiceprint_v1")
                    .put("centroid_b64", prefs.getString("centroid_b64", ""))
                    .put("sample_count", prefs.getInt("sample_count", 0))
                    .put("threshold", prefs.getString("threshold", "medium"))
                context.contentResolver.openOutputStream(dest, "w")?.use { out ->
                    out.write(json.toString().toByteArray(Charsets.UTF_8))
                } ?: return@withContext TransferResult(false, "No pude abrir el destino")
                TransferResult(true, "Huella de voz exportada")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    /** Imports a previously exported voice print. */
    suspend fun importVoicePrint(context: Context, src: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(src)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext TransferResult(false, "No pude abrir el archivo")

                val json = JSONObject(raw)
                if (json.optString("type") != "mnemosyne_voiceprint_v1") {
                    return@withContext TransferResult(false, "No es un archivo de huella de voz de esta app")
                }
                val centroid = json.optString("centroid_b64")
                if (centroid.isBlank()) {
                    return@withContext TransferResult(false, "El archivo no contiene huella")
                }
                context.getSharedPreferences("voice_print_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("centroid_b64", centroid)
                    .putInt("sample_count", json.optInt("sample_count", 1))
                    .putString("threshold", json.optString("threshold", "medium"))
                    .apply()
                TransferResult(true, "Huella de voz importada")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }
}
