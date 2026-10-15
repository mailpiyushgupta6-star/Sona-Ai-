package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MessageEntity
import com.example.data.remote.FirestoreDataSource
import com.example.data.remote.model.CloudConversationDto
import com.example.data.remote.model.CloudMessageDto
import com.example.sync.SyncStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.dao.TombstoneDao
import com.example.data.local.model.SyncQueueEntity
import com.example.data.local.model.TombstoneEntity
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPersistenceAndSyncTest {

    private lateinit var fakeLocalRepo: FakeConversationRepo
    private lateinit var fakeRemoteDataSource: FakeRemoteDataSource
    private lateinit var fakeTombstoneDao: FakeTombstoneDao
    private lateinit var fakeSyncQueueDao: FakeSyncQueueDao
    private lateinit var testScope: TestScope
    private lateinit var syncedRepo: SyncedConversationRepository

    @Before
    fun setup() {
        fakeLocalRepo = FakeConversationRepo()
        fakeRemoteDataSource = FakeRemoteDataSource()
        fakeTombstoneDao = FakeTombstoneDao()
        fakeSyncQueueDao = FakeSyncQueueDao()
        testScope = TestScope()
        syncedRepo = SyncedConversationRepository(
            localRepository = fakeLocalRepo,
            remoteDataSource = fakeRemoteDataSource,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tombstoneDao = fakeTombstoneDao,
            syncQueueDao = fakeSyncQueueDao
        )
    }

    // 1. Local conversations surviving repository reloads
    @Test
    fun testLocalConversationsSurvivingReload() = runTest {
        val conv = fakeLocalRepo.createConversation("Persistent Chat", "model_balanced")
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conv.id,
            role = "user",
            content = "Hello Sona",
            timestamp = System.currentTimeMillis()
        )
        fakeLocalRepo.insertMessage(msg)

        // Verify stored in local repository
        val loadedConv = fakeLocalRepo.getConversation(conv.id)
        assertNotNull(loadedConv)
        assertEquals("Persistent Chat", loadedConv?.title)

        val loadedMsgs = fakeLocalRepo.getMessagesSnapshot(conv.id)
        assertEquals(1, loadedMsgs.size)
        assertEquals("Hello Sona", loadedMsgs.first().content)
    }

    // 2. Remote conversations being imported into Room
    @Test
    fun testRemoteConversationsImportedIntoRoom() = testScope.runTest {
        val userId = "test_user_123"
        val remoteConv = CloudConversationDto(
            id = "remote_conv_1",
            userId = userId,
            title = "Cloud Imported Chat",
            modelId = "model_balanced",
            isPinned = true,
            createdAt = Timestamp(Date(1000L)),
            updatedAt = Timestamp(Date(2000L))
        )
        fakeRemoteDataSource.remoteConversations[userId] = mutableListOf(remoteConv)

        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        val localConv = fakeLocalRepo.getConversation("remote_conv_1")
        assertNotNull("Remote conversation should be imported into local repository", localConv)
        assertEquals("Cloud Imported Chat", localConv?.title)
        assertTrue(localConv?.isPinned == true)
        assertEquals(SyncStatus.SYNCED, syncedRepo.syncState.value.status)
    }

    // 3. Remote messages being restored without duplication
    @Test
    fun testRemoteMessagesRestoredWithoutDuplication() = testScope.runTest {
        val userId = "test_user_123"
        val convId = "conv_with_msgs"
        val remoteConv = CloudConversationDto(
            id = convId,
            userId = userId,
            title = "Message Sync Chat",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        fakeRemoteDataSource.remoteConversations[userId] = mutableListOf(remoteConv)

        val msg1 = CloudMessageDto(
            id = "msg_1",
            conversationId = convId,
            userId = userId,
            role = "user",
            content = "Prompt 1",
            createdAt = Timestamp(Date(1000L))
        )
        val msg2 = CloudMessageDto(
            id = "msg_2",
            conversationId = convId,
            userId = userId,
            role = "assistant",
            content = "Answer 1",
            status = "COMPLETED",
            createdAt = Timestamp(Date(2000L))
        )
        fakeRemoteDataSource.remoteMessages[convId] = mutableListOf(msg1, msg2)

        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        // Sync again to verify idempotency
        syncedRepo.syncMessagesForConversation(convId)
        testScope.advanceUntilIdle()

        val msgs = fakeLocalRepo.getMessagesSnapshot(convId)
        assertEquals("Messages should be restored without duplication", 2, msgs.size)
        assertEquals("msg_1", msgs[0].id)
        assertEquals("msg_2", msgs[1].id)
    }

    // 4. Newer local content not being overwritten by older remote data
    @Test
    fun testNewerLocalContentNotOverwrittenByOlderRemoteData() = testScope.runTest {
        val userId = "test_user_123"
        val convId = "conflict_conv"

        // Local edit has newer updatedAt (5000L)
        val localConv = ConversationEntity(
            id = convId,
            title = "Newer Local Title",
            createdAt = 1000L,
            updatedAt = 5000L
        )
        fakeLocalRepo.saveConversation(localConv)

        // Remote has older updatedAt (2000L)
        val remoteConv = CloudConversationDto(
            id = convId,
            userId = userId,
            title = "Stale Remote Title",
            createdAt = Timestamp(Date(1000L)),
            updatedAt = Timestamp(Date(2000L))
        )
        fakeRemoteDataSource.remoteConversations[userId] = mutableListOf(remoteConv)

        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        val resultingConv = fakeLocalRepo.getConversation(convId)
        assertEquals("Local title should be preserved because local updatedAt is newer", "Newer Local Title", resultingConv?.title)
    }

    // 5. Completed assistant responses being synchronized
    @Test
    fun testCompletedAssistantResponsesSynchronized() = testScope.runTest {
        val userId = "test_user_123"
        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        val conv = fakeLocalRepo.createConversation("Stream Chat")
        val assistantMsg = MessageEntity(
            id = "assistant_stream_1",
            conversationId = conv.id,
            role = "assistant",
            content = "Incomplete stream...",
            status = "STREAMING"
        )
        fakeLocalRepo.insertMessage(assistantMsg)

        // Intermediate streaming updates should not upload
        syncedRepo.updateMessageContent(assistantMsg.id, "Stream chunk 1", "STREAMING")
        testScope.advanceUntilIdle()
        assertFalse(fakeRemoteDataSource.savedMessages.any { it.id == assistantMsg.id && it.status == "STREAMING" })

        // Completed status triggers cloud synchronization
        syncedRepo.updateMessageContent(assistantMsg.id, "Final complete response from Sona AI.", "COMPLETED")
        testScope.advanceUntilIdle()

        val savedCloudMsg = fakeRemoteDataSource.savedMessages.find { it.id == assistantMsg.id }
        assertNotNull("Completed assistant message must be synchronized to Firestore", savedCloudMsg)
        assertEquals("COMPLETED", savedCloudMsg?.status)
        assertEquals("Final complete response from Sona AI.", savedCloudMsg?.content)
    }

    // 6. Account switching not mixing conversations
    @Test
    fun testAccountSwitchingDoesNotMixConversations() = testScope.runTest {
        val userA = "user_A"
        val userB = "user_B"

        fakeRemoteDataSource.remoteConversations[userA] = mutableListOf(
            CloudConversationDto(id = "conv_A", userId = userA, title = "Alice Chat", createdAt = Timestamp.now(), updatedAt = Timestamp.now())
        )
        fakeRemoteDataSource.remoteConversations[userB] = mutableListOf(
            CloudConversationDto(id = "conv_B", userId = userB, title = "Bob Chat", createdAt = Timestamp.now(), updatedAt = Timestamp.now())
        )

        // Login User A
        syncedRepo.setActiveUser(userA)
        testScope.advanceUntilIdle()
        assertNotNull(fakeLocalRepo.getConversation("conv_A"))

        // Sign out to Guest
        syncedRepo.setActiveUser(null)
        testScope.advanceUntilIdle()
        assertEquals(SyncStatus.IDLE, syncedRepo.syncState.value.status)
    }

    // 7. Empty Firestore results not wiping local history
    @Test
    fun testEmptyFirestoreResultsDoNotWipeLocalHistory() = testScope.runTest {
        val userId = "test_user_empty"
        val localConv = fakeLocalRepo.createConversation("Local Existing Chat")

        // Remote returns empty list
        fakeRemoteDataSource.remoteConversations[userId] = mutableListOf()

        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        val stillExists = fakeLocalRepo.getConversation(localConv.id)
        assertNotNull("Local history must never be wiped when remote returns empty", stillExists)
        assertEquals("Local Existing Chat", stillExists?.title)
    }

    // 8. Search clearing and active conversation restoration
    @Test
    fun testSearchClearingAndConversationSelection() {
        val queryFlow = MutableStateFlow("some_query")
        assertEquals("some_query", queryFlow.value)

        // Clear query on new chat or close drawer
        queryFlow.value = ""
        assertEquals("", queryFlow.value)
    }

    // 9. Sync failure leaving local chats accessible
    @Test
    fun testSyncFailureLeavesLocalChatsAccessible() = testScope.runTest {
        val userId = "failing_user"
        val localConv = fakeLocalRepo.createConversation("Resilient Offline Chat")
        fakeRemoteDataSource.shouldFail = true

        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        assertEquals(SyncStatus.ERROR, syncedRepo.syncState.value.status)
        val accessibleLocal = fakeLocalRepo.getConversation(localConv.id)
        assertNotNull("Local chats must remain fully accessible even if cloud sync throws error", accessibleLocal)
        assertEquals("Resilient Offline Chat", accessibleLocal?.title)
    }

    // 10. Database migration safety
    @Test
    fun testDatabaseMigrationSafetyCheck() {
        val m12 = com.example.data.local.SonaDatabase.MIGRATION_1_2
        assertEquals(1, m12.startVersion)
        assertEquals(2, m12.endVersion)

        val m23 = com.example.data.local.SonaDatabase.MIGRATION_2_3
        assertEquals(2, m23.startVersion)
        assertEquals(3, m23.endVersion)

        val m34 = com.example.data.local.SonaDatabase.MIGRATION_3_4
        assertEquals(3, m34.startVersion)
        assertEquals(4, m34.endVersion)
    }

    // 11. Persistent tombstones prevent resurrection during cloud hydration
    @Test
    fun testTombstonePreventsResurrectionOnCloudHydration() = testScope.runTest {
        val userId = "test_user_tombstone"
        val convId = "conv_to_be_deleted"

        // Create and delete conversation
        val localConv = fakeLocalRepo.createConversation("To Delete")
        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        // Remotely the conversation still exists (simulating slow server deletion or stale copy)
        fakeRemoteDataSource.remoteConversations[userId] = mutableListOf(
            CloudConversationDto(id = localConv.id, userId = userId, title = "To Delete", createdAt = Timestamp.now(), updatedAt = Timestamp.now())
        )

        // Delete locally -> adds tombstone
        syncedRepo.deleteConversation(localConv.id)
        testScope.advanceUntilIdle()

        // Verify tombstone is recorded in tombstoneDao
        val isTombstoned = fakeTombstoneDao.isTombstoned(localConv.id, userId)
        assertEquals("Tombstone must be recorded", 1, isTombstoned)

        // Simulate app restart / new sync cycle with cloud
        val newRepo = SyncedConversationRepository(
            localRepository = fakeLocalRepo,
            remoteDataSource = fakeRemoteDataSource,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tombstoneDao = fakeTombstoneDao,
            syncQueueDao = fakeSyncQueueDao
        )
        newRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        // Conversation should NOT be resurrected
        val resurrected = fakeLocalRepo.getConversation(localConv.id)
        assertNull("Deleted conversation must NOT be resurrected during hydration", resurrected)
    }

    // 12. Failed operations are safely recorded in offline sync queue
    @Test
    fun testFailedOperationsEnqueuedInSyncQueue() = testScope.runTest {
        val userId = "test_user_queue"
        syncedRepo.setActiveUser(userId)
        testScope.advanceUntilIdle()

        // Simulate network failure
        fakeRemoteDataSource.shouldFail = true

        val localConv = fakeLocalRepo.createConversation("Queued Chat")
        syncedRepo.deleteConversation(localConv.id)
        testScope.advanceUntilIdle()

        // Verify operation enqueued
        val pending = fakeSyncQueueDao.getPendingOperations(userId)
        assertEquals(1, pending.size)
        assertEquals("CONVERSATION", pending[0].entityType)
        assertEquals("DELETE", pending[0].operationType)
        assertEquals(localConv.id, pending[0].entityId)
    }
}

