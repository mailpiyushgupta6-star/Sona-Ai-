package com.example.data.remote.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

data class CloudConversationDto(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modelId: String = "model_balanced",
    val isPinned: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "modelId" to modelId,
            "isPinned" to isPinned,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

data class CloudMessageDto(
    val id: String = "",
    val conversationId: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val modelId: String? = null,
    val status: String = "COMPLETED",
    val attachmentUrl: String? = null,
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "conversationId" to conversationId,
            "userId" to userId,
            "role" to role,
            "content" to content,
            "status" to status,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
        )
        modelId?.let { map["modelId"] = it }
        attachmentUrl?.let { map["attachmentUrl"] = it }
        return map
    }
}

data class CloudMemoryDto(
    val id: String = "",
    val userId: String = "",
    val key: String = "",
    val content: String = "",
    val category: String = "general",
    val isEnabled: Boolean = true,
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "key" to key,
            "content" to content,
            "category" to category,
            "isEnabled" to isEnabled,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
        )
    }
}

data class CloudSettingsDto(
    val userId: String = "",
    val theme: String = "dark",
    val defaultModel: String = "model_balanced",
    val highThinkingDefault: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "theme" to theme,
            "defaultModel" to defaultModel,
            "highThinkingDefault" to highThinkingDefault,
            "cloudSyncEnabled" to cloudSyncEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
