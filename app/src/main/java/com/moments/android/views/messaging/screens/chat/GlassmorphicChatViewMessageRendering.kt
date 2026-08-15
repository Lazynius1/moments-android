package com.moments.android.views.messaging.screens.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.components.ChatBuzzTimelineEventRow
import com.moments.android.views.messaging.components.ChatBuzzToast
import com.moments.android.views.messaging.components.ChatConversationIntroRow
import com.moments.android.views.messaging.components.ChatFailedMessageRetryAction
import com.moments.android.views.messaging.components.ChatGlassmorphicBackground
import com.moments.android.views.messaging.components.ChatHistoryStartHeader
import com.moments.android.views.messaging.components.ChatRequestDisclaimerRow
import com.moments.android.views.messaging.components.ChatTimestampRevealState
import com.moments.android.views.messaging.components.GlassmorphicDateHeader
import com.moments.android.views.messaging.components.GlassmorphicReplyBar
import com.moments.android.views.messaging.components.GlassmorphicTypingIndicator
import com.moments.android.views.messaging.components.LocalChatFailedMessageRetryAction
import com.moments.android.views.messaging.components.PendingRequestMessageRow
import com.moments.android.views.messaging.components.chatMenuDimmedUnlessSelected
import com.moments.android.views.messaging.components.chatMenuDimmedWhenOpen
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageItem

/** Port de `GlassmorphicChatView+MessageRendering.swift`. */
data class ChatMessageRenderingCallbacks(
    val renderer: ChatMessageRendererCallbacks,
    val buzzText: (com.moments.android.views.messaging.services.ChatBuzzEvent) -> String,
    val onUnreadDivider: @Composable (ChatRenderRow.Message) -> Unit = {},
    val onReplyCancelled: () -> Unit = {},
    val onEditCancelled: () -> Unit = {},
    val onEditingStarted: (EnhancedMessage) -> Unit = {},
    val onCopy: (EnhancedMessage) -> Unit = {},
    val onForward: (EnhancedMessage) -> Unit = {},
    val onMoreReactions: (EnhancedMessage) -> Unit = {},
    val onToggleStar: (EnhancedMessage) -> Unit = {},
    val starredMessageIds: Set<String> = emptySet(),
)

/** ≡ iOS `isOutgoingItem(_:)`. */
fun isOutgoingItem(item: MessageItem, currentUserId: String): Boolean = when (item) {
    is MessageItem.Single -> item.message.senderId == currentUserId
    is MessageItem.MediaCluster -> item.messages.firstOrNull()?.senderId == currentUserId
}

@Composable
fun GlassmorphicChatRenderRow(
    row: ChatRenderRow,
    viewModel: EnhancedChatViewModel,
    adaptiveColors: AdaptiveColors,
    otherParticipantName: String,
    otherParticipantId: String,
    messagePresentation: ChatMessagePresentationState,
    callbacks: ChatMessageRenderingCallbacks,
    quickReactionEmoji: String,
    timestampRevealState: ChatTimestampRevealState = remember { ChatTimestampRevealState() },
    modifier: Modifier = Modifier,
) {
    val menuOpen = messagePresentation.menuSelection != null
    val selectedRowId = messagePresentation.menuSelection?.rowId
    when (row) {
        is ChatRenderRow.ConversationIntro -> ChatConversationIntroRow(
            row.context,
            otherParticipantName,
            otherParticipantId,
            adaptiveColors,
            // ≡ iOS: horizontal 18, top 18, bottom 8
            modifier
                .chatMenuDimmedWhenOpen(menuOpen)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        )
        is ChatRenderRow.RequestDisclaimer -> ChatRequestDisclaimerRow(
            requestDisclaimerForRendering(row.context),
            adaptiveColors,
            modifier.chatMenuDimmedWhenOpen(menuOpen).padding(horizontal = 18.dp, vertical = 6.dp),
        )
        is ChatRenderRow.PendingRequestMessage -> PendingRequestMessageRow(
            row.message,
            adaptiveColors,
            modifier.chatMenuDimmedWhenOpen(menuOpen).padding(horizontal = 14.dp, vertical = 4.dp),
        )
        is ChatRenderRow.Header -> GlassmorphicDateHeader(
            row.date,
            modifier.chatMenuDimmedWhenOpen(menuOpen).padding(vertical = 10.dp),
        )
        is ChatRenderRow.Message -> {
            val selected = selectedRowId == row.id
            val menuLiftOffsetY by animateFloatAsState(
                targetValue = if (selected) messagePresentation.menuSelection?.liftOffsetY ?: 0f else 0f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                label = "chatMessageMenuLift",
            )
            Column(
                modifier
                    .graphicsLayer { translationY = menuLiftOffsetY }
                    .chatMenuDimmedUnlessSelected(selected, menuOpen),
            ) {
                // ≡ iOS: unread divider con padding h18/v6 antes del mensaje (mismo VStack).
                callbacks.onUnreadDivider(row)
                GlassmorphicChatMessageItem(
                    row.item,
                    viewModel.messages.value,
                    viewModel,
                    messagePresentation,
                    callbacks.renderer,
                    quickReactionEmoji,
                    timestampRevealState,
                )
            }
        }
        is ChatRenderRow.Buzz -> ChatBuzzTimelineEventRow(
            callbacks.buzzText(row.event),
            row.event.senderId == viewModel.currentUserId,
            modifier.chatMenuDimmedWhenOpen(menuOpen),
        )
        ChatRenderRow.Typing -> GlassmorphicTypingIndicator(
            reduceMotion = MotionPolicy.reduceMotion,
            modifier = modifier
                .chatMenuDimmedWhenOpen(menuOpen)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        )
        ChatRenderRow.HistoryStart -> ChatHistoryStartHeader(
            adaptiveColors,
            modifier.chatMenuDimmedWhenOpen(menuOpen),
        )
    }
}

