package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.EntryEmbedding

@Dao
interface EntryEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: EntryEmbedding)

    @Query("SELECT * FROM entry_embeddings WHERE entryId = :entryId LIMIT 1")
    suspend fun forEntry(entryId: Long): EntryEmbedding?

    @Query("SELECT * FROM entry_embeddings")
    suspend fun all(): List<EntryEmbedding>

    @Query("SELECT entryId FROM entry_embeddings")
    suspend fun allEntryIds(): List<Long>

    @Query("SELECT COUNT(*) FROM entry_embeddings")
    suspend fun count(): Int

    @Query("DELETE FROM entry_embeddings WHERE entryId NOT IN (:alive)")
    suspend fun prune(alive: List<Long>)

    @Query("DELETE FROM entry_embeddings WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)
}
