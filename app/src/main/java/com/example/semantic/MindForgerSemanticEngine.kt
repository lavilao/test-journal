package com.example.semantic

import com.example.data.model.EntityItem
import com.example.data.model.EntityType
import com.example.data.model.JournalEntry
import com.example.data.model.Relationship
import com.example.data.model.RelationshipType
import com.example.data.model.Tag
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * MindForger-inspired on-device Information Retrieval and Semantic Intelligence Engine.
 * Operates purely locally with deterministic, explainable algorithms:
 * - Tokenization & stop-word filtering
 * - TF-IDF & BM25 keyword scoring
 * - Local entity discovery (People, Places, Organizations, Topics, Dates, URLs)
 * - Automatic tag generation with confidence
 * - Explainable semantic linking and relationship scoring
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
        "just", "really", "felt", "think", "thought", "going", "much", "also", "still", "well", "back", "even"
    )

    private val COMMON_PLACES = setOf(
        "park", "cafe", "coffee shop", "library", "starbucks", "beach", "mountain", "studio",
        "office", "airport", "station", "paris", "london", "tokyo", "new york", "san francisco",
        "kyoto", "berlin", "lake", "trail", "gym", "museum", "everglades", "yosemite", "hotel", "cabin"
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

        // Document frequency
        val docFrequencies = mutableMapOf<String, Int>()
        tokens.distinct().forEach { term ->
            val count = allEntries.count { other ->
                val otherText = "${other.title} ${other.body}".lowercase(Locale.ROOT)
                otherText.contains(term)
            }
            docFrequencies[term] = max(1, count)
        }

        // Compute TF-IDF score
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

    /**
     * Rule-based entity extraction for on-device privacy (MindForger non-LLM pattern).
     */
    fun extractEntities(title: String, body: String): List<EntityItem> {
        val fullText = "$title. $body"
        val discovered = mutableListOf<EntityItem>()
        val seen = mutableSetOf<String>()

        // 1. Explicit URLs
        val urlRegex = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)
        urlRegex.findAll(fullText).forEach { match ->
            val url = match.value.trimEnd('.', ',', ';')
            if (seen.add("URL:$url")) {
                discovered.add(
                    EntityItem(
                        canonicalName = url,
                        displayName = url,
                        type = EntityType.URL,
                        mentionCount = 1
                    )
                )
            }
        }

        // 2. Hashtags as Topics
        val hashRegex = Regex("""#([A-Za-z0-9_]+)""")
        hashRegex.findAll(fullText).forEach { match ->
            val tag = match.groupValues[1]
            val canonical = tag.lowercase(Locale.ROOT)
            if (seen.add("TOPIC:$canonical")) {
                discovered.add(
                    EntityItem(
                        canonicalName = canonical,
                        displayName = tag,
                        type = EntityType.TOPIC,
                        mentionCount = 1
                    )
                )
            }
        }

        // 3. Known places matching
        COMMON_PLACES.forEach { place ->
            val pattern = Regex("""\b${Regex.escape(place)}\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(fullText)) {
                val canonical = place.lowercase(Locale.ROOT)
                if (seen.add("PLACE:$canonical")) {
                    discovered.add(
                        EntityItem(
                            canonicalName = canonical,
                            displayName = place.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                            type = EntityType.PLACE,
                            mentionCount = 1
                        )
                    )
                }
            }
        }

        // 4. Known Organizations matching
        KNOWN_ORGS.forEach { org ->
            val pattern = Regex("""\b${Regex.escape(org)}\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(fullText)) {
                val canonical = org.lowercase(Locale.ROOT)
                if (seen.add("ORG:$canonical")) {
                    discovered.add(
                        EntityItem(
                            canonicalName = canonical,
                            displayName = org.replaceFirstChar { it.uppercase() },
                            type = EntityType.ORGANIZATION,
                            mentionCount = 1
                        )
                    )
                }
            }
        }

        // 5. Named Person Heuristic (Title Case words preceded by "with", "met", "saw", "talked to", "called")
        val personLeadIns = listOf("with", "met", "saw", "called", "visited", "texted", "emailed", "spoke with", "chatted with", "talked with", "talked to")
        personLeadIns.forEach { leadIn ->
            val regex = Regex("""\b$leadIn\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)\b""")
            regex.findAll(fullText).forEach { match ->
                val name = match.groupValues[1].trim()
                val canonical = name.lowercase(Locale.ROOT)
                if (canonical !in STOP_WORDS && canonical !in COMMON_PLACES && canonical !in KNOWN_ORGS && seen.add("PERSON:$canonical")) {
                    discovered.add(
                        EntityItem(
                            canonicalName = canonical,
                            displayName = name,
                            type = EntityType.PERSON,
                            mentionCount = 1
                        )
                    )
                }
            }
        }

        // 6. Generic Title Case proper nouns (2 capitalized words like "Central Park", "Sarah Connor", "Quantum Computing")
        val properNounRegex = Regex("""\b([A-Z][a-z]{2,}\s+[A-Z][a-z]{2,})\b""")
        properNounRegex.findAll(fullText).forEach { match ->
            val phrase = match.groupValues[1].trim()
            val canonical = phrase.lowercase(Locale.ROOT)
            val firstWord = phrase.substringBefore(" ").lowercase(Locale.ROOT)
            if (firstWord !in STOP_WORDS && seen.add("PHRASE:$canonical")) {
                // If it contains known place keywords
                val isPlace = COMMON_PLACES.any { canonical.contains(it) }
                val type = if (isPlace) EntityType.PLACE else EntityType.TOPIC
                discovered.add(
                    EntityItem(
                        canonicalName = canonical,
                        displayName = phrase,
                        type = type,
                        mentionCount = 1
                    )
                )
            }
        }

        return discovered
    }

    /**
     * Generate automatic tags for an entry based on extracted keywords, explicit tags, and entities.
     */
    fun generateTags(entry: JournalEntry, entities: List<EntityItem>, keywords: List<String>): List<Tag> {
        val tags = mutableListOf<Tag>()
        val seen = mutableSetOf<String>()

        // 1. Explicit hashtags from text
        val hashRegex = Regex("""#([A-Za-z0-9_]+)""")
        hashRegex.findAll("${entry.title} ${entry.body}").forEach { match ->
            val name = match.groupValues[1]
            val norm = name.lowercase(Locale.ROOT)
            if (seen.add(norm)) {
                tags.add(Tag(name = name, normalizedName = norm, source = "MANUAL", confidence = 1.0f))
            }
        }

        // 2. High-value entities as auto tags
        entities.forEach { entity ->
            val norm = entity.canonicalName.replace(Regex("[^a-z0-9]"), "")
            if (norm.length >= 3 && seen.add(norm)) {
                val confidence = when (entity.type) {
                    EntityType.TOPIC -> 0.95f
                    EntityType.PLACE -> 0.90f
                    EntityType.ORGANIZATION -> 0.85f
                    EntityType.PERSON -> 0.80f
                    else -> 0.70f
                }
                tags.add(Tag(name = entity.displayName, normalizedName = norm, source = "AUTO", confidence = confidence))
            }
        }

        // 3. Top keywords as auto tags
        keywords.take(4).forEach { kw ->
            val norm = kw.lowercase(Locale.ROOT)
            if (seen.add(norm)) {
                tags.add(Tag(name = kw, normalizedName = norm, source = "AUTO", confidence = 0.75f))
            }
        }

        return tags
    }

    /**
     * Computes an explainable semantic relationship between two journal entries.
     * Combines entity overlap, tag overlap, keyword/topic overlap, and temporal proximity.
     */
    fun computeRelationship(
        source: JournalEntry,
        target: JournalEntry,
        sourceEntities: List<EntityItem>,
        targetEntities: List<EntityItem>,
        sourceTags: List<Tag>,
        targetTags: List<Tag>
    ): Relationship? {
        if (source.id == target.id) return null

        // Shared Entities
        val sourceEntityNames = sourceEntities.map { it.canonicalName }.toSet()
        val targetEntityNames = targetEntities.map { it.canonicalName }.toSet()
        val sharedEntities = sourceEntityNames.intersect(targetEntityNames)

        // Shared Tags
        val sourceTagNames = sourceTags.map { it.normalizedName }.toSet()
        val targetTagNames = targetTags.map { it.normalizedName }.toSet()
        val sharedTags = sourceTagNames.intersect(targetTagNames)

        // Text & Keyword Token Jaccard
        val sourceTokens = tokenize("${source.title} ${source.body}").toSet()
        val targetTokens = tokenize("${target.title} ${target.body}").toSet()
        val sharedTokens = sourceTokens.intersect(targetTokens)
        val jaccard = if (sourceTokens.isEmpty() || targetTokens.isEmpty()) 0.0f
        else sharedTokens.size.toFloat() / (sourceTokens.size + targetTokens.size - sharedTokens.size)

        // Temporal Proximity (within 7 days gives a temporal bonus)
        val timeDiffMillis = abs(source.journalDate - target.journalDate)
        val daysDiff = timeDiffMillis / (1000 * 60 * 60 * 24)
        val temporalWeight = if (daysDiff <= 2) 0.15f else if (daysDiff <= 7) 0.08f else 0.0f

        val entityScore = (sharedEntities.size * 0.35f).coerceAtMost(0.50f)
        val tagScore = (sharedTags.size * 0.20f).coerceAtMost(0.30f)
        val tokenScore = (jaccard * 0.35f).coerceAtMost(0.30f)

        val totalScore = (entityScore + tagScore + tokenScore + temporalWeight).coerceIn(0.0f, 1.0f)

        // Sensitivity threshold: minimum 0.28 to form a meaningful connection
        if (totalScore < 0.28f) return null

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
        if (jaccard > 0.18f && sharedTokens.isNotEmpty()) {
            val topTokens = sharedTokens.take(2).joinToString(", ")
            reasons.add("similar topics: $topTokens")
        }
        if (daysDiff <= 2) {
            reasons.add("written in close timeframe")
        }

        val explanation = if (reasons.isNotEmpty()) {
            "Connected by " + reasons.joinToString(" and ") + " (${(totalScore * 100).toInt()}% match)"
        } else {
            "Semantically related entry (${(totalScore * 100).toInt()}% match)"
        }

        val relType = when {
            sharedEntities.isNotEmpty() -> RelationshipType.SHARED_ENTITY
            sharedTags.isNotEmpty() -> RelationshipType.SHARED_TAG
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
}