@Composable
fun ChatReplyAndEditingBar(
    replyingTo: EnhancedMessage?,
    editingMessage: EnhancedMessage?,
    otherParticipantName: String,
    adaptiveColors: AdaptiveColors,
    onReplyCancelled: () -> Unit,
    onEditingCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        replyingTo?.let { GlassmorphicReplyBar(it, otherParticipantName, onReplyCancelled) }
        if (editingMessage != null) {
            // ≡ iOS ultraThinMaterial.opacity(0.5) → canvas sólido AdaptiveColors
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(adaptiveColors.primary.copy(.06f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Edit, null, tint = adaptiveColors.primary, modifier = Modifier.padding(end = 2.dp))
                Text(
                    stringResource(R.string.chat_editing_title),
                    color = adaptiveColors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.chat_editing_cancel),
                    tint = adaptiveColors.primary.copy(.6f),
                    modifier = Modifier.clickable(onClick = onEditingCancelled),
                )
            }
        }
    }
}

@Composable
fun GlassmorphicChatRootContent(
    adaptiveColors: AdaptiveColors,
    viewModel: EnhancedChatViewModel,
    messagePresentation: ChatMessagePresentationState,
    buzzToastText: String?,
    isSearchVisible: Boolean,
    composerHeight: androidx.compose.ui.unit.Dp,
    onComposerHeightChange: (androidx.compose.ui.unit.Dp) -> Unit = {},
    content: @Composable () -> Unit,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // ≡ iOS environment(\.chatFailedMessageRetryAction)
    val failedRetry = remember(viewModel) {
        ChatFailedMessageRetryAction(
            canRetry = viewModel::canRetryMessage,
            retry = viewModel::retryFailedMessage,
        )
    }
    CompositionLocalProvider(LocalChatFailedMessageRetryAction provides failedRetry) {
        Box(modifier.fillMaxSize().background(adaptiveColors.chatBackground.first())) {
            ChatGlassmorphicBackground(
                adaptiveColors,
                // La incoming bubble es translúcida: modificar el fondo bajo ella
                // también modificaría su color aparente durante el long press.
                Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize()) { content() }
            // Android: sin ChatBottomWallpaperEdgeFade (iOS lo necesita por el chrome; aquí tapa mensajes).
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .chatMenuDimmedWhenOpen(messagePresentation.menuSelection != null)
                    .onSizeChanged { size ->
                        onComposerHeightChange(with(density) { size.height.toDp() })
                    },
            ) {
                if (!isSearchVisible) composer()
            }
            buzzToastText?.let { text ->
                ChatBuzzToast(text, Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 18.dp, end = 18.dp))
            }
        }
    }
}

/** ≡ iOS `pendingChatDisclaimerKey` / disclaimer.normal cuando context == nil. */
private fun requestDisclaimerForRendering(context: com.moments.android.views.messaging.core.PendingChatContext?): Int = when (context?.status) {
    com.moments.android.views.messaging.core.PendingChatContext.Status.INCOMING_REQUEST_PENDING -> R.string.chat_request_disclaimer_incoming
    com.moments.android.views.messaging.core.PendingChatContext.Status.OUTGOING_REQUEST_SENT -> R.string.chat_request_disclaimer_sent
    com.moments.android.views.messaging.core.PendingChatContext.Status.OUTGOING_REQUEST_DRAFT,
    com.moments.android.views.messaging.core.PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED -> R.string.chat_request_disclaimer_outgoing
    com.moments.android.views.messaging.core.PendingChatContext.Status.NORMAL_CONVERSATION,
    null -> R.string.chat_intro_disclaimer_normal
}
