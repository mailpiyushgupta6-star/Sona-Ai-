package com.example.data.repository

import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ConversationRepository {
    fun getConversations(): Flow<List<ConversationEntity>>
    fun searchConversations(query: String): Flow<List<ConversationEntity>>
    fun getMessages(conversationId: String): Flow<List<MessageEntity>>
    suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity>
    suspend fun getConversation(id: String): ConversationEntity?
    suspend fun createConversation(title: String = "New Chat", modelId: String = "model_balanced"): ConversationEntity
    suspend fun updateTitle(id: String, title: String)
    suspend fun togglePin(id: String, isPinned: Boolean)
    suspend fun deleteConversation(id: String)
    suspend fun clearAllConversations()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun updateMessageContent(id: String, content: String, status: String)
    suspend fun saveConversation(conversation: ConversationEntity)
    suspend fun getMessage(id: String): MessageEntity?
    suspend fun deleteMessage(id: String)
    suspend fun searchMessages(query: String): List<MessageEntity>
    fun getStats(): Flow<Pair<Int, Int>> // (conversationCount, messageCount)
}

class LocalConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    override fun getConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    override fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    override suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> =
        messageDao.getMessagesList(conversationId)

    override suspend fun getConversation(id: String): ConversationEntity? =
        conversationDao.getConversationById(id)

    override suspend fun createConversation(title: String, modelId: String): ConversationEntity {
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertConversation(newConv)
        return newConv
    }

    override suspend fun updateTitle(id: String, title: String) {
        conversationDao.updateTitle(id, title)
    }

    override suspend fun togglePin(id: String, isPinned: Boolean) {
        conversationDao.updatePinned(id, isPinned)
    }

    override suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversationById(id)
    }

    override suspend fun clearAllConversations() {
        conversationDao.deleteAllConversations()
    }

    override suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
        conversationDao.updateTimestamp(message.conversationId)
    }

    override suspend fun updateMessageContent(id: String, content: String, status: String) {
        messageDao.updateMessageContent(id, content, status)
    }

    override suspend fun deleteMessage(id: String) {
        messageDao.deleteMessageById(id)
    }

    override suspend fun saveConversation(conversation: ConversationEntity) {
        conversationDao.insertConversation(conversation)
    }

    override suspend fun getMessage(id: String): MessageEntity? {
        return messageDao.getMessageById(id)
    }

    override suspend fun searchMessages(query: String): List<MessageEntity> {
        return messageDao.searchMessages(query)
    }

    override fun getStats(): Flow<Pair<Int, Int>> {
        return kotlinx.coroutines.flow.combine(
            conversationDao.getConversationCount(),
            messageDao.getTotalMessageCount()
        ) { convCount, msgCount ->
            Pair(convCount, msgCount)
        }
    }
}
