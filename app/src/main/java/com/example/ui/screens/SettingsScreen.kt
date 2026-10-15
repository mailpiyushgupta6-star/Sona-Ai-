package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SonaModel
import com.example.chat.ChatUiState
import com.example.data.local.model.MemoryEntity
import com.example.ui.components.ModelSelectorDialog
import com.example.ui.theme.SonaAmber
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaEmerald
import com.example.ui.theme.SonaIndigo
import com.example.ui.theme.SonaViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: ChatUiState,
    onBack: () -> Unit,
    onSelectModel: (SonaModel) -> Unit,
    onToggleThinking: () -> Unit,
    onAddMemory: (String, String, String) -> Unit,
    onToggleMemory: (String, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAccount: () -> Unit,
    onSelectStorageLocation: (String) -> Unit = {}
) {
    BackHandler { onBack() }

    var showModelDialog by remember { mutableStateOf(false) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearMemoriesDialog by remember { mutableStateOf(false) }

    var newMemoryKey by remember { mutableStateOf("") }
    var newMemoryContent by remember { mutableStateOf("") }

    if (showModelDialog) {
        ModelSelectorDialog(
            currentModel = uiState.selectedModel,
            onModelSelected = onSelectModel,
            onDismissRequest = { showModelDialog = false }
        )
    }

    if (showAddMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Add Memory Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newMemoryKey,
                        onValueChange = { newMemoryKey = it },
                        label = { Text("Topic / Title (e.g., Coding Style)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newMemoryContent,
                        onValueChange = { newMemoryContent = it },
                        label = { Text("Memory Fact or Preference") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newMemoryKey.isNotBlank() && newMemoryContent.isNotBlank()) {
                            onAddMemory(newMemoryKey, newMemoryContent, "custom")
                            newMemoryKey = ""
                            newMemoryContent = ""
                            showAddMemoryDialog = false
                        }
                    }
                ) {
                    Text("Save", color = SonaCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear All Conversations?") },
            text = { Text("This will permanently delete all stored local chat conversations and message history from Room database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesDialog = false },
            title = { Text("Clear All Memories?") },
            text = { Text("This will erase all persistent user facts and context memories.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        showClearMemoriesDialog = false
                    }
                ) {
                    Text("Delete Memories", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoriesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to chat"
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 0. Account & Cloud Synchronization
            SettingsSectionHeader(title = "Account & Cloud Synchronization")
            SettingsCard {
                val isAuth = uiState.authState is com.example.auth.AuthState.Authenticated
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount() }
                        .padding(vertical = 4.dp)
                        .testTag("settings_account_tile")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isAuth) SonaEmerald.copy(alpha = 0.2f) else SonaCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isAuth) SonaEmerald else SonaCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAuth) {
                                (uiState.authState as com.example.auth.AuthState.Authenticated).user.displayName
                                    ?: (uiState.authState as com.example.auth.AuthState.Authenticated).user.email
                                    ?: "Google Account"
                            } else "Guest / Local Mode",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (isAuth) "Cloud sync active (Firestore)" else "Local storage only • Tap to sign in with Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAuth) SonaEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isAuth) "Manage" else "Sign In",
                        color = SonaCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Storage Location Option
            SettingsSectionHeader(title = "Storage Provider Location")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isCloud = uiState.storageLocation == "CLOUD_FIRESTORE"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCloud) SonaCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .clickable { onSelectStorageLocation("CLOUD_FIRESTORE") }
                            .padding(12.dp)
                            .testTag("storage_location_cloud")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (isCloud) SonaCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Storage (Firestore - Default)",
                                fontWeight = FontWeight.Bold,
                                color = if (isCloud) SonaCyan else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatic cloud backup and cross-device sync when signed in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isCloud) SonaAmber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .clickable { onSelectStorageLocation("PHONE_STORAGE") }
                            .padding(12.dp)
                            .testTag("storage_location_phone")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (!isCloud) SonaAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Storage (Room, local-only)",
                                fontWeight = FontWeight.Bold,
                                color = if (!isCloud) SonaAmber else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keep conversations strictly on this device. No cloud upload.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. AI Intelligence Model
            SettingsSectionHeader(title = "AI Intelligence & Model")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_model_selector")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SonaCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = SonaCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active Model", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(uiState.selectedModel.displayName, style = MaterialTheme.typography.bodySmall, color = SonaCyan)
                        }
                        Text("Change", color = SonaCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (uiState.selectedModel.supportsThinking) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SonaViolet.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Psychology, null, tint = SonaViolet, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High Thinking Mode", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Text("Activates deep multi-step cognitive reasoning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = uiState.isThinkingEnabled,
                                onCheckedChange = { onToggleThinking() },
                                colors = SwitchDefaults.colors(checkedThumbColor = SonaViolet, checkedTrackColor = SonaViolet.copy(alpha = 0.4f)),
                                modifier = Modifier.testTag("settings_thinking_switch")
                            )
                        }
                    }
                }
            }

            // 2. Google AI Studio Secrets & Credentials
            SettingsSectionHeader(title = "AI Studio Secrets & Provider")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (uiState.isApiKeyConfigured) SonaEmerald else SonaAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (uiState.isApiKeyConfigured) "Gemini API Configured" else "API Key Pending",
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isApiKeyConfigured) SonaEmerald else SonaAmber
                        )
                    }
                    Text(
                        text = if (uiState.isApiKeyConfigured)
                            "Ready. Requests are authorized via Google AI Studio's secure injection."
                        else
                            "To enable real Gemini generation, open the Secrets panel in AI Studio and add GEMINI_API_KEY with your Google Gemini API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Persistent Memory
            SettingsSectionHeader(title = "Conversation Memory & Context")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Memories (${uiState.memories.size})", fontWeight = FontWeight.SemiBold)
                            Text("Persists context across conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = { showAddMemoryDialog = true },
                            modifier = Modifier.testTag("add_memory_button")
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = SonaCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", color = SonaCyan)
                        }
                    }

                    if (uiState.memories.isEmpty()) {
                        Text(
                            text = "No saved memories. Add preferences or project context so Sona remembers them automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.memories.forEach { memory ->
                            MemoryRow(
                                memory = memory,
                                onToggle = { onToggleMemory(memory.id, it) },
                                onDelete = { onDeleteMemory(memory.id) }
                            )
                        }
                        TextButton(
                            onClick = { showClearMemoriesDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear All Memories", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Local Data & Persistence
            SettingsSectionHeader(title = "Local Persistence & Storage")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Saved Conversations", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiState.conversationCount}", fontWeight = FontWeight.Bold, color = SonaCyan)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Saved Messages", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiState.totalMessageCount}", fontWeight = FontWeight.Bold, color = SonaCyan)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearHistoryDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("clear_history_row")
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Clear All Chat History", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 5. System Quick Links
            SettingsSectionHeader(title = "Diagnostics & Permissions")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPermissions() }
                            .padding(vertical = 8.dp)
                            .testTag("settings_permissions_link")
                    ) {
                        Icon(Icons.Default.Security, null, tint = SonaCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permissions & Capabilities", fontWeight = FontWeight.SemiBold)
                            Text("Microphone, notifications, and network status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDiagnostics() }
                            .padding(vertical = 8.dp)
                            .testTag("settings_diagnostics_link")
                    ) {
                        Icon(Icons.Default.Info, null, tint = SonaIndigo, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Diagnostics & Technical Roadmap", fontWeight = FontWeight.SemiBold)
                            Text("Check implemented features vs future milestones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = SonaCyan,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
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
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun MemoryRow(
    memory: MemoryEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(memory.key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(memory.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = memory.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = SonaCyan, checkedTrackColor = SonaCyan.copy(alpha = 0.4f)),
            modifier = Modifier.size(36.dp)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}