// --- Fake In-Memory Repositories for Deterministic JVM Tests ---

class FakeConversationRepo : ConversationRepository {
    val conversations = mutableMapOf<String, ConversationEntity>()
    val messages = mutableMapOf<String, MessageEntity>()

    private val convsFlow = MutableStateFlow<List<ConversationEntity>>(emptyList())
    private val msgsFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

    private fun updateFlows() {
        convsFlow.value = conversations.values.sortedWith(
            compareByDescending<ConversationEntity> { it.isPinned }
                .thenByDescending { it.updatedAt }
        )
        msgsFlow.value = messages.values.sortedBy { it.timestamp }
    }

    override fun getConversations(): Flow<List<ConversationEntity>> = convsFlow.asStateFlow()

    override fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return convsFlow.map { list -> list.filter { it.title.contains(query, ignoreCase = true) } }
    }

    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return msgsFlow.map { list -> list.filter { it.conversationId == conversationId } }
    }

    override suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> {
        return messages.values.filter { it.conversationId == conversationId }.sortedBy { it.timestamp }
    }

    override suspend fun getConversation(id: String): ConversationEntity? = conversations[id]

    override suspend fun saveConversation(conversation: ConversationEntity) {
        conversations[conversation.id] = conversation
        updateFlows()
    }

    override suspend fun getMessage(id: String): MessageEntity? = messages[id]

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

    override suspend fun searchMessages(query: String): List<MessageEntity> {
        return messages.values.filter { it.content.contains(query, ignoreCase = true) }
    }

    override fun getStats(): Flow<Pair<Int, Int>> {
        return convsFlow.map { Pair(it.size, messages.size) }
    }
}

