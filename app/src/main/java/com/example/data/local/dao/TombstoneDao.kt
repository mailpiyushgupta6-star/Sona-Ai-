package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.model.TombstoneEntity

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones WHERE uid = :uid")
    suspend fun getTombstonesForUser(uid: String): List<TombstoneEntity>

    @Query("SELECT COUNT(*) FROM tombstones WHERE id = :id AND uid = :uid")
    suspend fun isTombstoned(id: String, uid: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: TombstoneEntity)

    @Query("DELETE FROM tombstones WHERE uid = :uid")
    suspend fun clearTombstonesForUser(uid: String)
}
