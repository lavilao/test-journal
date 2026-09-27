package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_records",
    indices = [Index(value = ["entryId"])]
)
data class AudioRecordItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val title: String = "Voice Reflection",
    val filePath: String,
    val durationMs: Long = 0,
    val transcript: String = "",
    val transcriptionStatus: String = "PENDING", // "PENDING", "COMPLETED", "UNAVAILABLE", "FAILED"
    val createdAt: Long = System.currentTimeMillis()
)
