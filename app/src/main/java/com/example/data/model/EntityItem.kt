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
    EMAIL,
    PHONE,
    ADDRESS,
    MONEY,
    PROJECT,
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
    val lastSeen: Long = System.currentTimeMillis(),
    val aliases: String = "" // comma-separated aliases for autolinking
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

@Entity(
    tableName = "entity_mentions",
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["entityId"]),
        Index(value = ["entryId", "startOffset"])
    ]
)
data class EntityMention(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val entityId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val rawText: String,
    val normalizedValue: String = "",
    val confidence: Float = 1.0f
)
