package com.example.tools

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun registerTool(tool: Tool) {
        if (tools.containsKey(tool.id)) {
            throw IllegalArgumentException("Tool with id ${tool.id} already registered.")
        }
        tools[tool.id] = tool
    }

    fun getTool(id: String): Tool? = tools[id]

    fun getToolDefinitions(): String {
        return tools.values.joinToString("\n") { 
            "- ${it.id}: ${it.description} (Requires Confirmation: ${it.requiresConfirmation})" 
        }
    }
}
