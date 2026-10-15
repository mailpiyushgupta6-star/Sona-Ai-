package com.example.tools

sealed interface ToolResult {
    data class Success(val output: String) : ToolResult
    data class Error(val message: String) : ToolResult
}

interface Tool {
    val id: String
    val description: String
    val requiresConfirmation: Boolean
    suspend fun execute(args: Map<String, String>): ToolResult
}
