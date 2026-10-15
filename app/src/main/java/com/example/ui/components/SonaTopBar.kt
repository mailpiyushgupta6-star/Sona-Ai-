package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SonaModel
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaViolet

@Composable
fun SonaTopBar(
    currentModel: SonaModel,
    isThinkingEnabled: Boolean,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    onThinkingToggle: () -> Unit,
    onNewChatClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Drawer button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("menu_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open conversation drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Center: Compact Sona AI / Model Selector & Thinking action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Model Selector Pill (clean, understated)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onModelClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("model_selector_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentModel.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Model",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // If Model supports Thinking, show Thinking Mode Toggle
                if (currentModel.supportsThinking) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isThinkingEnabled) Color(0xFF242424)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isThinkingEnabled) Color(0xFF383838)
                                else Color(0xFF303030),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onThinkingToggle() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("thinking_mode_toggle")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Toggle High Thinking",
                                tint = if (isThinkingEnabled) Color(0xFFF2F2F2) else Color(0xFFA3A3A3),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isThinkingEnabled) "Think On" else "Think",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isThinkingEnabled) Color(0xFFF2F2F2) else Color(0xFFA3A3A3)
                            )
                        }
                    }
                }
            }

            // Right: New Chat button (clean ghost action)
            IconButton(
                onClick = onNewChatClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Start New Chat",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
