package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ConversationEntity

// Palette constants strictly adhering to Sona AI ChatGPT-inspired specs:
private val DrawerBg = Color(0xFF101010)
private val DrawerSurface = Color(0xFF191919)
private val DrawerSelectedSurface = Color(0xFF252525)
private val DrawerBorder = Color(0xFF303030)
private val DrawerPrimaryText = Color(0xFFF2F2F2)
private val DrawerSecondaryText = Color(0xFFA3A3A3)
private val DrawerAccent = Color(0xFF90CAF9)

@Composable
fun ConversationDrawer(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onUpdateTitle: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAccount: () -> Unit,
    onCloseDrawer: () -> Unit = {}
) {
    var conversationToRename by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Rename Dialog
    if (conversationToRename != null) {
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            containerColor = DrawerSurface,
            title = {
                Text(
                    text = "Rename Chat",
                    color = DrawerPrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DrawerPrimaryText,
                        unfocusedTextColor = DrawerPrimaryText,
                        focusedBorderColor = DrawerAccent,
                        unfocusedBorderColor = DrawerBorder,
                        focusedContainerColor = DrawerBg,
                        unfocusedContainerColor = DrawerBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        conversationToRename?.let { conv ->
                            if (renameText.isNotBlank()) {
                                onUpdateTitle(conv.id, renameText.trim())
                            }
                        }
                        conversationToRename = null
                    }
                ) {
                    Text("Save", color = DrawerAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text("Cancel", color = DrawerSecondaryText)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(DrawerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("conversation_drawer_container")
    ) {
        // A. Header (Sona AI Identity & Close Button)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DrawerSurface)
                    .border(1.dp, DrawerBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = DrawerAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sona AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DrawerPrimaryText,
                    letterSpacing = 0.2.sp
                )
            }
            IconButton(
                onClick = {
                    if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                    onCloseDrawer()
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("drawer_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close drawer",
                    tint = DrawerSecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // B. Search and Settings row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DrawerSurface)
                    .border(1.dp, DrawerBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search chats",
                        tint = DrawerSecondaryText,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = DrawerPrimaryText,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(DrawerAccent),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_search_field"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search chats",
                                    color = DrawerSecondaryText.copy(alpha = 0.7f),
                                    fontSize = 13.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = DrawerSecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Settings button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DrawerSurface)
                    .border(1.dp, DrawerBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSettings)
                    .testTag("drawer_settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = DrawerSecondaryText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // C. Images and Projects:
        // Excluded: No real backend image gallery or project workspace implemented yet.
        // As per permanent project rules and instructions, no dead or fake buttons are created.

        // D. + New Chat action row
        DrawerActionRow(
            icon = Icons.Default.Add,
            title = "New chat",
            testTag = "drawer_new_chat_button",
            onClick = {
                if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                onNewChatClick()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // C. Recent Conversations Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (searchQuery.isEmpty()) "RECENT" else "FILTERED RESULTS (${conversations.size})",
                style = MaterialTheme.typography.labelSmall,
                color = if (searchQuery.isEmpty()) DrawerSecondaryText else DrawerAccent,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "Clear filter",
                    style = MaterialTheme.typography.labelSmall,
                    color = DrawerAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onSearchQueryChange("") }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scrollable conversation history list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No recent chats" else "No chats matching \"$searchQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = DrawerSecondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    var showItemMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) DrawerSelectedSurface
                                else Color.Transparent
                            )
                            .clickable {
                                if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                                onConversationClick(conv.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                            .testTag("conversation_item_${conv.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (conv.isPinned) Icons.Default.PushPin else Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = if (conv.isPinned) DrawerAccent else if (isSelected) DrawerPrimaryText else DrawerSecondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) DrawerPrimaryText else DrawerSecondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Quick actions overflow menu
                            Box {
                                IconButton(
                                    onClick = { showItemMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Chat actions",
                                        tint = if (isSelected) DrawerSecondaryText else DrawerSecondaryText.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showItemMenu,
                                    onDismissRequest = { showItemMenu = false },
                                    modifier = Modifier
                                        .background(DrawerSurface)
                                        .border(1.dp, DrawerBorder, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (conv.isPinned) "Unpin" else "Pin",
                                                color = DrawerPrimaryText,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = null,
                                                tint = DrawerAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            showItemMenu = false
                                            onTogglePin(conv.id, !conv.isPinned)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Rename",
                                                color = DrawerPrimaryText,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = DrawerSecondaryText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            showItemMenu = false
                                            renameText = conv.title
                                            conversationToRename = conv
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Delete",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            showItemMenu = false
                                            onDeleteConversation(conv.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // D. Bottom Account and Settings Area
        HorizontalDivider(
            color = DrawerBorder,
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DrawerNavigationRow(
                icon = Icons.Default.Person,
                title = "Account & Sync",
                testTag = "drawer_account_button",
                onClick = onOpenAccount
            )
            DrawerNavigationRow(
                icon = Icons.Default.Security,
                title = "Permissions & Tools",
                testTag = "drawer_permissions_button",
                onClick = onOpenPermissions
            )
            DrawerNavigationRow(
                icon = Icons.Default.Info,
                title = "Diagnostics",
                testTag = "drawer_diagnostics_button",
                onClick = onOpenDiagnostics
            )
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DrawerPrimaryText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = DrawerPrimaryText,
            fontSize = 14.5.sp
        )
    }
}

@Composable
private fun DrawerNavigationRow(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DrawerSecondaryText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = DrawerPrimaryText,
            fontSize = 14.sp
        )
    }
}
