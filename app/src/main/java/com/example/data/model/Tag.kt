package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val source: String = "AUTO", // "MANUAL" or "AUTO"
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["tagId"])
    ]
)
data class EntryTagCrossRef(
    val entryId: Long,
    val tagId: Long,
    val confidence: Float = 1.0f,
    val source: String = "AUTO"
)
