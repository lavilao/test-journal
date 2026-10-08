package com.example.semantic

import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.NeedleTools
import com.example.data.model.EntryWithRelations
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.ln

/** One semantic hit, ready for the UI. */
data class SemanticHit(
    val entryId: Long,
    val title: String,
    val snippet: String,
    val score: Float,
    /** Why this result surfaced (explainable search). */
    val reason: String
)

data class SemanticSearchUiState(
    val query: String = "",
    val hits: List<SemanticHit> = emptyList(),
    /** True when Needle 3 (local AI) re-ranked the results. */
    val viaNeedle: Boolean = false,
    /** True when the Needle embeddings (local vectors) participated. */
    val viaEmbeddings: Boolean = false,
    val needleRanking: List<Long> = emptyList(),
    val latencyMs: Long = 0,
    val isSearching: Boolean = false
)

/**
 * On-device semantic search over the user's journal.
 *
 * Two stages, both 100% local:
 *  1. Lexical candidate scoring (accent-insensitive, Spanish-aware: the
 *     classic tokenizer ate accented words like «día» → «d a», so we
 *     NFD-normalize before tokenizing) with title/tag boosts.
 *  2. Optional Needle 3 re-ranking: the top candidates are shown to the
 *     model as numbered excerpts and it picks which ones truly answer the
 *     query (synonyms and intent included), best first.
 *
 * Without the model the search works exactly the same minus stage 2 — the
 * feature never depends on a download.
 */
object SemanticSearchEngine {

    private const val CANDIDATE_LIMIT = 12
    private const val NEEDLE_TIMEOUT_MS = 20_000L

    private val STOP = setOf(
        "de", "la", "el", "que", "y", "en", "un", "una", "es", "por", "con", "para", "del",
        "los", "las", "mi", "mis", "su", "al", "lo", "como", "mas", "pero", "sobre", "este",
        "esta", "todo", "todos", "hay", "fue", "era", "son", "the", "of", "and", "to", "in",
        "a", "o", "u", "e", "me", "te", "se", "no", "si", "ya", "cuando", "donde", "quien",
        "what", "where", "when", "which", "with", "about", "find", "search", "nota", "notas"
    )

    // ------------------------------------------------------------------
    // Text utilities (Spanish-aware)
    // ------------------------------------------------------------------

    private fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    private fun normalize(text: String): String =
        stripAccents(text.lowercase(Locale.ROOT))

