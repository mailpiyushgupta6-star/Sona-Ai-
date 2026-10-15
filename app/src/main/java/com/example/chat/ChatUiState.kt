package com.example.chat

import com.example.ai.SonaModel
import com.example.auth.AuthState
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import com.example.sync.SyncState

data class ChatUiState(
    val activeConversation: ConversationEntity? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedModel: SonaModel = SonaModel.BALANCED,
    val isThinkingEnabled: Boolean = false,
    val isGenerating: Boolean = false,
    val currentStreamingText: String = "",
    val currentError: String? = null,
    val isApiKeyConfigured: Boolean = false,
    val conversationCount: Int = 0,
    val totalMessageCount: Int = 0,
    val authState: AuthState = AuthState.Loading,
    val syncState: SyncState = SyncState(),
    val storageLocation: String = "CLOUD_FIRESTORE",
    val contextualSuggestions: List<SmartSuggestion> = emptyList()
)
