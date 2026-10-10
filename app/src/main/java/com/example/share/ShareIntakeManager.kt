package com.example.share

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.model.AudioRecordItem
import com.example.data.model.JournalEntry
import com.example.repository.JournalRepository
import com.example.semantic.OcrIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Receives what the user SHARES to Mnemosyne from any other app — the
 * "No puedo compartir nada a Mnemosyne" fix. Handles:
 *
 *  - text/plain: shared text or links → note (title from EXTRA_SUBJECT or
 *    the first line; links are listed explicitly so they survive as links)
 *  - image/* (single or multiple): photos → journal entry with attachments,
 *    OCR-indexed when the toggle allows
 *  - audio/*: voice notes → entry with an audio record ready for the
 *    one-tap Whistle transcription
 *  - application/pdf: document → note; with document-OCR enabled the first
 *    pages are OCR'd so the PDF is searchable
 */
object ShareIntakeManager {

    /** Processes a share intent; returns a human summary or null when nothing handled. */
    suspend fun handle(context: Context, intent: Intent): String? = withContext(Dispatchers.IO) {
        when (intent.action) {
            Intent.ACTION_SEND -> handleSend(context, intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple(context, intent)
            else -> null
        }
    }

    private suspend fun handleSend(context: Context, intent: Intent): String? {
        val type = intent.type ?: return null
        return when {
            type.startsWith("image/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) ingestImages(context, listOf(uri)) else null
            }
            type.startsWith("audio/") || type.startsWith("video/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) ingestAudio(context, uri) else null
            }
            type == "application/pdf" -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) ingestPdf(context, uri) else null
            }
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) ingestText(context, text, intent.getStringExtra(Intent.EXTRA_SUBJECT)) else null
            }
            else -> null
        }
    }

    private suspend fun handleSendMultiple(context: Context, intent: Intent): String? {
        val type = intent.type ?: return null
        if (!type.startsWith("image/")) return null
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        if (uris.isEmpty()) return null
        return ingestImages(context, uris.take(20))
    }

    // ------------------------------------------------------------------
    // Ingesters
    // ------------------------------------------------------------------

    private suspend fun ingestText(context: Context, text: String, subject: String?): String {
        val repo = JournalRepository(context)
        val urls = Regex("https?://\\S+").findAll(text).map { it.value.trimEnd('.', ',') }.toList()
        val title = subject?.takeIf { it.isNotBlank() }
            ?: text.trim().substringBefore('\n').take(60).ifBlank {
                if (urls.isNotEmpty()) "Enlace compartido" else "Texto compartido"
            }
        val body = buildString {
            if (subject.isNullOrBlank()) append(text.trim())
            else {
                append("## ").append(subject.trim()).append("\n\n")
                append(text.trim())
            }
            if (urls.isNotEmpty()) {
                append("\n\nEnlaces:\n")
                urls.forEach { append("- ").append(it).append('\n') }
            }
        }
        return try {
            repo.saveEntry(
                JournalEntry(
                    title = title,
                    body = body,
                    journalDate = System.currentTimeMillis()
                ),
                manualTags = listOf("compartido")
            )
            if (urls.isNotEmpty()) "Enlace guardado en tu diario (${urls.size} url)."
            else "Texto guardado en tu diario."
        } catch (e: Exception) {
            "No pude guardar el texto compartido: ${e.message}"
        }
    }

    private suspend fun ingestImages(context: Context, uris: List<Uri>): String {
        val repo = JournalRepository(context)
        val title = if (uris.size == 1) "Foto compartida" else "Fotos compartidas (${uris.size})"
        return try {
            val entryId = repo.saveEntry(
                JournalEntry(
                    title = title,
                    body = "Compartido desde otra app el ${java.text.SimpleDateFormat(
                        "d 'de' MMMM 'a las' HH:mm", java.util.Locale.getDefault()
                    ).format(java.util.Date())}.",
                    journalDate = System.currentTimeMillis()
                ),
                manualTags = listOf("compartido", "foto")
            )
            var ok = 0
            uris.forEach { uri ->
                try {
                    repo.addPhotoToEntry(entryId, uri)
                    ok++
                } catch (_: Exception) {
                }
            }
            if (ok > 0) "$ok foto(s) guardadas en tu diario." else "No pude leer las fotos compartidas."
        } catch (e: Exception) {
            "No pude guardar las fotos: ${e.message}"
        }
    }

    private suspend fun ingestAudio(context: Context, uri: Uri): String {
        val repo = JournalRepository(context)
        return try {
            // Copy the shared audio into our own storage (content URIs from
            // other apps expire with the share grant).
            val ext = context.contentResolver.getType(uri)
                ?.substringAfter('/')
                ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                ?: "m4a"
            val dir = File(context.filesDir, "audio_recordings").apply { mkdirs() }
            val dest = File(dir, "shared_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return "No pude leer el audio compartido."

            val durationMs = readDurationMs(dest)
            val entryId = repo.saveEntry(
                JournalEntry(
                    title = "Audio compartido",
                    body = "Nota de voz compartida desde otra app. Toca «Transcribir» para pasarla a texto con la IA local.",
                    journalDate = System.currentTimeMillis()
                ),
                manualTags = listOf("compartido", "audio")
            )
            repo.attachAudioRecord(
                AudioRecordItem(
                    entryId = entryId,
                    title = "Audio compartido",
                    filePath = dest.absolutePath,
                    durationMs = durationMs,
                    transcriptionStatus = "PENDING"
                )
            )
            "Audio guardado en tu diario — listo para transcribir con un toque."
        } catch (e: Exception) {
            "No pude guardar el audio: ${e.message}"
        }
    }

    private suspend fun ingestPdf(context: Context, uri: Uri): String {
        val repo = JournalRepository(context)
        return try {
            val dir = File(context.filesDir, "shared_docs").apply { mkdirs() }
            val dest = File(dir, "doc_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return "No pude leer el documento compartido."

            var extracted = ""
            if (OcrIndexer.isDocOcrEnabled(context)) {
                extracted = OcrIndexer.extractPdfText(context, Uri.fromFile(dest), maxPages = 8)
            }

            val body = buildString {
                append("Documento compartido: `").append(dest.name).append("`\n\n")
                if (extracted.isNotBlank()) {
                    append("# Texto extraído (OCR local, primeras páginas)\n\n")
                    append(extracted.take(4000))
                } else {
                    append("Guardado en el almacenamiento de la app. Activa «OCR de documentos» ")
                    append("en Ajustes si quieres que el contenido sea buscable.")
                }
            }
            repo.saveEntry(
                JournalEntry(
                    title = "Documento: ${dest.name}",
                    body = body,
                    journalDate = System.currentTimeMillis()
                ),
                manualTags = listOf("compartido", "documento")
            )
            if (extracted.isNotBlank()) {
                "Documento guardado y con ${extracted.length} caracteres indexados por OCR."
            } else {
                "Documento guardado en tu diario."
            }
        } catch (e: Exception) {
            "No pude guardar el documento: ${e.message}"
        }
    }

    private fun readDurationMs(file: File): Long = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(file.absolutePath)
        val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        mmr.release()
        d
    } catch (_: Exception) {
        0L
    }
}
