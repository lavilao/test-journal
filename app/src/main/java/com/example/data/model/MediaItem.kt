package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["entryId", "sortOrder"])
    ]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val uri: String,
    val mimeType: String = "image/jpeg",
    val labelsJson: String = "", // JSON list of detected labels
    val faceCount: Int = 0,
    val ocrText: String = "",
    val caption: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
