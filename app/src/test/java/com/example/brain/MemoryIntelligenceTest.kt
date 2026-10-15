package com.example.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.MemoryDao
import com.example.data.local.model.MemoryEntity
import com.example.data.repository.LocalMemoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.room.Room
import com.example.data.local.SonaDatabase

@RunWith(RobolectricTestRunner::class)
class MemoryIntelligenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, SonaDatabase::class.java).allowMainThreadQueries().build()
    private val memoryDao = db.memoryDao()
    private val repository = LocalMemoryRepository(memoryDao)

    @Test
    fun `test memory CRUD and confirmation status`() = runBlocking {
        val memory = repository.addMemory(
            key = "user_name",
            content = "Piyush",
            category = "preference",
            isConfirmed = false // Candidate
        )

        val candidates = repository.getCandidateMemories().first()
        assertEquals(1, candidates.size)
        assertEquals("Piyush", candidates[0].content)

        repository.confirmMemory(memory.id)

        val confirmed = repository.getConfirmedMemories().first()
        assertEquals(1, confirmed.size)
        assertEquals("Piyush", confirmed[0].content)
        assertTrue(confirmed[0].isConfirmed)
    }

    @Test
    fun `test source preservation`() = runBlocking {
        repository.addMemory(
            key = "topic",
            content = "Fact",
            sourceMessageId = "msg_123",
            sourceConversationId = "conv_456"
        )

        val confirmed = repository.getConfirmedMemoriesList()
        assertEquals("msg_123", confirmed[0].sourceMessageId)
        assertEquals("conv_456", confirmed[0].sourceConversationId)
    }
}
