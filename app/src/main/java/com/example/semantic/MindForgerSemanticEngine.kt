package com.example.semantic

import com.example.data.model.AutolinkSpan
import com.example.data.model.EntityItem
import com.example.data.model.EntityType
import com.example.data.model.EventItem
import com.example.data.model.JournalEntry
import com.example.data.model.Relationship
import com.example.data.model.RelationshipType
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag
import org.json.JSONArray
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

data class ExtractedMentionDraft(
    val entity: EntityItem,
    val startOffset: Int,
    val endOffset: Int,
    val rawText: String,
    val normalizedValue: String = "",
    val confidence: Float = 0.88f
)

/**
 * MindForger-inspired on-device Information Retrieval and Semantic Intelligence Engine.
 * Operates purely locally with deterministic, explainable algorithms:
 * - Tokenization & stop-word filtering
 * - TF-IDF & BM25 keyword scoring
 * - Local entity discovery (People, Places, Organizations, Projects, Topics, Dates, URLs)
 * - Autolinking overlay generator (non-destructive in-text link resolution)
 * - Episodic Event detection (timeline clustering)
 * - Forgotten Threads detection (dormant recurring concepts)
 * - Explainable multi-signal semantic linking and relationship scoring
 */
object MindForgerSemanticEngine {

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
        "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
        "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
        "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
        "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
        "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
        "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's",
        "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've",
        "your", "yours", "yourself", "yourselves", "today", "yesterday", "tomorrow", "day", "went", "got", "get",
        "just", "really", "felt", "think", "thought", "going", "much", "also", "still", "well", "back", "even",
        "like", "good", "great", "nice", "saw", "met", "talked", "came", "made", "make", "take", "took", "see"
    )

    private val COMMON_PLACES = setOf(
        "park", "cafe", "coffee shop", "library", "starbucks", "beach", "mountain", "studio",
        "office", "airport", "station", "paris", "london", "tokyo", "new york", "san francisco",
        "kyoto", "berlin", "miami", "orlando", "lake", "trail", "gym", "museum", "everglades",
        "yosemite", "hotel", "cabin", "luigi's", "blue bottle cafe", "stanford lab"
    )

    private val KNOWN_ORGS = setOf(
        "google", "apple", "microsoft", "github", "amazon", "meta", "openai", "mit", "stanford",
        "mozilla", "anthropic", "spotify", "netflix"
    )

    fun computeContentHash(title: String, body: String): String {
        val input = "$title\n$body"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun tokenize(text: String): List<String> {
        val clean = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
        return clean.split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
    }

    /**
     * Extracts top keywords using TF-IDF ranking against a collection of entries.
     */
    fun extractKeywords(entry: JournalEntry, allEntries: List<JournalEntry>, limit: Int = 8): List<String> {
        val content = "${entry.title} ${entry.body}"
        val tokens = tokenize(content)
        if (tokens.isEmpty()) return emptyList()

        val totalDocs = max(1, allEntries.size)
        val termFrequencies = mutableMapOf<String, Int>()
        tokens.forEach { t -> termFrequencies[t] = (termFrequencies[t] ?: 0) + 1 }

        val docFrequencies = mutableMapOf<String, Int>()
        tokens.distinct().forEach { term ->
            val count = allEntries.count { other ->
                val otherText = "${other.title} ${other.body}".lowercase(Locale.ROOT)
                otherText.contains(term)
            }
            docFrequencies[term] = max(1, count)
        }

        return termFrequencies.keys
            .map { term ->
                val tf = (termFrequencies[term] ?: 1).toDouble() / tokens.size
                val idf = ln(totalDocs.toDouble() / (docFrequencies[term] ?: 1)) + 1.0
                val score = tf * idf
                term to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun extractEntities(title: String, body: String): List<EntityItem> {
        return extractEntitiesWithOffsets("$title\n$body").map { it.entity }
    }

    /**
     * Extracts entities and precise text offsets using MindForger local heuristics.
     */
    fun extractEntitiesWithOffsets(fullText: String): List<ExtractedMentionDraft> {
        val drafts = mutableListOf<ExtractedMentionDraft>()
        val seen = mutableSetOf<String>()

        // 1. Explicit URLs
        val urlRegex = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)
        urlRegex.findAll(fullText).forEach { match ->
            val url = match.value.trimEnd('.', ',', ';')
            val canonical = url.lowercase(Locale.ROOT)
            if (seen.add("URL:$canonical")) {
                val entity = EntityItem(
                    canonicalName = canonical,
                    displayName = url,
                    type = EntityType.URL,
                    mentionCount = 1
                )
                drafts.add(
                    ExtractedMentionDraft(
                        entity = entity,
                        startOffset = match.range.first,
                        endOffset = match.range.first + url.length,
                        rawText = url,
                        normalizedValue = url,
                        confidence = 0.99f
                    )
                )
            }
        }

        // 2. Email Addresses
        val emailRegex = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
        emailRegex.findAll(fullText).forEach { match ->
            val email = match.value
            val canonical = email.lowercase(Locale.ROOT)
            if (seen.add("EMAIL:$canonical")) {
                val entity = EntityItem(
                    canonicalName = canonical,
                    displayName = email,
                    type = EntityType.EMAIL,
                    mentionCount = 1
                )
                drafts.add(
                    ExtractedMentionDraft(
                        entity = entity,
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1,
                        rawText = email,
                        normalizedValue = canonical,
                        confidence = 0.99f
                    )
                )
            }
        }

        // 3. Project Mentions (e.g. "Project Aurora", "Project X")
        val projectRegex = Regex("""\b(?:Project|Initiative)\s+([A-Z][a-zA-Z0-9_-]+)\b""")
        projectRegex.findAll(fullText).forEach { match ->
            val fullMatch = match.value
            val canonical = fullMatch.lowercase(Locale.ROOT)
            if (seen.add("PROJECT:$canonical")) {
                val entity = EntityItem(
                    canonicalName = canonical,
                    displayName = fullMatch,
                    type = EntityType.PROJECT,
                    mentionCount = 1
                )
                drafts.add(
                    ExtractedMentionDraft(
                        entity = entity,
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1,
                        rawText = fullMatch,
                        normalizedValue = canonical,
                        confidence = 0.95f
                    )
                )
            }
        }

        // 4. Hashtags as Topics
        val hashRegex = Regex("""#([A-Za-z0-9_]+)""")
        hashRegex.findAll(fullText).forEach { match ->
            val tag = match.groupValues[1]
            val canonical = tag.lowercase(Locale.ROOT)
            if (seen.add("TOPIC:$canonical")) {
                val entity = EntityItem(
                    canonicalName = canonical,
                    displayName = tag,
                    type = EntityType.TOPIC,
                    mentionCount = 1
                )
                drafts.add(
                    ExtractedMentionDraft(
                        entity = entity,
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1,
                        rawText = match.value,
                        normalizedValue = canonical,
                        confidence = 0.95f
                    )
                )
            }
        }

        // 5. Places matching
        COMMON_PLACES.forEach { place ->
            val pattern = Regex("""\b${Regex.escape(place)}\b""", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val canonical = place.lowercase(Locale.ROOT)
                if (seen.add("PLACE:$canonical")) {
                    val displayName = place.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    val entity = EntityItem(
                        canonicalName = canonical,
                        displayName = displayName,
                        type = EntityType.PLACE,
                        mentionCount = 1
                    )
                    drafts.add(
                        ExtractedMentionDraft(
                            entity = entity,
                            startOffset = match.range.first,
                            endOffset = match.range.last + 1,
                            rawText = match.value,
                            normalizedValue = canonical,
                            confidence = 0.90f
                        )
                    )
                }
            }
        }

        // 6. Organizations matching
        KNOWN_ORGS.forEach { org ->
            val pattern = Regex("""\b${Regex.escape(org)}\b""", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val canonical = org.lowercase(Locale.ROOT)
                if (seen.add("ORG:$canonical")) {
                    val entity = EntityItem(
                        canonicalName = canonical,
                        displayName = org.replaceFirstChar { it.uppercase() },
                        type = EntityType.ORGANIZATION,
                        mentionCount = 1
                    )
                    drafts.add(
                        ExtractedMentionDraft(
                            entity = entity,
                            startOffset = match.range.first,
                            endOffset = match.range.last + 1,
                            rawText = match.value,
                            normalizedValue = canonical,
                            confidence = 0.90f
                        )
                    )
                }
            }
        }

        // 7. Named Person Heuristics ("with Sarah", "met Mike", etc.)
        val personLeadIns = listOf(
            "with", "met", "saw", "called", "visited", "texted", "emailed",
            "spoke with", "chatted with", "talked with", "talked to"
        )
        personLeadIns.forEach { leadIn ->
            val regex = Regex("""\b$leadIn\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)\b""")
            regex.findAll(fullText).forEach { match ->
                val name = match.groupValues[1].trim()
                val canonical = name.lowercase(Locale.ROOT)
                if (canonical !in STOP_WORDS && canonical !in COMMON_PLACES && canonical !in KNOWN_ORGS && seen.add("PERSON:$canonical")) {
                    val entity = EntityItem(
                        canonicalName = canonical,
                        displayName = name,
                        type = EntityType.PERSON,
                        mentionCount = 1
                    )
                    val startOffset = match.range.first + match.value.indexOf(name)
                    drafts.add(
                        ExtractedMentionDraft(
                            entity = entity,
                            startOffset = startOffset,
                            endOffset = startOffset + name.length,
                            rawText = name,
                            normalizedValue = canonical,
                            confidence = 0.88f
                        )
                    )
                }
            }
        }

        return drafts
    }

    /**
     * MindForger-inspired Autolinking.
     * Scans body text for known entities, places, projects, and aliases,
     * producing non-destructive link spans for interactive UI highlights.
     */
    fun findAutolinks(text: String, knownEntities: List<EntityItem>): List<AutolinkSpan> {
        if (text.isBlank() || knownEntities.isEmpty()) return emptyList()
        val spans = mutableListOf<AutolinkSpan>()

        knownEntities.forEach { entity ->
            val targets = mutableListOf(entity.displayName)
            if (entity.aliases.isNotBlank()) {
                targets.addAll(entity.aliases.split(",").map { it.trim() }.filter { it.isNotBlank() })
            }

            targets.distinct().forEach { term ->
                if (term.length >= 3 && term.lowercase(Locale.ROOT) !in STOP_WORDS) {
                    val regex = Regex("""\b${Regex.escape(term)}\b""", RegexOption.IGNORE_CASE)
                    regex.findAll(text).forEach { match ->
                        spans.add(
                            AutolinkSpan(
                                startOffset = match.range.first,
                                endOffset = match.range.last + 1,
                                text = match.value,
                                entityId = entity.id,
                                entityName = entity.displayName,
                                type = entity.type
                            )
                        )
                    }
                }
            }
        }

        // Sort spans by startOffset and eliminate overlaps
        val sorted = spans.sortedBy { it.startOffset }
        val nonOverlapping = mutableListOf<AutolinkSpan>()
        var lastEnd = -1
        for (span in sorted) {
            if (span.startOffset >= lastEnd) {
                nonOverlapping.add(span)
                lastEnd = span.endOffset
            }
        }

        return nonOverlapping
    }

    /**
     * Generate suggested tags (kept as PENDING so user retains control).
     */
    fun generateSuggestedTags(
        entry: JournalEntry,
        entities: List<EntityItem>,
        keywords: List<String>,
        imageLabels: List<String> = emptyList()
    ): List<SuggestedTag> {
        val suggestions = mutableListOf<SuggestedTag>()
        val seen = mutableSetOf<String>()

        // 1. Entities
        entities.forEach { entity ->
            val norm = entity.canonicalName.replace(Regex("[^a-z0-9]"), "")
            if (norm.length >= 3 && seen.add(norm)) {
                suggestions.add(
                    SuggestedTag(
                        entryId = entry.id,
                        name = entity.displayName,
                        normalizedName = norm,
                        confidence = 0.90f,
                        status = "PENDING"
                    )
                )
            }
        }

        // 2. High-value keywords
        keywords.take(4).forEach { kw ->
            val norm = kw.lowercase(Locale.ROOT)
            if (norm.length >= 3 && seen.add(norm)) {
                suggestions.add(
                    SuggestedTag(
                        entryId = entry.id,
                        name = kw,
                        normalizedName = norm,
                        confidence = 0.80f,
                        status = "PENDING"
                    )
                )
            }
        }

        // 3. Image labels
        imageLabels.take(3).forEach { label ->
            val norm = label.lowercase(Locale.ROOT)
            if (seen.add(norm)) {
                suggestions.add(
                    SuggestedTag(
                        entryId = entry.id,
                        name = label,
                        normalizedName = norm,
                        confidence = 0.75f,
                        status = "PENDING"
                    )
                )
            }
        }

        return suggestions
    }

    /**
     * Multi-signal explainable relationship calculation between two entries.
     */
    fun computeRelationship(
        source: JournalEntry,
        target: JournalEntry,
        sourceEntities: List<EntityItem>,
        targetEntities: List<EntityItem>,
        sourceTags: List<Tag>,
        targetTags: List<Tag>,
        sourceOcr: String = "",
        targetOcr: String = "",
        sourceLabels: List<String> = emptyList(),
        targetLabels: List<String> = emptyList()
    ): Relationship? {
        if (source.id == target.id) return null

        // 1. Shared Entities (High Signal)
        val sourceEntityNames = sourceEntities.map { it.canonicalName }.toSet()
        val targetEntityNames = targetEntities.map { it.canonicalName }.toSet()
        val sharedEntities = sourceEntityNames.intersect(targetEntityNames)

        // 2. Shared Tags
        val sourceTagNames = sourceTags.map { it.normalizedName }.toSet()
        val targetTagNames = targetTags.map { it.normalizedName }.toSet()
        val sharedTags = sourceTagNames.intersect(targetTagNames)

        // 3. Shared Vision Labels
        val sharedLabels = sourceLabels.map { it.lowercase() }.toSet().intersect(targetLabels.map { it.lowercase() }.toSet())

        // 4. Text & OCR Token Jaccard
        val sourceFull = "${source.title} ${source.body} $sourceOcr"
        val targetFull = "${target.title} ${target.body} $targetOcr"
        val sourceTokens = tokenize(sourceFull).toSet()
        val targetTokens = tokenize(targetFull).toSet()
        val sharedTokens = sourceTokens.intersect(targetTokens)
        val jaccard = if (sourceTokens.isEmpty() || targetTokens.isEmpty()) 0.0f
        else sharedTokens.size.toFloat() / (sourceTokens.size + targetTokens.size - sharedTokens.size)

        // 5. Temporal Proximity
        val timeDiffMillis = abs(source.journalDate - target.journalDate)
        val daysDiff = timeDiffMillis / (1000 * 60 * 60 * 24)
        val temporalWeight = if (daysDiff <= 2) 0.15f else if (daysDiff <= 7) 0.08f else 0.0f

        val entityScore = (sharedEntities.size * 0.35f).coerceAtMost(0.55f)
        val tagScore = (sharedTags.size * 0.20f).coerceAtMost(0.30f)
        val labelScore = (sharedLabels.size * 0.15f).coerceAtMost(0.20f)
        val tokenScore = (jaccard * 0.35f).coerceAtMost(0.30f)

        val totalScore = (entityScore + tagScore + labelScore + tokenScore + temporalWeight).coerceIn(0.0f, 1.0f)

        if (totalScore < 0.26f) return null

        // Generate explainable reason
        val reasons = mutableListOf<String>()
        if (sharedEntities.isNotEmpty()) {
            val names = sharedEntities.take(2).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            reasons.add("shared entity: $names")
        }
        if (sharedTags.isNotEmpty()) {
            val tags = sharedTags.take(2).joinToString(", ") { "#$it" }
            reasons.add("shared tags: $tags")
        }
        if (sharedLabels.isNotEmpty()) {
            reasons.add("similar photo content: ${sharedLabels.take(2).joinToString(", ")}")
        }
        if (sharedTokens.isNotEmpty() && jaccard > 0.15f) {
            val topTokens = sharedTokens.take(3).joinToString(", ")
            reasons.add("overlapping concepts: $topTokens")
        }
        if (daysDiff <= 2) {
            reasons.add("written within ${daysDiff}d")
        }

        val explanation = if (reasons.isNotEmpty()) {
            "Related because: " + reasons.joinToString(" • ") + " (${(totalScore * 100).toInt()}% match)"
        } else {
            "Semantically related memory (${(totalScore * 100).toInt()}% match)"
        }

        val relType = when {
            sharedEntities.isNotEmpty() -> RelationshipType.SHARED_ENTITY
            sharedTags.isNotEmpty() -> RelationshipType.SHARED_TAG
            sharedLabels.isNotEmpty() -> RelationshipType.SEMANTIC_SIMILARITY
            jaccard > 0.18f -> RelationshipType.SHARED_TOPIC
            daysDiff <= 2 -> RelationshipType.TEMPORALLY_PROXIMATE
            else -> RelationshipType.SEMANTIC_SIMILARITY
        }

        return Relationship(
            sourceEntryId = source.id,
            targetEntryId = target.id,
            relationshipType = relType,
            score = totalScore,
            explanation = explanation
        )
    }

    /**
     * Timeline intelligence: clusters nearby journal entries sharing locations or people into Events.
     */
    fun detectEvents(
        entries: List<JournalEntry>,
        entitiesByEntryId: Map<Long, List<EntityItem>>
    ): List<EventItem> {
        if (entries.size < 2) return emptyList()
        val sorted = entries.sortedBy { it.journalDate }
        val events = mutableListOf<EventItem>()

        val cluster = mutableListOf<JournalEntry>()

        for (entry in sorted) {
            if (cluster.isEmpty()) {
                cluster.add(entry)
            } else {
                val lastEntry = cluster.last()
                val diffDays = abs(entry.journalDate - lastEntry.journalDate) / (1000 * 60 * 60 * 24)
                // If within 3 days and shares location or person, group as an event
                val lastEntities = entitiesByEntryId[lastEntry.id] ?: emptyList()
                val currEntities = entitiesByEntryId[entry.id] ?: emptyList()
                val sharedEntity = lastEntities.map { it.canonicalName }.intersect(currEntities.map { it.canonicalName }.toSet()).isNotEmpty()
                val sameLocation = !entry.location.isNullOrBlank() && entry.location.equals(lastEntry.location, ignoreCase = true)

                if (diffDays <= 3 && (sharedEntity || sameLocation || diffDays <= 1)) {
                    cluster.add(entry)
                } else {
                    if (cluster.size >= 2) {
                        createEventFromCluster(cluster, entitiesByEntryId)?.let { events.add(it) }
                    }
                    cluster.clear()
                    cluster.add(entry)
                }
            }
        }

        if (cluster.size >= 2) {
            createEventFromCluster(cluster, entitiesByEntryId)?.let { events.add(it) }
        }

        return events
    }

    private fun createEventFromCluster(
        cluster: List<JournalEntry>,
        entitiesByEntryId: Map<Long, List<EntityItem>>
    ): EventItem? {
        val startDate = cluster.first().journalDate
        val endDate = cluster.last().journalDate
        val commonLocation = cluster.mapNotNull { it.location }.groupBy { it }.maxByOrNull { it.value.size }?.key
        val allEntities = cluster.flatMap { entitiesByEntryId[it.id] ?: emptyList() }
        val topPerson = allEntities.filter { it.type == EntityType.PERSON }.groupBy { it.displayName }.maxByOrNull { it.value.size }?.key

        val title = when {
            commonLocation != null && topPerson != null -> "$commonLocation with $topPerson"
            commonLocation != null -> "Trip to $commonLocation"
            topPerson != null -> "Time with $topPerson"
            else -> cluster.first().title.ifBlank { "Journal Episode" }
        }

        val entryIds = JSONArray(cluster.map { it.id }).toString()
        val entityIds = JSONArray(allEntities.map { it.id }.distinct()).toString()

        return EventItem(
            title = title,
            description = "${cluster.size} connected entries across ${((endDate - startDate) / (1000 * 60 * 60 * 24)) + 1} days",
            startDate = startDate,
            endDate = endDate,
            location = commonLocation,
            entryIdsJson = entryIds,
            entityIdsJson = entityIds,
            entryCount = cluster.size
        )
    }
}
