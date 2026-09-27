package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntityType {
    PERSON,
    PLACE,
    ORGANIZATION,
    EVENT,
    DATE,
    TOPIC,
    URL,
    OTHER
}

@Entity(
    tableName = "entities",
    indices = [
        Index(value = ["canonicalName", "type"], unique = true),
        Index(value = ["type"])
    ]
)
data class EntityItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalName: String,
    val displayName: String,
    val type: EntityType,
    val mentionCount: Int = 1,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "entry_entities",
    primaryKeys = ["entryId", "entityId"],
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["entityId"])
    ]
)
data class EntryEntityCrossRef(
    val entryId: Long,
    val entityId: Long,
    val confidence: Float = 1.0f,
    val mentionCount: Int = 1
)
