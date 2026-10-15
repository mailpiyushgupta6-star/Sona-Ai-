package com.example.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorToolTest {
    private val tool = CalculatorTool()

    @Test
    fun `test addition`() = runBlocking {
        val result = tool.execute(mapOf("op" to "add", "a" to "1", "b" to "2"))
        assertTrue(result is ToolResult.Success)
        assertEquals("3.0", (result as ToolResult.Success).output)
    }

    @Test
    fun `test division by zero`() = runBlocking {
        val result = tool.execute(mapOf("op" to "div", "a" to "1", "b" to "0"))
        assertTrue(result is ToolResult.Error)
    }
}
