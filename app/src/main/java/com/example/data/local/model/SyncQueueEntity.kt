package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey
    val operationId: String = UUID.randomUUID().toString(),
    val uid: String,
    val entityType: String, // "CONVERSATION", "MESSAGE", "MEMORY"
    val entityId: String,
    val operationType: String, // "CREATE", "UPDATE", "DELETE"
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val nextRetryTime: Long = 0L,
    val status: String = "PENDING", // "PENDING", "PROCESSING", "COMPLETED", "FAILED"
    val lastError: String? = null
)
