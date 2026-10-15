package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.local.model.MemoryEntity
import com.example.data.local.model.MessageEntity
import java.util.Locale

/**
 * Represents a contextually inferred next action for Sona AI.
 *
 * @param id Unique identifier
 * @param title Brief user-facing action title (e.g., "Summarize key points", "Create a quick quiz")
 * @param subtitle Short explanation or preview of what will happen
 * @param prompt The exact prompt dispatched to Sona AI when the user selects this action
 * @param icon The relevant icon symbol representing the capability
 */
data class SmartSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector = Icons.Default.AutoAwesome
)

/**
 * Context analyzer that extracts actionable follow-ups based on the user's conversation,
 * current intent, and relevant saved memory.
 *
 * Adheres strictly to the core principle:
 * - Generate candidate actions ONLY when directly useful and supported.
 * - If intent is unclear, context is insufficient, or no useful action exists, return an empty list.
 * - Never return generic fixed cards.
 * - Maximum of 3 suggestions at a time.
 */
object ContextSuggestionEngine {

    fun generateSuggestions(
        messages: List<MessageEntity>,
        memories: List<MemoryEntity>
    ): List<SmartSuggestion> {
        // Rule: If conversation is empty or less than 1 message, no fabricated suggestions.
        if (messages.isEmpty()) {
            return emptyList()
        }

        // Recent messages provide primary context (latest messages carry the highest weight)
        val nonSystemMessages = messages.filter { it.role in listOf("user", "assistant") && it.content.isNotBlank() }
        if (nonSystemMessages.isEmpty()) {
            return emptyList()
        }

        val lastMessage = nonSystemMessages.last()
        val lastUserMessage = nonSystemMessages.lastOrNull { it.role == "user" }
        val recentHistory = nonSystemMessages.takeLast(4)
        val combinedRecentText = recentHistory.joinToString(" ") { it.content }.lowercase(Locale.ROOT)
        val lastContent = lastMessage.content.lowercase(Locale.ROOT)
        val lastUserText = lastUserMessage?.content?.lowercase(Locale.ROOT) ?: ""

        val candidates = mutableListOf<SmartSuggestion>()

        // 1. Coding & App Development Context
        if (matchesProgramming(combinedRecentText, lastContent)) {
            if (hasErrorsOrBugs(combinedRecentText)) {
                candidates.add(
                    SmartSuggestion(
                        id = "code_fix_bug",
                        title = "Investigate error & debug",
                        subtitle = "Find root cause and propose the exact fix",
                        prompt = "Analyze the error details discussed above, pinpoint the exact root cause, and provide the corrected code with explanation.",
                        icon = Icons.Default.Code
                    )
                )
            } else if (hasUiDiscussed(combinedRecentText)) {
                candidates.add(
                    SmartSuggestion(
                        id = "code_ui_review",
                        title = "Review Compose UI layout",
                        subtitle = "Check responsive insets, accessibility & M3 spacing",
                        prompt = "Review the UI discussed above for Material 3 best practices, edge-to-edge window insets, and accessibility touch targets.",
                        icon = Icons.Default.Edit
                    )
                )
                candidates.add(
                    SmartSuggestion(
                        id = "code_next_feature",
                        title = "Plan next feature implementation",
                        subtitle = "Define state management and component structure",
                        prompt = "What is the recommended next step or feature architecture to implement following this UI component?",
                        icon = Icons.Default.Code
                    )
                )
            } else {
                candidates.add(
                    SmartSuggestion(
                        id = "code_refactor_clean",
                        title = "Refactor for clean architecture",
                        subtitle = "Optimize state management and separation of concerns",
                        prompt = "Refactor the code discussed into clean architectural layers with concise comments and type safety.",
                        icon = Icons.Default.Code
                    )
                )
                candidates.add(
                    SmartSuggestion(
                        id = "code_unit_test",
                        title = "Write unit tests",
                        subtitle = "Generate test cases for edge conditions and state flows",
                        prompt = "Write clean unit test cases for the logic discussed above, covering edge cases and unexpected inputs.",
                        icon = Icons.Default.Code
                    )
                )
            }
        }

        // 2. Poetry, Shayari & Creative Writing Context
        else if (matchesPoetryOrShayari(combinedRecentText)) {
            candidates.add(
                SmartSuggestion(
                    id = "poetry_improve_draft",
                    title = "Improve draft & refine meter",
                    subtitle = "Enhance the rhythm, rhyming flow, and vocabulary",
                    prompt = "Refine the lines discussed above: suggest 2 variations with deeper emotional depth and improved poetic meter.",
                    icon = Icons.Default.FormatQuote
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "poetry_reel_hook",
                    title = "Generate reel / short hook",
                    subtitle = "Craft catchy audio hooks & visual captions",
                    prompt = "Create 3 punchy reel hooks and matching captions based on this poetry/shayari for social sharing.",
                    icon = Icons.Default.Edit
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "poetry_meaning_context",
                    title = "Explain deep meaning & context",
                    subtitle = "Break down underlying metaphors and emotion",
                    prompt = "Explain the deeper meaning, emotions, and metaphors in these verses in simple, touching words.",
                    icon = Icons.Default.AutoAwesome
                )
            )
        }

        // 3. Exam Preparation, Study & Education Context
        else if (matchesStudyOrExam(combinedRecentText)) {
            candidates.add(
                SmartSuggestion(
                    id = "study_quick_quiz",
                    title = "Create a practice quiz",
                    subtitle = "5 quick questions to test understanding",
                    prompt = "Create a 5-question multiple choice practice quiz based on the key concepts we just covered.",
                    icon = Icons.Default.Quiz
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "study_summarize_revision",
                    title = "Revision summary sheet",
                    subtitle = "Key formulas, definitions & bullet points",
                    prompt = "Provide a high-yield exam revision sheet summarizing the critical definitions, concepts, and key points from our discussion.",
                    icon = Icons.Default.Summarize
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "study_mnemonics",
                    title = "Memory tricks & mnemonics",
                    subtitle = "Clever shortcuts to memorize difficult parts",
                    prompt = "Suggest easy memory aids or mnemonics to retain the hardest parts of this topic easily.",
                    icon = Icons.Default.Psychology
                )
            )
        }

        // 4. Recipes, Food & Cooking Context
        else if (matchesRecipesOrCooking(combinedRecentText)) {
            candidates.add(
                SmartSuggestion(
                    id = "recipe_step_by_step",
                    title = "Step-by-step cooking guide",
                    subtitle = "Precise timing, heat levels & visual cues",
                    prompt = "Break down the exact step-by-step cooking instructions with clear timings, heat levels, and chef tips for success.",
                    icon = Icons.Default.Restaurant
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "recipe_substitutions",
                    title = "Ingredient substitutions",
                    subtitle = "Healthy or readily available pantry alternatives",
                    prompt = "What are the best ingredient substitutions if certain items are unavailable or if I want a healthier twist?",
                    icon = Icons.Default.Restaurant
                )
            )
        }

        // 5. Long Explanations, Articles, or Reading Context
        else if (matchesInformationalTopic(lastMessage)) {
            candidates.add(
                SmartSuggestion(
                    id = "info_summarize_key_takeaways",
                    title = "Summarize key takeaways",
                    subtitle = "Extract top 3 most important conclusions",
                    prompt = "Summarize the top 3 most important takeaways from this response in concise bullet points.",
                    icon = Icons.Default.Summarize
                )
            )
            candidates.add(
                SmartSuggestion(
                    id = "info_dive_deeper",
                    title = "Deep dive into nuances",
                    subtitle = "Explore underlying mechanisms & trade-offs",
                    prompt = "Can you delve deeper into the core mechanisms, trade-offs, and practical implications of what you just explained?",
                    icon = Icons.AutoMirrored.Filled.MenuBook
                )
            )
        }

        // 6. Memory-assisted contextual relevance (if user has enabled active memories)
        if (candidates.size < 3 && memories.isNotEmpty()) {
            val relevantMemory = memories.firstOrNull { mem ->
                mem.isEnabled && (
                    combinedRecentText.contains(mem.key.lowercase(Locale.ROOT)) ||
                    combinedRecentText.contains(mem.category.lowercase(Locale.ROOT))
                )
            }
            if (relevantMemory != null) {
                candidates.add(
                    SmartSuggestion(
                        id = "memory_related_action_${relevantMemory.id}",
                        title = "Relate to saved preference",
                        subtitle = "Apply '${relevantMemory.key}' to current conversation",
                        prompt = "Taking into account my saved preference (${relevantMemory.key}: ${relevantMemory.content}), how should we apply this to what we are discussing?",
                        icon = Icons.Default.AutoAwesome
                    )
                )
            }
        }

        // Core principle enforcement: Return at most 3 suggestions, and if confidence/relevance is low, return empty list!
        return candidates.distinctBy { it.id }.take(3)
    }

