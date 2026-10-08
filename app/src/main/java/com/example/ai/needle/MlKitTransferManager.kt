package com.example.ai.needle

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

/**
 * Backup / restore for the DOWNLOADED ML Kit models (translate packs first:
 * they are the big ones, ~30 MB per language, and the user asked to carry
 * them between installs instead of re-downloading).
 *
 * HOW IT WORKS, HONESTLY: ML Kit stores downloaded models inside the app's
 * private storage (files dir). We scan that storage for model-looking files
 * (translate/vision/digital-ink folders, .tflite/.task blobs), zip them
 * with a manifest, and restore them in place. Models that Google keeps in
 * its own protected storage (some GMS-side caches) are simply not in the
 * app's dir and can't be exported — the UI reports exactly what was found.
 */
object MlKitTransferManager {

    data class TransferResult(val ok: Boolean, val message: String)

    data class ModelFile(val relPath: String, val sizeBytes: Long)

    /** Marker written into the zip so an import can identify our exports. */
    private const val MANIFEST_ENTRY = "manifest.json"

    private val SUSPICIOUS_DIR_TOKENS = listOf(
        "mlkit", "translate", "vision", "digitalink", "digital_ink",
        "language", "smartreply", "entity", "nl"
    )

    private val MODEL_EXTENSIONS = listOf(".tflite", ".task", ".lite", ".model")

    /** Max size of one file we are willing to zip (defensive). */
    private const val MAX_FILE_BYTES = 400L * 1024 * 1024

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    /**
     * Finds the model files in the app's private storage. Never touches the
     * Cactus models (they have their own export) nor user media.
     */
    fun discover(context: Context): List<ModelFile> {
        val out = mutableListOf<ModelFile>()
        val roots = listOfNotNull(
            context.filesDir,
            context.noBackupFilesDir
        )
        for (root in roots) {
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (rel.isBlank()) return@forEach
                    val lower = rel.lowercase(Locale.ROOT)
                    if (lower.startsWith("needle/") || lower.startsWith("voice_notes/")) return@forEach
                    if (lower.endsWith(".tmp") || lower.endsWith(".tmp-")) return@forEach
                    if (f.length() <= 0L || f.length() > MAX_FILE_BYTES) return@forEach
                    val inModelDir = lower.split('/').dropLast(1)
                        .any { dir -> SUSPICIOUS_DIR_TOKENS.any { dir.contains(it) } }
                    val modelExt = MODEL_EXTENSIONS.any { lower.endsWith(it) }
                    if (inModelDir || modelExt) {
                        out.add(ModelFile(rel, f.length()))
                    }
                }
        }
        return out
    }

    /** Language codes of the translate models ML Kit reports as installed. */
    suspend fun installedTranslateLanguages(): List<String> = try {
        val task = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
        val models = task.await()
        models.map { it.language }.sorted()
    } catch (_: Exception) {
        emptyList()
    }

    // ------------------------------------------------------------------
    // Export / import
    // ------------------------------------------------------------------

    suspend fun exportAll(context: Context, dest: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                val files = discover(app)
                if (files.isEmpty()) {
                    return@withContext TransferResult(
                        false,
                        "No encontré modelos descargables en el almacenamiento de la app " +
                                "(quizá Google los guarda fuera del alcance de la app en este dispositivo)."
                    )
                }
                val filesRoot = app.filesDir
                val noBackupRoot = app.noBackupFilesDir
                var written = 0
                app.contentResolver.openOutputStream(dest, "w")?.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        val manifest = JSONObject()
                            .put("type", "mnemosyne_mlkit_v1")
                            .put("device", Build.MODEL)
                            .put("exported_at", System.currentTimeMillis())
                            .put("files", files.size)
                        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        for (mf in files) {
                            val source = resolve(app, mf.relPath, filesRoot, noBackupRoot) ?: continue
                            zip.putNextEntry(ZipEntry("files/${mf.relPath}"))
                            source.inputStream().use { it.copyTo(zip, 64 * 1024) }
                            zip.closeEntry()
                            written++
                        }
                    }
                } ?: return@withContext TransferResult(false, "No pude abrir el destino")
                TransferResult(true, "Exportados $written archivos de modelos ML Kit " +
                        "(${formatBytes(files.sumOf { it.sizeBytes })}).")
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    suspend fun importAll(context: Context, src: Uri): TransferResult =
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                val filesRoot = app.filesDir
                val noBackupRoot = app.noBackupFilesDir
                var restored = 0
                var skipped = 0
                app.contentResolver.openInputStream(src)?.use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            try {
                                if (!entry.isDirectory && name.startsWith("files/") && name != MANIFEST_ENTRY) {
                                    val rel = name.removePrefix("files/")
                                    // Path traversal guard: only plain relative paths.
                                    if (rel.contains("..") || rel.startsWith("/") || rel.isBlank()) {
                                        skipped++
                                    } else {
                                        val target = File(filesRoot, rel)
                                        target.parentFile?.mkdirs()
                                        target.outputStream().use { zip.copyTo(it, 64 * 1024) }
                                        restored++
                                    }
                                } else if (!entry.isDirectory) {
                                    skipped++
                                }
                            } catch (_: Exception) {
                                skipped++
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: return@withContext TransferResult(false, "No pude abrir el archivo")

                if (restored == 0) {
                    TransferResult(false, "El zip no contenía modelos restaurables ($skipped ignorados).")
                } else {
                    TransferResult(
                        true,
                        "Restaurados $restored archivos de modelos ML Kit. Reinicia la app " +
                                "para que ML Kit los vuelva a leer."
                    )
                }
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun resolve(
        context: Context,
        relPath: String,
        filesRoot: File,
        noBackupRoot: File
    ): File? {
        val inFiles = File(filesRoot, relPath)
        if (inFiles.exists()) return inFiles
        val inNoBackup = File(noBackupRoot, relPath)
        if (inNoBackup.exists()) return inNoBackup
        return null
    }

    fun formatBytes(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

    /** Task → suspend (ML Kit APIs return gms Tasks). */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
            addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
}
