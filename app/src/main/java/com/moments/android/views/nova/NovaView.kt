package com.moments.android.views.nova

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewModelScope
import com.moments.android.R
import com.moments.android.views.messaging.components.ChatRecoveryGateView
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novasections.ConfettiView
import com.moments.android.views.nova.novasections.ConversationHistoryOverlay
import com.moments.android.views.nova.novasections.EnhancedChatBubble
import com.moments.android.views.nova.novasections.EnhancedInputBar
import com.moments.android.views.nova.novasections.ModernLoadingAnimation
import com.moments.android.views.nova.novasections.ModernWelcomeSection
import com.moments.android.views.nova.novasections.NovaAttachmentMenuPopover
import com.moments.android.views.nova.novasections.NovaAttachmentSheetKind
import com.moments.android.views.nova.novasections.NovaAttachmentSheetOverlay
import com.moments.android.views.nova.novasections.NovaBackground
import com.moments.android.views.nova.novasections.NovaEncryptionBadge
import com.moments.android.views.nova.novasections.NovaHeader
import com.moments.android.views.nova.novasections.NovaInputBarLayout
import com.moments.android.views.nova.ui.NovaActionConfirmationOverlay
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `Views/Nova/NovaView.swift`.
 * Gate de acceso chat + contenido seguro (conversación, chrome, overlays).
 */

private val TopOverlayHeight = 132.dp
private val BottomOverlayHeight = 88.dp

@Composable
fun NovaView() {
    // ≡ iOS: Available → content; else ChatRecoveryGateView wrapping content.
    // Android ChatRecoveryGateView ya ramifica Available → content().
    ChatRecoveryGateView(onCancel = null) {
        NovaSecureContent()
    }
}

