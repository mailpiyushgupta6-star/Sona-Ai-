package com.example.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiMessage
import com.example.ai.AiProvider
import com.example.ai.GenerationResult
import com.example.ai.SonaModel
import com.example.data.local.AppPreferences
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MessageEntity
import com.example.data.local.model.MemoryEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SonaBrainTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences
    private lateinit var fakeAiProvider: FakeAiProvider
    private lateinit var fakeConversationRepo: FakeConversationRepo
    private lateinit var fakeMemoryRepo: FakeMemoryRepo
    private lateinit var sonaBrain: SonaBrain

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
        fakeAiProvider = FakeAiProvider()
        fakeConversationRepo = FakeConversationRepo()
        fakeMemoryRepo = FakeMemoryRepo()
        sonaBrain = SonaBrain(
            aiProvider = fakeAiProvider,
            conversationRepository = fakeConversationRepo,
            memoryRepository = fakeMemoryRepo,
            appPreferences = appPreferences,
            toolRegistry = com.example.tools.ToolRegistry()
        )
    }

    @Test
    fun `test LOCAL_ONLY mode blocks cloud AI request`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_PHONE_STORAGE)
        
        val results = sonaBrain.generateResponse(
            prompt = "Hello",
            conversationId = "conv_1",
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        assertTrue(results.any { it is GenerationResult.Error && it.message.contains("Cloud AI is disabled") })
        assertEquals(0, fakeAiProvider.callCount)
    }

    @Test
    fun `test cloud enabled mode allows request`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)
        
        val results = sonaBrain.generateResponse(
            prompt = "Hello",
            conversationId = "conv_1",
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        assertTrue(results.any { it is GenerationResult.Complete })
        assertEquals(1, fakeAiProvider.callCount)
    }

    @Test
    fun `test context minimization - only recent history used`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)
        
        // Add 15 messages
        val convId = "conv_1"
        for (i in 1..15) {
            fakeConversationRepo.insertMessage(
                MessageEntity(
                    id = "msg_$i",
                    conversationId = convId,
                    role = if (i % 2 == 0) "assistant" else "user",
                    content = "Message $i",
                    timestamp = i.toLong()
                )
            )
        }

        sonaBrain.generateResponse(
            prompt = "Message 15",
            conversationId = convId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        assertEquals(10, sentMessages.size)
        // Verify recent messages are there
        assertTrue(sentMessages.any { it.content == "Message 6" })
        assertTrue(sentMessages.any { it.content == "Message 15" })
    }

    @Test
    fun `test cross conversation recall for Barfi movie recommendation`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Conversation 1: User asks for movie recommendation, assistant replies "Barfi!"
        val conv1Id = "conv_1"
        fakeConversationRepo.insertMessage(
            MessageEntity(
                id = "m1",
                conversationId = conv1Id,
                role = "user",
                content = "mujhe koi ek movie recommendation kro sirf naam batana",
                timestamp = 100L
            )
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(
                id = "m2",
                conversationId = conv1Id,
                role = "assistant",
                content = "Barfi!",
                timestamp = 200L
            )
        )

        // Conversation 2: User asks about previous recommendation
        val conv2Id = "conv_2"
        fakeConversationRepo.insertMessage(
            MessageEntity(
                id = "m3",
                conversationId = conv2Id,
                role = "user",
                content = "pichhli chats me tumne mujhe kaunsi movie recommend ki thi?",
                timestamp = 300L
            )
        )

        sonaBrain.generateResponse(
            prompt = "pichhli chats me tumne mujhe kaunsi movie recommend ki thi?",
            conversationId = conv2Id,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        assertTrue(sentMessages.any { it.content.contains("Barfi!") })
        assertTrue(sentMessages.any { it.content.contains("Relevant context from past conversations") })
    }

    @Test
    fun `test cross conversation recall with Hinglish wording variations`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        val conv1Id = "conv_1"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = conv1Id, role = "user", content = "koi achhi movie recommend karo", timestamp = 100L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = conv1Id, role = "assistant", content = "3 Idiots", timestamp = 200L)
        )

        val conv2Id = "conv_2"
        // Alternate Hinglish phrasing: "tumne pehle kya suggest kiya tha movie ke liye?"
        sonaBrain.generateResponse(
            prompt = "tumne pehle kya suggest kiya tha movie ke liye?",
            conversationId = conv2Id,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        assertTrue(sentMessages.any { it.content.contains("3 Idiots") })
    }

    @Test
    fun `test cross conversation recall prioritizes most recent conversation among multiple`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Older movie conversation
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_old_1", conversationId = "conv_old", role = "user", content = "movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_old_2", conversationId = "conv_old", role = "assistant", content = "Old Movie A", timestamp = 1100L)
        )

        // Newer movie conversation
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_new_1", conversationId = "conv_new", role = "user", content = "movie recommend karo", timestamp = 5000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_new_2", conversationId = "conv_new", role = "assistant", content = "New Movie B", timestamp = 5100L)
        )

        val currentConvId = "conv_current"
        sonaBrain.generateResponse(
            prompt = "kaunsi movie recommend ki thi?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context should be provided", contextMessage != null)
        assertTrue("Newer movie should be in context", contextMessage!!.content.contains("New Movie B"))
    }

    @Test
    fun `test no false recall when query has no matching history`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        val currentConvId = "conv_current"
        sonaBrain.generateResponse(
            prompt = "What is my flight ticket confirmation number?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        // Should NOT inject any "Relevant context from past conversations"
        assertFalse(sentMessages.any { it.content.contains("Relevant context from past conversations") })
    }

    @Test
    fun `testMovieRecallNotConfusedByRecentLaptopConversation`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Older movie conversation (timestamp 1000L)
        val movieConvId = "conv_movie"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_m1", conversationId = movieConvId, role = "user", content = "koi achhi movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_m2", conversationId = movieConvId, role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )

        // Newer laptop conversation (timestamp 9000L)
        val laptopConvId = "conv_laptop"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_l1", conversationId = laptopConvId, role = "user", content = "coding ke liye laptop suggest karo", timestamp = 9000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_l2", conversationId = laptopConvId, role = "assistant", content = "Lenovo ThinkPad", timestamp = 9100L)
        )

        val currentConvId = "conv_current"
        val responseList = sonaBrain.generateResponse(
            prompt = "Movie kaunsi suggest ki thi?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context should be provided", contextMessage != null)
        assertTrue("Context must contain movie recommendation", contextMessage!!.content.contains("3 Idiots"))
        assertFalse("Context must NOT contain unrelated laptop recommendation", contextMessage.content.contains("ThinkPad"))

        val finalResult = responseList.filterIsInstance<GenerationResult.Complete>().firstOrNull()
        assertTrue("Answer must mention 3 Idiots", finalResult?.fullText?.contains("3 Idiots") == true)
    }

    @Test
    fun `testLaptopRecallNotConfusedByMovieConversation`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        val movieConvId = "conv_movie"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_m1", conversationId = movieConvId, role = "user", content = "koi movie suggest karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_m2", conversationId = movieConvId, role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )

        val laptopConvId = "conv_laptop"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_l1", conversationId = laptopConvId, role = "user", content = "coding ke liye laptop bataya tha", timestamp = 2000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m_l2", conversationId = laptopConvId, role = "assistant", content = "Lenovo ThinkPad", timestamp = 2100L)
        )

        val currentConvId = "conv_current"
        val responseList = sonaBrain.generateResponse(
            prompt = "Coding ke liye laptop kaunsa bataya tha?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context should be provided", contextMessage != null)
        assertTrue("Context must contain laptop recommendation", contextMessage!!.content.contains("ThinkPad"))
        assertFalse("Context must NOT contain movie recommendation", contextMessage.content.contains("3 Idiots"))

        val finalResult = responseList.filterIsInstance<GenerationResult.Complete>().firstOrNull()
        assertTrue("Answer must mention ThinkPad", finalResult?.fullText?.contains("ThinkPad") == true)
    }

    @Test
    fun `testMobileCameraRecallAmongMultipleTopics`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Movie
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "conv_movie", role = "user", content = "movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = "conv_movie", role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )
        // Laptop
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l1", conversationId = "conv_laptop", role = "user", content = "laptop recommend karo", timestamp = 2000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l2", conversationId = "conv_laptop", role = "assistant", content = "ThinkPad", timestamp = 2100L)
        )
        // Mobile camera
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "p1", conversationId = "conv_phone", role = "user", content = "camera ke liye best phone recommend karo", timestamp = 3000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "p2", conversationId = "conv_phone", role = "assistant", content = "Google Pixel", timestamp = 3100L)
        )

        val currentConvId = "conv_current"
        val responseList = sonaBrain.generateResponse(
            prompt = "Camera ke liye phone kya recommend kiya tha?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context must be provided", contextMessage != null)
        assertTrue("Context must contain Pixel", contextMessage!!.content.contains("Pixel"))
        assertFalse("Context must NOT contain movie", contextMessage.content.contains("3 Idiots"))
        assertFalse("Context must NOT contain laptop", contextMessage.content.contains("ThinkPad"))

        val finalResult = responseList.filterIsInstance<GenerationResult.Complete>().firstOrNull()
        assertTrue("Answer must mention Pixel", finalResult?.fullText?.contains("Pixel") == true)
    }

    @Test
    fun `testAmbiguousSuggestionQueryRequestsClarification`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Multiple distinct recommendations exist
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "conv_movie", role = "user", content = "movie suggest karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = "conv_movie", role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l1", conversationId = "conv_laptop", role = "user", content = "laptop suggest karo", timestamp = 2000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l2", conversationId = "conv_laptop", role = "assistant", content = "ThinkPad", timestamp = 2100L)
        )

        val currentConvId = "conv_current"
        val responseList = sonaBrain.generateResponse(
            prompt = "Pehle kya suggest kiya tha?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context must be provided", contextMessage != null)
        assertTrue("Ambiguity note must be present", contextMessage!!.content.contains("AMBIGUOUS RECALL NOTE"))

        val finalResult = responseList.filterIsInstance<GenerationResult.Complete>().firstOrNull()
        // Assistant should request clarification rather than guessing
        assertTrue("Must request clarification", finalResult?.fullText?.contains("Aap movie, laptop") == true)
    }

    @Test
    fun `testExplicitTopicOutranksRecency`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Older movie conversation (1000L)
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "conv_movie", role = "user", content = "movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = "conv_movie", role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )

        // Newer laptop conversation (9999L)
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l1", conversationId = "conv_laptop", role = "user", content = "laptop recommend karo", timestamp = 9999L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "l2", conversationId = "conv_laptop", role = "assistant", content = "ThinkPad", timestamp = 10000L)
        )

        val currentConvId = "conv_current"
        sonaBrain.generateResponse(
            prompt = "Movie kaunsi suggest ki thi?",
            conversationId = currentConvId,
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Context must contain 3 Idiots despite older timestamp", contextMessage!!.content.contains("3 Idiots"))
        assertFalse("Context must NOT contain newer laptop conversation", contextMessage.content.contains("ThinkPad"))
    }

    @Test
    fun `testHistoricalAnswerRemainsPairedWithOriginalQuestion`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        val convId = "conv_movie_detailed"
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = convId, role = "user", content = "koi achhi comedy movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = convId, role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )

        sonaBrain.generateResponse(
            prompt = "Movie kaunsi suggest ki thi?",
            conversationId = "conv_current",
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        // Verify both the question and answer are in the exchange window
        assertTrue("User question must be in exchange", contextMessage!!.content.contains("koi achhi comedy movie recommend karo"))
        assertTrue("Assistant answer must be in exchange", contextMessage.content.contains("3 Idiots"))
    }

    @Test
    fun `testNoUnrelatedHistoricalContextInjected`() = runTest {
        appPreferences.setStorageLocation(AppPreferences.STORAGE_CLOUD_FIRESTORE)

        // Movie
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m1", conversationId = "conv_movie", role = "user", content = "movie recommend karo", timestamp = 1000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "m2", conversationId = "conv_movie", role = "assistant", content = "3 Idiots", timestamp = 1100L)
        )
        // Career
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "c1", conversationId = "conv_career", role = "user", content = "government exam preparation plan", timestamp = 2000L)
        )
        fakeConversationRepo.insertMessage(
            MessageEntity(id = "c2", conversationId = "conv_career", role = "assistant", content = "UPSC syllabus strategy", timestamp = 2100L)
        )

        sonaBrain.generateResponse(
            prompt = "Movie kaunsi suggest ki thi?",
            conversationId = "conv_current",
            model = SonaModel.BALANCED,
            thinkingEnabled = false
        ).toList()

        val sentMessages = fakeAiProvider.lastMessages
        val contextMessage = sentMessages.find { it.content.contains("Relevant context from past conversations") }
        assertTrue("Movie should be included", contextMessage!!.content.contains("3 Idiots"))
        assertFalse("Unrelated career chat must NOT be included", contextMessage.content.contains("UPSC"))
    }

    // --- Fakes ---

    class FakeAiProvider : AiProvider {
        var callCount = 0
        var lastMessages: List<AiMessage> = emptyList()
        override val providerName: String = "Fake"
        override val isConfigured: Boolean = true

        override fun streamGenerate(
            messages: List<AiMessage>,
            model: SonaModel,
            thinkingEnabled: Boolean,
            systemInstruction: String?
        ): Flow<GenerationResult> {
            callCount++
            lastMessages = messages
            val contextMsg = messages.find { it.content.contains("Relevant context from past conversations") }?.content ?: ""
            val responseText = when {
                contextMsg.contains("AMBIGUOUS RECALL NOTE") -> "Aap movie, laptop ya mobile recommendation ke baare mein pooch rahe hain?"
                contextMsg.contains("3 Idiots") -> "Maine aapko 3 Idiots suggest ki thi."
                contextMsg.contains("ThinkPad") -> "Coding ke liye maine Lenovo ThinkPad bataya tha."
                contextMsg.contains("Pixel") -> "Camera ke liye maine Google Pixel recommend kiya tha."
                contextMsg.contains("Barfi!") -> "Maine aapko Barfi! recommend ki thi."
                else -> "Fake response"
            }
            return flowOf(GenerationResult.Complete(responseText))
        }
    }

    class FakeConversationRepo : ConversationRepository {
        private val messages = mutableListOf<MessageEntity>()
        override fun getConversations() = flowOf(emptyList<ConversationEntity>())
        override fun searchConversations(query: String) = flowOf(emptyList<ConversationEntity>())
        override fun getMessages(conversationId: String) = flowOf(messages.filter { it.conversationId == conversationId })
        override suspend fun getMessagesSnapshot(conversationId: String) = messages.filter { it.conversationId == conversationId }
        override suspend fun getConversation(id: String): ConversationEntity? = null
        override suspend fun createConversation(title: String, modelId: String) = ConversationEntity(title = title)
        override suspend fun updateTitle(id: String, title: String) {}
        override suspend fun togglePin(id: String, isPinned: Boolean) {}
        override suspend fun deleteConversation(id: String) {}
        override suspend fun clearAllConversations() { messages.clear() }
        override suspend fun insertMessage(message: MessageEntity) { messages.add(message) }
        override suspend fun updateMessageContent(id: String, content: String, status: String) {}
        override suspend fun saveConversation(conversation: ConversationEntity) {}
        override suspend fun getMessage(id: String): MessageEntity? = messages.find { it.id == id }
        override suspend fun deleteMessage(id: String) { messages.removeIf { it.id == id } }
        override suspend fun searchMessages(query: String): List<MessageEntity> = messages.filter { it.content.contains(query) }
        override fun getStats() = flowOf(Pair(0, 0))
    }

    class FakeMemoryRepo : MemoryRepository {
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
        ) = MemoryEntity(key = key, content = content)
        override suspend fun updateMemory(memory: MemoryEntity) {}
        override suspend fun toggleMemory(id: String, isEnabled: Boolean) {}
        override suspend fun confirmMemory(id: String) {}
        override suspend fun deleteMemory(id: String) {}
        override suspend fun clearAllMemories() {}
        override fun getMemoryCount() = flowOf(0)
        override suspend fun getFormattedMemoryContext(): String = ""
    }
}
