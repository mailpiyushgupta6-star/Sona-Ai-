package com.example.androidapps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemControlManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testWifiCommands() {
        val resOn = SystemControlManager.executeCommand(context, "Wi-Fi on karo")
        assertNotNull(resOn)

        val resOff = SystemControlManager.executeCommand(context, "Wi-Fi off karo")
        assertNotNull(resOff)

        val resSet = SystemControlManager.executeCommand(context, "Wi-Fi settings kholo")
        assertNotNull(resSet)
        assertTrue(resSet is SystemControlResult.PanelOpened)
    }

    @Test
    fun testMobileDataCommands() {
        val resOn = SystemControlManager.executeCommand(context, "Mobile data on karo")
        assertNotNull(resOn)
        assertTrue(resOn is SystemControlResult.PanelOpened) // Restricted on Android 21+, opens settings panel

        val resOff = SystemControlManager.executeCommand(context, "Mobile data off karo")
        assertNotNull(resOff)
    }

    @Test
    fun testBluetoothCommands() {
        val res = SystemControlManager.executeCommand(context, "Bluetooth on karo")
        assertNotNull(res)
        assertTrue(res is SystemControlResult.PanelOpened)
    }

    @Test
    fn testTorchCommands() {
        val resOn = SystemControlManager.executeCommand(context, "Torch on karo")
        assertNotNull(resOn)

        val resOff = SystemControlManager.executeCommand(context, "Torch off karo")
        assertNotNull(resOff)
    }

    @Test
    fun testInternetAndQuickSettingsCommands() {
        val resInternet = SystemControlManager.executeCommand(context, "Internet settings kholo")
        assertNotNull(resInternet)
        assertTrue(resInternet is SystemControlResult.PanelOpened)

        val resQuick = SystemControlManager.executeCommand(context, "Quick settings kholo")
        assertNotNull(resQuick)
        assertTrue(resQuick is SystemControlResult.PanelOpened)
    }
}
