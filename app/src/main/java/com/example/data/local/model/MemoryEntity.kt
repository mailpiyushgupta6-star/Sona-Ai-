package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val key: String, // Short summary or topic e.g. "User preferred name", "Work context"
    val content: String,
    val category: String = "general", // "preference", "fact", "custom"
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val isConfirmed: Boolean = true,
    val sourceMessageId: String? = null,
    val sourceConversationId: String? = null
)
