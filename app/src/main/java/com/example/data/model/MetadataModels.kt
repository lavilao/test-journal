package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suggested_tags",
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["status"])
    ]
)
data class SuggestedTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val name: String,
    val normalizedName: String,
    val confidence: Float = 0.85f,
    val status: String = "PENDING", // "PENDING", "ACCEPTED", "DISMISSED"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["endDate"])
    ]
)
data class EventItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val startDate: Long,
    val endDate: Long,
    val location: String? = null,
    val entityIdsJson: String = "[]",
    val entryIdsJson: String = "[]",
    val entryCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "face_person_associations",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["personEntityId"]),
        Index(value = ["mediaId", "faceIndex"], unique = true)
    ]
)
data class FacePersonAssociation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val faceIndex: Int,
    val personEntityId: Long,
    val associatedAt: Long = System.currentTimeMillis()
)

data class AutolinkSpan(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val entityId: Long,
    val entityName: String,
    val type: EntityType
)

data class StorageBreakdown(
    val entryCount: Int = 0,
    val pageCount: Int = 0,
    val textEstimatedBytes: Long = 0,
    val photoCount: Int = 0,
    val photoBytes: Long = 0,
    val audioCount: Int = 0,
    val audioBytes: Long = 0,
    val entityCount: Int = 0,
    val relationshipCount: Int = 0,
    val ocrCharCount: Int = 0,
    val databaseBytes: Long = 0
)
