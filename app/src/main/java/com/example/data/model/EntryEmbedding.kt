package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored Needle embedding per journal entry — the index behind the
 * on-device SEMANTIC search (true vector similarity, not just TF-IDF) and
 * the assistant's RAG memory. The vector is the model's float embedding of
 * "title + body" serialized little-endian.
 */
@Entity(
    tableName = "entry_embeddings",
    indices = [Index("updatedAt")]
)
data class EntryEmbedding(
    @PrimaryKey val entryId: Long,
    val dim: Int,
    val updatedAt: Long,
    val vector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EntryEmbedding
        if (entryId != other.entryId) return false
        if (dim != other.dim) return false
        if (updatedAt != other.updatedAt) return false
        return vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = entryId.hashCode()
        result = 31 * result + dim
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}
