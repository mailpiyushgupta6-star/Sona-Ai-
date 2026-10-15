package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.FirestoreDataSource
import com.example.data.remote.model.CloudConversationDto
import com.example.data.remote.model.CloudMessageDto
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.util.UUID

@org.junit.Ignore("Requires local Firebase emulator")
class CloudSyncRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createConversation_authenticatedOwner_savesAndRetrievesSuccessfully() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreDataSource(firestore)

        val convId = UUID.randomUUID().toString()
        val conversation = CloudConversationDto(
            id = convId,
            userId = aliceUid,
            title = "Cloud Synced Chat",
            modelId = "model_balanced",
            isPinned = false,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.saveConversation(aliceUid, conversation)
        }

        val conversations = withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.observeConversations(aliceUid).first()
        }

        assertTrue(conversations.any { it.id == convId })
        val retrieved = conversations.first { it.id == convId }
        assertEquals("Cloud Synced Chat", retrieved.title)
    }

    @Test
    fun getConversations_crossUserAccess_isRejectedBySecurityRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceDataSource = FirestoreDataSource(firestore)

        val convId = UUID.randomUUID().toString()
        val conversation = CloudConversationDto(
            id = convId,
            userId = aliceUid,
            title = "Alice Secret Chat",
            modelId = "model_balanced",
            isPinned = false,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceDataSource.saveConversation(aliceUid, conversation)
        }

        // Switch to Bob
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobDataSource = FirestoreDataSource(firestore)

        try {
            // Bob tries to read Alice's collection
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobDataSource.observeConversations(aliceUid).first()
            }
            fail("Expected exception accessing another user's collection")
        } catch (e: Exception) {
            // Either dataSource requireUserId fails or Firestore PERMISSION_DENIED is thrown
            assertTrue(
                e is IllegalStateException ||
                (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
        }
    }

    @Test
    fun createMessage_authenticatedOwner_savesInSubcollection() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreDataSource(firestore)

        val convId = UUID.randomUUID().toString()
        val msgId = UUID.randomUUID().toString()

        val conversation = CloudConversationDto(
            id = convId,
            userId = aliceUid,
            title = "Thread Chat",
            modelId = "model_balanced",
            isPinned = false,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        dataSource.saveConversation(aliceUid, conversation)

        val message = CloudMessageDto(
            id = msgId,
            conversationId = convId,
            userId = aliceUid,
            role = "user",
            content = "Testing cloud sync",
            status = "COMPLETED",
            createdAt = Timestamp.now()
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.saveMessage(aliceUid, message)
        }

        val messages = withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.observeMessages(aliceUid, convId).first()
        }

        assertTrue(messages.any { it.id == msgId })
        assertEquals("Testing cloud sync", messages.first { it.id == msgId }.content)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@sona.ai"
        const val BOB_EMAIL = "bob@sona.ai"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
