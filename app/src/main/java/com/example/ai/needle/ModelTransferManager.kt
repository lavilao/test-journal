package com.example.ai.needle

import android.content.Context
import android.net.Uri
import com.example.speech.voiceprint.VoicePrintStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Backup / restore for the LOCAL models:
 *  - needle3.cact and whistle.cact (Cactus) — exported as plain files that
 *    can be re-imported on any device, so nothing depends on a server
 *    staying up or a URL remaining valid.
 *  - The voice print (Voice Match) — exported as a small JSON.
 *
 * All transfers go through the Storage Access Framework (user picks the
 * destination / source), nothing leaves the device, and imports are
 * validated (non-empty, sane minimum size) before replacing anything.
 */
object ModelTransferManager {

    data class TransferResult(val ok: Boolean, val message: String)

    private const val MIN_MODEL_BYTES = 1024L * 512 // 512 KB sanity floor

    // ------------------------------------------------------------------
    // Cactus model files
    // ------------------------------------------------------------------

    /** Copies a downloaded model to a user-picked SAF destination. */
    suspend fun exportModel(context: Context, model: String, dest: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val source: File = when (model) {
                    NeedleModelManager.NEEDLE_FILE -> NeedleModelManager.needleFile(context)
                    NeedleModelManager.WHISTLE_FILE -> NeedleModelManager.whistleFile(context)
                    else -> return@withContext TransferResult(false, "Modelo desconocido")
                }
                if (!source.exists()) {
                    return@withContext TransferResult(false, "El modelo no está descargado")
                }
                context.contentResolver.openOutputStream(dest, "w")?.use { out ->
                    source.inputStream().use { ins -> ins.copyTo(out, 64 * 1024) }
                } ?: return@withContext TransferResult(false, "No pude abrir el destino")
                TransferResult(true, "Exportados ${source.length() / (1024 * 1024)} MB de ${source.name}")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    /**
     * Imports a model from a user-picked SAF source. The bytes land in a
     * .tmp file first and are only promoted when they pass validation, so a
     * wrong or truncated file can never masquerade as a usable model.
     */
    suspend fun importModel(context: Context, model: String, src: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val target: File = when (model) {
                    NeedleModelManager.NEEDLE_FILE -> NeedleModelManager.needleFile(context)
                    NeedleModelManager.WHISTLE_FILE -> NeedleModelManager.whistleFile(context)
                    else -> return@withContext TransferResult(false, "Modelo desconocido")
                }
                val tmp = File(target.parentFile, "${target.name}.tmp")
                var bytes = 0L
                context.contentResolver.openInputStream(src)?.use { ins ->
                    FileOutputStream(tmp).use { out ->
                        bytes = ins.copyTo(out, 64 * 1024)
                    }
                } ?: run {
                    tmp.delete()
                    return@withContext TransferResult(false, "No pude abrir el archivo")
                }

                if (bytes < MIN_MODEL_BYTES) {
                    tmp.delete()
                    return@withContext TransferResult(
                        false,
                        "El archivo pesa ${bytes / 1024} KB: no parece un modelo válido"
                    )
                }

                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@withContext TransferResult(false, "No pude guardar el archivo importado")
                }

                // Reload so the model is immediately usable.
                NeedleRuntime.reset()
                NeedleRuntime.loadFromDisk(context)

                TransferResult(true, "${target.name} importado (${bytes / (1024 * 1024)} MB) y cargado")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
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
