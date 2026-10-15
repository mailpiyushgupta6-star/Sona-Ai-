package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isEnabled = 1 AND isConfirmed = 1 ORDER BY createdAt DESC")
    fun getConfirmedMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isEnabled = 1 AND isConfirmed = 1")
    suspend fun getConfirmedMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE isEnabled = 1 AND isConfirmed = 0 ORDER BY createdAt DESC")
    fun getCandidateMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isEnabled = 1")
    suspend fun getActiveMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%'")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleMemory(id: String, isEnabled: Boolean)
    
    @Query("UPDATE memories SET isConfirmed = 1 WHERE id = :id")
    suspend fun confirmMemory(id: String)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Query("SELECT COUNT(*) FROM memories")
    fun getMemoryCount(): Flow<Int>
}
