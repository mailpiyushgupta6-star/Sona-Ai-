package com.example.androidapps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun testWifiCommandOnAndOffPriority() {
        // ON intent
        val resOn1 = SystemControlManager.executeCommand(context, "Wi-Fi on karo")
        assertNotNull(resOn1)

        val resOn2 = SystemControlManager.executeCommand(context, "Wi-Fi chalu karo")
        assertNotNull(resOn2)

        // OFF intent (MUST evaluate to OFF even though "karo" is present)
        val resOff1 = SystemControlManager.executeCommand(context, "Wi-Fi off karo")
        assertNotNull(resOff1)

        val resOff2 = SystemControlManager.executeCommand(context, "Wi-Fi band karo")
        assertNotNull(resOff2)

        // Settings intent
        val resSet = SystemControlManager.executeCommand(context, "Wi-Fi settings kholo")
        assertNotNull(resSet)
        assertTrue(resSet is SystemControlResult.PanelOpened)
        assertEquals("Wi-Fi settings khol raha hoon.", (resSet as SystemControlResult.PanelOpened).message)
    }

    @Test
    fun testMobileDataCommands() {
        val resOn = SystemControlManager.executeCommand(context, "Mobile data on karo")
        assertNotNull(resOn)
        assertTrue(resOn is SystemControlResult.PanelOpened)

        val resOff = SystemControlManager.executeCommand(context, "Mobile data band karo")
        assertNotNull(resOff)
        assertTrue(resOff is SystemControlResult.PanelOpened)
    }

    @Test
    fun testBluetoothCommands() {
        val resOn = SystemControlManager.executeCommand(context, "Bluetooth on karo")
        assertNotNull(resOn)
        assertTrue(resOn is SystemControlResult.PanelOpened)

        val resSettings = SystemControlManager.executeCommand(context, "Bluetooth settings kholo")
        assertNotNull(resSettings)
        assertTrue(resSettings is SystemControlResult.PanelOpened)
    }

    @Test
    fun testTorchCommandVariants() {
        val resOn1 = SystemControlManager.executeCommand(context, "Torch on karo")
        assertNotNull(resOn1)

        val resOn2 = SystemControlManager.executeCommand(context, "Torch jalao")
        assertNotNull(resOn2)

        val resOff1 = SystemControlManager.executeCommand(context, "Torch off karo")
        assertNotNull(resOff1)

        val resOff2 = SystemControlManager.executeCommand(context, "Torch bujhao")
        assertNotNull(resOff2)

        val resOff3 = SystemControlManager.executeCommand(context, "Torch band karo")
        assertNotNull(resOff3)
    }

    @Test
    fun testInternetAndQuickSettingsCommands() {
        val resInternet = SystemControlManager.executeCommand(context, "Internet settings kholo")
        assertNotNull(resInternet)
        assertTrue(resInternet is SystemControlResult.PanelOpened)

        val resQuick = SystemControlManager.executeCommand(context, "Quick settings kholo")
        assertNotNull(resQuick)
    }

    @Test
    fun testUnknownAndAmbiguousCommandsReturnNull() {
        val resUnknown1 = SystemControlManager.executeCommand(context, "Play some music")
        assertNull(resUnknown1)

        val resUnknown2 = SystemControlManager.executeCommand(context, "Kholo")
        assertNull(resUnknown2)

        val resUnknown3 = SystemControlManager.executeCommand(context, "What is Wi-Fi?")
        assertNull(resUnknown3)
    }
}