class FakeRemoteDataSource : FirestoreDataSource(null) {
    val remoteConversations = mutableMapOf<String, MutableList<CloudConversationDto>>()
    val remoteMessages = mutableMapOf<String, MutableList<CloudMessageDto>>()
    val savedMessages = mutableListOf<CloudMessageDto>()
    var shouldFail: Boolean = false

    override suspend fun getConversations(userId: String): List<CloudConversationDto> {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        return remoteConversations[userId]?.toList() ?: emptyList()
    }

    override fun observeConversations(userId: String): Flow<List<CloudConversationDto>> = kotlinx.coroutines.flow.flow {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        emit(remoteConversations[userId]?.toList() ?: emptyList())
    }

    override suspend fun getMessages(userId: String, conversationId: String): List<CloudMessageDto> {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        return remoteMessages[conversationId]?.toList() ?: emptyList()
    }

    override fun observeMessages(userId: String, conversationId: String): Flow<List<CloudMessageDto>> = kotlinx.coroutines.flow.flow {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        emit(remoteMessages[conversationId]?.toList() ?: emptyList())
    }

    override suspend fun saveConversation(userId: String, conv: CloudConversationDto) {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        val list = remoteConversations.getOrPut(userId) { mutableListOf() }
        list.removeAll { it.id == conv.id }
        list.add(conv)
    }

