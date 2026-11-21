package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["journalDate"]),
        Index(value = ["createdAt"]),
        Index(value = ["contentHash"]),
        Index(value = ["parentEntryId"])
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
    val mood: String? = null,
    val language: String? = "es",
    val wordCount: Int = 0,
    val contentHash: String = "",
    val processedContentHash: String = "",
    val isFavorite: Boolean = false,
    val imageUri: String? = null,
    // Notion-style workspace hierarchy & database properties (v8)
    @ColumnInfo(defaultValue = "NULL")
    val parentEntryId: Long? = null,
    @ColumnInfo(defaultValue = "'📄'")
    val iconEmoji: String = "📄",
    @ColumnInfo(defaultValue = "NULL")
    val coverColorHex: String? = null,
    @ColumnInfo(defaultValue = "'PAGE'")
    val pageType: String = "PAGE",
    @ColumnInfo(defaultValue = "'Inbox'")
    val status: String = "Inbox",
    @ColumnInfo(defaultValue = "'Normal'")
    val priority: String = "Normal",
    @ColumnInfo(defaultValue = "NULL")
    val dueDate: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)
