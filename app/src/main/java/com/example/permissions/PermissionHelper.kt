package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class CapabilityPermission(
    val id: String,
    val title: String,
    val permissionName: String?,
    val description: String,
    val purpose: String,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val statusLabel: String
)

object PermissionHelper {

    fun getCapabilities(context: Context): List<CapabilityPermission> {
        val hasNetwork = isNetworkAvailable(context)
        val hasAudio = isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Granted at install time on Android 12 and below
        }

        return listOf(
            CapabilityPermission(
                id = "network",
                title = "Network & Cloud Connectivity",
                permissionName = Manifest.permission.INTERNET,
                description = "Enables communication with Google Gemini intelligence and streaming responses.",
                purpose = "Essential for all AI generation, multi-turn reasoning, and real-time streaming.",
                isGranted = hasNetwork,
                isRequired = true,
                statusLabel = if (hasNetwork) "Online & Connected" else "No Connection"
            ),
            CapabilityPermission(
                id = "audio",
                title = "Microphone & Voice Input",
                permissionName = Manifest.permission.RECORD_AUDIO,
                description = "Required for speech-to-text input and future Gemini 3.8 Live bidirectional audio sessions.",
                purpose = "Allows natural hands-free voice interactions with Sona AI.",
                isGranted = hasAudio,
                isRequired = false,
                statusLabel = if (hasAudio) "Granted" else "Action Required"
            ),
            CapabilityPermission(
                id = "notifications",
                title = "System Notifications",
                permissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null,
                description = "Alerts you when long-running reasoning tasks complete in background.",
                purpose = "Ensures you never miss completed research or high-thinking generations.",
                isGranted = hasNotifications,
                isRequired = false,
                statusLabel = if (hasNotifications) "Granted" else "Action Required"
            )
        )
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
