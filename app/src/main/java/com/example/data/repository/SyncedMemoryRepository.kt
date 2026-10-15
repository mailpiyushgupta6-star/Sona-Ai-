package com.example.data.repository

import android.util.Log
import com.example.data.local.model.MemoryEntity
import com.example.data.remote.FirestoreDataSource
import com.example.data.remote.model.CloudMemoryDto
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class SyncedMemoryRepository(
    private val localRepository: MemoryRepository,
    private val remoteDataSource: FirestoreDataSource,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MemoryRepository {

    private var activeUserId: String? = null
    private var isLocalOnlyMode: Boolean = false
    private var cloudMemoriesJob: kotlinx.coroutines.Job? = null

    fun setLocalOnly(localOnly: Boolean) {
        isLocalOnlyMode = localOnly
        if (localOnly) {
            cloudMemoriesJob?.cancel()
            cloudMemoriesJob = null
        } else {
            val uid = activeUserId
            if (uid != null) {
                setActiveUser(uid)
            }
        }
    }

    fun setActiveUser(userId: String?) {
        if (activeUserId == userId) return
        activeUserId = userId
        cloudMemoriesJob?.cancel()
        cloudMemoriesJob = null

        if (userId != null && !isLocalOnlyMode) {
            cloudMemoriesJob = scope.launch(ioDispatcher) {
                try {
                    remoteDataSource.observeMemories(userId).collect { remoteList ->
                        for (dto in remoteList) {
                            val entity = MemoryEntity(
                                id = dto.id,
                                key = dto.key,
                                content = dto.content,
                                category = dto.category,
                                isEnabled = dto.isEnabled,
                                createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                            localRepository.updateMemory(entity)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Sync", "Memories cloud sync failed: ${e.message}")
                }
            }
        }
    }

    override fun getAllMemories(): Flow<List<MemoryEntity>> =
        localRepository.getAllMemories()

    override fun getConfirmedMemories(): Flow<List<MemoryEntity>> = localRepository.getConfirmedMemories()
    override suspend fun getConfirmedMemoriesList(): List<MemoryEntity> = localRepository.getConfirmedMemoriesList()
    override fun getCandidateMemories(): Flow<List<MemoryEntity>> = localRepository.getCandidateMemories()
    override fun searchMemories(query: String): Flow<List<MemoryEntity>> = localRepository.searchMemories(query)

    override suspend fun addMemory(
        key: String, 
        content: String, 
        category: String, 
        isConfirmed: Boolean,
        sourceMessageId: String?,
        sourceConversationId: String?
    ): MemoryEntity {
        val created = localRepository.addMemory(key, content, category, isConfirmed, sourceMessageId, sourceConversationId)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val dto = CloudMemoryDto(
                        id = created.id,
                        userId = uid,
                        key = created.key,
                        content = created.content,
                        category = created.category,
                        isEnabled = created.isEnabled,
                        createdAt = Timestamp(Date(created.createdAt))
                    )
                    remoteDataSource.saveMemory(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for addMemory: ${e.message}")
                }
            }
        }
        return created
    }

    override suspend fun updateMemory(memory: MemoryEntity) {
        localRepository.updateMemory(memory)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val dto = CloudMemoryDto(
                        id = memory.id,
                        userId = uid,
                        key = memory.key,
                        content = memory.content,
                        category = memory.category,
                        isEnabled = memory.isEnabled,
                        createdAt = Timestamp(Date(memory.createdAt))
                    )
                    remoteDataSource.saveMemory(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for updateMemory: ${e.message}")
                }
            }
        }
    }

    override suspend fun toggleMemory(id: String, isEnabled: Boolean) {
        localRepository.toggleMemory(id, isEnabled)
    }

    override suspend fun confirmMemory(id: String) {
        localRepository.confirmMemory(id)
    }

    override suspend fun deleteMemory(id: String) {
        localRepository.deleteMemory(id)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    remoteDataSource.deleteMemory(uid, id)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for deleteMemory: ${e.message}")
                }
            }
        }
    }

    override suspend fun clearAllMemories() {
        localRepository.clearAllMemories()
    }

    override fun getMemoryCount(): Flow<Int> =
        localRepository.getMemoryCount()

    override suspend fun getFormattedMemoryContext(): String =
        localRepository.getFormattedMemoryContext()

    suspend fun importGuestMemoriesToCloud(userId: String): Int {
        var count = 0
        val memories = localRepository.getAllMemories().first()
        for (m in memories) {
            try {
                val dto = CloudMemoryDto(
                    id = m.id,
                    userId = userId,
                    key = m.key,
                    content = m.content,
                    category = m.category,
                    isEnabled = m.isEnabled,
                    createdAt = Timestamp(Date(m.createdAt))
                )
                remoteDataSource.saveMemory(userId, dto)
                count++
            } catch (e: Exception) {
                Log.w("Sync", "Failed to import memory ${m.id}: ${e.message}")
            }
        }
        return count
    }
}
