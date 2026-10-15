package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SonaDatabase
import com.example.data.local.model.ConversationEntity
import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: SonaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SonaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context verifies Sona AI app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sona AI", appName)
    }

    @Test
    fun `insert and retrieve conversation in Room database`() = runBlocking {
        val conv = ConversationEntity(
            id = "conv-1",
            title = "Test Chat",
            modelId = "model_balanced"
        )
        database.conversationDao().insertConversation(conv)

        val retrieved = database.conversationDao().getConversationById("conv-1")
        assertNotNull(retrieved)
        assertEquals("Test Chat", retrieved?.title)

        val msg = MessageEntity(
            id = "msg-1",
            conversationId = "conv-1",
            role = "user",
            content = "Hello Sona"
        )
        database.messageDao().insertMessage(msg)

        val messages = database.messageDao().getMessagesForConversation("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals("Hello Sona", messages[0].content)
    }

    @Test
    fun `insert and retrieve memory in Room database`() = runBlocking {
        val memory = MemoryEntity(
            id = "mem-1",
            key = "Preferred Language",
            content = "Kotlin",
            category = "preference"
        )
        database.memoryDao().insertMemory(memory)

        val memories = database.memoryDao().getConfirmedMemories().first()
        assertEquals(1, memories.size)
        assertEquals("Preferred Language", memories[0].key)
        assertEquals("Kotlin", memories[0].content)
    }
}
