package com.example.tools

import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.flow.first

class MemorySearchTool(
    private val memoryRepository: MemoryRepository
) : Tool {
    override val id = "memory_search"
    override val description = "Search saved memories. Args: query (search term)"
    override val requiresConfirmation = false

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult.Error("Missing query argument")
        
        val results = memoryRepository.searchMemories(query).first()
        if (results.isEmpty()) {
            return ToolResult.Success("No relevant memories found for: $query")
        }
        
        val formatted = results.joinToString("\n") { 
            "- [${it.key}]: ${it.content} (Source: ${it.sourceConversationId ?: "Manual"})"
        }
        return ToolResult.Success(formatted)
    }
}
