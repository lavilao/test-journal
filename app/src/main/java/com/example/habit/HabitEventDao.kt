package com.example.habit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HabitEventDao {

    @Insert
    suspend fun insertAll(events: List<HabitEvent>)

    @Insert
    suspend fun insert(event: HabitEvent): Long

    @Query("SELECT * FROM habit_events WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<HabitEvent>

    @Query("SELECT COUNT(*) FROM habit_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM habit_events WHERE type = :type")
    suspend fun countType(type: String): Int

    @Query("DELETE FROM habit_events WHERE timestamp < :before")
    suspend fun prune(before: Long): Int

    @Query("DELETE FROM habit_events")
    suspend fun clearAll()
}
