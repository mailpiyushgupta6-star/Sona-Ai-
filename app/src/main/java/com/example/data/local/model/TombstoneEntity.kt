package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey
    val id: String, // entityId
    val entityType: String, // "CONVERSATION", "MESSAGE", "MEMORY"
    val uid: String,
    val deletedAt: Long = System.currentTimeMillis()
)
