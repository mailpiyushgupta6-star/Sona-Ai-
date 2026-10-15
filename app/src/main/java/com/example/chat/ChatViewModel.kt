package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiMessage
import com.example.ai.AiProvider
import com.example.ai.GeminiProvider
import com.example.ai.GenerationResult
import com.example.ai.SonaModel
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.example.data.local.AppPreferences
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    val authRepository: com.example.auth.AuthRepository,
    private val sonaBrain: com.example.brain.SonaBrain,
    private val appPreferences: AppPreferences? = null,
    val appManager: com.example.androidapps.AppManager? = null,
    private val applicationContext: android.content.Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private val searchQueryFlow = MutableStateFlow("")
    private val activeConversationIdFlow = MutableStateFlow<String?>(null)

    init {
        val initStorageLocation = appPreferences?.getStorageLocation() ?: AppPreferences.STORAGE_CLOUD_FIRESTORE
        _uiState.update { it.copy(storageLocation = initStorageLocation) }
        val isPhoneStorage = initStorageLocation == AppPreferences.STORAGE_PHONE_STORAGE
        (conversationRepository as? com.example.data.repository.SyncedConversationRepository)?.setLocalOnly(isPhoneStorage)
        (memoryRepository as? com.example.data.repository.SyncedMemoryRepository)?.setLocalOnly(isPhoneStorage)

        // Observe Auth State
        authRepository.authState.onEach { auth ->
            _uiState.update { it.copy(authState = auth) }
            val uid = (auth as? com.example.auth.AuthState.Authenticated)?.user?.uid
            (conversationRepository as? com.example.data.repository.SyncedConversationRepository)?.setActiveUser(uid)
            (memoryRepository as? com.example.data.repository.SyncedMemoryRepository)?.setActiveUser(uid)
        }.launchIn(viewModelScope)

        // Observe Sync State
        (conversationRepository as? com.example.data.repository.SyncedConversationRepository)?.syncState?.onEach { sync ->
            _uiState.update { it.copy(syncState = sync) }
        }?.launchIn(viewModelScope)

        // Observe API key configuration status
        _uiState.update { it.copy(isApiKeyConfigured = sonaBrain.isConfigured) }

        // Observe conversations and search
        searchQueryFlow.flatMapLatest { query ->
            if (query.isBlank()) {
                conversationRepository.getConversations()
            } else {
                conversationRepository.searchConversations(query)
            }
        }.onEach { convList ->
            val currentUid = (_uiState.value.authState as? com.example.auth.AuthState.Authenticated)?.user?.uid
            val savedConvId = appPreferences?.let { prefs ->
                if (prefs.getLastAuthUserId() == currentUid) prefs.getLastActiveConversationId() else null
            }

            _uiState.update { current ->
                val active = if (current.activeConversation != null) {
                    val foundInList = convList.find { it.id == current.activeConversation.id }
                    if (foundInList != null) {
                        foundInList
                    } else if (current.messages.isEmpty()) {
                        current.activeConversation // Retain unpersisted temporary chat session
                    } else if (savedConvId != null) {
                        convList.find { it.id == savedConvId } ?: convList.firstOrNull()
                    } else {
                        convList.firstOrNull()
                    }
                } else if (savedConvId != null) {
                    convList.find { it.id == savedConvId } ?: convList.firstOrNull()
                } else {
                    convList.firstOrNull()
                }

                val finalActive = active ?: if (convList.isEmpty()) {
                    ConversationEntity(
                        id = UUID.randomUUID().toString(),
                        title = "New Chat",
                        modelId = current.selectedModel.id,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                } else null

                current.copy(conversations = convList, activeConversation = finalActive)
            }

            val currentActive = _uiState.value.activeConversation
            if (activeConversationIdFlow.value == null && currentActive != null) {
                activeConversationIdFlow.value = currentActive.id
                if (convList.any { it.id == currentActive.id }) {
                    appPreferences?.setLastActiveConversationId(currentActive.id)
                    appPreferences?.setLastAuthUserId(currentUid)
                }
            }
        }.launchIn(viewModelScope)

        // Observe messages for active conversation
        activeConversationIdFlow.flatMapLatest { convId ->
            if (convId != null) {
                conversationRepository.getMessages(convId)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }.onEach { msgList ->
            val suggestions = if (!_uiState.value.isGenerating) {
                ContextSuggestionEngine.generateSuggestions(msgList, _uiState.value.memories)
            } else {
                emptyList()
            }
            _uiState.update { it.copy(messages = msgList, contextualSuggestions = suggestions) }
        }.launchIn(viewModelScope)

        // Observe memories
        memoryRepository.getAllMemories().onEach { memoryList ->
            val updatedSuggestions = if (!_uiState.value.isGenerating && _uiState.value.messages.isNotEmpty()) {
                ContextSuggestionEngine.generateSuggestions(_uiState.value.messages, memoryList)
            } else {
                emptyList()
            }
            _uiState.update { it.copy(memories = memoryList, contextualSuggestions = updatedSuggestions) }
        }.launchIn(viewModelScope)

        // Observe statistics
        conversationRepository.getStats().onEach { (convs, msgs) ->
            _uiState.update { it.copy(conversationCount = convs, totalMessageCount = msgs) }
        }.launchIn(viewModelScope)
    }

    fun selectModel(model: SonaModel) {
        _uiState.update {
            it.copy(
                selectedModel = model,
                isThinkingEnabled = if (!model.supportsThinking) false else it.isThinkingEnabled
            )
        }
    }

    fun toggleThinking() {
        val model = _uiState.value.selectedModel
        if (model.supportsThinking) {
            _uiState.update { it.copy(isThinkingEnabled = !it.isThinkingEnabled) }
        }
    }

    fun setStorageLocation(location: String) {
        appPreferences?.setStorageLocation(location)
        _uiState.update { it.copy(storageLocation = location) }
        val isPhoneStorage = location == AppPreferences.STORAGE_PHONE_STORAGE
        (conversationRepository as? com.example.data.repository.SyncedConversationRepository)?.setLocalOnly(isPhoneStorage)
        (memoryRepository as? com.example.data.repository.SyncedMemoryRepository)?.setLocalOnly(isPhoneStorage)
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectConversation(conversationId: String) {
        stopGeneration()
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(conversationId)
            if (conv != null) {
                activeConversationIdFlow.value = conv.id
                val currentUid = (_uiState.value.authState as? com.example.auth.AuthState.Authenticated)?.user?.uid
                appPreferences?.setLastActiveConversationId(conv.id)
                appPreferences?.setLastAuthUserId(currentUid)

                // Trigger message hydration from cloud if authenticated
                (conversationRepository as? com.example.data.repository.SyncedConversationRepository)?.syncMessagesForConversation(conv.id)

                _uiState.update {
                    it.copy(
                        activeConversation = conv,
                        selectedModel = SonaModel.fromId(conv.modelId),
                        currentError = null,
                        contextualSuggestions = emptyList()
                    )
                }
            }
        }
    }

    fun startNewChat() {
        stopGeneration()
        searchQueryFlow.value = ""
        val defaultTitle = "New Chat"
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = defaultTitle,
            modelId = _uiState.value.selectedModel.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        activeConversationIdFlow.value = newConv.id

        _uiState.update {
            it.copy(
                activeConversation = newConv,
                currentError = null,
                messages = emptyList(),
                searchQuery = "",
                contextualSuggestions = emptyList()
            )
        }
    }

    fun updateConversationTitle(id: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            conversationRepository.updateTitle(id, newTitle.trim())
            if (_uiState.value.activeConversation?.id == id) {
                _uiState.update {
                    it.copy(activeConversation = it.activeConversation?.copy(title = newTitle.trim()))
                }
            }
        }
    }

    fun togglePinConversation(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            conversationRepository.togglePin(id, isPinned)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            if (_uiState.value.activeConversation?.id == id) {
                val remaining = _uiState.value.conversations.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    startNewChat()
                }
            }
        }
    }

    fun clearAllConversations() {
        stopGeneration()
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
            startNewChat()
        }
    }

    fun sendMessage(prompt: String, context: Context? = null, attachmentUri: Uri? = null) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank() && attachmentUri == null) return
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            var activeConv = _uiState.value.activeConversation
            val titlePreview = if (trimmedPrompt.isNotEmpty()) {
                if (trimmedPrompt.length > 30) trimmedPrompt.take(30) + "…" else trimmedPrompt
            } else "Image Analysis"

            if (activeConv == null) {
                activeConv = ConversationEntity(
                    id = UUID.randomUUID().toString(),
                    title = titlePreview,
                    modelId = _uiState.value.selectedModel.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                _uiState.update { it.copy(activeConversation = activeConv) }
            }

            val existingInRepo = conversationRepository.getConversation(activeConv.id)

            try {
                if (existingInRepo == null) {
                    // First message sent in temporary session: persist conversation exactly once
                    val convToSave = activeConv.copy(
                        title = if (activeConv.title == "New Chat" || activeConv.title.isBlank()) titlePreview else activeConv.title,
                        updatedAt = System.currentTimeMillis()
                    )
                    conversationRepository.saveConversation(convToSave)
                    activeConv = convToSave
                    activeConversationIdFlow.value = activeConv.id
                    val currentUid = (_uiState.value.authState as? com.example.auth.AuthState.Authenticated)?.user?.uid
                    appPreferences?.setLastActiveConversationId(activeConv.id)
                    appPreferences?.setLastAuthUserId(currentUid)
                    _uiState.update { it.copy(activeConversation = activeConv) }
                } else if (activeConv.title == "New Chat" && trimmedPrompt.isNotEmpty()) {
                    val newTitle = titlePreview
                    conversationRepository.updateTitle(activeConv.id, newTitle)
                    activeConv = activeConv.copy(title = newTitle)
                    _uiState.update { it.copy(activeConversation = activeConv) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(currentError = "Failed to save conversation: ${e.message}") }
                return@launch
            }

            // Convert image if provided
            var imageBase64: String? = null
            var imageMimeType: String? = null
            if (attachmentUri != null && context != null) {
                val (b64, mime) = readImageAsBase64(context, attachmentUri)
                imageBase64 = b64
                imageMimeType = mime
            }

            // 1. Insert User Message
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = activeConv.id,
                role = "user",
                content = trimmedPrompt,
                timestamp = System.currentTimeMillis(),
                attachmentUri = attachmentUri?.toString()
            )
            conversationRepository.insertMessage(userMsg)

            // Check if user prompt is a system control command (Wi-Fi, Mobile Data, Bluetooth, Torch, Internet Settings, Quick Settings)
            val chatCtx = getApplicationContextForChat()
            if (attachmentUri == null && chatCtx != null) {
                val sysControlResult = com.example.androidapps.SystemControlManager.executeCommand(chatCtx, trimmedPrompt)
                if (sysControlResult != null) {
                    val (assistantText, isError) = when (sysControlResult) {
                        is com.example.androidapps.SystemControlResult.ActionExecuted -> Pair(sysControlResult.message, false)
                        is com.example.androidapps.SystemControlResult.PanelOpened -> Pair(sysControlResult.message, false)
                        is com.example.androidapps.SystemControlResult.Failed -> Pair(sysControlResult.message, true)
                    }

                    val assistantMsg = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = activeConv.id,
                        role = "assistant",
                        content = assistantText,
                        timestamp = System.currentTimeMillis() + 1,
                        modelId = _uiState.value.selectedModel.id,
                        status = if (isError) "ERROR" else "COMPLETED"
                    )
                    conversationRepository.insertMessage(assistantMsg)

                    val updatedMessages = _uiState.value.messages + listOf(userMsg, assistantMsg)
                    val newSuggestions = ContextSuggestionEngine.generateSuggestions(
                        updatedMessages,
                        _uiState.value.memories
                    )
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentStreamingText = "",
                            currentError = if (isError) assistantText else null,
                            contextualSuggestions = newSuggestions
                        )
                    }
                    return@launch
                }
            }

            // Check if user prompt is a direct app launch command
            if (appManager != null && attachmentUri == null) {
                val appCmdResult = appManager.checkAndExecuteAppLaunchCommand(trimmedPrompt)
                if (appCmdResult !is com.example.androidapps.CommandLaunchResult.NotACommand) {
                    val (assistantText, isError) = when (appCmdResult) {
                        is com.example.androidapps.CommandLaunchResult.Launched ->
                            Pair(appCmdResult.confirmationMessage, false)
                        is com.example.androidapps.CommandLaunchResult.DisambiguationRequired ->
                            Pair(appCmdResult.message, false)
                        is com.example.androidapps.CommandLaunchResult.AppNotFound ->
                            Pair(appCmdResult.message, false)
                        is com.example.androidapps.CommandLaunchResult.Failed ->
                            Pair("⚠️ ${appCmdResult.appName} open nahi ho paya: ${appCmdResult.error}", true)
                        is com.example.androidapps.CommandLaunchResult.NotACommand ->
                            Pair("", false)
                    }

                    val assistantMsg = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = activeConv.id,
                        role = "assistant",
                        content = assistantText,
                        timestamp = System.currentTimeMillis() + 1,
                        modelId = _uiState.value.selectedModel.id,
                        status = if (isError) "ERROR" else "COMPLETED"
                    )
                    conversationRepository.insertMessage(assistantMsg)

                    val updatedMessages = _uiState.value.messages + listOf(userMsg, assistantMsg)
                    val newSuggestions = ContextSuggestionEngine.generateSuggestions(
                        updatedMessages,
                        _uiState.value.memories
                    )
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentStreamingText = "",
                            currentError = if (isError) assistantText else null,
                            contextualSuggestions = newSuggestions
                        )
                    }
                    return@launch
                }
            }

            // 2. Insert Assistant placeholder message
            val assistantMsgId = UUID.randomUUID().toString()
            val currentModel = _uiState.value.selectedModel
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                conversationId = activeConv.id,
                role = "assistant",
                content = "",
                timestamp = System.currentTimeMillis() + 1,
                modelId = currentModel.id,
                status = "STREAMING"
            )
            conversationRepository.insertMessage(assistantMsg)



            // 4. Start Streaming Generation via SonaBrain
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    currentStreamingText = "",
                    currentError = null,
                    contextualSuggestions = emptyList()
                )
            }

            generationJob = viewModelScope.launch {
                val streamFlow = sonaBrain.generateResponse(
                    prompt = trimmedPrompt,
                    conversationId = activeConv.id,
                    model = currentModel,
                    thinkingEnabled = _uiState.value.isThinkingEnabled,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType
                )

                val fullTextAccumulator = StringBuilder()

                streamFlow.collect { result ->
                    when (result) {
                        is GenerationResult.Chunk -> {
                            fullTextAccumulator.append(result.text)
                            _uiState.update { it.copy(currentStreamingText = fullTextAccumulator.toString()) }
                            conversationRepository.updateMessageContent(
                                id = assistantMsgId,
                                content = fullTextAccumulator.toString(),
                                status = "STREAMING"
                            )
                        }
                        is GenerationResult.Complete -> {
                            val finalAssistantText = result.fullText.ifEmpty { fullTextAccumulator.toString() }
                            conversationRepository.updateMessageContent(
                                id = assistantMsgId,
                                content = finalAssistantText,
                                status = "COMPLETED"
                            )
                            val updatedMessages = _uiState.value.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(content = finalAssistantText, status = "COMPLETED")
                                } else msg
                            }
                            val newSuggestions = ContextSuggestionEngine.generateSuggestions(
                                updatedMessages,
                                _uiState.value.memories
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    currentStreamingText = "",
                                    contextualSuggestions = newSuggestions
                                )
                            }
                        }
                        is GenerationResult.Error -> {
                            val errorContent = if (fullTextAccumulator.isNotEmpty()) {
                                "${fullTextAccumulator}\n\n[Generation Interrupted: ${result.message}]"
                            } else {
                                "⚠️ ${result.message}"
                            }
                            conversationRepository.updateMessageContent(
                                id = assistantMsgId,
                                content = errorContent,
                                status = "ERROR"
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    currentStreamingText = "",
                                    currentError = result.message
                                )
                            }
                        }
                        is GenerationResult.ThinkingChunk -> {
                            // High thinking chunk if supported
                        }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        if (_uiState.value.isGenerating) {
            generationJob?.cancel()
            generationJob = null
            _uiState.update { it.copy(isGenerating = false, currentStreamingText = "") }

            // Mark any streaming assistant message as COMPLETED
            val activeConv = _uiState.value.activeConversation ?: return
            viewModelScope.launch {
                val msgs = conversationRepository.getMessagesSnapshot(activeConv.id)
                val streamingMsg = msgs.lastOrNull { it.role == "assistant" && it.status == "STREAMING" }
                if (streamingMsg != null) {
                    val finalContent = streamingMsg.content.ifEmpty { "[Stopped by user]" }
                    conversationRepository.updateMessageContent(
                        id = streamingMsg.id,
                        content = finalContent,
                        status = "COMPLETED"
                    )
                }
            }
        }
    }

    fun retryLastMessage(context: Context? = null) {
        val msgs = _uiState.value.messages
        val lastUserMsg = msgs.lastOrNull { it.role == "user" } ?: return
        val lastAssistantMsg = msgs.lastOrNull { it.role == "assistant" }

        viewModelScope.launch {
            if (lastAssistantMsg != null) {
                conversationRepository.deleteMessage(lastAssistantMsg.id)
            }
            sendMessage(lastUserMsg.content, context, null)
        }
    }

    // Memory Controls
    fun addMemory(key: String, content: String, category: String = "general") {
        if (key.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            memoryRepository.addMemory(key.trim(), content.trim(), category)
        }
    }

    fun toggleMemory(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            memoryRepository.toggleMemory(id, isEnabled)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
        }
    }

    private fun getApplicationContextForChat(): android.content.Context? {
        return applicationContext
    }

    private fun readImageAsBase64(context: Context, uri: Uri): Pair<String?, String?> {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Resize if too huge to keep latency low
                val maxDim = 1024
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val targetW = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                    val targetH = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                    Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                } else bitmap

                val outputStream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val bytes = outputStream.toByteArray()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Pair(b64, mimeType)
            } else Pair(null, null)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }
    suspend fun importLocalDataToCloud(): Int {
        val uid = (_uiState.value.authState as? com.example.auth.AuthState.Authenticated)?.user?.uid
            ?: throw IllegalStateException("Must be signed in to upload local data")
        val convSyncedRepo = conversationRepository as? com.example.data.repository.SyncedConversationRepository
        val memSyncedRepo = memoryRepository as? com.example.data.repository.SyncedMemoryRepository
        val convCount = convSyncedRepo?.importGuestDataToCloud(uid) ?: 0
        memSyncedRepo?.importGuestMemoriesToCloud(uid)
        return convCount
    }
}

class ChatViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val authRepository: com.example.auth.AuthRepository,
    private val appPreferences: com.example.data.local.AppPreferences? = null,
    private val toolRegistry: com.example.tools.ToolRegistry,
    private val appManager: com.example.androidapps.AppManager? = null,
    private val applicationContext: android.content.Context? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val aiProvider = com.example.ai.GeminiProvider()
        val brain = com.example.brain.SonaBrain(
            aiProvider = aiProvider,
            conversationRepository = conversationRepository,
            memoryRepository = memoryRepository,
            appPreferences = appPreferences,
            toolRegistry = toolRegistry
        )
        return ChatViewModel(
            conversationRepository = conversationRepository,
            memoryRepository = memoryRepository,
            authRepository = authRepository,
            sonaBrain = brain,
            appPreferences = appPreferences,
            appManager = appManager,
            applicationContext = applicationContext
        ) as T
    }
}