@Composable
private fun NovaSecureContent() {
    val context = LocalContext.current
    val agent = remember(context) { NovaAgent(context) }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDark = isSystemInDarkTheme()

    var showHistory by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<NovaAttachmentSheetKind?>(null) }
    var plusButtonAnchor by remember { mutableStateOf(Rect.Zero) }

    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)
    val keyboardHeight = with(density) { imeBottom.toDp() }
    val safeAreaBottom = with(density) { navBottom.toDp() }
    val safeAreaTop = with(density) { statusTop.toDp() }
    val isKeyboardVisible = keyboardHeight > 0.dp

    val fadeBase = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val defaultUsername = stringResource(R.string.nova_user)

    fun hideKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun scrollToBottom(animatedDelayMs: Long = 100L) {
        scope.launch {
            delay(animatedDelayMs)
            val last = agent.conversationHistory.lastIndex
            if (last >= 0) {
                listState.animateScrollToItem(last)
            }
        }
    }

    LaunchedEffect(Unit) { agent.fetchUserData() }

    DisposableEffect(agent) {
        onDispose {
            agent.viewModelScope.launch { agent.finalizeOnExit() }
        }
    }

    LaunchedEffect(activeSheet) {
        if (activeSheet != null) hideKeyboard()
    }

    LaunchedEffect(agent.conversationHistory.size, agent.conversationHistory.lastOrNull()?.id) {
        if (agent.conversationHistory.isNotEmpty()) scrollToBottom(100)
    }

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && agent.conversationHistory.isNotEmpty()) {
            scrollToBottom(400)
        }
    }

    val lastAssistantId = agent.conversationHistory.lastOrNull { !it.isUser && !it.isSystem }?.id
    val lastUserId = agent.conversationHistory.lastOrNull { it.isUser }?.id
    val showWelcome =
        agent.userData != null &&
            !agent.isLoading &&
            agent.conversationHistory.isEmpty() &&
            agent.showSuggestedOptions

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                if (agent.pendingAction == null) hideKeyboard()
            },
    ) {
        NovaBackground(Modifier.fillMaxSize())

        // Conversation / welcome
        Box(Modifier.fillMaxSize()) {
            if (showWelcome) {
                ModernWelcomeSection(
                    agent = agent,
                    showSuggestedOptions = agent.showSuggestedOptions,
                    onShowSuggestedOptionsChange = agent::updateShowSuggestedOptions,
                    topClearance = safeAreaTop + TopOverlayHeight + 12.dp,
                    bottomClearance = BottomOverlayHeight + safeAreaBottom,
                )
            } else {
                val bottomPad = if (keyboardHeight > 0.dp) {
                    keyboardHeight + BottomOverlayHeight
                } else {
                    BottomOverlayHeight + safeAreaBottom
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = safeAreaTop + TopOverlayHeight,
                        bottom = bottomPad,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = agent.conversationHistory,
                        key = { "${it.id}_${if (it.isHistorical) "historical" else "new"}" },
                    ) { message ->
                        EnhancedChatBubble(
                            message = message,
                            username = agent.userData?.username?.takeIf { it.isNotBlank() } ?: defaultUsername,
                            onRegenerate = if (agent.canRetouchLastExchange && message.id == lastAssistantId) {
                                agent::regenerateLastResponse
                            } else {
                                null
                            },
                            onEdit = if (agent.canRetouchLastExchange && message.id == lastUserId) {
                                agent::beginEditingLastUserMessage
                            } else {
                                null
                            },
                        )
                    }
                    if (agent.isLoading && agent.pendingAction == null) {
                        item(key = "nova_loading") {
                            Box(Modifier.padding(vertical = 20.dp).fillMaxWidth()) {
                                ModernLoadingAnimation(agent.activeToolDisplayName)
                            }
                        }
                    }
                }
            }

            // Top fade
            NovaTopFadeGradient(
                base = fadeBase,
                isDark = isDark,
                height = safeAreaTop + 38.dp,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Header + encryption badge — debajo del status bar (edge-to-edge dialog).
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = safeAreaTop + 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NovaHeader(
                    agent = agent,
                    showConversationHistory = { showHistory = it },
                    showSuggestedOptions = agent::updateShowSuggestedOptions,
                    isShowingMemory = { showMemory = it },
                )
                NovaEncryptionBadge()
            }

            // Input bar: cerrado = nav + 8; abierto = solo ime (pegado al teclado).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (keyboardHeight > 0.dp) {
                            Modifier.windowInsetsPadding(WindowInsets.ime)
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(bottom = NovaInputBarLayout.bottomPaddingWithoutKeyboard)
                        },
                    ),
            ) {
                EnhancedInputBar(
                    agent = agent,
                    showSuggestedOptions = agent::updateShowSuggestedOptions,
                    activeAttachmentSheet = activeSheet,
                    onAttachmentSheetChange = { activeSheet = it },
                    onFocusChange = { focused ->
                        if (focused && agent.conversationHistory.isNotEmpty()) {
                            scrollToBottom(200)
                        }
                    },
                    onPlusBoundsChange = { plusButtonAnchor = it },
                )
            }
        }

        // Floating overlays
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.zIndex(2f),
        ) {
            ConversationHistoryOverlay(
                agent,
                { showHistory = it },
                agent::updateShowSuggestedOptions,
            )
        }

        if (agent.showCelebration) {
            ConfettiView(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f),
            )
            LaunchedEffect(Unit) {
                delay(4000)
                agent.showCelebration = false
            }
        }

        agent.pendingAction?.let { action ->
            Box(Modifier.zIndex(50f)) {
                NovaActionConfirmationOverlay(
                    action = action,
                    onConfirm = agent::confirmPendingAction,
                    onCancel = agent::cancelPendingAction,
                )
            }
        }

        NovaAttachmentMenuPopover(
            activeSheet = activeSheet,
            onSheetChange = { activeSheet = it },
            plusButtonAnchor = plusButtonAnchor,
            modifier = Modifier.zIndex(44f),
        )

        Box(Modifier.zIndex(45f)) {
            NovaAttachmentSheetOverlay(
                activeSheet = activeSheet,
                onSheetChange = { activeSheet = it },
                onCaptured = { image ->
                    agent.selectedImage = image
                    activeSheet = null
                },
                onAdd = { image ->
                    agent.selectedImage = image
                    activeSheet = null
                },
            )
        }
    }

    // ≡ `.sheet` + `.presentationDetents([.medium, .large])` → MomentsModalSheet
    if (showMemory) {
        MomentsModalSheet(
            onDismissRequest = {
                showMemory = false
                agent.reloadMemoryFromStore()
            },
            largeOnly = false,
        ) { dismiss ->
            NovaMemoryManagementView(
                onDismiss = {
                    dismiss()
                    agent.reloadMemoryFromStore()
                },
            )
        }
    }
}

@Composable
private fun NovaTopFadeGradient(
    base: Color,
    isDark: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to base.copy(alpha = if (isDark) 1f else 0.98f),
                        0.28f to base.copy(alpha = if (isDark) 0.96f else 0.9f),
                        0.64f to base.copy(alpha = if (isDark) 0.58f else 0.42f),
                        0.88f to base.copy(alpha = if (isDark) 0.14f else 0.08f),
                        1.0f to Color.Transparent,
                    ),
                ),
            ),
    )
}
