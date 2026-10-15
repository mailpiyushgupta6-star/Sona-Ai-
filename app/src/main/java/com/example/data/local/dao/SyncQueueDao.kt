package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE uid = :uid AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingOperations(uid: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE uid = :uid")
    fun observeQueue(uid: String): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncQueueEntity)

    @Update
    suspend fun updateOperation(operation: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE operationId = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("DELETE FROM sync_queue WHERE uid = :uid")
    suspend fun clearQueueForUser(uid: String)
}
