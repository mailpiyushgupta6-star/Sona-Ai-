package com.example.data.repository

import android.util.Log
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MessageEntity
import com.example.data.remote.FirestoreDataSource
import com.example.data.remote.model.CloudConversationDto
import com.example.data.remote.model.CloudMessageDto
import com.example.sync.SyncState
import com.example.sync.SyncStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.dao.TombstoneDao
import com.example.data.local.model.SyncQueueEntity
import com.example.data.local.model.TombstoneEntity
import java.util.Collections
import java.util.Date

class SyncedConversationRepository(
    private val localRepository: ConversationRepository,
    private val remoteDataSource: FirestoreDataSource,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tombstoneDao: TombstoneDao? = null,
    private val syncQueueDao: SyncQueueDao? = null
) : ConversationRepository {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var activeUserId: String? = null
    private var isLocalOnlyMode: Boolean = false
    private var cloudConversationsJob: Job? = null
    private var activeMessageSyncJob: Job? = null

    // Track recently deleted IDs to prevent accidental resurrection before cloud delete settles
    private val recentlyDeletedConversationIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun setLocalOnly(localOnly: Boolean) {
        isLocalOnlyMode = localOnly
        if (localOnly) {
            cloudConversationsJob?.cancel()
            cloudConversationsJob = null
            activeMessageSyncJob?.cancel()
            activeMessageSyncJob = null
            _syncState.value = SyncState(status = SyncStatus.IDLE, message = "Phone Storage Mode Active")
        } else {
            val uid = activeUserId
            if (uid != null) {
                _syncState.value = SyncState(status = SyncStatus.SYNCING)
                syncWithCloud(uid)
            }
        }
    }

    fun setActiveUser(userId: String?) {
        if (activeUserId == userId) return
        activeUserId = userId

        // Clear recently deleted items when user changes
        recentlyDeletedConversationIds.clear()

        // Cancel previous cloud listeners on user change or logout
        cloudConversationsJob?.cancel()
        cloudConversationsJob = null
        activeMessageSyncJob?.cancel()
        activeMessageSyncJob = null

        if (userId != null && !isLocalOnlyMode) {
            _syncState.value = SyncState(status = SyncStatus.SYNCING)
            syncWithCloud(userId)
        } else {
            _syncState.value = SyncState(
                status = SyncStatus.IDLE,
                message = if (isLocalOnlyMode) "Phone Storage Mode Active" else null
            )
        }
    }

    override fun getConversations(): Flow<List<ConversationEntity>> =
        localRepository.getConversations()

    override fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        localRepository.searchConversations(query)

    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        localRepository.getMessages(conversationId)

    override suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> =
        localRepository.getMessagesSnapshot(conversationId)

    override suspend fun getConversation(id: String): ConversationEntity? =
        localRepository.getConversation(id)

    override suspend fun saveConversation(conversation: ConversationEntity) {
        localRepository.saveConversation(conversation)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val dto = CloudConversationDto(
                        id = conversation.id,
                        userId = uid,
                        title = conversation.title,
                        modelId = conversation.modelId,
                        isPinned = conversation.isPinned,
                        createdAt = Timestamp(Date(conversation.createdAt)),
                        updatedAt = Timestamp(Date(conversation.updatedAt))
                    )
                    remoteDataSource.saveConversation(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for saveConversation: ${e.message}")
                }
            }
        }
    }

    override suspend fun getMessage(id: String): MessageEntity? =
        localRepository.getMessage(id)

    override suspend fun createConversation(title: String, modelId: String): ConversationEntity {
        val created = localRepository.createConversation(title, modelId)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val dto = CloudConversationDto(
                        id = created.id,
                        userId = uid,
                        title = created.title,
                        modelId = created.modelId,
                        isPinned = created.isPinned,
                        createdAt = Timestamp(Date(created.createdAt)),
                        updatedAt = Timestamp(Date(created.updatedAt))
                    )
                    remoteDataSource.saveConversation(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for createConversation: ${e.message}")
                }
            }
        }
        return created
    }

    override suspend fun updateTitle(id: String, title: String) {
        localRepository.updateTitle(id, title)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val local = localRepository.getConversation(id) ?: return@launch
                    val dto = CloudConversationDto(
                        id = local.id,
                        userId = uid,
                        title = title,
                        modelId = local.modelId,
                        isPinned = local.isPinned,
                        createdAt = Timestamp(Date(local.createdAt)),
                        updatedAt = Timestamp.now()
                    )
                    remoteDataSource.saveConversation(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for updateTitle: ${e.message}")
                }
            }
        }
    }

    override suspend fun togglePin(id: String, isPinned: Boolean) {
        localRepository.togglePin(id, isPinned)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val local = localRepository.getConversation(id) ?: return@launch
                    val dto = CloudConversationDto(
                        id = local.id,
                        userId = uid,
                        title = local.title,
                        modelId = local.modelId,
                        isPinned = isPinned,
                        createdAt = Timestamp(Date(local.createdAt)),
                        updatedAt = Timestamp.now()
                    )
                    remoteDataSource.saveConversation(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for togglePin: ${e.message}")
                }
            }
        }
    }

    override suspend fun deleteConversation(id: String) {
        recentlyDeletedConversationIds.add(id)
        localRepository.deleteConversation(id)
        val uid = activeUserId
        if (uid != null) {
            try {
                tombstoneDao?.insertTombstone(
                    TombstoneEntity(id = id, entityType = "CONVERSATION", uid = uid)
                )
            } catch (_: Exception) {}

            if (!isLocalOnlyMode) {
                scope.launch(ioDispatcher) {
                    try {
                        remoteDataSource.deleteConversation(uid, id)
                    } catch (e: Exception) {
                        Log.w("Sync", "Cloud sync failed for deleteConversation: ${e.message}")
                        syncQueueDao?.enqueue(
                            SyncQueueEntity(
                                uid = uid,
                                entityType = "CONVERSATION",
                                entityId = id,
                                operationType = "DELETE",
                                lastError = e.message
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun clearAllConversations() {
        localRepository.clearAllConversations()
    }

    override suspend fun insertMessage(message: MessageEntity) {
        localRepository.insertMessage(message)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode) {
            scope.launch(ioDispatcher) {
                try {
                    val dto = CloudMessageDto(
                        id = message.id,
                        conversationId = message.conversationId,
                        userId = uid,
                        role = message.role,
                        content = message.content,
                        modelId = message.modelId,
                        status = message.status,
                        attachmentUrl = message.attachmentUri,
                        createdAt = Timestamp(Date(message.timestamp))
                    )
                    remoteDataSource.saveMessage(uid, dto)
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for insertMessage: ${e.message}")
                }
            }
        }
    }

    override suspend fun updateMessageContent(id: String, content: String, status: String) {
        localRepository.updateMessageContent(id, content, status)
        val uid = activeUserId
        if (uid != null && !isLocalOnlyMode && (status == "COMPLETED" || status == "ERROR")) {
            scope.launch(ioDispatcher) {
                try {
                    val msg = localRepository.getMessage(id)
                    if (msg != null) {
                        val dto = CloudMessageDto(
                            id = msg.id,
                            conversationId = msg.conversationId,
                            userId = uid,
                            role = msg.role,
                            content = content,
                            modelId = msg.modelId,
                            status = status,
                            attachmentUrl = msg.attachmentUri,
                            createdAt = Timestamp(Date(msg.timestamp))
                        )
                        remoteDataSource.saveMessage(uid, dto)
                    }
                } catch (e: Exception) {
                    Log.w("Sync", "Cloud sync failed for completed message: ${e.message}")
                }
            }
        }
    }

    override suspend fun deleteMessage(id: String) {
        localRepository.deleteMessage(id)
    }

    override suspend fun searchMessages(query: String): List<MessageEntity> =
        localRepository.searchMessages(query)

    override fun getStats(): Flow<Pair<Int, Int>> =
        localRepository.getStats()

    fun syncWithCloud(userId: String) {
        if (isLocalOnlyMode) {
            _syncState.value = SyncState(status = SyncStatus.IDLE, message = "Phone Storage Mode Active")
            return
        }
        _syncState.value = SyncState(status = SyncStatus.SYNCING)
        cloudConversationsJob?.cancel()
        cloudConversationsJob = scope.launch(ioDispatcher) {
            try {
                // 1. Initial one-shot hydration of all remote conversations
                val remoteConversations = remoteDataSource.getConversations(userId)
                for (dto in remoteConversations) {
                    if (recentlyDeletedConversationIds.contains(dto.id) || (tombstoneDao != null && tombstoneDao.isTombstoned(dto.id, userId) > 0)) continue
                    val remoteUpdatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    val remoteCreatedAt = dto.createdAt?.toDate()?.time ?: remoteUpdatedAt
                    val existing = localRepository.getConversation(dto.id)

                    if (existing == null || remoteUpdatedAt >= existing.updatedAt) {
                        val convEntity = ConversationEntity(
                            id = dto.id,
                            title = dto.title.ifBlank { "New Chat" },
                            createdAt = remoteCreatedAt,
                            updatedAt = remoteUpdatedAt,
                            modelId = dto.modelId.ifBlank { "model_balanced" },
                            isPinned = dto.isPinned
                        )
                        localRepository.saveConversation(convEntity)

                        // Hydrate messages for this conversation if not present locally
                        try {
                            val remoteMsgs = remoteDataSource.getMessages(userId, dto.id)
                            for (msgDto in remoteMsgs) {
                                val existingMsg = localRepository.getMessage(msgDto.id)
                                val msgTime = msgDto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                if (existingMsg == null) {
                                    val msgEntity = MessageEntity(
                                        id = msgDto.id,
                                        conversationId = msgDto.conversationId,
                                        role = msgDto.role,
                                        content = msgDto.content,
                                        timestamp = msgTime,
                                        modelId = msgDto.modelId,
                                        status = msgDto.status,
                                        attachmentUri = msgDto.attachmentUrl
                                    )
                                    localRepository.insertMessage(msgEntity)
                                } else if (existingMsg.status != "STREAMING" && msgDto.status == "COMPLETED") {
                                    if (existingMsg.content != msgDto.content) {
                                        localRepository.updateMessageContent(msgDto.id, msgDto.content, msgDto.status)
                                    }
                                }
                            }
                        } catch (msgErr: Exception) {
                            Log.w("Sync", "Message hydration failed for ${dto.id}: ${msgErr.message}")
                        }
                    }
                }

                _syncState.value = SyncState(
                    status = SyncStatus.SYNCED,
                    lastSyncTime = System.currentTimeMillis()
                )

                // 2. Real-time observer for ongoing cloud updates
                remoteDataSource.observeConversations(userId).collect { liveConversations ->
                    for (dto in liveConversations) {
                        if (recentlyDeletedConversationIds.contains(dto.id) || (tombstoneDao != null && tombstoneDao.isTombstoned(dto.id, userId) > 0)) continue
                        val remoteUpdatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                        val remoteCreatedAt = dto.createdAt?.toDate()?.time ?: remoteUpdatedAt
                        val existing = localRepository.getConversation(dto.id)

                        if (existing == null || remoteUpdatedAt > existing.updatedAt) {
                            val convEntity = ConversationEntity(
                                id = dto.id,
                                title = dto.title.ifBlank { "New Chat" },
                                createdAt = remoteCreatedAt,
                                updatedAt = remoteUpdatedAt,
                                modelId = dto.modelId.ifBlank { "model_balanced" },
                                isPinned = dto.isPinned
                            )
                            localRepository.saveConversation(convEntity)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("Sync", "Cloud synchronization failed: ${e.message}")
                _syncState.value = SyncState(
                    status = SyncStatus.ERROR,
                    message = e.localizedMessage
                )
            }
        }
    }

    fun syncMessagesForConversation(conversationId: String) {
        val uid = activeUserId ?: return
        activeMessageSyncJob?.cancel()
        activeMessageSyncJob = scope.launch(ioDispatcher) {
            try {
                remoteDataSource.observeMessages(uid, conversationId).collect { remoteMsgs ->
                    for (dto in remoteMsgs) {
                        val existingMsg = localRepository.getMessage(dto.id)
                        val msgTime = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        if (existingMsg == null) {
                            val msgEntity = MessageEntity(
                                id = dto.id,
                                conversationId = dto.conversationId,
                                role = dto.role,
                                content = dto.content,
                                timestamp = msgTime,
                                modelId = dto.modelId,
                                status = dto.status,
                                attachmentUri = dto.attachmentUrl
                            )
                            localRepository.insertMessage(msgEntity)
                        } else if (existingMsg.status != "STREAMING" && dto.status == "COMPLETED") {
                            if (existingMsg.content != dto.content) {
                                localRepository.updateMessageContent(dto.id, dto.content, dto.status)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("Sync", "Message sync failed for conversation $conversationId: ${e.message}")
            }
        }
    }

    suspend fun importGuestDataToCloud(userId: String): Int {
        var importedCount = 0
        val localConversations = localRepository.getConversations().first()
        for (conv in localConversations) {
            try {
                val dto = CloudConversationDto(
                    id = conv.id,
                    userId = userId,
                    title = conv.title,
                    modelId = conv.modelId,
                    isPinned = conv.isPinned,
                    createdAt = Timestamp(Date(conv.createdAt)),
                    updatedAt = Timestamp(Date(conv.updatedAt))
                )
                remoteDataSource.saveConversation(userId, dto)

                val messages = localRepository.getMessagesSnapshot(conv.id)
                for (msg in messages) {
                    val msgDto = CloudMessageDto(
                        id = msg.id,
                        conversationId = conv.id,
                        userId = userId,
                        role = msg.role,
                        content = msg.content,
                        modelId = msg.modelId,
                        status = msg.status,
                        attachmentUrl = msg.attachmentUri,
                        createdAt = Timestamp(Date(msg.timestamp))
                    )
                    remoteDataSource.saveMessage(userId, msgDto)
                }
                importedCount++
            } catch (e: Exception) {
                Log.w("Sync", "Failed to import conversation ${conv.id}: ${e.message}")
            }
        }
        _syncState.value = SyncState(
            status = SyncStatus.SYNCED,
            lastSyncTime = System.currentTimeMillis(),
            message = "Imported $importedCount chats to cloud"
        )
        return importedCount
    }
}
