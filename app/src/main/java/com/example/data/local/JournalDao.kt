package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.EntityItem
import com.example.data.model.EntityType
import com.example.data.model.EntityWithEntries
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.EntryWithRelations
import com.example.data.model.JournalEntry
import com.example.data.model.MediaItem
import com.example.data.model.Relationship
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

    @Query("SELECT * FROM journal_entries")
    suspend fun getAllEntriesSnapshot(): List<JournalEntry>

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
}
