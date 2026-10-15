package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.FirebaseAuthRepository
import com.example.chat.ChatViewModel
import com.example.chat.ChatViewModelFactory
import com.example.data.local.SonaDatabase
import com.example.data.remote.FirestoreDataSource
import com.example.data.repository.LocalConversationRepository
import com.example.data.repository.LocalMemoryRepository
import com.example.data.repository.SyncedConversationRepository
import com.example.data.repository.SyncedMemoryRepository
import com.example.ui.auth.AccountDialog
import com.example.ui.components.ConversationDrawer
import com.example.ui.components.ModelSelectorDialog
import com.example.ui.components.SonaTopBar
import com.example.ui.navigation.SonaScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = SonaDatabase.getDatabase(applicationContext)
        val localConversationRepository = LocalConversationRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao()
        )
        val localMemoryRepository = LocalMemoryRepository(
            memoryDao = database.memoryDao()
        )
        val remoteDataSource = FirestoreDataSource(applicationContext)
        val syncedConversationRepository = SyncedConversationRepository(
            localRepository = localConversationRepository,
            remoteDataSource = remoteDataSource,
            scope = lifecycleScope,
            tombstoneDao = database.tombstoneDao(),
            syncQueueDao = database.syncQueueDao()
        )
        val syncedMemoryRepository = SyncedMemoryRepository(
            localRepository = localMemoryRepository,
            remoteDataSource = remoteDataSource,
            scope = lifecycleScope
        )
        val authRepository = FirebaseAuthRepository()

        // Attempt silent auto sign-in if previously authorized
        authRepository.attemptAutoSignIn(applicationContext, lifecycleScope)

        val appManager = com.example.androidapps.AndroidAppManager(applicationContext)
        val appPreferences = com.example.data.local.AppPreferences(applicationContext)
        val toolRegistry = com.example.tools.ToolRegistry()
        toolRegistry.registerTool(com.example.tools.CalculatorTool())
        toolRegistry.registerTool(com.example.tools.DateTimeTool())
        toolRegistry.registerTool(com.example.tools.MemorySearchTool(localMemoryRepository))
        toolRegistry.registerTool(com.example.tools.AppLauncherTool(appManager))
        toolRegistry.registerTool(com.example.tools.SystemControlTool(applicationContext))

        val viewModelFactory = ChatViewModelFactory(
            conversationRepository = syncedConversationRepository,
            memoryRepository = syncedMemoryRepository,
            authRepository = authRepository,
            appPreferences = appPreferences,
            toolRegistry = toolRegistry,
            appManager = appManager,
            applicationContext = applicationContext
        )

        setContent {
            SonaTheme {
                val viewModel: ChatViewModel = viewModel(factory = viewModelFactory)
                SonaApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SonaApp(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf<SonaScreen>(SonaScreen.Chat) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showModelDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    // Close drawer on back press if open in Chat screen
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    if (showModelDialog) {
        ModelSelectorDialog(
            currentModel = uiState.selectedModel,
            onModelSelected = { viewModel.selectModel(it) },
            onDismissRequest = { showModelDialog = false }
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            authRepository = viewModel.authRepository,
            authState = uiState.authState,
            syncState = uiState.syncState,
            onImportLocalData = { viewModel.importLocalDataToCloud() },
            onDismissRequest = { showAccountDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen is SonaScreen.Chat,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversation?.id,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onConversationClick = { convId ->
                    viewModel.selectConversation(convId)
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChatClick = {
                    viewModel.startNewChat()
                    coroutineScope.launch { drawerState.close() }
                },
                onUpdateTitle = { id, title -> viewModel.updateConversationTitle(id, title) },
                onTogglePin = { id, isPinned -> viewModel.togglePinConversation(id, isPinned) },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                onOpenSettings = {
                    currentScreen = SonaScreen.Settings
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenPermissions = {
                    currentScreen = SonaScreen.Permissions
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenDiagnostics = {
                    currentScreen = SonaScreen.Diagnostics
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenAccount = {
                    if (uiState.authState is com.example.auth.AuthState.Authenticated) {
                        showAccountDialog = true
                    } else {
                        currentScreen = SonaScreen.Auth
                    }
                    coroutineScope.launch { drawerState.close() }
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                is SonaScreen.Chat -> {
                    Scaffold(
                        topBar = {
                            SonaTopBar(
                                currentModel = uiState.selectedModel,
                                isThinkingEnabled = uiState.isThinkingEnabled,
                                onMenuClick = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onModelClick = { showModelDialog = true },
                                onThinkingToggle = { viewModel.toggleThinking() },
                                onNewChatClick = { viewModel.startNewChat() }
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        ChatScreen(
                            uiState = uiState,
                            onSendMessage = { text, uri ->
                                viewModel.sendMessage(text, null, uri)
                            },
                            onStopGeneration = { viewModel.stopGeneration() },
                            onRetry = { viewModel.retryLastMessage(null) },
                            onVoiceClick = { currentScreen = SonaScreen.Permissions },
                            onOpenSettings = { currentScreen = SonaScreen.Settings },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                is SonaScreen.Settings -> {
                    SettingsScreen(
                        uiState = uiState,
                        onBack = { currentScreen = SonaScreen.Chat },
                        onSelectModel = { viewModel.selectModel(it) },
                        onToggleThinking = { viewModel.toggleThinking() },
                        onAddMemory = { key, content, cat -> viewModel.addMemory(key, content, cat) },
                        onToggleMemory = { id, enabled -> viewModel.toggleMemory(id, enabled) },
                        onDeleteMemory = { id -> viewModel.deleteMemory(id) },
                        onClearAllMemories = { viewModel.clearAllMemories() },
                        onClearHistory = { viewModel.clearAllConversations() },
                        onOpenPermissions = { currentScreen = SonaScreen.Permissions },
                        onOpenDiagnostics = { currentScreen = SonaScreen.Diagnostics },
                        onOpenAccount = {
                            if (uiState.authState is com.example.auth.AuthState.Authenticated) {
                                showAccountDialog = true
                            } else {
                                currentScreen = SonaScreen.Auth
                            }
                        },
                        onSelectStorageLocation = { viewModel.setStorageLocation(it) }
                    )
                }

                is SonaScreen.Permissions -> {
                    PermissionsScreen(
                        appManager = viewModel.appManager,
                        onBack = { currentScreen = SonaScreen.Chat }
                    )
                }

                is SonaScreen.Diagnostics -> {
                    DiagnosticsScreen(
                        uiState = uiState,
                        onBack = { currentScreen = SonaScreen.Chat }
                    )
                }

                is SonaScreen.Auth -> {
                    AuthScreen(
                        authRepository = viewModel.authRepository,
                        authState = uiState.authState,
                        onAuthSuccess = { currentScreen = SonaScreen.Chat },
                        onContinueAsGuest = { currentScreen = SonaScreen.Chat }
                    )
                }
            }
        }
    }
}
