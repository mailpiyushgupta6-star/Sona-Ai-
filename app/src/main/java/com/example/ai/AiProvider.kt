package com.example.ai

import kotlinx.coroutines.flow.Flow

data class AiMessage(
    val role: String, // "user" or "model"
    val content: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

sealed interface GenerationResult {
    data class Chunk(val text: String) : GenerationResult
    data class ThinkingChunk(val text: String) : GenerationResult
    data class Complete(val fullText: String, val thinking: String? = null) : GenerationResult
    data class Error(val message: String, val isApiKeyMissing: Boolean = false) : GenerationResult
}

interface AiProvider {
    val providerName: String
    val isConfigured: Boolean
    fun streamGenerate(
        messages: List<AiMessage>,
        model: SonaModel,
        thinkingEnabled: Boolean = false,
        systemInstruction: String? = null
    ): Flow<GenerationResult>
}
