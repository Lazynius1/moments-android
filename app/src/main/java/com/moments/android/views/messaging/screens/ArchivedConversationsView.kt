package com.moments.android.views.messaging.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ConversationContextMenuInsets
import com.moments.android.views.messaging.components.ConversationContextMenuOverlay
import com.moments.android.views.messaging.components.ConversationListInteraction
import com.moments.android.views.messaging.components.ConversationMenuData
import com.moments.android.views.messaging.components.ConversationMenuSelection
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.MessagingViewModel

/**
 * Port de `Views/Messaging/Screens/ArchivedConversationsView.swift`.
 * Perfil desde fila: callback [onOpenProfile] (navegación de perfil pendiente de cablear).
 */
@Composable
fun ArchivedConversationsView(
    viewModel: MessagingViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenStory: (String) -> Unit = {},
    onMarkUnread: (Conversation) -> Unit = { viewModel.markConversationAsUnread(it) },
    onPin: (Conversation) -> Unit = { viewModel.togglePinned(it) },
    onMute: (Conversation) -> Unit = { viewModel.toggleMuted(it) },
    onUnarchive: (Conversation) -> Unit = { viewModel.unarchiveConversation(it) },
    onDelete: (Conversation) -> Unit = { viewModel.deleteConversation(it) },
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var conversationMenuSelection by remember { mutableStateOf<ConversationMenuSelection?>(null) }
    var conversationRowFrames by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val archived = viewModel.archivedConversations

    LaunchedEffect(archived.isEmpty()) {
        if (archived.isEmpty()) onBack()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            .onSizeChanged { containerSize = it },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.primary,
                    )
                }
                Text(
                    stringResource(R.string.messaging_section_archived),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            if (archived.isEmpty()) {
                ArchivedEmptyState()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    userScrollEnabled = conversationMenuSelection == null,
                ) {
                    items(
                        archived.filter { !it.id.isNullOrBlank() },
                        key = { it.id.orEmpty() },
                    ) { conversation ->
                        val id = conversation.id.orEmpty()
                        val selected = conversationMenuSelection?.item?.conversation?.id == id
                        GlassmorphicConversationRow(
                            conversation = conversation,
                            onOpenProfile = {
                                val trimmed = conversation.otherParticipantId.trim()
                                if (trimmed.isNotEmpty()) onOpenProfile(trimmed)
                            },
                            onTap = { onOpenConversation(conversation) },
                            onOpenStory = { userId ->
                                val trimmed = userId.trim()
                                if (trimmed.isNotEmpty()) onOpenStory(trimmed)
                            },
                            isMenuSelected = selected,
                            listInteraction = ConversationListInteraction(
                                onTap = { onOpenConversation(conversation) },
                                onLongPress = {
                                    val frame = conversationRowFrames[id] ?: return@ConversationListInteraction
                                    if (frame.width <= 0f || frame.height <= 0f) return@ConversationListInteraction
                                    conversationMenuSelection = ConversationMenuSelection(
                                        item = ConversationMenuData(
                                            conversation = conversation,
                                            unreadCount = conversation.unreadCount(uid.orEmpty()),
                                            isPinned = conversation.isPinned(uid),
                                            isMuted = conversation.isMuted(uid),
                                            isArchived = true,
                                        ),
                                        rowFrame = frame,
                                    )
                                },
                                onPressingChanged = {},
                            ),
                            modifier = Modifier.onGloballyPositioned { coords ->
                                conversationRowFrames = conversationRowFrames + (id to coords.boundsInRoot())
                            },
                        )
                    }
                }
            }
        }

        ConversationContextMenuOverlay(
            selection = conversationMenuSelection,
            containerSize = containerSize,
            safeAreaInsets = ConversationContextMenuInsets(),
            onDismiss = { conversationMenuSelection = null },
            onMarkUnread = {
                onMarkUnread(it)
                conversationMenuSelection = null
            },
            onPin = {
                onPin(it)
                conversationMenuSelection = null
            },
            onMute = {
                onMute(it)
                conversationMenuSelection = null
            },
            onArchive = { conversationMenuSelection = null },
            onUnarchive = {
                onUnarchive(it)
                conversationMenuSelection = null
            },
            onDelete = {
                onDelete(it)
                conversationMenuSelection = null
            },
        )
    }
}

@Composable
private fun ArchivedEmptyState() {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxSize()
            .momentsEmptyStateAppear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Inventory2,
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(44.dp),
        )
        Text(
            stringResource(R.string.messaging_section_archived),
            color = colors.primary.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
