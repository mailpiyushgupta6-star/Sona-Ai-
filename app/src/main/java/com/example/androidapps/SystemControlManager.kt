package com.example.androidapps

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

sealed interface SystemControlResult {
    data class ActionExecuted(val message: String) : SystemControlResult
    data class PanelOpened(val message: String) : SystemControlResult
    data class Failed(val message: String) : SystemControlResult
}

object SystemControlManager {

    /**
     * Parses and executes system control commands (Wi-Fi, Mobile Data, Bluetooth, Torch, Internet Settings, Quick Settings).
     */
    fun executeCommand(context: Context, prompt: String): SystemControlResult? {
        val trimmed = prompt.trim().lowercase().replace(Regex("[?!.]+$"), "").trim()

        // 1. Wi-Fi commands
        if (trimmed.contains("wifi") || trimmed.contains("wi-fi") || trimmed.contains("wi fi")) {
            if (trimmed.contains("on") || trimmed.contains("chalu") || trimmed.contains("karo") || trimmed.contains("enable") || trimmed.contains("start")) {
                return toggleWifi(context, true)
            } else if (trimmed.contains("off") || trimmed.contains("bandh") || trimmed.contains("disable") || trimmed.contains("stop")) {
                return toggleWifi(context, false)
            } else if (trimmed.contains("settings") || trimmed.contains("khol") || trimmed.contains("open")) {
                return openPanel(context, Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings khol raha hoon.")
            }
        }

        // 2. Mobile Data commands
        if (trimmed.contains("mobile data") || trimmed.contains("cellular") || trimmed.contains("data")) {
            if (trimmed.contains("on") || trimmed.contains("off") || trimmed.contains("chalu") || trimmed.contains("bandh") || trimmed.contains("enable") || trimmed.contains("disable")) {
                return openPanel(
                    context,
                    Settings.ACTION_DATA_ROAMING_SETTINGS,
                    "Android security restrictions prevent direct toggling of Mobile Data. Opening Internet & Network settings panel..."
                )
            } else if (trimmed.contains("settings") || trimmed.contains("khol") || trimmed.contains("open")) {
                return openPanel(context, Settings.ACTION_WIRELESS_SETTINGS, "Wireless settings khol raha hoon.")
            }
        }

        // 3. Bluetooth commands
        if (trimmed.contains("bluetooth")) {
            if (trimmed.contains("on") || trimmed.contains("off") || trimmed.contains("chalu") || trimmed.contains("bandh") || trimmed.contains("enable") || trimmed.contains("disable") || trimmed.contains("khol") || trimmed.contains("open")) {
                return openBluetooth(context)
            }
        }

        // 4. Torch / Flashlight commands
        if (trimmed.contains("torch") || trimmed.contains("flashlight") || trimmed.contains("flash light") || trimmed.contains("flash")) {
            if (trimmed.contains("on") || trimmed.contains("chalu") || trimmed.contains("enable") || trimmed.contains("start") || trimmed.contains("jala") || trimmed.contains("on karo")) {
                return toggleTorch(context, true)
            } else if (trimmed.contains("off") || trimmed.contains("bandh") || trimmed.contains("disable") || trimmed.contains("stop") || trimmed.contains("bujha") || trimmed.contains("off karo")) {
                return toggleTorch(context, false)
            }
        }

        // 5. Internet Settings commands
        if (trimmed.contains("internet settings") || trimmed.contains("network settings") || trimmed.contains("wifi settings")) {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            return launchIntentSafely(context, intent, "Internet settings panel khol raha hoon.")
        }

        // 6. Quick Settings commands
        if (trimmed.contains("quick settings") || trimmed.contains("quick panel") || trimmed.contains("status bar")) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            return launchIntentSafely(context, intent, "System Settings khol raha hoon.")
        }

        return null
    }

    private fun toggleWifi(context: Context, enable: Boolean): SystemControlResult {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                val success = try {
                    wifiManager.setWifiEnabled(enable)
                } catch (_: Exception) {
                    false
                }
                if (success) {
                    val status = if (enable) "on" else "off"
                    SystemControlResult.ActionExecuted("Wi-Fi successfully turned $status.")
                } else {
                    openPanel(
                        context,
                        Settings.ACTION_WIFI_SETTINGS,
                        "Direct Wi-Fi toggle is restricted on this Android version. Opening Wi-Fi settings..."
                    )
                }
            } else {
                openPanel(context, Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings...")
            }
        } catch (e: Exception) {
            openPanel(
                context,
                Settings.ACTION_WIFI_SETTINGS,
                "Wi-Fi toggle restricted by system. Opening Wi-Fi settings..."
            )
        }
    }

    private fun openBluetooth(context: Context): SystemControlResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        return launchIntentSafely(context, intent, "Bluetooth settings khol raha hoon.")
    }

    private fun toggleTorch(context: Context, enable: Boolean): SystemControlResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return SystemControlResult.Failed("Torch / CameraManager not available on this device.")

            val cameraIds = cameraManager.cameraIdList
            val cameraId = cameraIds.firstOrNull { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            } ?: return SystemControlResult.Failed("⚠️ Torch / Flashlight is not available on this device.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, enable)
                val status = if (enable) "on" else "off"
                SystemControlResult.ActionExecuted("Torch successfully turned $status.")
            } else {
                SystemControlResult.Failed("Torch control requires Android 6.0 (Marshmallow) or higher.")
            }
        } catch (e: CameraAccessException) {
            SystemControlResult.Failed("⚠️ Torch unavailable or camera in use: ${e.localizedMessage}")
        } catch (e: SecurityException) {
            SystemControlResult.Failed("⚠️ Camera permission required for torch control.")
        } catch (e: Exception) {
            SystemControlResult.Failed("⚠️ Failed to toggle torch: ${e.localizedMessage}")
        }
    }

    private fun openPanel(context: Context, action: String, message: String): SystemControlResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SystemControlResult.PanelOpened(message)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                SystemControlResult.PanelOpened("Opening system settings...")
            } catch (ex: Exception) {
                SystemControlResult.Failed("Failed to open settings panel: ${ex.localizedMessage}")
            }
        }
    }

    private fun launchIntentSafely(context: Context, intent: Intent, message: String): SystemControlResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.PanelOpened(message)
        } catch (e: Exception) {
            SystemControlResult.Failed("Failed to launch settings: ${e.localizedMessage}")
        }
    }
}
