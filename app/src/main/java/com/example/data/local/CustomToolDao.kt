package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomTool
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomToolDao {

    @Query("SELECT * FROM custom_tools ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomTool>>

    @Query("SELECT * FROM custom_tools ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CustomTool>

    @Query("SELECT * FROM custom_tools WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomTool?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tool: CustomTool): Long

    @Update
    suspend fun update(tool: CustomTool)

    @Query("DELETE FROM custom_tools WHERE id = :id")
    suspend fun deleteById(id: Long)
}