    private fun tokenize(text: String): List<String> =
        normalize(text)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOP }

    // ------------------------------------------------------------------
    // Stage 1: lexical scoring
    // ------------------------------------------------------------------

    private data class ScoredEntry(
        val entry: EntryWithRelations,
        val score: Float,
        val reason: String
    )

    private fun scoreLexically(query: String, entries: List<EntryWithRelations>): List<ScoredEntry> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            // Fallback to a plain accent-insensitive contains match.
            val q = normalize(query.trim())
            if (q.isBlank()) return emptyList()
            return entries.mapNotNull { e ->
                val inTitle = normalize(e.entry.title).contains(q)
                val inBody = normalize(e.entry.body).contains(q)
                if (inTitle || inBody) {
                    ScoredEntry(
                        e,
                        if (inTitle) 3f else 1.5f,
                        if (inTitle) "título contiene «$q»" else "contenido contiene «$q»"
                    )
                } else null
            }.sortedByDescending { it.score }
        }

        val docTokens = entries.associate { e ->
            e.entry.id to (
                tokenize("${e.entry.title} ${e.entry.title} ${e.entry.body}") +
                    e.tags.flatMap { tokenize(it.name) }
                )
        }
        val totalDocs = max(1, entries.size)

        return entries.map { e ->
            val tokens = docTokens[e.entry.id] ?: emptyList()
            var score = 0f
            val matched = mutableListOf<String>()
            queryTokens.forEach { qt ->
                val tf = tokens.count { it == qt || (it.length > 4 && qt.length > 4 && (it.startsWith(qt) || qt.startsWith(it))) }
                if (tf > 0) {
                    val docsHaving = entries.count { other ->
                        (docTokens[other.entry.id] ?: emptyList()).any { it == qt }
                    }.coerceAtLeast(1)
                    val idf = ln(totalDocs.toDouble() / docsHaving) + 1.0
                    val isTag = e.tags.any { normalize(it.name).contains(qt) }
                    score += (tf * idf).toFloat() * if (isTag) 3f else 1f
                    if (matched.size < 3) matched.add(qt)
                }
            }
            // Full query in title: strong signal.
            val normTitle = normalize(e.entry.title)
            if (normalize(query.trim()).length >= 3 && normTitle.contains(normalize(query.trim()))) {
                score += 4f
            }
            ScoredEntry(e, score, if (matched.isEmpty()) "" else "coincide con: ${matched.joinToString()}")
        }.filter { it.score > 0f }
            .sortedByDescending { it.score }
    }

    // ------------------------------------------------------------------
    // Stage 2: Needle re-ranking
    // ------------------------------------------------------------------

    private fun buildSearchTurn(query: String, candidates: List<ScoredEntry>): String {
        val sb = StringBuilder()
        sb.append("Consulta de búsqueda del usuario: «").append(query.trim()).append("».\n")
        sb.append("Candidatos (id | título | extracto):\n")
        candidates.take(CANDIDATE_LIMIT).forEach { c ->
            val clean = c.entry.entry.body
                .replace(Regex("[#*_`>\\[\\]]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(140)
            sb.append(c.entry.entry.id)
                .append(" | ")
                .append(c.entry.entry.title.ifBlank { "(sin título)" }.take(60))
                .append(" | ")
                .append(clean)
                .append('\n')
        }
        sb.append("Llama a buscar_en_notas con consulta=«")
            .append(query.trim())
            .append("» e ids_relevantes= los ids que de VERDAD responden a la consulta ")
            .append("(sinónimos e intención cuentan), del más al menos relevante. ")
            .append("Si ninguno la responde, lista vacía.")
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Full semantic search, three local stages:
     *  1. Lexical candidate scoring (TF-IDF-like, Spanish-aware).
     *  1b. TRUE vector similarity with the Needle embeddings (when the model
     *      is downloaded): entries with zero lexical overlap still surface —
     *      that is the whole point of embeddings.
     *  2. Optional Needle 3 re-ranking of the merged candidates.
     *
     * Without the model the search works exactly as before minus 1b/2.
     */
    suspend fun search(
        query: String,
        entries: List<EntryWithRelations>,
        context: android.content.Context
    ): SemanticSearchUiState {
        val started = System.currentTimeMillis()
        if (query.isBlank() || entries.isEmpty()) {
            return SemanticSearchUiState(query = query)
        }

        val lexicalAll = scoreLexically(query, entries)
        val lexical = lexicalAll.take(CANDIDATE_LIMIT)

        // ---- Stage 1b: vector similarity with the Needle embeddings ----
        var viaEmbeddings = false
        val embedScores: List<Pair<Long, Float>> =
            if (NeedleEmbeddings.isAvailable(context) && NeedleModelManager.isAssistantEnabled(context)) {
                try {
                    NeedleEmbeddings.ensureIndexed(context, entries, maxNew = 6)
                    kotlinx.coroutines.withTimeoutOrNull(12_000) {
                        NeedleEmbeddings.search(context, query, entries, topK = CANDIDATE_LIMIT)
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        if (embedScores.isNotEmpty()) viaEmbeddings = true

        if (lexical.isEmpty() && !viaEmbeddings) {
            return SemanticSearchUiState(query = query, latencyMs = System.currentTimeMillis() - started)
        }

        // ---- Merge lexical + vector scores into one candidate list ----
        val maxLex = (lexicalAll.firstOrNull()?.score ?: 0f).coerceAtLeast(1f)
        val byEntry = entries.associateBy { it.entry.id }
        val candidates: List<ScoredEntry> = if (viaEmbeddings) {
            val merged = mutableListOf<ScoredEntry>()
            val seen = HashSet<Long>()
            embedScores.forEach { (id, cos) ->
                val e = byEntry[id] ?: return@forEach
                if (seen.add(id)) {
                    val lexScore = lexicalAll.firstOrNull { it.entry.entry.id == id }?.score ?: 0f
                    val combined = 0.55f * cos.coerceAtLeast(0f) + 0.45f * (lexScore / maxLex)
                    val hasLex = lexScore > 0f
                    merged.add(
                        ScoredEntry(
                            e, combined,
                            if (hasLex) "texto + semántica (${(cos * 100).toInt()}%)" else "semántica (${(cos * 100).toInt()}%)"
                        )
                    )
                }
            }
            lexical.forEach {
                if (seen.add(it.entry.entry.id)) {
                    merged.add(ScoredEntry(it.entry, 0.45f * (it.score / maxLex), it.reason))
                }
            }
            merged.sortedByDescending { it.score }.take(CANDIDATE_LIMIT)
        } else {
            lexical
        }

        // ---- Stage 2: Needle re-ranking of the merged candidates ----
        var viaNeedle = false
        var needleRanking: List<Long> = emptyList()

        val modelReady = NeedleRuntime.isReady() ||
            (NeedleModelManager.isNeedleDownloaded(context) &&
                NeedleRuntime.loadFromDisk(context) > 0 &&
                NeedleRuntime.isReady())
        if (modelReady && NeedleModelManager.isAssistantEnabled(context)) {
            try {
                val ranked = kotlinx.coroutines.withTimeoutOrNull(NEEDLE_TIMEOUT_MS) {
                    NeedleTools.rankNotes(buildSearchTurn(query, candidates))
                }
                if (ranked != null) {
                    viaNeedle = true
                    needleRanking = ranked
                }
            } catch (_: Exception) {
                // Model unavailable: lexical ranking is the answer.
            }
        }

        val byId = candidates.associateBy { it.entry.entry.id }
        val ordered = mutableListOf<ScoredEntry>()
        if (viaNeedle) {
            needleRanking.forEach { id -> byId[id]?.let { ordered.add(it) } }
            candidates.forEach { if (it !in ordered) ordered.add(it) }
        } else {
            ordered.addAll(candidates)
        }

        val hits = ordered.take(CANDIDATE_LIMIT).map { c ->
            SemanticHit(
                entryId = c.entry.entry.id,
                title = c.entry.entry.title.ifBlank { "(sin título)" },
                snippet = c.entry.entry.body
                    .replace(Regex("[#*_`>]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(120),
                score = c.score,
                reason = if (viaNeedle && c.entry.entry.id in needleRanking) {
                    if (viaEmbeddings) "IA local: responde a la intención (embeddings)" else "IA local: responde a la intención"
                } else {
                    c.reason.ifBlank { "coincidencia de texto" }
                }
            )
        }

        return SemanticSearchUiState(
            query = query,
            hits = hits,
            viaNeedle = viaNeedle,
            viaEmbeddings = viaEmbeddings,
            needleRanking = needleRanking,
            latencyMs = System.currentTimeMillis() - started
        )
    }
}
