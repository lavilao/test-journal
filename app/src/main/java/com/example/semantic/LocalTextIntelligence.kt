package com.example.semantic

import android.content.Context
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.NeedleTools
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Local article summary returned by [LocalTextIntelligence]. */
data class ArticleSummary(
    val bullets: List<String>,
    val readingMinutes: Int,
    val viaNeedle: Boolean = false
)

/** Actionable reminder/task extracted locally from a memory or voice transcript. */
data class ExtractedActionItem(
    val title: String,
    val dueOffsetMinutes: Int? = null,
    val source: String = "nota"
)

/**
 * Deterministic + optional Needle 3 on-device text intelligence:
 *  - Article summarization (Needle 3 when loaded + extractive TF/position fallback).
 *  - Action-item extraction from journal notes & voice transcripts.
 *  - Concise title suggestions for long untitled notes.
 *  - Pure-Kotlin JSON string array parsing safe on both Android and JVM tests.
 */
object LocalTextIntelligence {

    private val STOPWORDS = setOf(
        "de", "la", "el", "que", "y", "en", "un", "una", "es", "por", "con", "para", "del",
        "los", "las", "mi", "mis", "su", "sus", "al", "lo", "como", "mas", "pero", "sobre",
        "este", "esta", "esto", "todo", "todos", "hay", "fue", "era", "son", "ser", "esta",
        "entre", "desde", "hasta", "tambien", "muy", "sin", "nos", "the", "of", "and", "to",
        "in", "a", "an", "is", "are", "was", "were", "for", "with", "on", "at", "by", "from",
        "that", "this", "it", "as", "or", "be"
    )

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    private fun wordTokens(text: String): List<String> =
        normalize(text)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOPWORDS }

    fun estimateReadingMinutes(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        if (words <= 0) return 1
        return max(1, ceil(words / 180.0).toInt())
    }

    /**
     * Summarizes an article locally: tries Needle 3 when available & enabled,
     * and seamlessly falls back to [summarizeArticleExtractive].
     */
    suspend fun summarizeArticle(
        context: Context,
        title: String,
        body: String,
        maxBullets: Int = 3
    ): ArticleSummary {
        val readingMin = estimateReadingMinutes(body)
        val needleAllowed = NeedleRuntime.isSupported() &&
            NeedleModelManager.isAssistantEnabled(context) &&
            (NeedleRuntime.isReady() || NeedleModelManager.isNeedleDownloaded(context))

        if (needleAllowed) {
            if (!NeedleRuntime.isReady()) {
                try {
                    NeedleModelManager.ensureLoaded(context)
                } catch (_: Exception) {
                }
            }
            if (NeedleRuntime.isReady()) {
                val aiBullets = withTimeoutOrNull(18_000L) {
                    NeedleTools.summarizeArticle(title, body)
                }
                if (!aiBullets.isNullOrEmpty()) {
                    return ArticleSummary(
                        bullets = aiBullets.take(maxBullets),
                        readingMinutes = readingMin,
                        viaNeedle = true
                    )
                }
            }
        }
        return summarizeArticleExtractive(title, body, maxBullets)
    }

    /**
     * Pure-Kotlin extractive summary: scores sentences by non-stopword TF,
     * overlap with the article title, presence of concrete figures/dates,
     * and lead position, returning [maxBullets] non-redundant key points.
     */
    fun summarizeArticleExtractive(
        title: String,
        body: String,
        maxBullets: Int = 3
    ): ArticleSummary {
        val cleanBody = body
            .replace(Regex("\\s+"), " ")
            .trim()
        val readingMin = estimateReadingMinutes(cleanBody)
        if (cleanBody.isBlank()) {
            return ArticleSummary(emptyList(), readingMin, viaNeedle = false)
        }

        val rawSentences = cleanBody
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.length >= 28 }

        if (rawSentences.isEmpty()) {
            val clipped = cleanBody.take(200)
            return ArticleSummary(
                bullets = listOf(if (cleanBody.length > 200) "$clipped…" else clipped),
                readingMinutes = readingMin,
                viaNeedle = false
            )
        }
        if (rawSentences.size <= maxBullets) {
            return ArticleSummary(
                bullets = rawSentences.map { s -> if (s.length > 210) s.take(207) + "…" else s },
                readingMinutes = readingMin,
                viaNeedle = false
            )
        }

        val tf = HashMap<String, Int>()
        rawSentences.forEach { sentence ->
            wordTokens(sentence).forEach { w -> tf[w] = (tf[w] ?: 0) + 1 }
        }
        val maxTf = (tf.values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val titleTokens = wordTokens(title).toSet()

        data class ScoredSentence(val idx: Int, val text: String, val score: Float, val tokens: Set<String>)

        val scored = rawSentences.mapIndexed { idx, sentence ->
            val tokens = wordTokens(sentence)
            val uniqueTokens = tokens.toSet()
            var score = 0f
            uniqueTokens.forEach { w ->
                score += (tf[w] ?: 0) / maxTf
            }
            if (uniqueTokens.isNotEmpty()) {
                score /= kotlin.math.sqrt(uniqueTokens.size.toFloat())
            }
            val titleOverlap = if (titleTokens.isEmpty()) 0 else uniqueTokens.intersect(titleTokens).size
            score += titleOverlap * 0.65f
            if (idx == 0) score += 0.45f
            if (idx == rawSentences.lastIndex) score += 0.15f
            if (Regex("\\b\\d{1,4}([.,/%€$]|\\b)").containsMatchIn(sentence)) score += 0.25f
            if (sentence.length > 240) score *= 0.85f
            ScoredSentence(idx, sentence, score, uniqueTokens)
        }.sortedByDescending { it.score }

        val picked = mutableListOf<ScoredSentence>()
        for (candidate in scored) {
            if (picked.size >= maxBullets) break
            val tooSimilar = picked.any { existing ->
                val union = existing.tokens.union(candidate.tokens).size
                if (union == 0) false
                else existing.tokens.intersect(candidate.tokens).size.toFloat() / union >= 0.55f
            }
            if (!tooSimilar) picked.add(candidate)
        }

        val bullets = picked
            .sortedBy { it.idx }
            .map { s ->
                val t = s.text.trim()
                if (t.length > 210) t.take(207).trimEnd() + "…" else t
            }

        return ArticleSummary(bullets = bullets, readingMinutes = readingMin, viaNeedle = false)
    }

    /**
     * Detects actionable to-do items in note markdown (`- [ ] ...`) or natural Spanish/English prose
     * and voice transcripts (`"tengo que..."`, `"pendiente: ..."`, `"recordar ..."`, `"llamar a ..."`).
     */
    fun extractActionItems(
        title: String,
        body: String,
        transcripts: List<String> = emptyList(),
        maxItems: Int = 5
    ): List<ExtractedActionItem> {
        val candidates = mutableListOf<Pair<String, String>>()
        body.lines().forEach { line -> candidates.add(line to "nota") }
        transcripts.forEach { transcript ->
            transcript.split(Regex("[.\\n!?]+")).forEach { seg ->
                candidates.add(seg to "voz")
            }
        }
        if (candidates.isEmpty() && title.isNotBlank()) {
            candidates.add(title to "nota")
        }

        val todoCheckbox = Regex("^\\s*[-*]\\s*\\[\\s]\\s*(.+)$")
        val explicitTag = Regex(
            "^\\s*(?:TODO|TO-DO|PENDIENTE|TAREA|ACCIÓN|ACCION|RECORDATORIO)\\s*[:\\-]\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
        val phrasePattern = Regex(
            "\\b(?:tengo que|hay que|debo|necesito|acordarme de|recuerdame|recuérdame|no olvidar(?:me de)?|pendiente de|remember to|need to|must)\\s+([^.;!?\\n]{4,90})",
            RegexOption.IGNORE_CASE
        )
        val imperativeListLine = Regex(
            "^\\s*[-*•]?\\s*((?:llamar|comprar|enviar|mandar|pagar|revisar|reservar|pedir|escribir|terminar|recoger|confirmar|buscar|preparar|agendar|call|buy|send|pay|check)\\b[^.\\n!?]{3,80})",
            RegexOption.IGNORE_CASE
        )

        val out = mutableListOf<ExtractedActionItem>()
        val seenKeys = HashSet<String>()

        fun addCandidate(rawText: String, source: String) {
            if (out.size >= maxItems) return
            val cleaned = rawText
                .trim()
                .trimStart('-', '*', '•', '[', ']', ' ')
                .trimEnd('.', ',', ';', ' ')
                .replace(Regex("\\s+"), " ")
            if (cleaned.length < 5 || cleaned.length > 95) return
            val formatted = cleaned.replaceFirstChar { it.uppercase() }
            val key = normalize(formatted)
            if (seenKeys.add(key)) {
                out.add(
                    ExtractedActionItem(
                        title = formatted,
                        dueOffsetMinutes = inferDueOffsetMinutes(formatted),
                        source = source
                    )
                )
            }
        }

        for ((rawLine, source) in candidates) {
            if (out.size >= maxItems) break
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            // Skip already-checked items: - [x]
            if (Regex("^\\s*[-*]\\s*\\[[xX]]").containsMatchIn(trimmed)) continue

            todoCheckbox.find(trimmed)?.let {
                addCandidate(it.groupValues[1], source)
                continue
            }
            explicitTag.find(trimmed)?.let {
                addCandidate(it.groupValues[1], source)
                continue
            }
            val phraseMatches = phrasePattern.findAll(trimmed).toList()
            if (phraseMatches.isNotEmpty()) {
                phraseMatches.forEach { m -> addCandidate(m.groupValues[1], source) }
                continue
            }
            imperativeListLine.find(trimmed)?.let {
                addCandidate(it.groupValues[1], source)
            }
        }

        return out
    }

    private fun inferDueOffsetMinutes(text: String): Int? {
        val norm = normalize(text)
        Regex("\\ben\\s+(\\d{1,3})\\s*(hora|horas|h)\\b").find(norm)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            if (h != null && h in 1..48) return h * 60
        }
        Regex("\\ben\\s+(\\d{1,3})\\s*(minuto|minutos|min)\\b").find(norm)?.let { m ->
            val mins = m.groupValues[1].toIntOrNull()
            if (mins != null && mins in 1..1440) return mins
        }
        return when {
            norm.contains("manana") -> 24 * 60
            norm.contains("esta tarde") || norm.contains("esta noche") -> 4 * 60
            norm.contains("hoy") -> 2 * 60
            else -> null
        }
    }

    /** Suggests a concise, natural title from the first meaningful sentence/line of [body]. */
    fun suggestTitleIfBlank(body: String): String {
        val firstLine = body.lineSequence()
            .map { it.replace(Regex("^[#*>`\\-\\s]+"), "").trim() }
            .firstOrNull { it.length >= 12 }
            ?: return ""
        val firstClause = firstLine
            .split(Regex("[.!?\\n]"))
            .firstOrNull { it.trim().length >= 8 }
            ?.trim()
            ?: firstLine
        val cleaned = firstClause.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= 44) return cleaned.replaceFirstChar { it.uppercase() }
        val cut = cleaned.take(44).substringBeforeLast(' ').ifBlank { cleaned.take(42) }
        return cut.trimEnd(',', ';', ':') + "…"
    }

    /** Parses simple JSON string arrays (`["a","b"]`) in pure Kotlin without requiring `android.org.json`. */
    fun parseJsonStringList(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "[]") return emptyList()
        if (!trimmed.startsWith("[")) {
            return trimmed.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }
        val matches = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(trimmed)
        return matches
            .map { it.groupValues[1].replace("\\\"", "\"").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
}
