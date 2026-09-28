package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.AudioRecordItem
import com.example.data.model.AutolinkSpan
import com.example.data.model.EntityItem
import com.example.data.model.EntityMention
import com.example.data.model.EntityType
import com.example.data.model.EntityWithEntries
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.EntryWithRelations
import com.example.data.model.EventItem
import com.example.data.model.FacePersonAssociation
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.data.model.HybridSearchResult
import com.example.data.model.JournalEntry
import com.example.data.model.JournalPage
import com.example.data.model.KnowledgeGraphData
import com.example.data.model.MediaItem
import com.example.data.model.RelatedEntryDetail
import com.example.data.model.Relationship
import com.example.data.model.StorageBreakdown
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag
import java.io.File
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
import java.util.Calendar
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
    val allEvents: Flow<List<EventItem>> = dao.getAllEvents()

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

    fun getSuggestedTagsForEntry(entryId: Long): Flow<List<SuggestedTag>> =
        dao.getSuggestedTagsForEntry(entryId)

    fun getFaceAssociationsForMedia(mediaId: Long): Flow<List<FacePersonAssociation>> =
        dao.getFaceAssociationsForMedia(mediaId)

    fun getMediaForPerson(personId: Long): Flow<List<MediaItem>> =
        dao.getMediaForPerson(personId)

    fun getForgottenThreads(): Flow<List<EntityItem>> {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return dao.getForgottenThreads(cutoffTimestamp = thirtyDaysAgo, minMentions = 2)
    }

    suspend fun getOnThisDayMemories(): List<JournalEntry> = withContext(Dispatchers.IO) {
        val all = dao.getAllEntriesSnapshot()
        val calToday = Calendar.getInstance()
        val currentMonth = calToday.get(Calendar.MONTH)
        val currentDay = calToday.get(Calendar.DAY_OF_MONTH)
        val currentYear = calToday.get(Calendar.YEAR)

        all.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.journalDate }
            cal.get(Calendar.MONTH) == currentMonth &&
                    cal.get(Calendar.DAY_OF_MONTH) == currentDay &&
                    cal.get(Calendar.YEAR) < currentYear
        }
    }

    /**
     * Compute Autolink Spans for an entry without modifying original source text.
     */
    suspend fun getAutolinksForEntry(entryId: Long): List<AutolinkSpan> = withContext(Dispatchers.IO) {
        val entry = dao.getEntryById(entryId) ?: return@withContext emptyList()
        val knownEntities = dao.getAllEntitiesSnapshot()
        MindForgerSemanticEngine.findAutolinks(entry.body, knownEntities)
    }

    /**
     * Immediate save of journal entry, followed by background asynchronous semantic processing.
     * Auto-save calls this frequently without blocking the UI.
     */
    fun persistImageToLocalStorage(sourceUri: Uri): String {
        return try {
            val scheme = sourceUri.scheme
            if (scheme == "file" || scheme == null) {
                return sourceUri.toString()
            }
            val imagesDir = File(context.filesDir, "journal_images").apply { mkdirs() }
            val extension = context.contentResolver.getType(sourceUri)?.let { mime ->
                when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
            } ?: "jpg"
            val destFile = File(imagesDir, "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            sourceUri.toString()
        }
    }

    suspend fun saveEntry(
        entry: JournalEntry,
        manualTags: List<String> = emptyList(),
        attachedImageUri: Uri? = null,
        pages: List<JournalPage> = emptyList(),
        audioRecords: List<AudioRecordItem> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val wordCount = entry.body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val pagesText = pages.joinToString("\n") { "${it.title}\n${it.body}" }
        val currentHash = MindForgerSemanticEngine.computeContentHash(entry.title, "${entry.body}\n$pagesText")

        val savedImageUri = if (attachedImageUri != null) {
            persistImageToLocalStorage(attachedImageUri)
        } else {
            entry.imageUri
        }

        val entryToSave = entry.copy(
            wordCount = wordCount,
            contentHash = currentHash,
            updatedAt = System.currentTimeMillis(),
            imageUri = savedImageUri
        )

        val entryId = if (entryToSave.id == 0L) {
            dao.insertEntry(entryToSave)
        } else {
            dao.updateEntry(entryToSave)
            entryToSave.id
        }

        // Save pages
        if (pages.isNotEmpty()) {
            pages.forEachIndexed { idx, page ->
                val pHash = MindForgerSemanticEngine.computeContentHash(page.title, page.body)
                val pageToSave = page.copy(entryId = entryId, pageIndex = idx, contentHash = pHash, updatedAt = System.currentTimeMillis())
                if (pageToSave.id == 0L) {
                    dao.insertPage(pageToSave)
                } else {
                    dao.updatePage(pageToSave)
                }
            }
        }

        // Save pending or edited audio recordings
        if (audioRecords.isNotEmpty()) {
            audioRecords.forEach { audio ->
                if (audio.id == 0L) {
                    dao.insertAudioRecord(audio.copy(entryId = entryId))
                } else {
                    dao.updateAudioRecord(audio.copy(entryId = entryId))
                }
            }
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
                        ocrText = analysis.ocrText,
                        caption = ""
                    )
                )
            }
        }

        // Trigger asynchronous background semantic pipeline
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }

        entryId
    }

    suspend fun savePage(page: JournalPage): Long = withContext(Dispatchers.IO) {
        val pHash = MindForgerSemanticEngine.computeContentHash(page.title, page.body)
        val toSave = page.copy(contentHash = pHash, updatedAt = System.currentTimeMillis())
        val id = if (toSave.id == 0L) {
            dao.insertPage(toSave)
        } else {
            dao.updatePage(toSave)
            toSave.id
        }
        semanticScope.launch {
            processSemanticIntelligence(toSave.entryId)
        }
        id
    }

    suspend fun deletePage(pageId: Long, entryId: Long) = withContext(Dispatchers.IO) {
        dao.deletePageById(pageId)
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }
    }

    suspend fun addPhotoToEntry(entryId: Long, uri: Uri, caption: String = ""): Long = withContext(Dispatchers.IO) {
        val persistentUriString = persistImageToLocalStorage(uri)
        val persistentUri = Uri.parse(persistentUriString)
        val mediaId = dao.insertMediaItem(
            MediaItem(
                entryId = entryId,
                uri = persistentUriString,
                caption = caption
            )
        )
        semanticScope.launch {
            val analysis = MlKitAnalyzer.analyzeImageFromUri(context, persistentUri)
            val labelsJson = JSONArray(analysis.labels).toString()
            val existing = dao.getMediaById(mediaId)
            if (existing != null) {
                dao.updateMediaItem(
                    existing.copy(
                        labelsJson = labelsJson,
                        faceCount = analysis.faceCount,
                        ocrText = analysis.ocrText
                    )
                )
            }
            processSemanticIntelligence(entryId)
        }
        mediaId
    }

    suspend fun updatePhotoCaption(mediaId: Long, caption: String) = withContext(Dispatchers.IO) {
        val media = dao.getMediaById(mediaId) ?: return@withContext
        dao.updateMediaItem(media.copy(caption = caption))
    }

    suspend fun deletePhoto(mediaId: Long) = withContext(Dispatchers.IO) {
        val media = dao.getMediaById(mediaId)
        dao.deleteMediaById(mediaId)
        media?.let {
            semanticScope.launch {
                processSemanticIntelligence(it.entryId)
            }
        }
    }

    suspend fun addAudioRecord(
        entryId: Long,
        title: String,
        filePath: String,
        durationMs: Long,
        transcript: String,
        status: String
    ): Long = withContext(Dispatchers.IO) {
        val id = dao.insertAudioRecord(
            AudioRecordItem(
                entryId = entryId,
                title = title,
                filePath = filePath,
                durationMs = durationMs,
                transcript = transcript,
                transcriptionStatus = status
            )
        )
        // Voice -> Memory: Asynchronously process semantic intelligence from transcript
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }
        id
    }

    suspend fun updateAudioRecordTranscript(
        audioId: Long,
        entryId: Long,
        transcript: String,
        status: String = "COMPLETED"
    ) = withContext(Dispatchers.IO) {
        dao.updateAudioTranscript(audioId, transcript.trim(), status)
        // Reset processedContentHash on the entry so semantic extraction incorporates new transcript
        val entry = dao.getEntryById(entryId)
        if (entry != null) {
            dao.updateEntry(entry.copy(processedContentHash = ""))
        }
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }
    }

    suspend fun deleteAudioRecord(audioId: Long, entryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteAudioRecordById(audioId)
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }
    }

    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        dao.clearTagsForEntry(entryId)
        dao.clearEntitiesForEntry(entryId)
        dao.clearMentionsForEntry(entryId)
        dao.clearSuggestedTagsForEntry(entryId)
        dao.deleteRelationshipsForEntry(entryId)
        dao.deleteMediaForEntry(entryId)
        dao.deletePagesForEntry(entryId)
        dao.deleteAudioRecordsForEntry(entryId)
        dao.deleteEntryById(entryId)
        // Clean up orphaned entities/concepts and tags so they don't remain when notes are deleted
        dao.pruneOrphanEntities()
        dao.pruneOrphanTags()
    }

    suspend fun removeTagFromEntry(entryId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        dao.removeTagFromEntry(entryId, tagId)
        dao.pruneOrphanTags()
        semanticScope.launch {
            processSemanticIntelligence(entryId)
        }
    }

    /**
     * Accept a suggested tag: marks as ACCEPTED and adds to permanent tags.
     */
    suspend fun acceptSuggestedTag(suggestedTag: SuggestedTag) = withContext(Dispatchers.IO) {
        dao.updateSuggestedTagStatus(suggestedTag.id, "ACCEPTED")
        val existingTag = dao.getTagByNormalized(suggestedTag.normalizedName)
        val tagId = existingTag?.id ?: dao.insertTag(
            Tag(name = suggestedTag.name, normalizedName = suggestedTag.normalizedName, source = "AUTO", confidence = suggestedTag.confidence)
        )
        dao.insertEntryTagCrossRef(EntryTagCrossRef(entryId = suggestedTag.entryId, tagId = tagId, confidence = suggestedTag.confidence, source = "AUTO"))
    }

    /**
     * Dismiss a suggested tag.
     */
    suspend fun dismissSuggestedTag(suggestedTagId: Long) = withContext(Dispatchers.IO) {
        dao.updateSuggestedTagStatus(suggestedTagId, "DISMISSED")
    }

    /**
     * Associates a detected face in a media item with a Person Entity.
     */
    suspend fun associateFaceWithPerson(mediaId: Long, faceIndex: Int, personEntityId: Long) = withContext(Dispatchers.IO) {
        dao.insertFacePersonAssociation(
            FacePersonAssociation(
                mediaId = mediaId,
                faceIndex = faceIndex,
                personEntityId = personEntityId
            )
        )
    }

    /**
     * Background Semantic Pipeline (ML Kit + MindForger).
     * Extracts entities & precise text offsets, creates suggested tags, computes explainable links, and clusters events.
     */
    suspend fun processSemanticIntelligence(entryId: Long) = withContext(Dispatchers.IO) {
        val entry = dao.getEntryById(entryId) ?: return@withContext
        val allEntries = dao.getAllEntriesSnapshot()
        val pages = dao.getPagesForEntrySnapshot(entryId)
        val audioRecords = dao.getAudioRecordsForEntrySnapshot(entryId)
        val mediaList = dao.getMediaForEntry(entryId).first()

        val fullText = buildString {
            append(entry.title).append("\n").append(entry.body)
            pages.forEach { p ->
                append("\n\n").append(p.title).append("\n").append(p.body)
            }
            audioRecords.forEach { a ->
                if (a.transcript.isNotBlank()) {
                    append("\n\nVoice: ").append(a.transcript)
                }
            }
            mediaList.forEach { m ->
                if (m.ocrText.isNotBlank()) {
                    append("\n\nPhoto Text: ").append(m.ocrText)
                }
            }
        }

        val fullHash = MindForgerSemanticEngine.computeContentHash(entry.title, fullText)
        if (fullHash == entry.processedContentHash && entry.processedContentHash.isNotBlank()) {
            // Unchanged content, skip re-processing to save battery
            return@withContext
        }

        // 1. Language Detection via MLKit
        val detectedLang = MlKitAnalyzer.identifyLanguage(fullText)

        // 2. ML Kit + MindForger Entity Extraction
        val mlKitEntities = MlKitAnalyzer.extractEntitiesWithMlKit(fullText)
        val ruleBasedMentions = MindForgerSemanticEngine.extractEntitiesWithOffsets(fullText)

        // Clear existing derived metadata for this entry
        dao.clearEntitiesForEntry(entryId)
        dao.clearMentionsForEntry(entryId)
        dao.clearSuggestedTagsForEntry(entryId)

        val insertedEntityIds = mutableListOf<Long>()
        val mentionsToInsert = mutableListOf<EntityMention>()

        // Process Rule-Based Entities (People, Places, Organizations, Projects, Topics)
        ruleBasedMentions.forEach { draft ->
            val existing = dao.getEntityByCanonicalAndType(draft.entity.canonicalName, draft.entity.type)
            val entityId = if (existing != null) {
                dao.updateEntity(
                    existing.copy(
                        mentionCount = existing.mentionCount + 1,
                        lastSeen = System.currentTimeMillis()
                    )
                )
                existing.id
            } else {
                dao.insertEntity(draft.entity)
            }
            insertedEntityIds.add(entityId)
            dao.insertEntryEntityCrossRef(EntryEntityCrossRef(entryId = entryId, entityId = entityId, confidence = draft.confidence))

            mentionsToInsert.add(
                EntityMention(
                    entryId = entryId,
                    entityId = entityId,
                    startOffset = draft.startOffset,
                    endOffset = draft.endOffset,
                    rawText = draft.rawText,
                    normalizedValue = draft.normalizedValue,
                    confidence = draft.confidence
                )
            )
        }

        // Process ML Kit Entities (Dates, Money, URLs, Emails, Phones, Addresses)
        mlKitEntities.forEach { mlEntity ->
            val canonical = mlEntity.normalizedValue.ifBlank { mlEntity.rawText }.lowercase(Locale.ROOT)
            val existing = dao.getEntityByCanonicalAndType(canonical, mlEntity.type)
            val entityId = if (existing != null) {
                dao.updateEntity(existing.copy(mentionCount = existing.mentionCount + 1, lastSeen = System.currentTimeMillis()))
                existing.id
            } else {
                dao.insertEntity(
                    EntityItem(
                        canonicalName = canonical,
                        displayName = mlEntity.rawText,
                        type = mlEntity.type,
                        mentionCount = 1
                    )
                )
            }
            insertedEntityIds.add(entityId)
            dao.insertEntryEntityCrossRef(EntryEntityCrossRef(entryId = entryId, entityId = entityId, confidence = mlEntity.confidence))

            mentionsToInsert.add(
                EntityMention(
                    entryId = entryId,
                    entityId = entityId,
                    startOffset = mlEntity.startOffset,
                    endOffset = mlEntity.endOffset,
                    rawText = mlEntity.rawText,
                    normalizedValue = mlEntity.normalizedValue,
                    confidence = mlEntity.confidence
                )
            )
        }

        if (mentionsToInsert.isNotEmpty()) {
            dao.insertEntityMentions(mentionsToInsert)
        }

        // 3. Keyword Extraction & Suggested Tags (kept PENDING for user review)
        val currentEntities = dao.getEntitiesForEntry(entryId)
        val keywords = MindForgerSemanticEngine.extractKeywords(entry, allEntries)
        val imageLabels = mediaList.flatMap {
            try {
                val arr = JSONArray(it.labelsJson)
                (0 until arr.length()).map { i -> arr.getString(i) }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val suggestedTags = MindForgerSemanticEngine.generateSuggestedTags(entry, currentEntities, keywords, imageLabels)
        dao.insertSuggestedTags(suggestedTags)

        // 4. Semantic Linking / Relationship Discovery
        val currentTags = dao.getTagsForEntry(entryId)
        val currentOcr = mediaList.joinToString(" ") { it.ocrText }

        dao.deleteRelationshipsForEntry(entryId)

        allEntries.filter { it.id != entryId }.forEach { otherEntry ->
            val otherEntities = dao.getEntitiesForEntry(otherEntry.id)
            val otherTags = dao.getTagsForEntry(otherEntry.id)
            val otherMedia = dao.getMediaForEntry(otherEntry.id).first()
            val otherOcr = otherMedia.joinToString(" ") { it.ocrText }
            val otherLabels = otherMedia.flatMap {
                try {
                    val arr = JSONArray(it.labelsJson)
                    (0 until arr.length()).map { i -> arr.getString(i) }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val rel = MindForgerSemanticEngine.computeRelationship(
                source = entry,
                target = otherEntry,
                sourceEntities = currentEntities,
                targetEntities = otherEntities,
                sourceTags = currentTags,
                targetTags = otherTags,
                sourceOcr = currentOcr,
                targetOcr = otherOcr,
                sourceLabels = imageLabels,
                targetLabels = otherLabels
            )

            if (rel != null) {
                dao.insertRelationship(rel)
            }
        }

        // 5. Update Episodic Events
        val entitiesMap = mutableMapOf<Long, List<EntityItem>>()
        allEntries.forEach { e -> entitiesMap[e.id] = dao.getEntitiesForEntry(e.id) }
        val events = MindForgerSemanticEngine.detectEvents(allEntries, entitiesMap)
        dao.clearAllEvents()
        events.forEach { dao.insertEvent(it) }

        // Mark as processed
        dao.updateEntry(
            entry.copy(
                language = detectedLang,
                processedContentHash = fullHash
            )
        )
    }

    /**
     * Completely rebuilds all derived semantic metadata from scratch.
     * Original journal text is preserved untouched.
     */
    suspend fun rebuildAllSemanticMetadata() = withContext(Dispatchers.IO) {
        dao.clearAllEntityMentions()
        dao.clearAllEntryEntities()
        dao.clearAllRelationships()
        dao.clearAllSuggestedTags()
        dao.clearAllEvents()
        dao.resetAllProcessedContentHashes()

        val allEntries = dao.getAllEntriesSnapshot()
        allEntries.forEach { entry ->
            processSemanticIntelligence(entry.id)
        }
    }

    /**
     * Smart Hybrid Search combining Exact Text, BM25 tokens, Tags, Extracted Entities, Multi-pages, Voice Transcripts, and OCR.
     * Supports metadata filters like `place:Miami`, `after:2024`, `before:2026`, `type:audio`, `type:photo`.
     */
    suspend fun searchHybrid(query: String): List<HybridSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        var placeFilter: String? = null
        var afterYear: Int? = null
        var beforeYear: Int? = null
        var typeFilter: String? = null

        val tokensList = trimmed.split(Regex("\\s+")).toMutableList()
        val iterator = tokensList.iterator()
        while (iterator.hasNext()) {
            val token = iterator.next()
            if (token.startsWith("place:", ignoreCase = true)) {
                placeFilter = token.substring(6).trim().lowercase(Locale.ROOT)
                iterator.remove()
            } else if (token.startsWith("after:", ignoreCase = true)) {
                afterYear = token.substring(6).toIntOrNull()
                iterator.remove()
            } else if (token.startsWith("before:", ignoreCase = true)) {
                beforeYear = token.substring(7).toIntOrNull()
                iterator.remove()
            } else if (token.startsWith("type:", ignoreCase = true)) {
                typeFilter = token.substring(5).trim().lowercase(Locale.ROOT)
                iterator.remove()
            }
        }

        val cleanSearch = tokensList.joinToString(" ").trim()
        val allEntries = dao.getAllEntriesWithRelations().first()
        val queryTokens = if (cleanSearch.isNotBlank()) MindForgerSemanticEngine.tokenize(cleanSearch).toSet() else emptySet()

        val results = mutableListOf<HybridSearchResult>()

        allEntries.forEach { item ->
            val entry = item.entry

            // Apply metadata filters
            if (placeFilter != null) {
                val matchesLocation = entry.location?.lowercase(Locale.ROOT)?.contains(placeFilter) == true
                val matchesEntityPlace = item.entities.any { (it.type == EntityType.PLACE || it.type == EntityType.ADDRESS) && it.canonicalName.contains(placeFilter) }
                if (!matchesLocation && !matchesEntityPlace) return@forEach
            }

            if (afterYear != null || beforeYear != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = entry.journalDate }
                val entryYear = cal.get(Calendar.YEAR)
                if (afterYear != null && entryYear < afterYear) return@forEach
                if (beforeYear != null && entryYear > beforeYear) return@forEach
            }

            if (typeFilter != null) {
                if (typeFilter == "audio" && item.audioRecords.isEmpty()) return@forEach
                if (typeFilter == "photo" && item.mediaItems.isEmpty() && item.entry.imageUri.isNullOrBlank()) return@forEach
            }

            var score = 0.0f
            val reasons = mutableListOf<String>()
            val matchedTags = mutableListOf<String>()
            val matchedEntities = mutableListOf<String>()
            val matchedOcrTerms = mutableListOf<String>()

            if (cleanSearch.isBlank()) {
                // Query was only metadata filters
                score = 0.85f
                reasons.add("Matches filter")
            } else {
                val lowerTitle = entry.title.lowercase(Locale.ROOT)
                val lowerBody = entry.body.lowercase(Locale.ROOT)
                val searchLower = cleanSearch.lowercase(Locale.ROOT)

                // 1. Exact match in title
                if (lowerTitle.contains(searchLower)) {
                    score += 0.50f
                    reasons.add("Exact title match")
                }

                // 2. Exact match in body
                if (lowerBody.contains(searchLower)) {
                    score += 0.35f
                    reasons.add("Exact body match")
                }

                // 3. Multi-page match
                item.pages.forEach { page ->
                    if (page.title.lowercase(Locale.ROOT).contains(searchLower) || page.body.lowercase(Locale.ROOT).contains(searchLower)) {
                        score += 0.30f
                        reasons.add("In section: ${page.title}")
                    }
                }

                // 4. Voice transcript match
                item.audioRecords.forEach { audio ->
                    if (audio.transcript.lowercase(Locale.ROOT).contains(searchLower)) {
                        score += 0.35f
                        reasons.add("In voice note")
                    }
                }

                // 5. Token overlap (BM25 keyword matching)
                val allTokens = MindForgerSemanticEngine.tokenize("${entry.title} ${entry.body} ${item.pages.joinToString(" ") { it.body }}").toSet()
                val sharedTokens = queryTokens.intersect(allTokens)
                if (sharedTokens.isNotEmpty()) {
                    val tokenScore = (sharedTokens.size.toFloat() / queryTokens.size.coerceAtLeast(1)) * 0.40f
                    score += tokenScore
                    reasons.add("Keywords: ${sharedTokens.joinToString(", ")}")
                }

                // 6. Entity match
                item.entities.forEach { entity ->
                    if (entity.displayName.lowercase(Locale.ROOT).contains(searchLower) ||
                        queryTokens.any { entity.canonicalName.contains(it) }
                    ) {
                        score += 0.38f
                        matchedEntities.add(entity.displayName)
                        reasons.add("Entity: ${entity.displayName} (${entity.type.name})")
                    }
                }

                // 7. Tag match
                item.tags.forEach { tag ->
                    if (tag.name.lowercase(Locale.ROOT).contains(searchLower) ||
                        queryTokens.any { tag.normalizedName.contains(it) }
                    ) {
                        score += 0.30f
                        matchedTags.add("#${tag.name}")
                        reasons.add("Tag: #${tag.name}")
                    }
                }

                // 8. OCR Text match
                item.mediaItems.forEach { media ->
                    if (media.ocrText.lowercase(Locale.ROOT).contains(searchLower) ||
                        media.caption.lowercase(Locale.ROOT).contains(searchLower)
                    ) {
                        score += 0.28f
                        matchedOcrTerms.add(cleanSearch)
                        reasons.add("In photo text/caption")
                    }
                }
            }

            if (score > 0.15f) {
                val snippetText = if (entry.body.isNotBlank()) entry.body else item.pages.firstOrNull()?.body ?: ""
                val snippet = snippetText.take(120).replace("\n", " ") + (if (snippetText.length > 120) "..." else "")

                results.add(
                    HybridSearchResult(
                        entry = entry,
                        matchedScore = score.coerceAtMost(1.0f),
                        matchedReason = reasons.distinct().take(3).joinToString(" • "),
                        snippet = snippet,
                        matchedTags = matchedTags,
                        matchedEntities = matchedEntities,
                        matchedOcrTerms = matchedOcrTerms
                    )
                )
            }
        }

        results.sortedByDescending { it.matchedScore }
    }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val entries = dao.getAllEntriesSnapshot()
        val allMedia = dao.getAllMediaSnapshot()
        val allAudio = dao.getAllAudioRecordsSnapshot()
        val allEntities = dao.getAllEntitiesSnapshot()
        val allRel = dao.getAllRelationshipsSnapshot()

        var textBytes = 0L
        entries.forEach {
            textBytes += (it.title.length + it.body.length) * 2L
        }

        var photoBytes = 0L
        allMedia.forEach { m ->
            try {
                if (m.uri.startsWith("file://") || m.uri.startsWith("/")) {
                    val path = m.uri.removePrefix("file://")
                    val f = File(path)
                    if (f.exists()) photoBytes += f.length()
                }
            } catch (_: Exception) {}
        }

        var audioBytes = 0L
        allAudio.forEach { a ->
            try {
                val f = File(a.filePath)
                if (f.exists()) audioBytes += f.length()
            } catch (_: Exception) {}
        }

        var ocrChars = 0
        allMedia.forEach { ocrChars += it.ocrText.length }

        val dbFile = context.getDatabasePath("mnemosyne_journal.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

        StorageBreakdown(
            entryCount = entries.size,
            pageCount = 0,
            textEstimatedBytes = textBytes,
            photoCount = allMedia.size,
            photoBytes = photoBytes,
            audioCount = allAudio.size,
            audioBytes = audioBytes,
            entityCount = allEntities.size,
            relationshipCount = allRel.size,
            ocrCharCount = ocrChars,
            databaseBytes = dbBytes
        )
    }

    suspend fun clearOcrCache() = withContext(Dispatchers.IO) {
        val allMedia = dao.getAllMediaSnapshot()
        allMedia.forEach { m ->
            if (m.ocrText.isNotBlank()) {
                dao.updateMediaItem(m.copy(ocrText = ""))
            }
        }
    }

    /**
     * Builds data for interactive 2D Knowledge Graph visualization.
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
            item.entities.take(5).forEach { entity ->
                val entityNodeId = "entity_${entity.id}"
                if (seenNodeIds.add(entityNodeId)) {
                    val nodeType = when (entity.type) {
                        EntityType.PERSON -> GraphNodeType.PERSON
                        EntityType.PLACE, EntityType.ADDRESS -> GraphNodeType.PLACE
                        EntityType.ORGANIZATION -> GraphNodeType.ORGANIZATION
                        EntityType.PROJECT -> GraphNodeType.PROJECT
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

        processSemanticIntelligence(id1)
        processSemanticIntelligence(id2)
        processSemanticIntelligence(id3)
        processSemanticIntelligence(id4)
    }
}
