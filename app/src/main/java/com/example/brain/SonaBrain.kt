package com.example.brain

import com.example.ai.AiMessage
import com.example.ai.AiProvider
import com.example.ai.GenerationResult
import com.example.ai.SonaModel
import com.example.data.local.AppPreferences
import com.example.data.local.model.MessageEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SonaBrain(
    private val aiProvider: AiProvider,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val appPreferences: AppPreferences?,
    private val toolRegistry: com.example.tools.ToolRegistry
) {
    val isConfigured: Boolean get() = aiProvider.isConfigured

    /**
     * Orchestrates the AI generation flow with privacy-first principles.
     */
    fun generateResponse(
        prompt: String,
        conversationId: String,
        model: SonaModel,
        thinkingEnabled: Boolean,
        imageBase64: String? = null,
        imageMimeType: String? = null
    ): Flow<GenerationResult> = flow {
        // 1. Enforce Privacy at the Network Boundary
        val storageLocation = appPreferences?.getStorageLocation() ?: AppPreferences.STORAGE_CLOUD_FIRESTORE
        if (storageLocation == AppPreferences.STORAGE_PHONE_STORAGE) {
            emit(
                GenerationResult.Error(
                    "Cloud AI is disabled in Phone Storage mode to preserve your privacy. " +
                            "Switch to Cloud mode in Settings if you wish to use Sona's AI features."
                )
            )
            return@flow
        }

        // 2. Load relevant context and memories
        val context = prepareContext(prompt, conversationId, imageBase64, imageMimeType)

        // 3. Build System Instruction with relevant memories
        val systemInstruction = buildSystemInstruction()

        // 4. Delegate to the provider abstraction (with tool loop)
        var currentContext = context
        var toolExecuted = false
        
        repeat(3) { // Max 3 tool iterations
            var generatedText = ""
            var completeResult: GenerationResult.Complete? = null
            
            aiProvider.streamGenerate(
                messages = currentContext,
                model = model,
                thinkingEnabled = thinkingEnabled,
                systemInstruction = systemInstruction
            ).collect { result ->
                if (result is GenerationResult.Chunk) {
                    generatedText += result.text
                    emit(result)
                } else if (result is GenerationResult.Complete) {
                    completeResult = result
                    generatedText = result.fullText
                } else {
                    emit(result)
                }
            }
            
            val toolCall = parseToolCall(generatedText)
            if (toolCall != null) {
                emit(GenerationResult.Chunk("\n[Executing tool: ${toolCall.first}]...\n"))
                val toolResult = toolRegistry.getTool(toolCall.first)?.execute(toolCall.second)
                    ?: com.example.tools.ToolResult.Error("Tool not found: ${toolCall.first}")
                
                val output = when (toolResult) {
                    is com.example.tools.ToolResult.Success -> toolResult.output
                    is com.example.tools.ToolResult.Error -> "Error: ${toolResult.message}"
                }
                
                val nextContext = currentContext.toMutableList()
                nextContext.add(AiMessage("assistant", generatedText))
                nextContext.add(AiMessage("user", "Tool output: $output"))
                currentContext = nextContext
                toolExecuted = true
            } else {
                if (completeResult != null) emit(completeResult!!)
                return@flow
            }
        }
    }

    private fun parseToolCall(text: String): Pair<String, Map<String, String>>? {
        val regex = Regex("\\[TOOL:(\\w+)\\]\\s*(.*)")
        val match = regex.find(text) ?: return null
        val toolId = match.groupValues[1]
        val argsString = match.groupValues[2]
        
        val args = argsString.split(" ").associate {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }.filter { it.key.isNotEmpty() }
        
        return Pair(toolId, args)
    }

    private suspend fun prepareContext(
        prompt: String,
        conversationId: String,
        imageBase64: String?,
        imageMimeType: String?
    ): List<AiMessage> {
        val context = mutableListOf<AiMessage>()

        // A. Get recent conversation history (limit to 10 for minimization)
        val history = conversationRepository.getMessagesSnapshot(conversationId)
        val recentMessages = history.takeLast(10)

        // B. Search for relevant past messages across all conversations (topic-aware relevance scoring)
        val parsedQuery = extractQueryTerms(prompt)
        val searchTerms = parsedQuery.allSearchTerms
        val matchedMessages = mutableSetOf<MessageEntity>()
        if (searchTerms.isNotEmpty()) {
            for (term in searchTerms) {
                val found = conversationRepository.searchMessages(term)
                matchedMessages.addAll(found)
            }
        }

        // Group matched messages by conversation (excluding current conversation and recent messages)
        val conversationMatches = mutableMapOf<String, MutableList<MessageEntity>>()
        for (msg in matchedMessages) {
            if (msg.conversationId == conversationId || recentMessages.any { it.id == msg.id }) continue
            conversationMatches.getOrPut(msg.conversationId) { mutableListOf() }.add(msg)
        }

        // Score each candidate conversation based on topic relevance vs generic keywords
        val scoredCandidates = mutableListOf<ScoredConversation>()
        for ((convId, _) in conversationMatches) {
            val allConvMsgs = conversationRepository.getMessagesSnapshot(convId)
            val fullText = allConvMsgs.joinToString(" ") { it.content }.lowercase()

            var topicScore = 0
            for (t in parsedQuery.topicTerms) {
                if (fullText.contains(t)) {
                    topicScore += 10
                }
            }

            var genericScore = 0
            for (g in parsedQuery.genericTerms) {
                if (fullText.contains(g)) {
                    genericScore += 1
                }
            }

            // If user specified explicit topic(s), disqualify conversations with 0 topic score
            if (parsedQuery.topicTerms.isNotEmpty() && topicScore == 0) {
                continue
            }

            val maxTime = allConvMsgs.maxOfOrNull { it.timestamp } ?: 0L
            scoredCandidates.add(
                ScoredConversation(
                    conversationId = convId,
                    matchedMessages = allConvMsgs,
                    topicScore = topicScore,
                    genericScore = genericScore,
                    latestTimestamp = maxTime
                )
            )
        }

        // Check for ambiguous query: user asked generic recall question with NO specific topic, but multiple candidate conversations exist
        val isAmbiguousQuery = parsedQuery.topicTerms.isEmpty() && 
                               parsedQuery.genericTerms.isNotEmpty() && 
                               scoredCandidates.size > 1

        val relevantExchanges = mutableListOf<String>()

        if (isAmbiguousQuery) {
            val topicsFound = scoredCandidates.mapNotNull { cand ->
                val text = cand.matchedMessages.joinToString(" ") { it.content }.lowercase()
                when {
                    text.contains("movie") || text.contains("film") || text.contains("barfi") || text.contains("idiots") -> "movie recommendation"
                    text.contains("laptop") || text.contains("macbook") || text.contains("thinkpad") -> "laptop recommendation"
                    text.contains("phone") || text.contains("mobile") || text.contains("camera") || text.contains("pixel") -> "mobile/phone recommendation"
                    text.contains("career") || text.contains("exam") || text.contains("job") -> "career/exam preparation"
                    else -> null
                }
            }.distinct()

            val ambiguitySummary = topicsFound.joinToString(", ")
            relevantExchanges.add(
                "[AMBIGUOUS RECALL NOTE]: The user asked a generic question ('$prompt') without specifying a topic, but multiple previous recommendations exist in history ($ambiguitySummary).\n" +
                "INSTRUCTION: Ask the user a brief, polite clarification question specifying the available topics (e.g., 'Aap ${ambiguitySummary.replace(", ", " ya ")} ke baare mein pooch rahe hain?'). Do not arbitrarily pick one."
            )
        } else {
            // Sort by: topicScore (descending), then genericScore (descending), then recency (descending)
            val sortedCandidates = scoredCandidates
                .sortedWith(
                    compareByDescending<ScoredConversation> { it.topicScore }
                        .thenByDescending { it.genericScore }
                        .thenByDescending { it.latestTimestamp }
                )
                .take(3)

            for (candidate in sortedCandidates) {
                val convMessages = candidate.matchedMessages
                val bestMsg = convMessages.find { msg ->
                    parsedQuery.topicTerms.any { msg.content.contains(it, ignoreCase = true) }
                } ?: convMessages.first()

                val msgIndex = convMessages.indexOfFirst { it.id == bestMsg.id }
                if (msgIndex != -1) {
                    val start = maxOf(0, msgIndex - 2)
                    val end = minOf(convMessages.size - 1, msgIndex + 3)
                    val exchangeWindow = convMessages.subList(start, end + 1)

                    val exchangeText = exchangeWindow.joinToString("\n") { "${it.role}: ${it.content}" }
                    relevantExchanges.add("[Conversation ID: ${candidate.conversationId}]\n$exchangeText")
                }
            }
        }

        // C. Combine into AiMessages
        if (relevantExchanges.isNotEmpty()) {
            val relatedText = relevantExchanges.joinToString("\n\n")
            context.add(
                AiMessage(
                    role = "user",
                    content = "Relevant context from past conversations:\n$relatedText"
                )
            )
            context.add(
                AiMessage(
                    role = "assistant",
                    content = "Understood. I will use this historical context to inform our current conversation."
                )
            )
        }

        // Add recent history
        recentMessages.forEach { msg ->
            context.add(
                AiMessage(
                    role = msg.role,
                    content = msg.content,
                    // Only include image for the current (last) message if applicable
                    imageBase64 = if (msg.content == prompt) imageBase64 else null,
                    imageMimeType = if (msg.content == prompt) imageMimeType else null
                )
            )
        }

        return context
    }

    private suspend fun buildSystemInstruction(): String {
        val memoryContext = memoryRepository.getFormattedMemoryContext()
        val tools = toolRegistry.getToolDefinitions()
        return buildString {
            append("You are Sona AI, a native, intelligent, and thoughtful Android AI assistant. ")
            append("You are helpful, concise, articulate, and accurate. ")
            append("Format responses with clear markdown, bullet points, and code blocks where helpful.\n")
            if (memoryContext.isNotBlank()) {
                append("\n$memoryContext\n")
            }
            append("\nYou have access to the following tools. If necessary, invoke them using the format [TOOL:tool_id] arg1=value1 arg2=value2:\n")
            append(tools)
            append("\nCRITICAL MEMORY & RECALL RULE: When asked about past conversations, recommendations, or earlier answers, ONLY use the provided historical context. If no relevant past history is found or provided, clearly and honestly state that you do not have a record of it. Never invent, hallucinate, or guess past conversations.")
        }
    }

    data class ParsedQuery(
        val topicTerms: List<String>,
        val genericTerms: List<String>
    ) {
        val allSearchTerms: List<String>
            get() = (topicTerms + genericTerms).distinct()
    }

    private data class ScoredConversation(
        val conversationId: String,
        val matchedMessages: List<MessageEntity>,
        val topicScore: Int,
        val genericScore: Int,
        val latestTimestamp: Long
    )

    private fun extractQueryTerms(prompt: String): ParsedQuery {
        val rawWords = prompt.split(" ", ",", ".", "?", "!", "\n", "\t", ":", ";")
            .map { it.trim().lowercase() }
            .filter { it.length > 2 }
            .filter { !stopWords.contains(it) }

        val topicTerms = mutableListOf<String>()
        val genericTerms = mutableListOf<String>()

        for (w in rawWords) {
            when {
                // Topic stems
                w.startsWith("movie") || w.startsWith("film") || w.startsWith("cinema") -> topicTerms.add("movie")
                w.startsWith("laptop") || w.startsWith("macbook") || w.startsWith("computer") -> topicTerms.add("laptop")
                w.startsWith("phone") || w.startsWith("mobile") -> topicTerms.add("phone")
                w.startsWith("camera") -> topicTerms.add("camera")
                w.startsWith("coding") || w.startsWith("code") || w.startsWith("programming") -> topicTerms.add("coding")
                w.startsWith("career") || w.startsWith("job") -> topicTerms.add("job")
                w.startsWith("exam") || w.startsWith("gov") -> topicTerms.add("exam")
                w.startsWith("book") || w.startsWith("novel") -> topicTerms.add("book")

                // Generic recall action words
                genericRecallWords.contains(w) || w.startsWith("recommend") || w.startsWith("suggest") || 
                w.startsWith("pichhl") || w.startsWith("pichl") || w.startsWith("puran") || w.startsWith("batay") -> {
                    when {
                        w.startsWith("recommend") -> genericTerms.add("recommend")
                        w.startsWith("suggest") -> genericTerms.add("suggest")
                        w.startsWith("batay") -> genericTerms.add("batay")
                        else -> genericTerms.add(w)
                    }
                }

                // If not generic, treat as specific topic term
                else -> topicTerms.add(w)
            }
        }

        return ParsedQuery(
            topicTerms = topicTerms.distinct(),
            genericTerms = genericTerms.distinct()
        )
    }

    private val genericRecallWords = setOf(
        "suggest", "suggestion", "recommend", "recommendation",
        "bataya", "batayi", "pehle", "pichhli", "pichli", "purani",
        "earlier", "previous", "before", "history", "chat", "chats",
        "batao", "batana", "kaunsi", "kaunsa", "kya", "tha", "thi", "wali",
        "kiya", "karo", "kro", "karne", "karna", "karke", "bhi"
    )

    private val stopWords = setOf(
        "this", "that", "there", "what", "where", "when", "with", "from",
        "then", "their", "about", "could", "would", "should", "your", "mine",
        "hai", "aur", "koi", "liye", "sirf", "naam", "karo", "dekh", "agar", "nahi",
        "kiya", "kar", "ho", "gaya", "gayi", "hain", "hoon", "thi", "tha", "raha", "rahi"
    )
}