    private fun matchesProgramming(recentText: String, lastText: String): Boolean {
        val keywords = listOf(
            "kotlin", "jetpack compose", "compose", "android", "gradle", "viewmodel", "room", "stateflow",
            "coroutine", "flow", "api", "function", "class", "interface", "bug", "error", "exception",
            "crash", "nullpointer", "git", "python", "javascript", "typescript", "react", "sql", "json"
        )
        return keywords.any { recentText.contains(it) || lastText.contains(it) }
    }

    private fun hasErrorsOrBugs(text: String): Boolean {
        val errorWords = listOf("error", "exception", "failed", "crash", "bug", "unresolved reference", "syntaxerror", "issue")
        return errorWords.any { text.contains(it) }
    }

    private fun hasUiDiscussed(text: String): Boolean {
        val uiWords = listOf("ui", "screen", "composable", "button", "layout", "scaffold", "modifier", "theme", "design")
        return uiWords.any { text.contains(it) }
    }

    private fun matchesPoetryOrShayari(recentText: String): Boolean {
        val keywords = listOf(
            "shayari", "shayar", "ghazal", "nazm", "poetry", "poem", "rhyme", "verses", "sher", "couplet",
            "sad shayari", "love shayari", "urdu poetry", "hindi kavita", "kavita", "meter"
        )
        return keywords.any { recentText.contains(it) }
    }

    private fun matchesStudyOrExam(recentText: String): Boolean {
        val keywords = listOf(
            "exam", "test", "study", "syllabus", "upsc", "gate", "neet", "jee", "quiz", "revision",
            "chapter", "subject", "physics", "chemistry", "biology", "history", "mathematics", "math",
            "formula", "concept", "lecture", "homework", "assignment"
        )
        return keywords.any { recentText.contains(it) }
    }

    private fun matchesRecipesOrCooking(recentText: String): Boolean {
        val keywords = listOf(
            "recipe", "cooking", "cook", "ingredients", "dish", "curry", "bake", "baking", "paneer",
            "chicken", "sauce", "masala", "tablespoon", "simmer", "boil", "fry", "kitchen"
        )
        return keywords.any { recentText.contains(it) }
    }

    private fun matchesInformationalTopic(lastMessage: MessageEntity): Boolean {
        // Only trigger informational takeaways if the assistant just provided a long detailed response (> 200 characters)
        return lastMessage.role == "assistant" && lastMessage.content.length > 200 && lastMessage.status == "COMPLETED"
    }
}
