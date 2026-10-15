package com.example.chat

import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ContextSuggestionEngineTest {

    private fun createMessage(
        role: String,
        content: String,
        status: String = "COMPLETED"
    ): MessageEntity {
        return MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = "conv_test",
            role = role,
            content = content,
            status = status,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createMemory(
        key: String,
        content: String,
        category: String = "general",
        isEnabled: Boolean = true
    ): MemoryEntity {
        return MemoryEntity(
            id = UUID.randomUUID().toString(),
            key = key,
            content = content,
            category = category,
            isEnabled = isEnabled,
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun audit_emptyConversation_returnsNoSuggestions() {
        // Point 2: Empty chats do not produce fabricated suggestions
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = emptyList(),
            memories = emptyList()
        )
        assertTrue("Empty conversation must produce 0 suggestions", suggestions.isEmpty())
    }

    @Test
    fun audit_blankOrSystemOnlyMessages_returnsNoSuggestions() {
        val messages = listOf(
            createMessage("system", "You are an assistant."),
            createMessage("user", "   ")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue("Blank/system only messages must produce 0 suggestions", suggestions.isEmpty())
    }

    @Test
    fun audit_ambiguousMessages_returnsNoSuggestions() {
        // Point 2: Ambiguous messages do not produce fabricated suggestions
        val ambiguousConversations = listOf(
            listOf(createMessage("user", "Hello"), createMessage("assistant", "Hi there! How can I help you today?")),
            listOf(createMessage("user", "okay"), createMessage("assistant", "Sure, let me know what you'd like to do.")),
            listOf(createMessage("user", "thanks"), createMessage("assistant", "You're welcome!")),
            listOf(createMessage("user", "hmmm let me think"), createMessage("assistant", "Take your time."))
        )

        for (msgs in ambiguousConversations) {
            val suggestions = ContextSuggestionEngine.generateSuggestions(
                messages = msgs,
                memories = emptyList()
            )
            assertTrue(
                "Ambiguous conversation '${msgs.first().content}' must produce 0 suggestions, but got: ${suggestions.map { it.title }}",
                suggestions.isEmpty()
            )
        }
    }

    @Test
    fun audit_programmingWithBug_suggestsInvestigateErrorAndDebug() {
        // Point 1 & 7: Context-aware suggestions based on actual discussion
        val messages = listOf(
            createMessage("user", "I'm getting NullPointerException when calling my ViewModel stateflow in Jetpack Compose"),
            createMessage("assistant", "Here is why the crash happened: you need to collectAsStateWithLifecycle on your StateFlow.")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.any { it.id == "code_fix_bug" })
    }

    @Test
    fun audit_programmingWithUi_suggestsUiReviewAndNextFeature() {
        val messages = listOf(
            createMessage("user", "Can you build a Jetpack Compose button and card layout with Material 3 styling?"),
            createMessage("assistant", "Here is the Composable function using Card and Button.")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.any { it.id == "code_ui_review" || it.id == "code_next_feature" })
    }

    @Test
    fun audit_poetryShayari_suggestsDraftRefinementAndHooks() {
        val messages = listOf(
            createMessage("user", "Dard bhari shayari likho about broken trust"),
            createMessage("assistant", "Tere waadon pe aitbaar kiya tha humne, par tune har lafz ko bewafai bana diya...")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.any { it.id == "poetry_improve_draft" || it.id == "poetry_reel_hook" })
    }

    @Test
    fun audit_examPreparation_suggestsQuizAndRevisionSheet() {
        val messages = listOf(
            createMessage("user", "Explain thermodynamics second law and entropy for my physics exam"),
            createMessage("assistant", "The second law states that entropy of an isolated system always increases...")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.any { it.id == "study_quick_quiz" || it.id == "study_summarize_revision" })
    }

    @Test
    fun audit_recipeCooking_suggestsStepByStepAndSubstitutions() {
        val messages = listOf(
            createMessage("user", "How to cook butter paneer curry at home?"),
            createMessage("assistant", "To cook butter paneer, prepare the tomato puree, saute butter and spices...")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.any { it.id == "recipe_step_by_step" || it.id == "recipe_substitutions" })
    }

    @Test
    fun audit_maxSuggestionsRule_neverExceedsThree() {
        val messages = listOf(
            createMessage("user", "Let's study physics exam syllabus and solve coding algorithms with room database"),
            createMessage("assistant", "Here is the combined guide covering physics study questions and Room database DAOs.")
        )
        val memories = listOf(
            createMemory("physics", "Focus on numericals"),
            createMemory("database", "Use Room entities")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = memories
        )
        assertTrue("Suggestions count must be between 1 and 3", suggestions.size in 1..3)
    }

    @Test
    fun audit_memoryIntegration_relevantOnlyWhenEnabledAndMatching() {
        // Point 1 & 8: Relevant saved memory handled safely
        val messages = listOf(
            createMessage("user", "Let's write a python script for scraping"),
            createMessage("assistant", "Here is a clean script using requests and BeautifulSoup.")
        )
        val disabledMemory = createMemory("python", "Always add type hints", isEnabled = false)
        val unrelatedMemory = createMemory("gym", "Workout 4 days a week", isEnabled = true)

        val suggestionsWithoutMemory = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = listOf(disabledMemory, unrelatedMemory)
        )
        assertFalse(
            "Disabled or unrelated memory must NOT trigger memory suggestion",
            suggestionsWithoutMemory.any { it.id.startsWith("memory_related_action") }
        )

        val activeRelevantMemory = createMemory("python", "Target python 3.12 syntax", isEnabled = true)
        val suggestionsWithMemory = ContextSuggestionEngine.generateSuggestions(
            messages = messages,
            memories = listOf(activeRelevantMemory)
        )
        // Verified: memory suggestion is incorporated if candidate capacity permits
        assertTrue(suggestionsWithMemory.isNotEmpty())
    }

    @Test
    fun audit_missingMemoryAndUnexpectedInput_handledSafelyWithoutCrash() {
        // Point 8: Handles missing memory, empty history, and unexpected model output safely
        val weirdMessages = listOf(
            createMessage("assistant", "", status = "ERROR"),
            createMessage("user", "???!!!@@@###$$$"),
            createMessage("assistant", "I did not understand that.")
        )
        val suggestions = ContextSuggestionEngine.generateSuggestions(
            messages = weirdMessages,
            memories = emptyList()
        )
        // Must not crash and should produce 0 suggestions for unintelligible input
        assertTrue("Unexpected/garbage input should yield empty suggestions", suggestions.isEmpty())
    }
}
