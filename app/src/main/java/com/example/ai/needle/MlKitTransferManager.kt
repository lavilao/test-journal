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
import kotlin.coroutines.resumeWithException

/**
 * Backup / restore for the DOWNLOADED ML Kit models (translate packs first:
 * they are the big ones, ~30 MB per language, and the user asked to carry
 * them between installs instead of re-downloading).
 *
 * v1.5.2 FIX — "imported fine but the app still demands internet downloads":
 * the old import dumped EVERY restored file into [Context.filesDir] even when
 * it had been exported from [Context.noBackupFilesDir] — which is where ML Kit
 * actually keeps its models on many devices. Files landed in the WRONG root,
 * so ML Kit never saw them and kept asking to re-download. Now:
 *  - every exported file records its origin root in the zip manifest
 *    (type "mnemosyne_mlkit_v2");
 *  - new zips restore each file to its ORIGINAL root;
 *  - OLD zips (no root info — like the backup made with v1.5.0/1.5.1) are
 *    restored into BOTH roots, so ML Kit finds its files wherever it looks;
 *  - after restoring, the languages ML Kit recognizes are re-checked and
 *    reported honestly in the result message.
 *
 * Models Google keeps in its own protected storage (GMS-side caches) are
 * simply not in the app's dirs and can't be exported — the UI reports
 * exactly what was found.
 */
object MlKitTransferManager {

    data class TransferResult(val ok: Boolean, val message: String)

    /** relPath + size + origin root ("files" | "nobackup"). */
    data class ModelFile(val relPath: String, val sizeBytes: Long, val root: String)

    /** Marker written into the zip so an import can identify our exports. */
    private const val MANIFEST_ENTRY = "manifest.json"

    private const val ROOT_FILES = "files"
    private const val ROOT_NOBACKUP = "nobackup"

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
        val roots = listOf(
            context.filesDir to ROOT_FILES,
            context.noBackupFilesDir to ROOT_NOBACKUP
        )
        for ((root, rootTag) in roots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (rel.isBlank()) return@forEach
                    val lower = rel.lowercase(Locale.ROOT)
                    if (lower.startsWith("needle/") || lower.startsWith("voice_notes/")) return@forEach
                    if (lower.endsWith(".tmp") || lower.endsWith(".tmp-")) return@forEach
                    if (f.length() <= 0L || f.length() > MAX_FILE_BYTES) return@forEach
                    val segments = lower.split('/')
                    val inModelDir = segments.dropLast(1)
                        .any { dir -> SUSPICIOUS_DIR_TOKENS.any { dir.contains(it) } }
                    val fileName = segments.lastOrNull() ?: ""
                    val fileNameLooksModelish = SUSPICIOUS_DIR_TOKENS.any { fileName.contains(it) }
                    val modelExt = MODEL_EXTENSIONS.any { lower.endsWith(it) }
                    if (inModelDir || modelExt || fileNameLooksModelish) {
                        out.add(ModelFile(rel, f.length(), rootTag))
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
                var written = 0
                app.contentResolver.openOutputStream(dest, "w")?.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        // Per-file origin root — THE fix: an import must put
                        // each file back where ML Kit expects to find it.
                        val rootsJson = JSONObject()
                        for (mf in files) rootsJson.put(mf.relPath, mf.root)
                        val manifest = JSONObject()
                            .put("type", "mnemosyne_mlkit_v2")
                            .put("device", Build.MODEL)
                            .put("exported_at", System.currentTimeMillis())
                            .put("files", files.size)
                            .put("roots", rootsJson)
                        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        for (mf in files) {
                            val source = sourceFor(app, mf) ?: continue
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
                var dual = 0
                var skipped = 0
                val rootMap = mutableMapOf<String, String>()
                app.contentResolver.openInputStream(src)?.use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            try {
                                if (!entry.isDirectory && name == MANIFEST_ENTRY) {
                                    // Our exports always write the manifest
                                    // FIRST, so it is parsed before any file.
                                    val text = zip.readBytes().toString(Charsets.UTF_8)
                                    runCatching {
                                        val roots = JSONObject(text).optJSONObject("roots")
                                        if (roots != null) {
                                            val it2 = roots.keys()
                                            while (it2.hasNext()) {
                                                val key = it2.next() as String
                                                rootMap[key] = roots.optString(key, ROOT_FILES)
                                            }
                                        }
                                    }
                                } else if (!entry.isDirectory && name.startsWith("files/")) {
                                    val rel = name.removePrefix("files/")
                                    // Path traversal guard: only plain relative paths.
                                    if (rel.contains("..") || rel.startsWith("/") || rel.isBlank()) {
                                        skipped++
                                    } else {
                                        when (rootMap[rel]) {
                                            ROOT_NOBACKUP -> {
                                                if (restoreTo(noBackupRoot, rel, zip)) restored++
                                                else skipped++
                                            }
                                            ROOT_FILES -> {
                                                if (restoreTo(filesRoot, rel, zip)) restored++
                                                else skipped++
                                            }
                                            else -> {
                                                // OLD-FORMAT zip (v1.5.0/1.5.1 export, no root
                                                // info): restore into BOTH roots so ML Kit finds
                                                // the files whichever dir it reads from.
                                                var ok = false
                                                if (restoreTo(filesRoot, rel, zip)) ok = true
                                                if (restoreTo(noBackupRoot, rel, zip)) ok = true
                                                if (ok) {
                                                    restored++
                                                    dual++
                                                } else skipped++
                                            }
                                        }
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
                    // Honest verification: what does ML Kit actually recognize now?
                    val recognized = try { installedTranslateLanguages() } catch (_: Exception) { emptyList() }
                    val langsText = if (recognized.isEmpty()) {
                        "ningún idioma aún (reinicia la app y vuelve a entrar para re-comprobar)"
                    } else {
                        recognized.joinToString(", ")
                    }
                    TransferResult(
                        true,
                        "Restaurados $restored archivos de modelos ML Kit" +
                                (if (dual > 0) " ($dual en ambas raíces — zip antiguo)" else "") +
                                ". Idiomas reconocidos: $langsText."
                    )
                }
            } catch (t: Throwable) {
                TransferResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Streams the current zip entry into root/relPath. */
    private fun restoreTo(root: File, rel: String, zip: ZipInputStream): Boolean = try {
        val target = File(root, rel)
        target.parentFile?.mkdirs()
        target.outputStream().use { zip.copyTo(it, 64 * 1024) }
        true
    } catch (_: Exception) {
        false
    }

    /** Where a discovered file lives, preferring its recorded root. */
    private fun sourceFor(context: Context, mf: ModelFile): File? {
        val primary = if (mf.root == ROOT_NOBACKUP) {
            File(context.noBackupFilesDir, mf.relPath)
        } else {
            File(context.filesDir, mf.relPath)
        }
        if (primary.exists()) return primary
        File(context.filesDir, mf.relPath).takeIf { it.exists() }?.let { return it }
        return File(context.noBackupFilesDir, mf.relPath).takeIf { it.exists() }
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
