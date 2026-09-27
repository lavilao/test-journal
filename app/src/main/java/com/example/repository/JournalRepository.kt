package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.EntityItem
import com.example.data.model.EntityType
import com.example.data.model.EntityWithEntries
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.EntryWithRelations
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.data.model.HybridSearchResult
import com.example.data.model.JournalEntry
import com.example.data.model.KnowledgeGraphData
import com.example.data.model.MediaItem
import com.example.data.model.RelatedEntryDetail
import com.example.data.model.Relationship
import com.example.data.model.Tag
import com.example.semantic.MindForgerSemanticEngine
import com.example.semantic.MlKitAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class JournalRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val dao = database.journalDao()
    private val semanticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allEntriesWithRelations: Flow<List<EntryWithRelations>> = dao.getAllEntriesWithRelations()
    val allEntities: Flow<List<EntityItem>> = dao.getAllEntities()
    val allTags: Flow<List<Tag>> = dao.getAllTags()

    fun getEntryWithRelations(id: Long): Flow<EntryWithRelations?> = dao.getEntryWithRelationsFlow(id)

    fun getEntityWithEntries(entityId: Long): Flow<EntityWithEntries?> = dao.getEntityWithEntries(entityId)

    fun getRelatedEntries(entryId: Long): Flow<List<RelatedEntryDetail>> {
        return dao.getRelationshipsForEntry(entryId).map { relationships ->
            relationships.mapNotNull { rel ->
                val otherId = if (rel.sourceEntryId == entryId) rel.targetEntryId else rel.sourceEntryId
                val otherEntry = dao.getEntryById(otherId)
                if (otherEntry != null) {
                    RelatedEntryDetail(entry = otherEntry, relationship = rel)
                } else null
            }
        }
    }

    /**
     * Immediate save of journal entry, followed by background asynchronous semantic processing.
     */
    suspend fun saveEntry(
        entry: JournalEntry,
        manualTags: List<String> = emptyList(),
        attachedImageUri: Uri? = null
    ): Long = withContext(Dispatchers.IO) {
        val wordCount = entry.body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val currentHash = MindForgerSemanticEngine.computeContentHash(entry.title, entry.body)

        val entryToSave = entry.copy(
            wordCount = wordCount,
            contentHash = currentHash,
            updatedAt = System.currentTimeMillis(),
            imageUri = attachedImageUri?.toString() ?: entry.imageUri
        )

        val entryId = if (entryToSave.id == 0L) {
            dao.insertEntry(entryToSave)
        } else {
            dao.updateEntry(entryToSave)
            entryToSave.id
        }

        // Insert manual tags
        manualTags.forEach { tagName ->
            val norm = tagName.trim().lowercase(Locale.ROOT)
            if (norm.isNotBlank()) {
                val existingTag = dao.getTagByNormalized(norm)
                val tagId = existingTag?.id ?: dao.insertTag(
                    Tag(name = tagName.trim(), normalizedName = norm, source = "MANUAL", confidence = 1.0f)
                )
                dao.insertEntryTagCrossRef(EntryTagCrossRef(entryId = entryId, tagId = tagId, source = "MANUAL"))
            }
        }

        // Process Attached Image asynchronously
        attachedImageUri?.let { uri ->
            semanticScope.launch {
                val analysis = MlKitAnalyzer.analyzeImageFromUri(context, uri)
                val labelsJson = JSONArray(analysis.labels).toString()
                dao.insertMediaItem(
                    MediaItem(
                        entryId = entryId,
                        uri = uri.toString(),
                        labelsJson = labelsJson,
                        faceCount = analysis.faceCount,
                        ocrText = analysis.ocrText
                    )
                )

                // If OCR detected text or labels, enrich tags
                analysis.labels.take(3).forEach { label ->
                    val norm = label.lowercase(Locale.ROOT)
                    val existingTag = dao.getTagByNormalized(norm)
                    val tagId = existingTag?.id ?: dao.insertTag(
                        Tag(name = label, normalizedName = norm, source = "AUTO", confidence = 0.80f)
                    )
                    dao.insertEntryTagCrossRef(EntryTagCrossRef(entryId = entryId, tagId = tagId, source = "AUTO"))
                }
            }
        }

        // Trigger asynchronous background semantic pipeline
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }

        entryId
    }

    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        dao.clearTagsForEntry(entryId)
        dao.clearEntitiesForEntry(entryId)
        dao.deleteRelationshipsForEntry(entryId)
        dao.deleteMediaForEntry(entryId)
        dao.deleteEntryById(entryId)
    }

    /**
     * Background Semantic Pipeline (MindForger-inspired + ML Kit).
     * Safe, idempotent, cancellable.
     */
    suspend fun processSemanticIntelligence(entryId: Long) = withContext(Dispatchers.IO) {
        val entry = dao.getEntryById(entryId) ?: return@withContext
        val allEntries = dao.getAllEntriesSnapshot()

        // 1. Language Detection via MLKit
        val detectedLang = MlKitAnalyzer.identifyLanguage("${entry.title} ${entry.body}")

        // 2. Local Entity Extraction
        val extractedEntities = MindForgerSemanticEngine.extractEntities(entry.title, entry.body)

        // Clear existing automatic entities for this entry
        dao.clearEntitiesForEntry(entryId)

        extractedEntities.forEach { entityItem ->
            val existing = dao.getEntityByCanonicalAndType(entityItem.canonicalName, entityItem.type)
            val entityId = if (existing != null) {
                dao.updateEntity(
                    existing.copy(
                        mentionCount = existing.mentionCount + 1,
                        lastSeen = System.currentTimeMillis()
                    )
                )
                existing.id
            } else {
                dao.insertEntity(entityItem)
            }
            dao.insertEntryEntityCrossRef(
                EntryEntityCrossRef(entryId = entryId, entityId = entityId, confidence = 0.88f)
            )
        }

        // 3. Keyword Extraction (TF-IDF) & Automatic Tagging
        val keywords = MindForgerSemanticEngine.extractKeywords(entry, allEntries)
        val generatedTags = MindForgerSemanticEngine.generateTags(entry, extractedEntities, keywords)

        generatedTags.forEach { autoTag ->
            val existing = dao.getTagByNormalized(autoTag.normalizedName)
            val tagId = existing?.id ?: dao.insertTag(autoTag)
            dao.insertEntryTagCrossRef(
                EntryTagCrossRef(
                    entryId = entryId,
                    tagId = tagId,
                    confidence = autoTag.confidence,
                    source = autoTag.source
                )
            )
        }

        // 4. Semantic Linking / Relationship Discovery
        val currentEntities = dao.getEntitiesForEntry(entryId)
        val currentTags = dao.getTagsForEntry(entryId)

        dao.deleteRelationshipsForEntry(entryId)

        allEntries.filter { it.id != entryId }.forEach { otherEntry ->
            val otherEntities = dao.getEntitiesForEntry(otherEntry.id)
            val otherTags = dao.getTagsForEntry(otherEntry.id)

            val rel = MindForgerSemanticEngine.computeRelationship(
                source = entry,
                target = otherEntry,
                sourceEntities = currentEntities,
                targetEntities = otherEntities,
                sourceTags = currentTags,
                targetTags = otherTags
            )

            if (rel != null) {
                dao.insertRelationship(rel)
            }
        }

        // Mark as processed
        dao.updateEntry(
            entry.copy(
                language = detectedLang,
                processedContentHash = entry.contentHash
            )
        )
    }

    /**
     * Hybrid Search combining Exact Text, BM25 tokens, Tags, and Extracted Entities.
     */
    suspend fun searchHybrid(query: String): List<HybridSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val allEntries = dao.getAllEntriesWithRelations().first()
        val queryTokens = MindForgerSemanticEngine.tokenize(trimmed).toSet()

        val results = mutableListOf<HybridSearchResult>()

        allEntries.forEach { item ->
            val entry = item.entry
            var score = 0.0f
            val reasons = mutableListOf<String>()
            val matchedTags = mutableListOf<String>()
            val matchedEntities = mutableListOf<String>()

            val lowerTitle = entry.title.lowercase(Locale.ROOT)
            val lowerBody = entry.body.lowercase(Locale.ROOT)

            // 1. Exact match in title
            if (lowerTitle.contains(trimmed.lowercase(Locale.ROOT))) {
                score += 0.50f
                reasons.add("Exact title match")
            }

            // 2. Exact match in body
            if (lowerBody.contains(trimmed.lowercase(Locale.ROOT))) {
                score += 0.35f
                reasons.add("Exact body match")
            }

            // 3. Token overlap (BM25-style keyword matching)
            val entryTokens = MindForgerSemanticEngine.tokenize("${entry.title} ${entry.body}").toSet()
            val sharedTokens = queryTokens.intersect(entryTokens)
            if (sharedTokens.isNotEmpty()) {
                val tokenScore = (sharedTokens.size.toFloat() / queryTokens.size) * 0.40f
                score += tokenScore
                reasons.add("Keywords: ${sharedTokens.joinToString(", ")}")
            }

            // 4. Entity match
            item.entities.forEach { entity ->
                if (entity.displayName.lowercase(Locale.ROOT).contains(trimmed.lowercase(Locale.ROOT)) ||
                    queryTokens.any { entity.canonicalName.contains(it) }
                ) {
                    score += 0.35f
                    matchedEntities.add(entity.displayName)
                    reasons.add("Entity: ${entity.displayName} (${entity.type.name})")
                }
            }

            // 5. Tag match
            item.tags.forEach { tag ->
                if (tag.name.lowercase(Locale.ROOT).contains(trimmed.lowercase(Locale.ROOT)) ||
                    queryTokens.any { tag.normalizedName.contains(it) }
                ) {
                    score += 0.30f
                    matchedTags.add("#${tag.name}")
                    reasons.add("Tag: #${tag.name}")
                }
            }

            if (score > 0.15f) {
                // Generate contextual snippet
                val snippetIndex = lowerBody.indexOf(trimmed.lowercase(Locale.ROOT))
                val snippet = if (snippetIndex >= 0) {
                    val start = (snippetIndex - 40).coerceAtLeast(0)
                    val end = (snippetIndex + trimmed.length + 80).coerceAtMost(entry.body.length)
                    "..." + entry.body.substring(start, end).replace("\n", " ") + "..."
                } else {
                    entry.body.take(120).replace("\n", " ") + (if (entry.body.length > 120) "..." else "")
                }

                results.add(
                    HybridSearchResult(
                        entry = entry,
                        matchedScore = score.coerceAtMost(1.0f),
                        matchedReason = reasons.distinct().take(3).joinToString(" • "),
                        snippet = snippet,
                        matchedTags = matchedTags,
                        matchedEntities = matchedEntities
                    )
                )
            }
        }

        results.sortedByDescending { it.matchedScore }
    }

    /**
     * Builds data for interactive 2D Knowledge Graph visualization.
     * Computes initial node positions around a ring for organic force layout.
     */
    suspend fun getKnowledgeGraphData(): KnowledgeGraphData = withContext(Dispatchers.IO) {
        val entries = dao.getAllEntriesWithRelations().first()
        val relationships = dao.getAllRelationshipsSnapshot()

        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        val seenNodeIds = mutableSetOf<String>()

        val totalItems = entries.size + 1
        var index = 0

        // Add Journal Entry Nodes
        entries.forEach { item ->
            val nodeId = "entry_${item.entry.id}"
            if (seenNodeIds.add(nodeId)) {
                val angle = (2 * Math.PI * index / totalItems).toFloat()
                val radius = 280f
                nodes.add(
                    GraphNode(
                        id = nodeId,
                        rawId = item.entry.id,
                        label = item.entry.title.ifBlank { "Untitled" },
                        type = GraphNodeType.ENTRY,
                        x = 450f + radius * cos(angle),
                        y = 450f + radius * sin(angle),
                        size = 32f
                    )
                )
                index++
            }

            // Add Entity Nodes & connecting edges
            item.entities.take(4).forEach { entity ->
                val entityNodeId = "entity_${entity.id}"
                if (seenNodeIds.add(entityNodeId)) {
                    val nodeType = when (entity.type) {
                        EntityType.PERSON -> GraphNodeType.PERSON
                        EntityType.PLACE -> GraphNodeType.PLACE
                        EntityType.ORGANIZATION -> GraphNodeType.ORGANIZATION
                        else -> GraphNodeType.TOPIC
                    }
                    val offsetAngle = index * 0.7f
                    nodes.add(
                        GraphNode(
                            id = entityNodeId,
                            rawId = entity.id,
                            label = entity.displayName,
                            type = nodeType,
                            x = 450f + 180f * cos(offsetAngle),
                            y = 450f + 180f * sin(offsetAngle),
                            size = 24f
                        )
                    )
                    index++
                }

                edges.add(
                    GraphEdge(
                        sourceId = nodeId,
                        targetId = entityNodeId,
                        label = entity.type.name,
                        weight = 0.8f
                    )
                )
            }
        }

        // Add Inter-Entry Semantic Relationship Edges
        relationships.forEach { rel ->
            val src = "entry_${rel.sourceEntryId}"
            val tgt = "entry_${rel.targetEntryId}"
            if (seenNodeIds.contains(src) && seenNodeIds.contains(tgt)) {
                edges.add(
                    GraphEdge(
                        sourceId = src,
                        targetId = tgt,
                        label = "${(rel.score * 100).toInt()}%",
                        weight = rel.score
                    )
                )
            }
        }

        KnowledgeGraphData(nodes = nodes, edges = edges)
    }

    /**
     * Complete offline JSON export for zero lock-in privacy.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val entries = dao.getAllEntriesWithRelations().first()
        val jsonArray = JSONArray()

        entries.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.entry.id)
            obj.put("title", item.entry.title)
            obj.put("body", item.entry.body)
            obj.put("journalDate", item.entry.journalDate)
            obj.put("createdAt", item.entry.createdAt)
            obj.put("location", item.entry.location ?: "")
            obj.put("mood", item.entry.mood ?: "")
            obj.put("language", item.entry.language ?: "en")

            val tagsArray = JSONArray()
            item.tags.forEach { tagsArray.put(it.name) }
            obj.put("tags", tagsArray)

            val entitiesArray = JSONArray()
            item.entities.forEach { e ->
                val eObj = JSONObject()
                eObj.put("name", e.displayName)
                eObj.put("type", e.type.name)
                entitiesArray.put(eObj)
            }
            obj.put("entities", entitiesArray)

            jsonArray.put(obj)
        }

        jsonArray.toString(2)
    }

    /**
     * Markdown bundle export with YAML frontmatter.
     */
    suspend fun exportToMarkdownBundle(): String = withContext(Dispatchers.IO) {
        val entries = dao.getAllEntriesWithRelations().first()
        val builder = StringBuilder()

        entries.forEach { item ->
            builder.append("---\n")
            builder.append("title: \"${item.entry.title}\"\n")
            builder.append("date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(item.entry.journalDate)}\n")
            if (!item.entry.location.isNullOrBlank()) builder.append("location: \"${item.entry.location}\"\n")
            if (!item.entry.mood.isNullOrBlank()) builder.append("mood: \"${item.entry.mood}\"\n")
            if (item.tags.isNotEmpty()) builder.append("tags: [${item.tags.joinToString(", ") { "\"${it.name}\"" }}]\n")
            if (item.entities.isNotEmpty()) builder.append("entities: [${item.entities.joinToString(", ") { "\"${it.displayName}\"" }}]\n")
            builder.append("---\n\n")
            builder.append(item.entry.body)
            builder.append("\n\n---\n\n")
        }

        builder.toString()
    }

    /**
     * Seeds initial interconnected sample journal entries so the user experiences the knowledge graph immediately.
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.getEntryCount() > 0) return@withContext

        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        val seed1 = JournalEntry(
            title = "Morning Coffee & MindForger Architecture",
            body = "Met with Sarah at Blue Bottle Cafe to discuss on-device personal knowledge management. We talked about MindForger's information retrieval algorithms and how to adapt TF-IDF and BM25 for offline Android notes without requiring any remote cloud servers. Beautiful crisp morning.",
            journalDate = now - 3 * dayMillis,
            location = "Blue Bottle Cafe",
            mood = "Inspired",
            language = "en"
        )
        val id1 = saveEntry(seed1, listOf("pkm", "offline", "coffee"))

        val seed2 = JournalEntry(
            title = "Testing ML Kit On-Device Translation",
            body = "Researched local translation using Google ML Kit. Sarah recommended testing Spanish and Japanese language packs. The beauty of this approach is zero latency and complete user privacy. Visited Stanford Lab in the afternoon.",
            journalDate = now - 2 * dayMillis,
            location = "Stanford Lab",
            mood = "Focused",
            language = "en"
        )
        val id2 = saveEntry(seed2, listOf("mlkit", "translation", "privacy"))

        val seed3 = JournalEntry(
            title = "Everglades Expedition with Mike",
            body = "Went hiking at Everglades with Mike. Saw three alligators sunbathing by the canal trail! Such an incredible landscape. Later we stopped by Starbucks for iced tea and reviewed our field notes.",
            journalDate = now - 1 * dayMillis,
            location = "Everglades",
            mood = "Joyful",
            language = "en"
        )
        val id3 = saveEntry(seed3, listOf("hiking", "nature", "everglades"))

        val seed4 = JournalEntry(
            title = "Knowledge Graphs & Semantic Memory",
            body = "Reflecting on how personal memory works. When I talked with Sarah earlier this week at Blue Bottle Cafe, she mentioned that human recall relies on associative nodes rather than linear folders. Implementing associative semantic links in Mnemosyne feels like giving memory digital form.",
            journalDate = now,
            location = "Home Study",
            mood = "Thoughtful",
            language = "en"
        )
        val id4 = saveEntry(seed4, listOf("philosophy", "memory", "pkm"))

        // Run semantic pipeline for all seeds to generate immediate relationships
        processSemanticIntelligence(id1)
        processSemanticIntelligence(id2)
        processSemanticIntelligence(id3)
        processSemanticIntelligence(id4)
    }
}
