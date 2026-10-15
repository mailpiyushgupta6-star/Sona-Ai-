package com.example.tools

import android.content.Context
import com.example.androidapps.SystemControlManager
import com.example.androidapps.SystemControlResult

class SystemControlTool(
    private val context: Context
) : Tool {
    override val id: String = "system_control"
    override val description: String =
        "Controls system settings (Wi-Fi, Mobile Data, Bluetooth, Torch, Internet settings, Quick settings). Arguments: command=<action>"
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val command = args["command"] ?: args["action"] ?: args["query"] ?: ""
        if (command.isBlank()) {
            return ToolResult.Error("No system control command provided.")
        }

        val result = SystemControlManager.executeCommand(context, command)
            ?: return ToolResult.Error("Unknown system control command: '$command'")

        return when (result) {
            is SystemControlResult.ActionExecuted -> ToolResult.Success(result.message)
            is SystemControlResult.PanelOpened -> ToolResult.Success(result.message)
            is SystemControlResult.Failed -> ToolResult.Error(result.message)
        }
    }
}
