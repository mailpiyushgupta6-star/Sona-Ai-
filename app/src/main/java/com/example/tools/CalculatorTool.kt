package com.example.tools

class CalculatorTool : Tool {
    override val id = "calculator"
    override val description = "Perform basic arithmetic: add, subtract, multiply, divide. Args: op (add|sub|mul|div), a (number), b (number)"
    override val requiresConfirmation = false

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val op = args["op"]
        val a = args["a"]?.toDoubleOrNull()
        val b = args["b"]?.toDoubleOrNull()

        if (op == null || a == null || b == null) {
            return ToolResult.Error("Missing or invalid arguments. Required: op, a, b")
        }

        return try {
            val result = when (op) {
                "add" -> a + b
                "sub" -> a - b
                "mul" -> a * b
                "div" -> {
                    if (b == 0.0) return ToolResult.Error("Division by zero")
                    a / b
                }
                else -> return ToolResult.Error("Unknown operation: $op")
            }
            ToolResult.Success(result.toString())
        } catch (e: Exception) {
            ToolResult.Error("Calculation failed: ${e.message}")
        }
    }
}
