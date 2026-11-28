package com.example.semantic

import com.example.data.model.EntryWithRelations
import java.util.Locale
import kotlin.math.min

/**
 * «Esto se conecta con…» for the desk capture panel (suggestion 2).
 *
 * Pure and local, with no database and no network: it scores the notes already
 * in memory with the same signals the journal uses for its relationship graph
 * — shared entities, shared tags and shared tokens (Jaccard) — through the
 * existing [MindForgerSemanticEngine] tokenizer and entity extractor.
 *
 * With an empty draft it seeds from the last note you wrote, so opening the
 * panel still proposes real connections instead of nothing.
 */
object DeskConnectionEngine {

    data class Connection(
        val entryId: Long,
        val title: String,
        val emoji: String,
        val reason: String,
        val score: Float,
        val dateMillis: Long
    )

    private const val MAX_CANDIDATES = 300
    private const val MIN_SCORE = 0.22f

    fun connectionsFor(
        draft: String,
        entries: List<EntryWithRelations>,
        limit: Int = 3
    ): List<Connection> {
        val wanted = limit.coerceIn(1, 4)
        val active = entries.filter { !it.entry.isArchived && it.entry.id > 0L }
        if (active.isEmpty()) return emptyList()

        // Empty or very short draft: connect to the latest note instead of
        // showing an empty card the moment the panel opens.
        val draftText = draft.trim()
        val seedItem = if (draftText.length >= 3) null else active.maxByOrNull { it.entry.updatedAt }
        val seedText = if (draftText.length >= 3) {
            draftText
        } else {
            seedItem?.let { "${it.entry.title}\n${it.entry.body}" }.orEmpty()
        }
        if (seedText.isBlank()) return emptyList()

        val seedId = seedItem?.entry?.id ?: -1L
        val seedTitle = seedText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.take(80).orEmpty()
        val seedEntities = MindForgerSemanticEngine.extractEntities(seedTitle, seedText)
        val seedEntityNames = seedEntities
            .map { it.canonicalName.lowercase(Locale.ROOT) }
            .toSet()
        val seedTokens = MindForgerSemanticEngine.tokenize(seedText).toSet()

        // Existing tags are few; a tag counts as sought when the draft
        // mentions its name, otherwise tag overlap can never match a draft.
        val normalizedDraft = seedText.lowercase(Locale.ROOT)
        val knownTags = active.asSequence()
            .flatMap { it.tags.asSequence() }
            .map { it.normalizedName.lowercase(Locale.ROOT) }
            .filter { it.length >= 3 }
            .toSet()
        val seedTags = knownTags.filter { normalizedDraft.contains(it) }.toSet()

        if (seedTokens.isEmpty() && seedEntityNames.isEmpty() && seedTags.isEmpty()) {
            return emptyList()
        }

        return active.asSequence()
            .filter { it.entry.id != seedId }
            .sortedByDescending { it.entry.updatedAt }
            .take(MAX_CANDIDATES)
            .mapNotNull { item ->
                val targetTokens = MindForgerSemanticEngine
                    .tokenize("${item.entry.title}\n${item.entry.body}")
                    .toSet()
                val sharedTokens = targetTokens.intersect(seedTokens)
                val unionSize = targetTokens.size + seedTokens.size - sharedTokens.size
                val jaccard = if (unionSize <= 0) 0f else sharedTokens.size.toFloat() / unionSize

                val targetEntityNames = item.entities
                    .map { it.canonicalName.lowercase(Locale.ROOT) }
                    .toSet()
                val sharedEntities = targetEntityNames.intersect(seedEntityNames)
                val sharedTags = item.tags
                    .map { it.normalizedName.lowercase(Locale.ROOT) }
                    .filter { it in seedTags }

                val entityScore = min(0.55f, sharedEntities.size * 0.35f)
                val tagScore = min(0.30f, sharedTags.size * 0.20f)
                val tokenScore = min(0.30f, jaccard * 0.9f)
                val score = entityScore + tagScore + tokenScore

                val hasSignal = sharedEntities.isNotEmpty() ||
                    sharedTags.isNotEmpty() ||
                    sharedTokens.size >= 2
                if (!hasSignal || score < MIN_SCORE) return@mapNotNull null

                Connection(
                    entryId = item.entry.id,
                    title = item.entry.title.ifBlank { "Sin título" },
                    emoji = item.entry.iconEmoji.ifBlank { "📄" },
                    reason = reasonFor(sharedEntities.toList(), sharedTags, sharedTokens),
                    score = score,
                    dateMillis = item.entry.journalDate
                )
            }
            .sortedByDescending { it.score }
            .take(wanted)
            .toList()
    }

    private fun reasonFor(
        sharedEntities: List<String>,
        sharedTags: List<String>,
        sharedTokens: Set<String>
    ): String = when {
        sharedEntities.isNotEmpty() -> "Sobre " + sharedEntities
            .take(2)
            .joinToString(", ") { it.replaceFirstChar { c -> c.uppercase(Locale.getDefault()) } }

        sharedTags.isNotEmpty() -> "Comparte " + sharedTags.take(2).joinToString(", ") { "#$it" }
        sharedTokens.isNotEmpty() -> "Temas en común: " + sharedTokens.take(3).joinToString(", ")
        else -> "Relacionado con lo que escribes"
    }
}
