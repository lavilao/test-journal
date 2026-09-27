package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.AudioRecordItem
import com.example.data.model.EntityItem
import com.example.data.model.EntityMention
import com.example.data.model.EntityType
import com.example.data.model.EntityWithEntries
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.EntryWithRelations
import com.example.data.model.EventItem
import com.example.data.model.FacePersonAssociation
import com.example.data.model.JournalEntry
import com.example.data.model.JournalPage
import com.example.data.model.MediaItem
import com.example.data.model.Relationship
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    // === Entries ===
    @Query("SELECT * FROM journal_entries ORDER BY journalDate DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY journalDate DESC")
    fun getAllEntriesWithRelations(): Flow<List<EntryWithRelations>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun getEntryWithRelationsFlow(id: Long): Flow<EntryWithRelations?>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryWithRelations(id: Long): EntryWithRelations?

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): JournalEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry): Long

    @Update
    suspend fun updateEntry(entry: JournalEntry)

    @Delete
    suspend fun deleteEntry(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun getEntryCount(): Int

    @Query("SELECT * FROM journal_entries ORDER BY journalDate DESC")
    suspend fun getAllEntriesSnapshot(): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE journalDate BETWEEN :startTime AND :endTime ORDER BY journalDate DESC")
    suspend fun getEntriesInRange(startTime: Long, endTime: Long): List<JournalEntry>

    // === Tags ===
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalized LIMIT 1")
    suspend fun getTagByNormalized(normalized: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryTagCrossRef(crossRef: EntryTagCrossRef)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun clearTagsForEntry(entryId: Long)

    @Query("SELECT t.* FROM tags t INNER JOIN entry_tags et ON t.id = et.tagId WHERE et.entryId = :entryId")
    suspend fun getTagsForEntry(entryId: Long): List<Tag>

    // === Suggested Tags ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestedTag(tag: SuggestedTag): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestedTags(tags: List<SuggestedTag>)

    @Query("SELECT * FROM suggested_tags WHERE entryId = :entryId ORDER BY confidence DESC")
    fun getSuggestedTagsForEntry(entryId: Long): Flow<List<SuggestedTag>>

    @Query("UPDATE suggested_tags SET status = :status WHERE id = :id")
    suspend fun updateSuggestedTagStatus(id: Long, status: String)

    @Query("DELETE FROM suggested_tags WHERE entryId = :entryId")
    suspend fun clearSuggestedTagsForEntry(entryId: Long)

    // === Entities ===
    @Query("SELECT * FROM entities ORDER BY mentionCount DESC, canonicalName ASC")
    fun getAllEntities(): Flow<List<EntityItem>>

    @Query("SELECT * FROM entities WHERE type = :type ORDER BY mentionCount DESC")
    fun getEntitiesByType(type: EntityType): Flow<List<EntityItem>>

    @Query("SELECT * FROM entities WHERE canonicalName = :canonical AND type = :type LIMIT 1")
    suspend fun getEntityByCanonicalAndType(canonical: String, type: EntityType): EntityItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntity(entity: EntityItem): Long

    @Update
    suspend fun updateEntity(entity: EntityItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryEntityCrossRef(crossRef: EntryEntityCrossRef)

    @Query("DELETE FROM entry_entities WHERE entryId = :entryId")
    suspend fun clearEntitiesForEntry(entryId: Long)

    @Transaction
    @Query("SELECT * FROM entities WHERE id = :entityId LIMIT 1")
    fun getEntityWithEntries(entityId: Long): Flow<EntityWithEntries?>

    @Query("SELECT e.* FROM entities e INNER JOIN entry_entities ee ON e.id = ee.entityId WHERE ee.entryId = :entryId")
    suspend fun getEntitiesForEntry(entryId: Long): List<EntityItem>

    @Query("SELECT * FROM entities ORDER BY lastSeen DESC")
    suspend fun getAllEntitiesSnapshot(): List<EntityItem>

    // Forgotten threads: recurring entities (>= 2 mentions) where lastSeen is older than cutoff
    @Query("SELECT * FROM entities WHERE mentionCount >= :minMentions AND lastSeen < :cutoffTimestamp ORDER BY lastSeen ASC")
    fun getForgottenThreads(cutoffTimestamp: Long, minMentions: Int = 2): Flow<List<EntityItem>>

    // === Entity Mentions (Offsets & In-Text Annotations) ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntityMention(mention: EntityMention): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntityMentions(mentions: List<EntityMention>)

    @Query("SELECT * FROM entity_mentions WHERE entryId = :entryId ORDER BY startOffset ASC")
    fun getMentionsForEntry(entryId: Long): Flow<List<EntityMention>>

    @Query("SELECT * FROM entity_mentions WHERE entryId = :entryId ORDER BY startOffset ASC")
    suspend fun getMentionsForEntrySnapshot(entryId: Long): List<EntityMention>

    @Query("DELETE FROM entity_mentions WHERE entryId = :entryId")
    suspend fun clearMentionsForEntry(entryId: Long)

    @Query("SELECT * FROM entity_mentions WHERE entityId = :entityId")
    fun getMentionsForEntity(entityId: Long): Flow<List<EntityMention>>

    // === Relationships ===
    @Query("SELECT * FROM relationships WHERE sourceEntryId = :entryId OR targetEntryId = :entryId ORDER BY score DESC")
    fun getRelationshipsForEntry(entryId: Long): Flow<List<Relationship>>

    @Query("SELECT * FROM relationships ORDER BY score DESC")
    fun getAllRelationships(): Flow<List<Relationship>>

    @Query("SELECT * FROM relationships")
    suspend fun getAllRelationshipsSnapshot(): List<Relationship>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: Relationship): Long

    @Query("DELETE FROM relationships WHERE sourceEntryId = :entryId OR targetEntryId = :entryId")
    suspend fun deleteRelationshipsForEntry(entryId: Long)

    // === Media ===
    @Query("SELECT * FROM media_items WHERE entryId = :entryId")
    fun getMediaForEntry(entryId: Long): Flow<List<MediaItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItem): Long

    @Query("DELETE FROM media_items WHERE entryId = :entryId")
    suspend fun deleteMediaForEntry(entryId: Long)

    @Query("SELECT m.* FROM media_items m INNER JOIN face_person_associations fpa ON m.id = fpa.mediaId WHERE fpa.personEntityId = :personEntityId")
    fun getMediaForPerson(personEntityId: Long): Flow<List<MediaItem>>

    // === Face Person Associations ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacePersonAssociation(association: FacePersonAssociation): Long

    @Query("SELECT * FROM face_person_associations WHERE mediaId = :mediaId")
    fun getFaceAssociationsForMedia(mediaId: Long): Flow<List<FacePersonAssociation>>

    @Query("DELETE FROM face_person_associations WHERE mediaId = :mediaId AND faceIndex = :faceIndex")
    suspend fun deleteFaceAssociation(mediaId: Long, faceIndex: Int)

    // === Events (Timeline Grouping) ===
    @Query("SELECT * FROM events ORDER BY startDate DESC")
    fun getAllEvents(): Flow<List<EventItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventItem): Long

    @Query("DELETE FROM events")
    suspend fun clearAllEvents()

    // === Rebuild Derived Metadata ===
    @Query("DELETE FROM entity_mentions")
    suspend fun clearAllEntityMentions()

    @Query("DELETE FROM entry_entities")
    suspend fun clearAllEntryEntities()

    @Query("DELETE FROM relationships")
    suspend fun clearAllRelationships()

    @Query("DELETE FROM suggested_tags")
    suspend fun clearAllSuggestedTags()

    @Query("UPDATE journal_entries SET processedContentHash = ''")
    suspend fun resetAllProcessedContentHashes()

    // === Multi-page Support ===
    @Query("SELECT * FROM journal_pages WHERE entryId = :entryId ORDER BY pageIndex ASC")
    fun getPagesForEntry(entryId: Long): Flow<List<JournalPage>>

    @Query("SELECT * FROM journal_pages WHERE entryId = :entryId ORDER BY pageIndex ASC")
    suspend fun getPagesForEntrySnapshot(entryId: Long): List<JournalPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: JournalPage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<JournalPage>)

    @Update
    suspend fun updatePage(page: JournalPage)

    @Delete
    suspend fun deletePage(page: JournalPage)

    @Query("DELETE FROM journal_pages WHERE entryId = :entryId")
    suspend fun deletePagesForEntry(entryId: Long)

    @Query("DELETE FROM journal_pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)

    // === Audio Records (Voice Journal) ===
    @Query("SELECT * FROM audio_records WHERE entryId = :entryId ORDER BY createdAt DESC")
    fun getAudioRecordsForEntry(entryId: Long): Flow<List<AudioRecordItem>>

    @Query("SELECT * FROM audio_records WHERE entryId = :entryId ORDER BY createdAt DESC")
    suspend fun getAudioRecordsForEntrySnapshot(entryId: Long): List<AudioRecordItem>

    @Query("SELECT * FROM audio_records")
    suspend fun getAllAudioRecordsSnapshot(): List<AudioRecordItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioRecord(item: AudioRecordItem): Long

    @Update
    suspend fun updateAudioRecord(item: AudioRecordItem)

    @Query("DELETE FROM audio_records WHERE id = :id")
    suspend fun deleteAudioRecordById(id: Long)

    @Query("DELETE FROM audio_records WHERE entryId = :entryId")
    suspend fun deleteAudioRecordsForEntry(entryId: Long)

    // === Media Item Updates ===
    @Update
    suspend fun updateMediaItem(item: MediaItem)

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): MediaItem?

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: Long)

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaSnapshot(): List<MediaItem>
}
