package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["journalDate"]),
        Index(value = ["createdAt"]),
        Index(value = ["contentHash"])
    ]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val journalDate: Long = System.currentTimeMillis(),
    val location: String? = null,
    val mood: String? = null, // e.g. "Thoughtful", "Inspired", "Peaceful", "Focused", "Joyful"
    val language: String? = "en",
    val wordCount: Int = 0,
    val contentHash: String = "",
    val processedContentHash: String = "",
    val isFavorite: Boolean = false,
    val imageUri: String? = null
)
