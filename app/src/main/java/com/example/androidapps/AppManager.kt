package com.example.androidapps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings

sealed interface LaunchResult {
    data class Success(val appName: String, val packageName: String) : LaunchResult
    data class NotFound(val target: String) : LaunchResult
    data class Error(val message: String) : LaunchResult
}

sealed interface CommandLaunchResult {
    data class Launched(
        val appName: String,
        val packageName: String,
        val confirmationMessage: String
    ) : CommandLaunchResult

    data class DisambiguationRequired(
        val query: String,
        val candidates: List<InstalledAppInfo>,
        val message: String
    ) : CommandLaunchResult

    data class AppNotFound(
        val queriedApp: String,
        val message: String
    ) : CommandLaunchResult

    data class Failed(
        val appName: String,
        val error: String
    ) : CommandLaunchResult

    data class NotACommand(val reason: String) : CommandLaunchResult
}

interface AppManager {
    fun getInstalledLaunchableApps(): List<InstalledAppInfo>
    fun searchApps(query: String): List<InstalledAppInfo>
    fun launchApp(packageName: String): LaunchResult
    fun launchSpecialIntent(action: String): LaunchResult
    fun launchAppByQuery(query: String, language: CommandLanguage = CommandLanguage.English): CommandLaunchResult
    fun checkAndExecuteAppLaunchCommand(prompt: String): CommandLaunchResult
    fun launchAppByCommand(command: String): CommandLaunchResult = checkAndExecuteAppLaunchCommand(command)
}

