package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ChatUiState
import com.example.permissions.PermissionHelper
import com.example.ui.theme.SonaAmber
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaEmerald
import com.example.ui.theme.SonaIndigo

enum class FeatureStatus(val label: String, val color: Color) {
    IMPLEMENTED("Implemented", Color(0xFF10B981)),
    IN_PROGRESS("In Progress", Color(0xFFF59E0B)),
    PLANNED("Planned", Color(0xFF6366F1))
}

data class FeatureItem(
    val name: String,
    val description: String,
    val status: FeatureStatus
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    uiState: ChatUiState,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val isNetworkOnline = PermissionHelper.isNetworkAvailable(context)

    val features = listOf(
        FeatureItem(
            name = "Native Chat Shell & UI",
            description = "Obsidian dark theme, edge-to-edge layout, responsive drawer, and bottom composer.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Local Conversation Persistence",
            description = "Full Room SQLite database schema with reactive Flows and cascading deletion.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Multi-Model Intelligence Switching",
            description = "Gemini 3.5 Flash, Gemini 3.1 Pro (Thinking), and Gemini 3.1 Flash-Lite.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "High Thinking Mode",
            description = "Deep multi-step reasoning with ThinkingLevel.HIGH for Gemini 3.1 Pro.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Streaming Responses & Cancellation",
            description = "SSE token streaming over HTTP with instant user cancellation support.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Persistent User Memory",
            description = "Entity storage and system instruction context injection across chats.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Multimodal Image Analysis Entry",
            description = "Zero-permission Android Photo Picker and Base64 inlineData encoding.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Permission & Capability Inspector",
            description = "Real Android permission queries for audio, notifications, and network.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Gemini 3.8 Live API Audio Conversation",
            description = "Bidirectional audio streaming WebSocket integration for voice conversation.",
            status = FeatureStatus.IN_PROGRESS
        ),
        FeatureItem(
            name = "PackageManager App Discovery & Launch",
            description = "Installed app discovery, icons, in-app search, launch intents, and Hinglish/English command launcher.",
            status = FeatureStatus.IMPLEMENTED
        ),
        FeatureItem(
            name = "Cloud Database Sync (Firebase)",
            description = "Optional remote cloud sync with Google Sign-In and Firestore.",
            status = FeatureStatus.PLANNED
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("diagnostics_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Environment Telemetry Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SonaCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Platform Runtime Environment",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    TelemetryRow("Android Version", "API Level ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                    TelemetryRow("UI Framework", "Jetpack Compose (Material 3)")
                    TelemetryRow("Local Database", "Room SQLite (v1 schema)")
                    TelemetryRow("Network Status", if (isNetworkOnline) "Connected (Internet Active)" else "Offline")
                    TelemetryRow("API Key Status", if (uiState.isApiKeyConfigured) "Injected & Active" else "Pending in Secrets")
                    TelemetryRow("Active Model", uiState.selectedModel.displayName)
                }
            }

            Text(
                text = "Feature Implementation Checklist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SonaCyan,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Feature Checklist Cards
            features.forEach { feature ->
                FeatureStatusRow(feature = feature)
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureStatusRow(feature: FeatureItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(feature.status.color.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = feature.status.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = feature.status.color
                )
            }
        }
    }
}
