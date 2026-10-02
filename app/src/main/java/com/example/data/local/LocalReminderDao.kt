package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LocalReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalReminderDao {
    @Query("SELECT * FROM local_reminders ORDER BY isCompleted ASC, dueTimestamp ASC")
    fun getAllReminders(): Flow<List<LocalReminder>>

    @Query("SELECT * FROM local_reminders WHERE isCompleted = 0 ORDER BY dueTimestamp ASC LIMIT 10")
    fun getActiveReminders(): Flow<List<LocalReminder>>

    @Query("SELECT * FROM local_reminders WHERE isCompleted = 0 ORDER BY dueTimestamp ASC LIMIT 1")
    fun getNextActiveReminder(): Flow<LocalReminder?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: LocalReminder): Long

    @Update
    suspend fun updateReminder(reminder: LocalReminder)

    @Query("UPDATE local_reminders SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean)

    @Delete
    suspend fun deleteReminder(reminder: LocalReminder)

    @Query("DELETE FROM local_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)
}
