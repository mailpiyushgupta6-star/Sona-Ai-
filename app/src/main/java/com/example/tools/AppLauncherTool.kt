package com.example.tools

import com.example.androidapps.AppManager
import com.example.androidapps.CommandLaunchResult

class AppLauncherTool(
    private val appManager: AppManager
) : Tool {
    override val id: String = "app_launcher"
    override val description: String =
        "Launches an installed Android application or device settings. Arguments: app=<appName or command>"
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val appQuery = args["app"] ?: args["name"] ?: args["command"] ?: ""
        if (appQuery.isBlank()) {
            return ToolResult.Error("No app name provided.")
        }

        return when (val result = appManager.launchAppByCommand(appQuery)) {
            is CommandLaunchResult.Launched -> {
                ToolResult.Success(result.confirmationMessage)
            }
            is CommandLaunchResult.DisambiguationRequired -> {
                ToolResult.Success(result.message)
            }
            is CommandLaunchResult.AppNotFound -> {
                ToolResult.Error(result.message)
            }
            is CommandLaunchResult.Failed -> {
                ToolResult.Error("Failed to launch ${result.appName}: ${result.error}")
            }
            is CommandLaunchResult.NotACommand -> {
                // Direct fallback query launch
                when (val directResult = appManager.launchAppByQuery(appQuery)) {
                    is CommandLaunchResult.Launched -> ToolResult.Success(directResult.confirmationMessage)
                    is CommandLaunchResult.AppNotFound -> ToolResult.Error(directResult.message)
                    is CommandLaunchResult.DisambiguationRequired -> ToolResult.Success(directResult.message)
                    is CommandLaunchResult.Failed -> ToolResult.Error(directResult.error)
                    else -> ToolResult.Error("App '$appQuery' not found.")
                }
            }
        }
    }
}
