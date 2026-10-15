package com.example.androidapps

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.example.tools.AppLauncherTool
import com.example.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppManagerTest {

    private lateinit var context: Context
    private lateinit var appManager: AndroidAppManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val packageManager = context.packageManager
        val shadowPm = shadowOf(packageManager)

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // 1. YouTube
        val ytResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.google.android.youtube"
                name = "com.google.android.youtube.HomeActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.google.android.youtube"
                    name = "YouTube"
                    flags = 0
                }
            }
            nonLocalizedLabel = "YouTube"
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, ytResolveInfo)
        shadowPm.addActivityIfNotPresent(
            ComponentName("com.google.android.youtube", "com.google.android.youtube.HomeActivity")
        )

        // 2. Google Chrome
        val chromeResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.google.android.apps.chrome.Main"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.chrome"
                    name = "Google Chrome"
                    flags = 0
                }
            }
            nonLocalizedLabel = "Google Chrome"
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, chromeResolveInfo)
        shadowPm.addActivityIfNotPresent(
            ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main")
        )

        // 3. Settings (system app)
        val settingsResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.Settings"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.settings"
                    name = "Settings"
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            }
            nonLocalizedLabel = "Settings"
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, settingsResolveInfo)
        shadowPm.addActivityIfNotPresent(
            ComponentName("com.android.settings", "com.android.settings.Settings")
        )

        appManager = AndroidAppManager(context)
    }

    @Test
    fun testGetInstalledLaunchableApps() {
        val apps = appManager.getInstalledLaunchableApps()
        assertTrue(apps.isNotEmpty())
        assertTrue(apps.any { it.packageName == "com.google.android.youtube" })
        assertTrue(apps.any { it.packageName == "com.android.chrome" })
        assertTrue(apps.any { it.packageName == "com.android.settings" && it.isSystemApp })
    }

    @Test
    fun testSearchApps() {
        val results = appManager.searchApps("Chrome")
        assertEquals(1, results.size)
        assertEquals("com.android.chrome", results[0].packageName)
    }

    @Test
    fun testLaunchAppDirectly() {
        val result = appManager.launchApp("com.google.android.youtube")
        assertTrue(result is LaunchResult.Success)
        val nextIntent = shadowOf(context as Application).nextStartedActivity
        assertEquals("com.google.android.youtube", nextIntent.component?.packageName)
    }

    @Test
    fun testCommandHinglishYouTubeKholo() {
        val result = appManager.checkAndExecuteAppLaunchCommand("YouTube kholo")
        assertTrue(result is CommandLaunchResult.Launched)
        val launched = result as CommandLaunchResult.Launched
        assertEquals("YouTube", launched.appName)
        assertEquals("com.google.android.youtube", launched.packageName)
        assertEquals("YouTube khol raha hoon...", launched.confirmationMessage)
    }

    @Test
    fun testCommandHinglishChromeOpenKaro() {
        val result = appManager.checkAndExecuteAppLaunchCommand("Chrome open karo")
        assertTrue(result is CommandLaunchResult.Launched)
        val launched = result as CommandLaunchResult.Launched
        assertEquals("Google Chrome", launched.appName)
        assertEquals("com.android.chrome", launched.packageName)
        assertEquals("Google Chrome khol raha hoon...", launched.confirmationMessage)
    }

    @Test
    fun testCommandEnglishOpenSettings() {
        val result = appManager.checkAndExecuteAppLaunchCommand("Open Settings")
        assertTrue(result is CommandLaunchResult.Launched)
        val launched = result as CommandLaunchResult.Launched
        assertEquals("Settings", launched.appName)
        assertEquals("Opening Settings...", launched.confirmationMessage)
    }

    @Test
    fun testCommandAppNotFound() {
        val result = appManager.checkAndExecuteAppLaunchCommand("Spotify kholo")
        assertTrue(result is CommandLaunchResult.AppNotFound)
        val notFound = result as CommandLaunchResult.AppNotFound
        assertEquals("Aapke phone me 'spotify' app install nahi hai.", notFound.message)
    }

    @Test
    fun testCommandNotACommand() {
        val result = appManager.checkAndExecuteAppLaunchCommand("What is the weather today?")
        assertTrue(result is CommandLaunchResult.NotACommand)
    }

    @Test
    fun testAppLauncherToolExecution() = runBlocking {
        val tool = AppLauncherTool(appManager)
        assertEquals("app_launcher", tool.id)

        val success = tool.execute(mapOf("app" to "YouTube"))
        assertTrue(success is ToolResult.Success)

        val notFound = tool.execute(mapOf("app" to "UnknownAppXYZ"))
        assertTrue(notFound is ToolResult.Error)
    }
}
