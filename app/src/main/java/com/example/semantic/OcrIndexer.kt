package com.example.semantic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.AppDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * OCR settings + on-demand indexing, with the toggle the user asked for
 * ("interruptor, pues puede petar el dispositivo"):
 *
 *  - IMAGE OCR (default ON): the existing vision pass on attached photos.
 *  - DOCUMENT OCR (default OFF, heavier): renders PDF pages via
 *    PdfRenderer and OCRs each page so shared documents become searchable.
 *  - backfill: one-by-one indexing of photos that never got OCR text, so
 *    old images join the search too — bounded and cancellable.
 */
object OcrIndexer {

    private const val PREFS = "search_prefs"
    private const val KEY_IMAGE_OCR = "ocr_images_enabled"
    private const val KEY_DOC_OCR = "ocr_docs_enabled"

    // ------------------------------------------------------------------
    // Toggles
    // ------------------------------------------------------------------

    fun isImageOcrEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IMAGE_OCR, true)

    fun setImageOcrEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IMAGE_OCR, enabled).apply()
    }

    fun isDocOcrEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOC_OCR, false)

    fun setDocOcrEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOC_OCR, enabled).apply()
    }

    // ------------------------------------------------------------------
    // Text extraction
    // ------------------------------------------------------------------

    /** OCR of a single bitmap (Latin script, on-device). */
    suspend fun ocrBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                30, TimeUnit.SECONDS
            )
            recognizer.close()
            result.text.trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Extracts text from a PDF by rendering pages to bitmaps and OCRing each
     * one — fully on-device, capped at [maxPages] so a 200-page book cannot
     * "petar" anything.
     */
    suspend fun extractPdfText(context: Context, uri: Uri, maxPages: Int = 8): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext ""
                PdfRendererCompat.use(pfd) { renderer ->
                    val pages = minOf(renderer.pageCount, maxPages)
                    for (i in 0 until pages) {
                        if (!isActive) break
                        val page = renderer.openPage(i)
                        try {
                            val targetW = 1080
                            val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                            val w = (page.width * scale).toInt().coerceAtLeast(320)
                            val h = (page.height * scale).toInt().coerceAtLeast(320)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null,
                                android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = ocrBitmap(bmp)
                            bmp.recycle()
                            if (text.isNotBlank()) {
                                if (sb.isNotBlank()) sb.append('\n')
                                sb.append(text)
                            }
                        } finally {
                            try { page.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { pfd?.close() } catch (_: Exception) {}
            }
            sb.toString().trim()
        }

    // ------------------------------------------------------------------
    // Backfill of already-saved images
    // ------------------------------------------------------------------

    /**
     * OCRs saved photos that have no OCR text yet (skip when image OCR is
     * off). Runs ONE at a time; [maxImages] bounds the work per invocation.
     * Returns how many images were indexed.
     */
    suspend fun backfillImageOcr(context: Context, maxImages: Int = 40): Int =
        withContext(Dispatchers.IO) {
            if (!isImageOcrEnabled(context)) return@withContext 0
            val dao = AppDatabase.getInstance(context).journalDao()
            val pending = try {
                dao.getAllMediaSnapshot().filter { it.ocrText.isBlank() }
            } catch (_: Exception) {
                emptyList()
            }
            var done = 0
            for (media in pending) {
                if (done >= maxImages) break
                if (!isActive) break
                try {
                    val bmp = decodeBitmapSafely(context, Uri.parse(media.uri)) ?: continue
                    val text = ocrBitmap(bmp)
                    bmp.recycle()
                    if (text.isNotBlank()) {
                        dao.getMediaById(media.id)?.let { fresh ->
                            dao.updateMediaItem(fresh.copy(ocrText = text))
                        }
                        done++
                    } else {
                        // Mark as seen with an empty-but-done sentinel so we
                        // do not rescan the same blank image every time.
                        dao.getMediaById(media.id)?.let { fresh ->
                            dao.updateMediaItem(fresh.copy(ocrText = " "))
                        }
                    }
                } catch (_: Exception) {
                }
            }
            done
        }

    private fun decodeBitmapSafely(context: Context, uri: Uri): Bitmap? = try {
        // file:// URIs are the norm for our own storage; content:// works too.
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (_: Exception) {
        null
    }
}

/** Small compatibility shim so PdfRenderer usage stays tidy. */
private object PdfRendererCompat {
    inline fun use(pfd: ParcelFileDescriptor, block: (android.graphics.pdf.PdfRenderer) -> Unit) {
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            block(renderer)
        } finally {
            try { renderer.close() } catch (_: Exception) {}
        }
    }
}
