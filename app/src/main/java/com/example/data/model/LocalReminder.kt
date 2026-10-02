package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_reminders",
    indices = [
        Index(value = ["dueTimestamp"]),
        Index(value = ["isCompleted"])
    ]
)
data class LocalReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "Personal", // Personal, Health, Finance, Projects
    val dueTimestamp: Long = System.currentTimeMillis() + 3600_000,
    val isCompleted: Boolean = false,
    val priority: Int = 1, // 0 = low, 1 = normal, 2 = high
    val createdAt: Long = System.currentTimeMillis()
)
