package com.moments.android.views.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moments.android.views.messaging.components.ChatRecoveryGateView
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.nova.agent.NovaAgent
import com.moments.android.views.nova.novacore.NovaColors
import com.moments.android.views.nova.novasections.ConfettiView
import com.moments.android.views.nova.novasections.ConversationHistoryOverlay
import com.moments.android.views.nova.novasections.EnhancedChatBubble
import com.moments.android.views.nova.novasections.EnhancedInputBar
import com.moments.android.views.nova.novasections.ModernLoadingAnimation
import com.moments.android.views.nova.novasections.ModernWelcomeSection
import com.moments.android.views.nova.novasections.NovaAttachmentMenuPopover
import com.moments.android.views.nova.novasections.NovaAttachmentSheetKind
import com.moments.android.views.nova.novasections.NovaAttachmentSheetOverlay
import com.moments.android.views.nova.novasections.NovaEncryptionBadge
import com.moments.android.views.nova.novasections.NovaHeader
import com.moments.android.views.nova.ui.NovaActionConfirmationOverlay
import kotlinx.coroutines.delay

@Composable
fun NovaView() {
    val context = LocalContext.current
    val agent = remember(context) { NovaAgent(context) }
    val access by ChatAccessCoordinator.accessState.collectAsState()
    LaunchedEffect(Unit) { ChatAccessCoordinator.ensureAccess() }
    ChatRecoveryGateView(access) { NovaSecureContent(agent) }
}

@Composable
private fun NovaSecureContent(agent: NovaAgent) {
    var showHistory by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<NovaAttachmentSheetKind?>(null) }
    LaunchedEffect(Unit) { agent.fetchUserData() }
    Box(Modifier.fillMaxSize().background(NovaColors.background)) {
        if (agent.userData != null && !agent.isLoading && agent.conversationHistory.isEmpty() && agent.showSuggestedOptions) {
            ModernWelcomeSection(agent, true) { prompt -> agent.inputText = prompt; agent.sendMessage() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 112.dp, bottom = 96.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                items(agent.conversationHistory, key = { it.id }) { message ->
                    EnhancedChatBubble(
                        message,
                        agent.userData?.username.orEmpty(),
                        onRegenerate = if (agent.canRetouchLastExchange && message == agent.conversationHistory.lastOrNull { !it.isUser && !it.isSystem }) agent::regenerateLastResponse else null,
                        onEdit = if (agent.canRetouchLastExchange && message == agent.conversationHistory.lastOrNull { it.isUser }) agent::beginEditingLastUserMessage else null,
                    )
                }
                if (agent.isLoading && agent.pendingAction == null) item { ModernLoadingAnimation(agent.activeToolDisplayName) }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            NovaHeader(agent, { showHistory = it }, agent::updateShowSuggestedOptions, { showMemory = it })
            Box(Modifier.align(Alignment.CenterHorizontally)) { NovaEncryptionBadge() }
        }
        EnhancedInputBar(agent, agent::updateShowSuggestedOptions, activeSheet, { activeSheet = it }, modifier = Modifier.align(Alignment.BottomCenter))
        if (showHistory) ConversationHistoryOverlay(agent, { showHistory = it }, agent::updateShowSuggestedOptions)
        if (showMemory) NovaMemoryManagementView(onDismiss = { showMemory = false })
        NovaAttachmentMenuPopover(activeSheet, { activeSheet = it })
        NovaAttachmentSheetOverlay(activeSheet, { activeSheet = it }, { image -> agent.selectedImage = image; activeSheet = null }, { image -> agent.selectedImage = image; activeSheet = null })
        agent.pendingAction?.let { action -> NovaActionConfirmationOverlay(action, agent::confirmPendingAction, agent::cancelPendingAction) }
        if (agent.showCelebration) {
            ConfettiView(Modifier.fillMaxSize())
            LaunchedEffect(Unit) { delay(4000); agent.showCelebration = false }
        }
    }
}
