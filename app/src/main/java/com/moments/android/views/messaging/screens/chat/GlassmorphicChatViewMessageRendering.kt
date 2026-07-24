package com.moments.android.views.messaging.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.components.ChatBottomWallpaperEdgeFade
import com.moments.android.views.messaging.components.ChatBuzzTimelineEventRow
import com.moments.android.views.messaging.components.ChatBuzzToast
import com.moments.android.views.messaging.components.ChatConversationIntroRow
import com.moments.android.views.messaging.components.ChatHistoryStartHeader
import com.moments.android.views.messaging.components.ChatMessageContextMenuOverlay
import com.moments.android.views.messaging.components.ChatMessageMenuCallbacks
import com.moments.android.views.messaging.components.ChatRequestDisclaimerRow
import com.moments.android.views.messaging.components.GlassmorphicReplyBar
import com.moments.android.views.messaging.components.GlassmorphicTypingIndicator
import com.moments.android.views.messaging.components.ChatGlassmorphicBackground
import com.moments.android.views.messaging.components.PendingRequestMessageRow
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.EnhancedChatViewModel

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
)

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
    modifier: Modifier = Modifier,
) {
    val menuOpen = messagePresentation.menuSelection != null
    val dim = if (menuOpen && row !is ChatRenderRow.Message) .42f else 1f
    when (row) {
        is ChatRenderRow.ConversationIntro -> ChatConversationIntroRow(row.context, otherParticipantName, otherParticipantId, adaptiveColors, modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        is ChatRenderRow.RequestDisclaimer -> ChatRequestDisclaimerRow(requestDisclaimerForRendering(row.context), adaptiveColors, modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        is ChatRenderRow.PendingRequestMessage -> PendingRequestMessageRow(row.message, adaptiveColors, modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        is ChatRenderRow.Header -> GlassmorphicDateHeader(row.date, modifier.padding(vertical = 10.dp))
        is ChatRenderRow.Message -> Column(modifier) {
            callbacks.onUnreadDivider(row)
            GlassmorphicChatMessageItem(row.item, viewModel.messages.value, viewModel, messagePresentation, callbacks.renderer, quickReactionEmoji)
        }
        is ChatRenderRow.Buzz -> ChatBuzzTimelineEventRow(callbacks.buzzText(row.event), row.event.senderId == viewModel.currentUserId, modifier)
        ChatRenderRow.Typing -> GlassmorphicTypingIndicator(reduceMotion = false, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp))
        ChatRenderRow.HistoryStart -> ChatHistoryStartHeader(adaptiveColors, modifier)
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
            Row(
                Modifier.fillMaxWidth().background(adaptiveColors.primary.copy(.06f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Edit, null, tint = adaptiveColors.primary, modifier = Modifier.padding(end = 2.dp))
                Text(stringResource(R.string.chat_editing_title), color = adaptiveColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Close, stringResource(R.string.chat_editing_cancel), tint = adaptiveColors.primary.copy(.6f), modifier = Modifier.clickable(onClick = onEditingCancelled))
            }
        }
    }
}

@Composable
fun GlassmorphicDateHeader(date: java.util.Date, modifier: Modifier = Modifier) {
    val text = remember(date) { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(date) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(.1f)).padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
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
    content: @Composable () -> Unit,
    composer: @Composable () -> Unit,
    callbacks: ChatMessageRenderingCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(adaptiveColors.chatBackground.first())) {
        ChatGlassmorphicBackground(adaptiveColors, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize()) { content() }
        ChatBottomWallpaperEdgeFade(adaptiveColors.chatBackground.first(), composerHeight, modifier = Modifier.align(Alignment.BottomCenter))
        Box(Modifier.align(Alignment.BottomCenter)) {
            if (!isSearchVisible) composer()
        }
        buzzToastText?.let { text ->
            ChatBuzzToast(text, Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 18.dp, end = 18.dp))
        }
        ChatMessageContextMenuOverlay(
            selection = messagePresentation.menuSelection,
            currentUserId = viewModel.currentUserId,
            callbacks = ChatMessageMenuCallbacks(
                onDeleteForEveryone = viewModel::deleteMessageForEveryone,
                onDeleteForMe = viewModel::deleteMessageForMe,
                onEdit = callbacks.onEditingStarted,
                onReply = callbacks.renderer.onReply,
                onCopy = callbacks.onCopy,
                onForward = callbacks.onForward,
                onToggleStar = viewModel::toggleStar,
                onReaction = { message, emoji -> viewModel.addReaction(message, emoji) },
                onMoreReactions = callbacks.onMoreReactions,
            ),
            onDismiss = messagePresentation::clearMessageOptions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun requestDisclaimerForRendering(context: com.moments.android.models.PendingChatContext?): Int = when (context?.status) {
    com.moments.android.models.PendingChatContext.Status.INCOMING_REQUEST_PENDING -> R.string.chat_request_disclaimer_incoming
    com.moments.android.models.PendingChatContext.Status.OUTGOING_REQUEST_SENT -> R.string.chat_request_disclaimer_sent
    com.moments.android.models.PendingChatContext.Status.OUTGOING_REQUEST_DRAFT,
    com.moments.android.models.PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED -> R.string.chat_request_disclaimer_outgoing
    com.moments.android.models.PendingChatContext.Status.NORMAL_CONVERSATION,
    null -> R.string.chat_intro_disclaimer_normal
}
