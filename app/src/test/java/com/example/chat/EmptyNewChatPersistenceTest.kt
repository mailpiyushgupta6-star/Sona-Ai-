package com.example.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiMessage
import com.example.ai.AiProvider
import com.example.ai.GenerationResult
import com.example.ai.SonaModel
import com.example.auth.AuthRepository
import com.example.auth.AuthState
import com.example.auth.AuthUser
import com.example.brain.SonaBrain
import com.example.data.local.AppPreferences
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.dao.TombstoneDao
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import com.example.data.local.model.SyncQueueEntity
import com.example.data.local.model.TombstoneEntity
import com.example.data.remote.FirestoreDataSource
import com.example.data.remote.model.CloudConversationDto
import com.example.data.remote.model.CloudMessageDto
import com.example.data.repository.ConversationRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.SyncedConversationRepository
import com.example.tools.ToolRegistry
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class EmptyNewChatPersistenceTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences
    private lateinit var fakeLocalRepo: FakeLocalConvRepo
    private lateinit var fakeRemoteDataSource: TestRemoteDataSource
    private lateinit var fakeTombstoneDao: TestTombstoneDao
    private lateinit var fakeSyncQueueDao: TestSyncQueueDao
    private lateinit var fakeMemoryRepo: TestMemoryRepo
    private lateinit var fakeAuthRepo: TestAuthRepo
    private lateinit var fakeAiProvider: TestAiProvider
    private lateinit var testScope: TestScope
    private lateinit var syncedRepo: SyncedConversationRepository
    private lateinit var sonaBrain: SonaBrain

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
        fakeLocalRepo = FakeLocalConvRepo()
        fakeRemoteDataSource = TestRemoteDataSource()
        fakeTombstoneDao = TestTombstoneDao()
        fakeSyncQueueDao = TestSyncQueueDao()
        fakeMemoryRepo = TestMemoryRepo()
        fakeAuthRepo = TestAuthRepo()
        fakeAiProvider = TestAiProvider()
        testScope = TestScope()

        syncedRepo = SyncedConversationRepository(
            localRepository = fakeLocalRepo,
            remoteDataSource = fakeRemoteDataSource,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tombstoneDao = fakeTombstoneDao,
            syncQueueDao = fakeSyncQueueDao
        )

        sonaBrain = SonaBrain(
            aiProvider = fakeAiProvider,
            conversationRepository = syncedRepo,
            memoryRepository = fakeMemoryRepo,
            appPreferences = appPreferences,
            toolRegistry = ToolRegistry()
        )
    }

    private fun createViewModel(): ChatViewModel {
        return ChatViewModel(
            conversationRepository = syncedRepo,
            memoryRepository = fakeMemoryRepo,
            authRepository = fakeAuthRepo,
            sonaBrain = sonaBrain,
            appPreferences = appPreferences
        )
    }

    // 1. testOpeningNewChatDoesNotPersistEmptyConversation
    @Test
    fun testOpeningNewChatDoesNotPersistEmptyConversation() = testScope.runTest {
        val viewModel = createViewModel()
        testScope.advanceUntilIdle()

        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        val activeConv = viewModel.uiState.value.activeConversation
        assertNotNull("Active conversation in UI state should be non-null for input readiness", activeConv)
        assertEquals("New Chat", activeConv?.title)

        val persistedConvs = fakeLocalRepo.getConversations().first()
        assertTrue("No empty conversation should be persisted in local repository", persistedConvs.isEmpty())
        assertTrue("No sync queue entry should be created", fakeSyncQueueDao.queue.isEmpty())
    }

    // 2. testSendingFirstMessagePersistsConversationExactlyOnce
    @Test
    fun testSendingFirstMessagePersistsConversationExactlyOnce() = testScope.runTest {
        val viewModel = createViewModel()
        testScope.advanceUntilIdle()

        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        viewModel.sendMessage("Hello Sona! Recommend a good movie")
        testScope.advanceUntilIdle()

        val persistedConvs = fakeLocalRepo.getConversations().first()
        assertEquals("Exactly 1 conversation should be persisted after first message", 1, persistedConvs.size)

        val conv = persistedConvs.first()
        assertEquals("Hello Sona! Recommend a good m…", conv.title)

        val messages = fakeLocalRepo.getMessagesSnapshot(conv.id)
        assertTrue("User message should be persisted", messages.any { it.content == "Hello Sona! Recommend a good movie" })
    }

    // 3. testAbandonedEmptyChatDoesNotAppearAfterRestart
    @Test
    fun testAbandonedEmptyChatDoesNotAppearAfterRestart() = testScope.runTest {
        val v1 = createViewModel()
        testScope.advanceUntilIdle()

        v1.startNewChat()
        testScope.advanceUntilIdle()

        assertTrue("Repository must be empty while in temporary chat session", fakeLocalRepo.getConversations().first().isEmpty())

        // Simulate app/process restart by creating new ViewModel instance
        val v2 = createViewModel()
        testScope.advanceUntilIdle()

        assertTrue("Abandoned empty conversation must not appear in history after restart", v2.uiState.value.conversations.isEmpty())
        assertTrue("Local repository must remain empty", fakeLocalRepo.getConversations().first().isEmpty())
    }

    // 4. testUnsentDraftDoesNotCreateConversation
    @Test
    fun testUnsentDraftDoesNotCreateConversation() = testScope.runTest {
        val viewModel = createViewModel()
        testScope.advanceUntilIdle()

        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        // User typed draft but never called sendMessage
        val persistedConvs = fakeLocalRepo.getConversations().first()
        assertTrue("Unsent draft must not persist conversation to database", persistedConvs.isEmpty())
        assertTrue("No cloud sync operations should be queued for unsent draft", fakeSyncQueueDao.queue.isEmpty())
    }

    // 5. testExistingConversationIsPreserved
    @Test
    fun testExistingConversationIsPreserved() = testScope.runTest {
        val existingConv = ConversationEntity(
            id = "existing_123",
            title = "Existing Chat",
            modelId = "model_balanced",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        fakeLocalRepo.saveConversation(existingConv)
        fakeLocalRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "existing_123", role = "user", content = "Existing prompt", timestamp = 1500L)
        )

        val viewModel = createViewModel()
        testScope.advanceUntilIdle()

        viewModel.selectConversation("existing_123")
        testScope.advanceUntilIdle()

        assertEquals("existing_123", viewModel.uiState.value.activeConversation?.id)

        // Open a new chat session
        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        val persisted = fakeLocalRepo.getConversation("existing_123")
        assertNotNull("Existing conversation must be preserved", persisted)
        assertEquals("Existing Chat", persisted?.title)

        val msgs = fakeLocalRepo.getMessagesSnapshot("existing_123")
        assertEquals(1, msgs.size)
        assertEquals("Existing prompt", msgs.first().content)
    }

    // 6. testConversationNamedNewChatWithRealMessagesIsPreserved
    @Test
    fun testConversationNamedNewChatWithRealMessagesIsPreserved() = testScope.runTest {
        val newChatWithMsgs = ConversationEntity(
            id = "conv_new_chat_with_msgs",
            title = "New Chat",
            modelId = "model_balanced",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        fakeLocalRepo.saveConversation(newChatWithMsgs)
        fakeLocalRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "conv_new_chat_with_msgs", role = "user", content = "What is Kotlin?", timestamp = 1500L)
        )

        val viewModel = createViewModel()
        testScope.advanceUntilIdle()

        val convList = viewModel.uiState.value.conversations
        assertTrue("Conversation named 'New Chat' with real messages must be preserved", convList.any { it.id == "conv_new_chat_with_msgs" })

        val localConv = fakeLocalRepo.getConversation("conv_new_chat_with_msgs")
        assertNotNull("Record must exist in local repository", localConv)
    }

    // 7. testFirstMessageFailureDoesNotLeaveEmptyConversation
    @Test
    fun testFirstMessageFailureDoesNotLeaveEmptyConversation() = testScope.runTest {
        val failingLocalRepo = FailingLocalConvRepo()
        val failingSyncedRepo = SyncedConversationRepository(
            localRepository = failingLocalRepo,
            remoteDataSource = fakeRemoteDataSource,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tombstoneDao = fakeTombstoneDao,
            syncQueueDao = fakeSyncQueueDao
        )

        val failingSonaBrain = SonaBrain(
            aiProvider = fakeAiProvider,
            conversationRepository = failingSyncedRepo,
            memoryRepository = fakeMemoryRepo,
            appPreferences = appPreferences,
            toolRegistry = ToolRegistry()
        )

        val viewModel = ChatViewModel(
            conversationRepository = failingSyncedRepo,
            memoryRepository = fakeMemoryRepo,
            authRepository = fakeAuthRepo,
            sonaBrain = failingSonaBrain,
            appPreferences = appPreferences
        )
        testScope.advanceUntilIdle()

        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        failingLocalRepo.failSave = true
        viewModel.sendMessage("Will fail to save")
        testScope.advanceUntilIdle()

        assertNotNull("Error state should be set on failure", viewModel.uiState.value.currentError)

        val persisted = failingLocalRepo.getConversations().first()
        assertTrue("No empty or partial conversation should remain on first message failure", persisted.isEmpty())
    }

    // 8. testLocalOnlyModeRemainsLocal
    @Test
    fun testLocalOnlyModeRemainsLocal() = testScope.runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_PHONE_STORAGE)

        val viewModel = createViewModel()
        viewModel.setStorageLocation(AppPreferences.STORAGE_PHONE_STORAGE)
        testScope.advanceUntilIdle()

        viewModel.startNewChat()
        testScope.advanceUntilIdle()

        viewModel.sendMessage("Local only test query")
        testScope.advanceUntilIdle()

        assertTrue("No remote messages should be saved in LOCAL_ONLY mode", fakeRemoteDataSource.savedMessages.isEmpty())
        assertTrue("No remote conversations should be saved in LOCAL_ONLY mode", fakeRemoteDataSource.remoteConversations.isEmpty())
    }

    // --- Test Fakes ---

    open class FakeLocalConvRepo : ConversationRepository {
        protected val conversations = mutableMapOf<String, ConversationEntity>()
        protected val messages = mutableMapOf<String, MessageEntity>()
        private val _convsFlow = MutableStateFlow<List<ConversationEntity>>(emptyList())

        protected fun updateFlows() {
            _convsFlow.value = conversations.values.sortedByDescending { it.updatedAt }
        }

        override fun getConversations(): Flow<List<ConversationEntity>> = _convsFlow.asStateFlow()

        override fun searchConversations(query: String): Flow<List<ConversationEntity>> =
            _convsFlow.map { list -> list.filter { it.title.contains(query, ignoreCase = true) } }

        override fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
            flowOf(messages.values.filter { it.conversationId == conversationId }.sortedBy { it.timestamp })

        override suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> =
            messages.values.filter { it.conversationId == conversationId }.sortedBy { it.timestamp }

        override suspend fun getConversation(id: String): ConversationEntity? = conversations[id]

        override suspend fun createConversation(title: String, modelId: String): ConversationEntity {
            val conv = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                modelId = modelId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            conversations[conv.id] = conv
            updateFlows()
            return conv
        }

        override suspend fun updateTitle(id: String, title: String) {
            conversations[id]?.let {
                conversations[id] = it.copy(title = title, updatedAt = System.currentTimeMillis())
                updateFlows()
            }
        }

        override suspend fun togglePin(id: String, isPinned: Boolean) {
            conversations[id]?.let {
                conversations[id] = it.copy(isPinned = isPinned)
                updateFlows()
            }
        }

        override suspend fun deleteConversation(id: String) {
            conversations.remove(id)
            messages.entries.removeAll { it.value.conversationId == id }
            updateFlows()
        }

        override suspend fun clearAllConversations() {
            conversations.clear()
            messages.clear()
            updateFlows()
        }

        override suspend fun insertMessage(message: MessageEntity) {
            messages[message.id] = message
            conversations[message.conversationId]?.let {
                conversations[message.conversationId] = it.copy(updatedAt = System.currentTimeMillis())
            }
            updateFlows()
        }

        override suspend fun updateMessageContent(id: String, content: String, status: String) {
            messages[id]?.let {
                messages[id] = it.copy(content = content, status = status)
                updateFlows()
            }
        }

        override suspend fun deleteMessage(id: String) {
            messages.remove(id)
            updateFlows()
        }

        override suspend fun saveConversation(conversation: ConversationEntity) {
            conversations[conversation.id] = conversation
            updateFlows()
        }

        override suspend fun getMessage(id: String): MessageEntity? = messages[id]

        override suspend fun searchMessages(query: String): List<MessageEntity> =
            messages.values.filter { it.content.contains(query, ignoreCase = true) }

        override fun getStats(): Flow<Pair<Int, Int>> =
            _convsFlow.map { Pair(it.size, messages.size) }
    }

    class FailingLocalConvRepo : FakeLocalConvRepo() {
        var failSave: Boolean = false

        override suspend fun saveConversation(conversation: ConversationEntity) {
            if (failSave) throw IllegalStateException("Simulated database disk failure")
            super.saveConversation(conversation)
        }
    }

    class TestRemoteDataSource : FirestoreDataSource(null) {
        val remoteConversations = mutableMapOf<String, MutableList<CloudConversationDto>>()
        val savedMessages = mutableListOf<CloudMessageDto>()

        override suspend fun saveConversation(userId: String, conv: CloudConversationDto) {
            val list = remoteConversations.getOrPut(userId) { mutableListOf() }
            list.removeAll { it.id == conv.id }
            list.add(conv)
        }

        override suspend fun saveMessage(userId: String, message: CloudMessageDto) {
            savedMessages.add(message)
        }
    }

    class TestTombstoneDao : TombstoneDao {
        override suspend fun getTombstonesForUser(uid: String) = emptyList<TombstoneEntity>()
        override suspend fun isTombstoned(id: String, uid: String) = 0
        override suspend fun insertTombstone(tombstone: TombstoneEntity) {}
        override suspend fun clearTombstonesForUser(uid: String) {}
    }

    class TestSyncQueueDao : SyncQueueDao {
        val queue = mutableListOf<SyncQueueEntity>()
        override suspend fun getPendingOperations(uid: String) = queue.filter { it.uid == uid && it.status == "PENDING" }
        override fun observeQueue(uid: String) = flowOf(queue.filter { it.uid == uid })
        override suspend fun enqueue(operation: SyncQueueEntity) { queue.add(operation) }
        override suspend fun updateOperation(operation: SyncQueueEntity) {}
        override suspend fun deleteOperation(operationId: String) {}
        override suspend fun clearQueueForUser(uid: String) {}
    }

    class TestMemoryRepo : MemoryRepository {
        override fun getAllMemories() = flowOf(emptyList<MemoryEntity>())
        override fun getConfirmedMemories() = flowOf(emptyList<MemoryEntity>())
        override suspend fun getConfirmedMemoriesList() = emptyList<MemoryEntity>()
        override fun getCandidateMemories() = flowOf(emptyList<MemoryEntity>())
        override fun searchMemories(query: String) = flowOf(emptyList<MemoryEntity>())
        override suspend fun addMemory(
            key: String,
            content: String,
            category: String,
            isConfirmed: Boolean,
            sourceMessageId: String?,
            sourceConversationId: String?
        ): MemoryEntity = MemoryEntity(key = key, content = content, category = category, isConfirmed = isConfirmed)

        override suspend fun updateMemory(memory: MemoryEntity) {}
        override suspend fun toggleMemory(id: String, isEnabled: Boolean) {}
        override suspend fun confirmMemory(id: String) {}
        override suspend fun deleteMemory(id: String) {}
        override suspend fun clearAllMemories() {}
        override fun getMemoryCount(): Flow<Int> = flowOf(0)
        override suspend fun getFormattedMemoryContext(): String = ""
    }

    class TestAuthRepo : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()
        override val currentUserId: String? get() = (_authState.value as? AuthState.Authenticated)?.user?.uid
        override val currentUser: FirebaseUser? get() = null

        override fun attemptAutoSignIn(context: Context, scope: CoroutineScope) {}
        override fun signInWithGoogle(
            context: Context,
            scope: CoroutineScope,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
            onCancelled: () -> Unit
        ) {
            _authState.value = AuthState.Authenticated(
                AuthUser(uid = "user_123", email = "test@example.com", displayName = "Test User", photoUrl = null)
            )
            onSuccess()
        }

        override fun signUpWithEmail(
            email: String,
            password: String,
            displayName: String?,
            scope: CoroutineScope,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            _authState.value = AuthState.Authenticated(
                AuthUser(uid = "user_123", email = email, displayName = displayName, photoUrl = null)
            )
            onSuccess()
        }

        override fun signInWithEmail(
            email: String,
            password: String,
            scope: CoroutineScope,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            _authState.value = AuthState.Authenticated(
                AuthUser(uid = "user_123", email = email, displayName = "Test", photoUrl = null)
            )
            onSuccess()
        }

        override fun sendPasswordResetEmail(
            email: String,
            scope: CoroutineScope,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) { onSuccess() }

        override fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit) {
            _authState.value = AuthState.Guest
            onComplete()
        }

        override fun continueAsGuest() {
            _authState.value = AuthState.Guest
        }
    }

    class TestAiProvider : AiProvider {
        override val providerName: String = "Test"
        override val isConfigured: Boolean = true
        override fun streamGenerate(
            messages: List<AiMessage>,
            model: SonaModel,
            thinkingEnabled: Boolean,
            systemInstruction: String?
        ): Flow<GenerationResult> = flowOf(GenerationResult.Complete("Response from Sona AI"))
    }
}
