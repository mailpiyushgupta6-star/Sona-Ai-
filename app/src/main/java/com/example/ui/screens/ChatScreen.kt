package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ChatUiState
import com.example.ui.components.MessageComposer
import com.example.ui.components.MessageItem
import com.example.ui.theme.SonaAmber
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaIndigo
import com.example.ui.theme.SonaViolet

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String, Uri?) -> Unit,
    onStopGeneration: () -> Unit,
    onRetry: () -> Unit,
    onVoiceClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom whenever new messages arrive or streaming updates
    LaunchedEffect(uiState.messages.size, uiState.currentStreamingText) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Warning Banner if API key is not configured in Secrets
        if (!uiState.isApiKeyConfigured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SonaAmber.copy(alpha = 0.15f))
                    .border(1.dp, SonaAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("api_key_warning_banner")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SonaAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini API Key pending in AI Studio Secrets. Tap to view status & settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Message Thread Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyChatWelcome(
                    suggestions = uiState.contextualSuggestions,
                    onSuggestionClick = { prompt ->
                        onSendMessage(prompt, null)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        val isStreaming = uiState.isGenerating &&
                                message.role == "assistant" &&
                                message == uiState.messages.lastOrNull()
                        MessageItem(
                            message = message,
                            isStreaming = isStreaming,
                            onRetry = onRetry
                        )
                    }

                    // Contextual Action Cards in dedicated area at end of thread when valid suggestions exist
                    if (!uiState.isGenerating && uiState.contextualSuggestions.isNotEmpty()) {
                        item(key = "contextual_suggestions_container") {
                            ContextualSuggestionsSection(
                                suggestions = uiState.contextualSuggestions,
                                onSuggestionClick = { prompt ->
                                    onSendMessage(prompt, null)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Message Composer
        MessageComposer(
            isGenerating = uiState.isGenerating,
            onSendMessage = onSendMessage,
            onStopGeneration = onStopGeneration,
            onVoiceClick = onVoiceClick
        )
    }
}

@Composable
private fun EmptyChatWelcome(
    suggestions: List<com.example.chat.SmartSuggestion> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome Heading & Subtitle
        Text(
            text = "Welcome back!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "What can I help you with today?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Context-aware action cards on Home screen only when valid suggestions exist
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            ContextualSuggestionsSection(
                suggestions = suggestions,
                onSuggestionClick = onSuggestionClick
            )
        }
    }
}

@Composable
private fun ContextualSuggestionsSection(
    suggestions: List<com.example.chat.SmartSuggestion>,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFA3A3A3),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SUGGESTED NEXT ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }

        suggestions.forEach { suggestion ->
            ContextualActionCard(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion.prompt) }
            )
        }
    }
}

@Composable
private fun ContextualActionCard(
    suggestion: com.example.chat.SmartSuggestion,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF191919))
            .border(
                width = 1.dp,
                color = Color(0xFF2E2E2E),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("suggestion_card_${suggestion.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = null,
                tint = Color(0xFF90CAF9),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF2F2F2)
                )
                Text(
                    text = suggestion.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA3A3A3)
                )
            }
        }
    }
}
