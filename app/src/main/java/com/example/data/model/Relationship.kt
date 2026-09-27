package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RelationshipType {
    SHARED_ENTITY,
    SHARED_TOPIC,
    SHARED_TAG,
    TEMPORALLY_PROXIMATE,
    SEMANTIC_SIMILARITY
}

@Entity(
    tableName = "relationships",
    indices = [
        Index(value = ["sourceEntryId"]),
        Index(value = ["targetEntryId"]),
        Index(value = ["sourceEntryId", "targetEntryId"], unique = true)
    ]
)
data class Relationship(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceEntryId: Long,
    val targetEntryId: Long,
    val relationshipType: RelationshipType,
    val score: Float, // 0.0 to 1.0
    val explanation: String,
    val createdAt: Long = System.currentTimeMillis()
)
