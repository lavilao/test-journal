package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_pages",
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["entryId", "pageIndex"])
    ]
)
data class JournalPage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val pageIndex: Int,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val contentHash: String = "",
    val processedContentHash: String = ""
)
