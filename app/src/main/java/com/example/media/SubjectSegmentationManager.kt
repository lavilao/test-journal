package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * On-device photo surgery with ML Kit Subject Segmentation (via Play
 * Services, inference runs locally):
 *
 *  1. removeBackground — keeps the salient subject(s) and makes the
 *     background transparent (the model's foreground bitmap directly).
 *  2. removeSubject — the inverse: erases the subject(s) and fills the hole
 *     with a diffusion inpaint from the surrounding pixels, so family photos
 *     can lose a passer-by without a black scar.
 *
 * Large images are downscaled to MAX_SIDE before processing so the device
 * never "peta" (the user's exact worry when they asked for the feature).
 */
object SubjectSegmentationManager {

    private const val MAX_SIDE = 1600

    enum class Op { REMOVE_BACKGROUND, REMOVE_SUBJECT }

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
    private fun segmentForeground(input: Bitmap): Bitmap? = try {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        val task = segmenter.process(InputImage.fromBitmap(input, 0))
        val result = Tasks.await(task, 60, TimeUnit.SECONDS)
        val fg = result?.foregroundBitmap
        segmenter.close()
        fg
    } catch (_: Exception) {
        null
    }

    /** Processes [sourceUri] and writes the result as a PNG in journal_images. */
    suspend fun process(context: Context, sourceUri: Uri, op: Op): File? =
        withContext(Dispatchers.IO) {
            val input = loadScaled(context, sourceUri) ?: return@withContext null
            val foreground = segmentForeground(input) ?: return@withContext null
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
            FileOutputStream(outFile).use { output.compress(Bitmap.CompressFormat.PNG, 95, it) }
            if (output != input) output.recycle()
            input.recycle()
            if (mask !== foreground) mask.recycle()
            foreground.recycle()
            outFile
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
        minX = (minX - pad).coerceAtLeast(0)
        minY = (minY - pad).coerceAtLeast(0)
        maxX = (maxX + pad).coerceAtMost(w - 1)
        maxY = (maxY + pad).coerceAtMost(h - 1)
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
