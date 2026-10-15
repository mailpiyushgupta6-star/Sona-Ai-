package com.example.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateTimeTool : Tool {
    override val id = "date_time"
    override val description = "Return the current local date and time."
    override val requiresConfirmation = false

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return ToolResult.Success(dateFormat.format(Date()))
    }
}