class AndroidAppManager(
    private val context: Context
) : AppManager {

    companion object {
        val APP_ALIASES: Map<String, List<String>> = mapOf(
            "chrome" to listOf("Google Chrome", "Chrome", "com.android.chrome"),
            "youtube" to listOf("YouTube", "com.google.android.youtube"),
            "whatsapp" to listOf("WhatsApp", "WhatsApp Messenger", "com.whatsapp"),
            "settings" to listOf("Settings", "System Settings", "com.android.settings"),
            "camera" to listOf("Camera", "Google Camera", "Open Camera", "com.android.camera", "com.google.android.GoogleCamera"),
            "maps" to listOf("Google Maps", "Maps", "com.google.android.apps.maps"),
            "gmail" to listOf("Gmail", "Google Mail", "com.google.android.gm"),
            "photos" to listOf("Google Photos", "Photos", "Gallery", "com.google.android.apps.photos", "com.google.android.gallery3d"),
            "gallery" to listOf("Gallery", "Photos", "Google Photos"),
            "files" to listOf("Files", "Files by Google", "My Files", "com.google.android.apps.nbu.files"),
            "play store" to listOf("Google Play Store", "Play Store", "com.android.vending"),
            "playstore" to listOf("Google Play Store", "Play Store", "com.android.vending"),
            "calculator" to listOf("Calculator", "com.google.android.calculator", "com.android.calculator2"),
            "clock" to listOf("Clock", "Alarm", "com.google.android.deskclock", "com.android.deskclock"),
            "calendar" to listOf("Google Calendar", "Calendar", "com.google.android.calendar"),
            "contacts" to listOf("Contacts", "Google Contacts", "com.google.android.contacts"),
            "phone" to listOf("Phone", "Dialer", "com.google.android.dialer", "com.android.dialer"),
            "dialer" to listOf("Phone", "Dialer", "com.google.android.dialer", "com.android.dialer"),
            "messages" to listOf("Messages", "Google Messages", "com.google.android.apps.messaging"),
            "sms" to listOf("Messages", "Google Messages", "com.google.android.apps.messaging"),
            "music" to listOf("YT Music", "YouTube Music", "Spotify", "Music", "com.spotify.music")
        )
    }

    override fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val seenPackages = mutableSetOf<String>()
        val apps = mutableListOf<InstalledAppInfo>()

        for (info in resolveInfos) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg.isBlank() || seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            val label = try {
                info.loadLabel(pm)?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                    ?: activityInfo.applicationInfo?.loadLabel(pm)?.toString()?.trim()
                    ?: pkg
            } catch (_: Exception) {
                pkg
            }

            val icon = try {
                info.loadIcon(pm) ?: activityInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            val isSystem = try {
                val appInfo = activityInfo.applicationInfo
                appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) {
                false
            }

            apps.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSystem,
                    icon = icon
                )
            )
        }

        return apps.sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
    }

    override fun searchApps(query: String): List<InstalledAppInfo> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return getInstalledLaunchableApps()
        return getInstalledLaunchableApps().filter {
            it.appName.lowercase().contains(trimmed) || it.packageName.lowercase().contains(trimmed)
        }
    }

    override fun launchApp(packageName: String): LaunchResult {
        // Special case: Settings
        if (packageName.equals("settings", ignoreCase = true) ||
            packageName.equals("com.android.settings", ignoreCase = true)
        ) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.android.settings")
                ?: Intent(Settings.ACTION_SETTINGS)
            return launchIntentSafely(intent, "Settings", "com.android.settings")
        }

        // Special case: Camera
        if (packageName.equals("camera", ignoreCase = true)) {
            val cameraApp = findInstalledCameraApp()
            if (cameraApp != null) {
                return launchApp(cameraApp.packageName)
            }
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            return launchIntentSafely(intent, "Camera", "camera")
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NotFound("App '$packageName' is not installed or has no launchable activity.")

        return launchIntentSafely(launchIntent, getAppLabel(packageName), packageName)
    }

    override fun launchSpecialIntent(action: String): LaunchResult {
        val intent = Intent(action)
        return launchIntentSafely(intent, action, action)
    }

    override fun launchAppByQuery(query: String, language: CommandLanguage): CommandLaunchResult {
        val cleanQuery = query.trim().lowercase()
        val installedApps = getInstalledLaunchableApps()

        // 1. Check special target "settings"
        if (cleanQuery == "settings") {
            val settingsApp = installedApps.firstOrNull {
                it.appName.equals("settings", ignoreCase = true) || it.packageName == "com.android.settings"
            }
            val res = if (settingsApp != null) {
                launchApp(settingsApp.packageName)
            } else {
                launchSpecialIntent(Settings.ACTION_SETTINGS)
            }
            return handleLaunchResult(res, "Settings", language)
        }

        // 2. Check special target "camera"
        if (cleanQuery == "camera") {
            val cameraMatches = installedApps.filter {
                it.appName.lowercase().contains("camera") || it.packageName.lowercase().contains("camera")
            }
            if (cameraMatches.size > 1) {
                val exact = cameraMatches.firstOrNull { it.appName.equals("camera", ignoreCase = true) }
                if (exact != null) {
                    val res = launchApp(exact.packageName)
                    return handleLaunchResult(res, exact.appName, language)
                }
                return createDisambiguationResult(query, cameraMatches, language)
            } else if (cameraMatches.size == 1) {
                val res = launchApp(cameraMatches.first().packageName)
                return handleLaunchResult(res, cameraMatches.first().appName, language)
            } else {
                val res = launchSpecialIntent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                return handleLaunchResult(res, "Camera", language)
            }
        }

        // 3. Exact app name match
        val exactMatches = installedApps.filter { it.appName.trim().equals(cleanQuery, ignoreCase = true) }
        if (exactMatches.size == 1) {
            val app = exactMatches.first()
            val res = launchApp(app.packageName)
            return handleLaunchResult(res, app.appName, language)
        } else if (exactMatches.size > 1) {
            return createDisambiguationResult(query, exactMatches, language)
        }

        // 4. Known aliases match
        val aliases = APP_ALIASES[cleanQuery] ?: emptyList()
        if (aliases.isNotEmpty()) {
            val aliasMatches = installedApps.filter { app ->
                aliases.any { alias ->
                    app.appName.equals(alias, ignoreCase = true) || app.packageName.equals(alias, ignoreCase = true)
                }
            }
            if (aliasMatches.size == 1) {
                val app = aliasMatches.first()
                val res = launchApp(app.packageName)
                return handleLaunchResult(res, app.appName, language)
            } else if (aliasMatches.size > 1) {
                val exact = aliasMatches.firstOrNull { it.appName.equals(cleanQuery, ignoreCase = true) }
                if (exact != null) {
                    val res = launchApp(exact.packageName)
                    return handleLaunchResult(res, exact.appName, language)
                }
                return createDisambiguationResult(query, aliasMatches, language)
            }
        }

        // 5. Word boundary / starts-with match
        val wordMatches = installedApps.filter { app ->
            val words = app.appName.split(" ", "-", "_")
            words.any { it.equals(cleanQuery, ignoreCase = true) } ||
                    app.appName.lowercase().startsWith(cleanQuery)
        }
        if (wordMatches.size == 1) {
            val app = wordMatches.first()
            val res = launchApp(app.packageName)
            return handleLaunchResult(res, app.appName, language)
        } else if (wordMatches.size > 1) {
            val exact = wordMatches.firstOrNull { it.appName.equals(cleanQuery, ignoreCase = true) }
            if (exact != null) {
                val res = launchApp(exact.packageName)
                return handleLaunchResult(res, exact.appName, language)
            }
            return createDisambiguationResult(query, wordMatches, language)
        }

        // 6. Substring match
        val substringMatches = installedApps.filter { app ->
            app.appName.lowercase().contains(cleanQuery)
        }
        if (substringMatches.size == 1) {
            val app = substringMatches.first()
            val res = launchApp(app.packageName)
            return handleLaunchResult(res, app.appName, language)
        } else if (substringMatches.size > 1) {
            return createDisambiguationResult(query, substringMatches, language)
        }

        // Not installed / not found
        val notFoundMessage = when (language) {
            is CommandLanguage.Hinglish -> "Aapke phone me '$query' app install nahi hai."
            is CommandLanguage.English -> "'$query' is not installed on your device."
        }
        return CommandLaunchResult.AppNotFound(query, notFoundMessage)
    }

    override fun checkAndExecuteAppLaunchCommand(prompt: String): CommandLaunchResult {
        val parsed = AppCommandParser.parse(prompt)
            ?: return CommandLaunchResult.NotACommand("Prompt is not an app launch command.")

        return launchAppByQuery(parsed.targetApp, parsed.language)
    }

    private fun launchIntentSafely(intent: Intent, appName: String, packageName: String): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            LaunchResult.Success(appName = appName, packageName = packageName)
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Error("App activity not found or app was uninstalled.")
        } catch (_: SecurityException) {
            LaunchResult.Error("Security restriction prevented launching $appName.")
        } catch (e: Exception) {
            LaunchResult.Error(e.localizedMessage ?: "Failed to launch $appName.")
        }
    }

    private fun handleLaunchResult(
        result: LaunchResult,
        appName: String,
        language: CommandLanguage
    ): CommandLaunchResult {
        return when (result) {
            is LaunchResult.Success -> {
                val confirmation = when (language) {
                    is CommandLanguage.Hinglish -> "$appName khol raha hoon..."
                    is CommandLanguage.English -> "Opening $appName..."
                }
                CommandLaunchResult.Launched(
                    appName = result.appName,
                    packageName = result.packageName,
                    confirmationMessage = confirmation
                )
            }
            is LaunchResult.NotFound -> {
                val msg = when (language) {
                    is CommandLanguage.Hinglish -> "Aapke phone me '$appName' app install nahi hai."
                    is CommandLanguage.English -> "'$appName' is not installed on your device."
                }
                CommandLaunchResult.AppNotFound(appName, msg)
            }
            is LaunchResult.Error -> {
                CommandLaunchResult.Failed(appName, result.message)
            }
        }
    }

    private fun createDisambiguationResult(
        query: String,
        candidates: List<InstalledAppInfo>,
        language: CommandLanguage
    ): CommandLaunchResult {
        val names = candidates.map { it.appName }.distinct().joinToString(", ")
        val message = when (language) {
            is CommandLanguage.Hinglish -> "Mujhe multiple apps mile: $names. Aap kaunsa app kholna chahte hain?"
            is CommandLanguage.English -> "I found multiple matching apps: $names. Which one did you mean?"
        }
        return CommandLaunchResult.DisambiguationRequired(query, candidates, message)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun findInstalledCameraApp(): InstalledAppInfo? {
        val apps = getInstalledLaunchableApps()
        return apps.firstOrNull { it.appName.equals("camera", ignoreCase = true) }
            ?: apps.firstOrNull { it.appName.lowercase().contains("camera") }
            ?: apps.firstOrNull { it.packageName.lowercase().contains("camera") }
    }
}
