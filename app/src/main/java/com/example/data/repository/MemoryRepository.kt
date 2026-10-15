package com.example.data.repository

import com.example.data.local.dao.MemoryDao
import com.example.data.local.model.MemoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MemoryRepository {
    fun getAllMemories(): Flow<List<MemoryEntity>>
    fun getConfirmedMemories(): Flow<List<MemoryEntity>>
    suspend fun getConfirmedMemoriesList(): List<MemoryEntity>
    fun getCandidateMemories(): Flow<List<MemoryEntity>>
    fun searchMemories(query: String): Flow<List<MemoryEntity>>
    suspend fun addMemory(
        key: String, 
        content: String, 
        category: String = "general", 
        isConfirmed: Boolean = true,
        sourceMessageId: String? = null,
        sourceConversationId: String? = null
    ): MemoryEntity
    suspend fun updateMemory(memory: MemoryEntity)
    suspend fun toggleMemory(id: String, isEnabled: Boolean)
    suspend fun confirmMemory(id: String)
    suspend fun deleteMemory(id: String)
    suspend fun clearAllMemories()
    fun getMemoryCount(): Flow<Int>
    suspend fun getFormattedMemoryContext(): String
}

class LocalMemoryRepository(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun getAllMemories(): Flow<List<MemoryEntity>> =
        memoryDao.getAllMemories()

    override fun getConfirmedMemories(): Flow<List<MemoryEntity>> = memoryDao.getConfirmedMemories()
    override suspend fun getConfirmedMemoriesList(): List<MemoryEntity> = memoryDao.getConfirmedMemoriesList()
    override fun getCandidateMemories(): Flow<List<MemoryEntity>> = memoryDao.getCandidateMemories()
    override fun searchMemories(query: String): Flow<List<MemoryEntity>> = memoryDao.searchMemories(query)

    override suspend fun addMemory(
        key: String, 
        content: String, 
        category: String, 
        isConfirmed: Boolean,
        sourceMessageId: String?,
        sourceConversationId: String?
    ): MemoryEntity {
        val memory = MemoryEntity(
            id = UUID.randomUUID().toString(),
            key = key,
            content = content,
            category = category,
            createdAt = System.currentTimeMillis(),
            isEnabled = true,
            isConfirmed = isConfirmed,
            sourceMessageId = sourceMessageId,
            sourceConversationId = sourceConversationId
        )
        memoryDao.insertMemory(memory)
        return memory
    }

    override suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory)
    }

    override suspend fun toggleMemory(id: String, isEnabled: Boolean) {
        memoryDao.toggleMemory(id, isEnabled)
    }

    override suspend fun confirmMemory(id: String) {
        memoryDao.confirmMemory(id)
    }

    override suspend fun deleteMemory(id: String) {
        memoryDao.deleteMemoryById(id)
    }

    override suspend fun clearAllMemories() {
        memoryDao.deleteAllMemories()
    }

    override fun getMemoryCount(): Flow<Int> = memoryDao.getMemoryCount()

    override suspend fun getFormattedMemoryContext(): String {
        val active = memoryDao.getConfirmedMemoriesList()
        if (active.isEmpty()) return ""
        return buildString {
            append("User Persistent Memory & Preferences:\n")
            active.forEach { memory ->
                append("- [${memory.key}]: ${memory.content}\n")
            }
        }
    }
}