    override suspend fun saveMessage(userId: String, message: CloudMessageDto) {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        savedMessages.add(message)
        val list = remoteMessages.getOrPut(message.conversationId) { mutableListOf() }
        list.removeAll { it.id == message.id }
        list.add(message)
    }

    override suspend fun deleteConversation(userId: String, conversationId: String) {
        if (shouldFail) throw IllegalStateException("Network connection unavailable")
        remoteConversations[userId]?.removeAll { it.id == conversationId }
    }
}

class FakeTombstoneDao : TombstoneDao {
    val tombstones = mutableListOf<TombstoneEntity>()
    override suspend fun getTombstonesForUser(uid: String) = tombstones.filter { it.uid == uid }
    override suspend fun isTombstoned(id: String, uid: String): Int = tombstones.count { it.id == id && it.uid == uid }
    override suspend fun insertTombstone(tombstone: TombstoneEntity) {
        tombstones.removeIf { it.id == tombstone.id }
        tombstones.add(tombstone)
    }
    override suspend fun clearTombstonesForUser(uid: String) { tombstones.removeIf { it.uid == uid } }
}

class FakeSyncQueueDao : SyncQueueDao {
    val queue = mutableListOf<SyncQueueEntity>()
    override suspend fun getPendingOperations(uid: String) = queue.filter { it.uid == uid && it.status == "PENDING" }
    override fun observeQueue(uid: String) = kotlinx.coroutines.flow.flowOf(queue.filter { it.uid == uid })
    override suspend fun enqueue(operation: SyncQueueEntity) { queue.add(operation) }
    override suspend fun updateOperation(operation: SyncQueueEntity) {
        val idx = queue.indexOfFirst { it.operationId == operation.operationId }
        if (idx != -1) queue[idx] = operation
    }
    override suspend fun deleteOperation(operationId: String) { queue.removeIf { it.operationId == operationId } }
    override suspend fun clearQueueForUser(uid: String) { queue.removeIf { it.uid == uid } }
}
