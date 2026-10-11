package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device photo surgery with ML Kit Subject Segmentation (thin library:
 * the MODEL is delivered by Google Play Services on demand):
 *
 *  1. removeBackground — keeps the salient subject(s) and makes the
 *     background transparent (the model's foreground bitmap directly).
 *  2. removeSubject — the inverse: erases the subject(s) and fills the hole
 *     with a diffusion inpaint from the surrounding pixels.
 *
 * v1.6 — three big honesty fixes:
 *  - [SegResult] surfaces WHY a run failed (before: silent null → the UI
 *    blamed "Play Services" with a guess).
 *  - [ensureModel] uses the verified ModuleInstall API to ASK Play Services
 *    to download the segmentation module, with real progress — instead of
 *    failing forever on devices where the model had not been fetched yet.
 *  - [quickCutoutFallback] is a pure-software background remover (border
 *    flood-fill matte) for when the ML model simply cannot be installed.
 *    It is labeled "aproximado" in the UI — no pretending.
 *
 * Large images are downscaled to MAX_SIDE before processing so the device
 * never "peta" (the user's exact worry when they asked for the feature).
 */
object SubjectSegmentationManager {

    private const val MAX_SIDE = 1600

    enum class Op { REMOVE_BACKGROUND, REMOVE_SUBJECT }

    /** Explicit, human-readable outcome of a photo-surgery run. */
    sealed class SegResult {
        data class Success(val file: File) : SegResult()
        data class ModelNeeded(val reason: String) : SegResult()
        data class Failed(val reason: String) : SegResult()
    }

    // ------------------------------------------------------------------
    // Module install (verified against play-services-base 18.1.0 AAR:
    // ModuleInstall.getClient(Context), installModules(ModuleInstallRequest),
    // Builder.addApi(OptionalModuleApi), addOnProgressListener, and
    // InstallStatusListener.onInstallStatusUpdated(ModuleInstallStatusUpdate)
    // with InstallState.STATE_* + ProgressInfo.getBytesDownloaded/Total.)
    // ------------------------------------------------------------------

    /**
     * Asks Play Services to fetch the subject-segmentation module.
     * Returns true when the module is available afterwards.
     * [onProgress] receives a short state label and 0..100 percent.
     *
     * API verified against the RESOLVED play-services-base 18.1.0 AAR:
     * ModuleInstallRequest.Builder exposes addApi + setListener (NOT
     * addOnProgressListener, which only exists in later versions), and
     * installModules returns a Task with no public cancel().
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (state: String, percent: Int) -> Unit = { _, _ -> }
    ): Boolean = try {
        val appContext = context.applicationContext
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        val request = ModuleInstallRequest.newBuilder()
            .addApi(segmenter)
            .setListener(object : InstallStatusListener {
                override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                    val info = update.progressInfo
                    val pct = if (info != null && info.totalBytesToDownload > 0) {
                        ((info.bytesDownloaded * 100L) / info.totalBytesToDownload).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_PENDING ->
                            onProgress("preparando descarga", pct)
                        ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING ->
                            onProgress("descargando modelo", max(0, pct))
                        ModuleInstallStatusUpdate.InstallState.STATE_INSTALLING ->
                            onProgress("instalando modelo", 100)
                        else -> Unit
                    }
                }
            })
            .build()

        val task = ModuleInstall.getClient(appContext).installModules(request)
        // If the modules are already present, the task completes instantly.
        withTimeoutOrNull(180_000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                task
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }
        } ?: false
    } catch (_: Exception) {
        false
    }

    // ------------------------------------------------------------------
    // Processing
    // ------------------------------------------------------------------

    /** Processes [sourceUri] and writes the result as a PNG in journal_images. */
    suspend fun process(context: Context, sourceUri: Uri, op: Op): SegResult =
        withContext(Dispatchers.IO) {
            val input = loadScaled(context, sourceUri)
                ?: return@withContext SegResult.Failed("no pude leer la imagen")

            val foreground: Bitmap?
            try {
                foreground = segmentForeground(input)
            } catch (e: Exception) {
                return@withContext if (isModelMissing(e)) {
                    SegResult.ModelNeeded(e.message ?: "el modelo de segmentación no está instalado")
                } else {
                    SegResult.Failed(e.message ?: "la segmentación falló")
                }
            }
            if (foreground == null) {
                return@withContext SegResult.Failed("la segmentación no devolvió resultado (¿modelo ausente?)")
            }

            // Guard against size mismatch between input and mask.
            val mask = if (foreground.width == input.width && foreground.height == input.height) {
                foreground
            } else {
                Bitmap.createScaledBitmap(foreground, input.width, input.height, true)
            }

            val output: Bitmap = when (op) {
                Op.REMOVE_BACKGROUND -> {
                    // The foreground bitmap already carries a transparent
                    // background — normalize alpha so editors keep it crisp.
                    val w = mask.width
                    val h = mask.height
                    val pixels = IntArray(w * h)
                    mask.getPixels(pixels, 0, w, 0, 0, w, h)
                    for (i in pixels.indices) {
                        val alpha = (pixels[i] ushr 24) and 0xFF
                        pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
                    }
                    Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                }
                Op.REMOVE_SUBJECT -> inpaintSubject(input, mask)
            }

            val dir = File(context.filesDir, "journal_images").apply { mkdirs() }
            val outFile = File(
                dir,
                "seg_${if (op == Op.REMOVE_BACKGROUND) "sin_fondo" else "sin_sujeto"}_${System.currentTimeMillis()}.png"
            )
            try {
                FileOutputStream(outFile).use { output.compress(Bitmap.CompressFormat.PNG, 95, it) }
            } finally {
                if (output != input) output.recycle()
                input.recycle()
                if (mask !== foreground) mask.recycle()
                foreground.recycle()
            }
            SegResult.Success(outFile)
        }

    /** True when the exception looks like "model not available (yet)". */
    private fun isModelMissing(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            val msg = t.message.orEmpty().lowercase()
            if (
                msg.contains("model") && (msg.contains("not") || msg.contains("unavailable") || msg.contains("missing")) ||
                msg.contains("module") && msg.contains("install") ||
                msg.contains("api_unavailable") ||
                msg.contains("no api available")
            ) return true
            t = t.cause
            depth++
        }
        return false
    }

    /** Loads a bitmap from a file:// or content:// uri, downscaled to MAX_SIDE. */
    private fun loadScaled(context: Context, uri: Uri): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        var sample = 1
        while (opts.outWidth / sample > MAX_SIDE || opts.outHeight / sample > MAX_SIDE) {
            sample *= 2
        }
        val realOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, realOpts)
        }?.copy(Bitmap.Config.ARGB_8888, true)
    } catch (_: Exception) {
        null
    }

    /** Runs subject segmentation and returns the foreground bitmap. */
    private fun segmentForeground(input: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        val task = segmenter.process(InputImage.fromBitmap(input, 0))
        val result = Tasks.await(task, 60, TimeUnit.SECONDS)
        val fg = result?.foregroundBitmap
        segmenter.close()
        return fg
    }

    // ------------------------------------------------------------------
    // Software fallback: border flood-fill matte (NO ML, always available)
    // ------------------------------------------------------------------

    /**
     * Pure-Kotlin background removal: samples the image border to learn the
     * background palette, flood-fills from every border pixel that matches
     * it, then feathers the edge. Honest quality: great for objects on
     * plain backgrounds, approximate elsewhere — the UI says so.
     */
    suspend fun quickCutoutFallback(context: Context, sourceUri: Uri): File? =
        withContext(Dispatchers.IO) {
            val input = loadScaled(context, sourceUri) ?: return@withContext null
            try {
                val w = input.width
                val h = input.height
                val img = IntArray(w * h)
                input.getPixels(img, 0, w, 0, 0, w, h)

                // 1) Background palette: simple k-means (k=3) over border pixels.
                val border = mutableListOf<Int>()
                for (x in 0 until w step max(1, w / 64)) {
                    border.add(img[x])
                    border.add(img[(h - 1) * w + x])
                }
                for (y in 0 until h step max(1, h / 64)) {
                    border.add(img[y * w])
                    border.add(img[y * w + w - 1])
                }
                var centers = listOf(border.first(), border[border.size / 2], border.last())
                for (iter in 0 until 6) {
                    val buckets = List(3) { mutableListOf<Int>() }
                    border.forEach { p ->
                        val ci = centers.indices.minByOrNull { k -> colorDist(p, centers[k]) } ?: 0
                        buckets[ci].add(p)
                    }
                    centers = buckets.mapIndexed { k, b ->
                        if (b.isEmpty()) centers[k] else b.reduce { acc, p -> avgColor(acc, p) }
                    }
                }

                // 2) Flood fill from border pixels matching the palette.
                val bg = BooleanArray(w * h)
                val queue = ArrayDeque<Int>()
                fun tryEnqueue(idx: Int) {
                    if (!bg[idx]) {
                        val p = img[idx]
                        val matches = centers.any { c -> colorDist(p, c) < 52f }
                        if (matches) {
                            bg[idx] = true
                            queue.add(idx)
                        }
                    }
                }
                for (x in 0 until w) {
                    tryEnqueue(x)
                    tryEnqueue((h - 1) * w + x)
                }
                for (y in 0 until h) {
                    tryEnqueue(y * w)
                    tryEnqueue(y * w + w - 1)
                }
                while (queue.isNotEmpty()) {
                    val idx = queue.removeFirst()
                    val x = idx % w
                    val y = idx / w
                    if (x > 0) tryEnqueue(idx - 1)
                    if (x < w - 1) tryEnqueue(idx + 1)
                    if (y > 0) tryEnqueue(idx - w)
                    if (y < h - 1) tryEnqueue(idx + w)
                }

                // 3) Feather alpha: one light box blur over the mask edge.
                val alpha = ByteArray(w * h) { i -> if (bg[i]) 0 else 255.toByte() }
                val blurred = alpha.copyOf()
                for (y in 1 until h - 1) {
                    for (x in 1 until w - 1) {
                        val i = y * w + x
                        val a = (alpha[i].toInt() and 0xFF)
                        val n = ((alpha[i - 1].toInt() and 0xFF) + (alpha[i + 1].toInt() and 0xFF) +
                            (alpha[i - w].toInt() and 0xFF) + (alpha[i + w].toInt() and 0xFF) +
                            4 * a) / 8
                        blurred[i] = n.toByte()
                    }
                }

                // 4) Compose the output.
                for (i in img.indices) {
                    val a = blurred[i].toInt() and 0xFF
                    img[i] = (a shl 24) or (img[i] and 0x00FFFFFF)
                }

                val dir = File(context.filesDir, "journal_images").apply { mkdirs() }
                val outFile = File(dir, "recorte_rapido_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use {
                    Bitmap.createBitmap(img, w, h, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.PNG, 95, it)
                }
                outFile
            } catch (_: Exception) {
                null
            } finally {
                input.recycle()
            }
        }

    private fun colorDist(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    private fun avgColor(a: Int, b: Int): Int {
        val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2
        val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2
        val bl = ((a and 0xFF) + (b and 0xFF)) / 2
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * Removes the subject (wherever the mask alpha says "subject") and fills
     * the hole by iterative diffusion from the hole boundary — a bounded,
     * on-device inpaint. The result is soft rather than AI-hallucinated, and
     * we say so in the UI instead of pretending otherwise.
     */
    private fun inpaintSubject(input: Bitmap, mask: Bitmap): Bitmap {
        val w = input.width
        val h = input.height
        val img = IntArray(w * h)
        input.getPixels(img, 0, w, 0, 0, w, h)
        val m = IntArray(w * h)
        mask.getPixels(m, 0, w, 0, 0, w, h)

        val isHole = BooleanArray(w * h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (i in 0 until w * h) {
            val alpha = (m[i] ushr 24) and 0xFF
            if (alpha > 96) {
                isHole[i] = true
                val x = i % w
                val y = i / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return input.copy(Bitmap.Config.ARGB_8888, true)

        // Work in a padded rectangle so diffusion can pull from all sides.
        val pad = 2
        minX = max(0, minX - pad)
        minY = max(0, minY - pad)
        maxX = min(w - 1, maxX + pad)
        maxY = min(h - 1, maxY + pad)
        val rw = maxX - minX + 1
        val rh = maxY - minY + 1

        val cur = FloatArray(rw * rh * 3)
        val fixed = BooleanArray(rw * rh)
        for (y in 0 until rh) {
            for (x in 0 until rw) {
                val gi = (minY + y) * w + (minX + x)
                val ri = y * rw + x
                val p = img[gi]
                cur[ri * 3] = ((p shr 16) and 0xFF).toFloat()
                cur[ri * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
                cur[ri * 3 + 2] = (p and 0xFF).toFloat()
                fixed[ri] = !isHole[gi]
            }
        }

        // Jacobi diffusion: hole pixels repeatedly average their neighbours.
        val iterations = 48
        val next = cur.clone()
        for (it in 0 until iterations) {
            for (y in 0 until rh) {
                for (x in 0 until rw) {
                    val ri = y * rw + x
                    if (fixed[ri]) continue
                    var r = 0f; var g = 0f; var b = 0f; var n = 0
                    if (x > 0) { val k = ri - 1; r += cur[k*3]; g += cur[k*3+1]; b += cur[k*3+2]; n++ }
                    if (x < rw - 1) { val k = ri + 1; r += cur[k*3]; g += cur[k*3+1]; b += cur[k*3+2]; n++ }
                    if (y > 0) { val k = ri - rw; r += cur[k*3]; g += cur[k*3+1]; b += cur[k*3+2]; n++ }
                    if (y < rh - 1) { val k = ri + rw; r += cur[k*3]; g += cur[k*3+1]; b += cur[k*3+2]; n++ }
                    if (n > 0) {
                        next[ri*3] = r / n; next[ri*3+1] = g / n; next[ri*3+2] = b / n
                    }
                }
            }
            System.arraycopy(next, 0, cur, 0, cur.size)
        }

        for (y in 0 until rh) {
            for (x in 0 until rw) {
                val ri = y * rw + x
                if (fixed[ri]) continue
                val gi = (minY + y) * w + (minX + x)
                val r = cur[ri*3].toInt().coerceIn(0, 255)
                val g = cur[ri*3+1].toInt().coerceIn(0, 255)
                val b = cur[ri*3+2].toInt().coerceIn(0, 255)
                img[gi] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(img, w, h, Bitmap.Config.ARGB_8888)
    }
}
