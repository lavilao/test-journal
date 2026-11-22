package com.example.ai.needle

import android.content.Context
import com.example.location.SmartPlaces
import java.text.Normalizer
import java.util.Locale

/**
 * Builds focused, newline-separated biasing keyword lists for Whistle's
 * Aho-Corasick decoder (`needle.h` keywords parameter) and merges overlapping
 * chunk transcripts for recordings longer than 30 seconds.
 */
object WhistleKeywordBuilder {

    private const val MAX_KEYWORDS = 36
    private const val CACHE_TTL_MS = 30_000L

    private val TOOL_SPOKEN_KEYWORDS: Map<String, List<String>> = mapOf(
        "abrir_app" to listOf("abre", "abrir", "whatsapp", "youtube", "cámara", "calendario", "spotify"),
        "cerrar_app" to listOf("cierra", "cerrar"),
        "llamar_contacto" to listOf("llama a", "llamar a", "marca a"),
        "mandar_mensaje" to listOf("mensaje a", "manda un mensaje", "sms a"),
        "buscar_en_telefono" to listOf("busca en el teléfono", "dónde está"),
        "buscar_en_web" to listOf("busca en internet", "googlea"),
        "buscar_en_notas" to listOf("mis notas", "buscar en notas", "qué nota", "diario sobre"),
        "crear_nota" to listOf("apunta", "anota", "escribe", "escríbeme", "toma nota", "nueva nota", "guarda nota"),
        "crear_tarea" to listOf("recuérdame", "tarea", "apúntame", "tengo que"),
        "crear_evento" to listOf("evento", "calendario", "reunión", "cita"),
        "siguiente_evento" to listOf("próximo evento", "qué tengo hoy", "agenda de hoy"),
        "calcular" to listOf("cuánto es", "cuanto es", "calcula"),
        "crear_temporizador" to listOf("temporizador", "cronómetro", "pon un temporizador"),
        "crear_alarma" to listOf("alarma", "pon una alarma"),
        "linterna" to listOf("linterna", "enciende la linterna", "apaga la linterna"),
        "pasos_hoy" to listOf("pasos", "cuántos pasos"),
        "volumen" to listOf("volumen", "sube el volumen", "baja el volumen", "silencio", "vibración"),
        "grabar_audio" to listOf("nota de voz", "graba mi voz"),
        "abrir_escaner_lens" to listOf("escáner", "escanea", "lens"),
        "traducir_texto" to listOf("traduce", "traducir"),
        "guardar_lugar" to listOf("guarda este lugar")
    )

    @Volatile private var cachedAssistantAt: Long = 0L
    @Volatile private var cachedAssistantKeywords: String? = null

    fun invalidate() {
        cachedAssistantAt = 0L
        cachedAssistantKeywords = null
    }

    /** Keywords biased toward active assistant tools, custom tools, and saved places. */
    fun forAssistant(context: Context): String? {
        val now = System.currentTimeMillis()
        val existing = cachedAssistantKeywords
        if (existing != null && now - cachedAssistantAt < CACHE_TTL_MS) {
            return existing
        }

        val items = LinkedHashSet<String>()
        items.add("Mnemosyne")

        for ((toolId, spoken) in TOOL_SPOKEN_KEYWORDS) {
            if (NeedleTools.isToolEnabled(context, toolId)) {
                spoken.forEach { items.add(it) }
            }
        }

        try {
            CustomToolRegistry.snapshot().forEach { tool ->
                tool.label.trim().takeIf { it.length in 2..40 }?.let { items.add(it) }
            }
        } catch (_: Exception) {
        }

        try {
            SmartPlaces.places(context).forEach { place ->
                place.name.trim().takeIf { it.length in 2..40 }?.let { items.add(it) }
            }
        } catch (_: Exception) {
        }

        val built = formatKeywordList(items)
        cachedAssistantKeywords = built
        cachedAssistantAt = now
        return built
    }

    /** Keywords biased toward the active wake word plus assistant commands. */
    fun forHotword(context: Context, wakeWord: String): String? {
        val items = LinkedHashSet<String>()
        val cleanWake = wakeWord.trim()
        if (cleanWake.isNotBlank()) {
            items.add(cleanWake)
            cleanWake.split(Regex("\\s+")).forEach { part ->
                if (part.length >= 3) items.add(part)
            }
        }
        forAssistant(context)
            ?.split('\n')
            ?.forEach { items.add(it) }
        return formatKeywordList(items)
    }

    /** Keywords biased toward personal journal context, saved places, and optional entity names. */
    fun forJournal(context: Context, extraKeywords: List<String> = emptyList()): String? {
        val items = LinkedHashSet<String>()
        items.add("Mnemosyne")
        extraKeywords.forEach { kw ->
            val clean = kw.trim()
            if (clean.length in 2..40) items.add(clean)
        }
        try {
            SmartPlaces.places(context).forEach { place ->
                place.name.trim().takeIf { it.length in 2..40 }?.let { items.add(it) }
            }
        } catch (_: Exception) {
        }
        try {
            CustomToolRegistry.snapshot().forEach { tool ->
                tool.label.trim().takeIf { it.length in 2..40 }?.let { items.add(it) }
            }
        } catch (_: Exception) {
        }
        listOf("diario", "memoria", "recordatorio", "calendario").forEach { items.add(it) }
        return formatKeywordList(items)
    }

    /** Keywords biased toward Mirage visual OCR/naming (saved places + entry context). */
    fun forMirage(context: Context, extraKeywords: List<String> = emptyList()): String? {
        val items = LinkedHashSet<String>()
        items.add("Mnemosyne")
        extraKeywords.forEach { kw ->
            val clean = kw.trim()
            if (clean.length in 2..40) items.add(clean)
        }
        try {
            SmartPlaces.places(context).forEach { place ->
                place.name.trim().takeIf { it.length in 2..40 }?.let { items.add(it) }
            }
        } catch (_: Exception) {
        }
        return formatKeywordList(items)
    }

    internal fun formatKeywordList(candidates: Iterable<String>, limit: Int = MAX_KEYWORDS): String? {
        val seenLower = HashSet<String>()
        val result = ArrayList<String>(limit)
        for (raw in candidates) {
            val clean = raw.replace('\n', ' ').trim()
            if (clean.length < 2 || clean.length > 48) continue
            val key = clean.lowercase(Locale.ROOT)
            if (seenLower.add(key)) {
                result.add(clean)
                if (result.size >= limit) break
            }
        }
        return if (result.isEmpty()) null else result.joinToString("\n")
    }

    /**
     * Stitches consecutive overlapping transcription windows without repeating
     * words across the ~2 second chunk overlap boundary.
     */
    fun mergeChunkTranscripts(accumulated: String, nextChunk: String): String {
        val left = accumulated.trim()
        val right = nextChunk.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left

        val leftWords = left.split(Regex("\\s+"))
        val rightWords = right.split(Regex("\\s+"))
        val maxCheck = minOf(8, leftWords.size, rightWords.size)

        var overlapCount = 0
        for (k in maxCheck downTo 1) {
            var allMatch = true
            for (i in 0 until k) {
                val a = normalizeToken(leftWords[leftWords.size - k + i])
                val b = normalizeToken(rightWords[i])
                if (a.isEmpty() || a != b) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) {
                overlapCount = k
                break
            }
        }

        val tail = rightWords.drop(overlapCount).joinToString(" ")
        return if (tail.isEmpty()) left else "$left $tail"
    }

    private fun normalizeToken(token: String): String =
        Normalizer.normalize(token, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
