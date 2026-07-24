@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moments.android.views.messaging.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AcceptMessageRequestResult
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.models.PendingChatContext
import com.moments.android.models.PendingChatTimelineMessage
import androidx.compose.foundation.gestures.detectTapGestures
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.ChatMessageBubbleCallbacks
import com.moments.android.views.messaging.components.ChatMessageMenuSelection
import com.moments.android.views.messaging.components.ChatNoticeTimelineRow
import com.moments.android.views.messaging.components.ChatRequestInviteNotice
import com.moments.android.views.messaging.components.GlassmorphicClusterRow
import com.moments.android.views.messaging.components.GlassmorphicInputBar
import com.moments.android.views.messaging.components.GlassmorphicMessageRow
import com.moments.android.views.messaging.components.ChatBubbleAnchorMetrics
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.MessageItem
import com.moments.android.views.messaging.services.ChatSessionEngine
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `GlassmorphicChatView+ComposerAndChrome.swift`.
 *
 * La pantalla raíz todavía se porta en su propio archivo Swift. Por eso los efectos que
 * pertenecen a navegación, requests y hojas se exponen como contratos: este chrome conserva
 * exactamente las transiciones de estado sin inventar una segunda pantalla de chat.
 */
data class SharedMedia(
    val id: String,
    val type: MediaType,
    val thumbnailUrl: String,
    val originalUrl: String,
    val senderId: String,
    val timestamp: Date,
    val sourceMessage: EnhancedMessage? = null,
    val allowsSaving: Boolean = true,
) {
    enum class MediaType { IMAGE, VIDEO }
}

fun sharedMediaFrom(message: EnhancedMessage): SharedMedia? {
    val mediaUrl = message.mediaUrl ?: return null
    if (message.type !in setOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.EPHEMERAL)) return null
    return SharedMedia(
        id = message.id,
        type = if (message.type == MessageType.VIDEO) SharedMedia.MediaType.VIDEO else SharedMedia.MediaType.IMAGE,
        thumbnailUrl = message.thumbnailUrl ?: mediaUrl,
        originalUrl = mediaUrl,
        senderId = message.senderId,
        timestamp = message.timestamp,
        sourceMessage = message,
        allowsSaving = message.type != MessageType.EPHEMERAL && !message.isVanishModeMessage,
    )
}

fun sharedMediaItemsForOverlay(messages: List<EnhancedMessage>, selecting: EnhancedMessage): List<SharedMedia> {
    val selected = sharedMediaFrom(selecting) ?: return messages.mapNotNull(::sharedMediaFrom)
    if (selecting.type == MessageType.EPHEMERAL) return listOf(selected)
    val items = messages.mapNotNull(::sharedMediaFrom)
    return if (items.any { it.id == selected.id }) items else items + selected
}

data class PendingMessageRequestOperations(
    val isLoading: Boolean = false,
    val send: (receiverId: String, text: String, completion: (Result<Unit>) -> Unit) -> Unit = { _, _, completion -> completion(Result.failure(UnsupportedOperationException())) },
    val accept: (requestId: String, completion: (Result<AcceptMessageRequestResult>) -> Unit) -> Unit = { _, completion -> completion(Result.failure(UnsupportedOperationException())) },
    val cancel: (requestId: String, completion: (Result<Unit>) -> Unit) -> Unit = { _, completion -> completion(Result.failure(UnsupportedOperationException())) },
    val reject: (requestId: String, completion: (Result<Unit>) -> Unit) -> Unit = { _, completion -> completion(Result.failure(UnsupportedOperationException())) },
    val block: (requestId: String, completion: (Result<Unit>) -> Unit) -> Unit = { _, completion -> completion(Result.failure(UnsupportedOperationException())) },
)

@Stable
class ChatComposerAndChromeController(
    pendingChatContext: PendingChatContext? = null,
    private val requestOperations: PendingMessageRequestOperations = PendingMessageRequestOperations(),
    private val onDraftCleared: () -> Unit = {},
    private val onAccepted: (String) -> Unit = {},
    private val onDismissed: () -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) {
    var pendingChatContext by mutableStateOf(pendingChatContext)
        private set

    val isPendingChat: Boolean get() = pendingChatContext != null
    val pendingChatCanType: Boolean get() = pendingChatContext?.status == PendingChatContext.Status.OUTGOING_REQUEST_DRAFT
    val isRequestLoading: Boolean get() = requestOperations.isLoading
    val pendingChatTimelineMessage: PendingChatTimelineMessage?
        get() {
            val context = pendingChatContext ?: return null
            context.request?.let { return PendingChatTimelineMessage.from(it, it.senderId) }
            val text = context.initialText?.trim().orEmpty()
            return text.takeIf { it.isNotEmpty() }?.let { PendingChatTimelineMessage.outgoingText(it, context.otherUserId) }
        }

    fun updatePendingContext(context: PendingChatContext?) {
        pendingChatContext = context
    }

    fun sendPendingMessageRequest(text: String, onTextChanged: (String) -> Unit) {
        val context = pendingChatContext ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || context.status != PendingChatContext.Status.OUTGOING_REQUEST_DRAFT) return
        requestOperations.send(context.otherUserId, trimmed) { result ->
            result.onSuccess {
                pendingChatContext = context.copy(status = PendingChatContext.Status.OUTGOING_REQUEST_SENT, initialText = trimmed)
                onTextChanged("")
                onDraftCleared()
            }.onFailure { error ->
                // iOS treats the duplicate-request response as an already sent request.
                if (error.message?.contains("409") == true) {
                    pendingChatContext = context.copy(status = PendingChatContext.Status.OUTGOING_REQUEST_SENT, initialText = null)
                    onTextChanged("")
                } else onError(error)
            }
        }
    }

    fun acceptPendingMessageRequest(replyText: String?, onTextChanged: (String) -> Unit, onReplyAfterAcceptance: (String, String) -> Unit) {
        val context = pendingChatContext ?: return
        val requestId = context.request?.id ?: return
        requestOperations.accept(requestId) { result ->
            result.onSuccess { accepted ->
                replyText?.trim()?.takeIf { it.isNotEmpty() }?.let { onReplyAfterAcceptance(accepted.conversationId, it) }
                onTextChanged("")
                onDraftCleared()
                pendingChatContext = null
                onAccepted(accepted.conversationId)
            }.onFailure(onError)
        }
    }

    fun cancelPendingMessageRequest(currentText: String, onTextChanged: (String) -> Unit) {
        val context = pendingChatContext ?: return
        if (context.direction != PendingChatContext.Direction.OUTGOING) return
        val requestId = context.request?.id
        if (requestId == null) {
            pendingChatContext = context.resetToDraft()
            return
        }
        requestOperations.cancel(requestId) { result ->
            result.onSuccess {
                pendingChatContext = context.resetToDraft()
                if (currentText.isEmpty()) onTextChanged(context.initialText.orEmpty())
            }.onFailure(onError)
        }
    }

    fun deletePendingMessageRequest() {
        val requestId = pendingChatContext?.request?.id
        if (requestId == null) {
            onDismissed()
            return
        }
        requestOperations.reject(requestId) { result -> result.onSuccess { onDismissed() }.onFailure(onError) }
    }

    fun blockPendingMessageRequest() {
        val requestId = pendingChatContext?.request?.id ?: return
        requestOperations.block(requestId) { result -> result.onSuccess { onDismissed() }.onFailure(onError) }
    }
}

@Composable
fun rememberChatComposerAndChromeController(
    pendingChatContext: PendingChatContext?,
    requestOperations: PendingMessageRequestOperations = PendingMessageRequestOperations(),
    onDraftCleared: () -> Unit = {},
    onAccepted: (String) -> Unit = {},
    onDismissed: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
): ChatComposerAndChromeController = remember(requestOperations, onDraftCleared, onAccepted, onDismissed, onError) {
    ChatComposerAndChromeController(pendingChatContext, requestOperations, onDraftCleared, onAccepted, onDismissed, onError)
}.also { it.updatePendingContext(pendingChatContext) }

@Composable
fun ChatComposerChrome(
    controller: ChatComposerAndChromeController,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    isOtherParticipantBlockedByCurrentUser: Boolean,
    isOtherParticipantUnavailable: Boolean,
    otherParticipantDisplayName: String,
    vanishModeActive: Boolean,
    isRecordingVoice: Boolean,
    isVoiceRecordingLocked: Boolean,
    recordingSeconds: Long,
    editingMessage: EnhancedMessage? = null,
    replyingTo: EnhancedMessage? = null,
    onEditingFinished: () -> Unit = {},
    onReplyingFinished: () -> Unit = {},
    onUnblock: () -> Unit = {},
    onOpenAttachments: () -> Unit = {},
    onStartVoiceRecording: () -> Unit = {},
    onFinishVoiceRecording: (Boolean) -> Unit = {},
    onReport: () -> Unit = {},
    onReplyAfterAcceptance: (String, String) -> Unit = { _, _ -> },
    viewModel: EnhancedChatViewModel,
    modifier: Modifier = Modifier,
) {
    val context = controller.pendingChatContext
    when {
        isOtherParticipantBlockedByCurrentUser -> BlockedByMeChatInputBar(onUnblock, modifier)
        isOtherParticipantUnavailable -> UnavailableChatInputBar(modifier)
        context?.status == PendingChatContext.Status.OUTGOING_REQUEST_SENT -> PendingRequestSentInputBar(
            onCancel = { controller.cancelPendingMessageRequest(messageText, onMessageTextChange) },
            modifier = modifier,
        )
        context?.status == PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED -> RequestsClosedInputBar(
            displayName = context.otherUsername.ifBlank { otherParticipantDisplayName },
            modifier = modifier,
        )
        else -> Column(modifier) {
            if (context?.status == PendingChatContext.Status.INCOMING_REQUEST_PENDING) {
                IncomingRequestActionBar(
                    isLoading = controller.isRequestLoading,
                    onAccept = { controller.acceptPendingMessageRequest(null, onMessageTextChange, onReplyAfterAcceptance) },
                    onDelete = controller::deletePendingMessageRequest,
                    onBlock = controller::blockPendingMessageRequest,
                    onReport = onReport,
                )
            }
            if (controller.pendingChatCanType && context != null) {
                ChatRequestInviteNotice(context.otherUsername, context.otherUsername, com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme()))
            }
            GlassmorphicInputBar(
                text = messageText,
                onTextChange = onMessageTextChange,
                isRecordingVoice = isRecordingVoice,
                isVoiceRecordingLocked = isVoiceRecordingLocked,
                recordingSeconds = recordingSeconds,
                isVanishModeActive = vanishModeActive,
                allowsAttachments = !controller.isPendingChat,
                onSend = {
                    val outgoing = messageText.trim()
                    if (outgoing.isEmpty()) return@GlassmorphicInputBar
                    when {
                        controller.pendingChatCanType -> controller.sendPendingMessageRequest(outgoing, onMessageTextChange)
                        context?.status == PendingChatContext.Status.INCOMING_REQUEST_PENDING -> controller.acceptPendingMessageRequest(outgoing, onMessageTextChange, onReplyAfterAcceptance)
                        editingMessage != null -> {
                            viewModel.editMessage(editingMessage, outgoing)
                            onEditingFinished()
                            onMessageTextChange("")
                        }
                        else -> {
                            viewModel.sendTextMessage(outgoing, replyingTo?.id)
                            onReplyingFinished()
                            onMessageTextChange("")
                        }
                    }
                },
                onOpenAttachments = onOpenAttachments,
                onStartVoiceRecording = onStartVoiceRecording,
                onFinishVoiceRecording = onFinishVoiceRecording,
            )
        }
    }
}

@Composable
fun RequestsClosedInputBar(displayName: String, modifier: Modifier = Modifier) = ChatComposerInfoPill(
    icon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
    text = stringResource(R.string.chat_request_closed_input, displayName),
    modifier = modifier,
)

@Composable
fun PendingRequestSentInputBar(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clip(RoundedCornerShape(28.dp)).momentsChromeGlass(RoundedCornerShape(28.dp), interactive = false).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Send, null, tint = if (dark) Color.White.copy(.7f) else Color.Black.copy(.58f), modifier = Modifier.size(17.dp))
        Text(stringResource(R.string.chat_request_sent_input), modifier = Modifier.weight(1f), color = if (dark) Color.White.copy(.7f) else Color.Black.copy(.58f), fontSize = 14.sp, maxLines = 2)
        Text(
            stringResource(R.string.chat_request_sent_cancel),
            modifier = Modifier.clip(CircleShape).momentsChromeGlass(CircleShape, interactive = true).combinedClickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (dark) Color.White else Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun IncomingRequestActionBar(isLoading: Boolean, onAccept: () -> Unit, onDelete: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Column(modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(if (dark) Color.White else Color.Black).combinedClickable(enabled = !isLoading, onClick = onAccept).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = if (dark) Color.Black else Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Default.CheckCircle, null, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.message_requests_accept), color = if (dark) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChatRequestSecondaryButton(R.string.message_requests_delete, onDelete, Modifier.weight(1f), destructive = true, enabled = !isLoading)
            ChatRequestSecondaryButton(R.string.message_requests_block_user, onBlock, Modifier.weight(1f), destructive = true, enabled = !isLoading)
            ChatRequestSecondaryButton(R.string.chat_request_report, onReport, Modifier.weight(1f), enabled = !isLoading)
        }
    }
}

@Composable
private fun ChatRequestSecondaryButton(textRes: Int, onClick: () -> Unit, modifier: Modifier, destructive: Boolean = false, enabled: Boolean = true) {
    val dark = isSystemInDarkTheme()
    Text(
        stringResource(textRes),
        modifier = modifier.clip(CircleShape).momentsChromeGlass(CircleShape, interactive = enabled).combinedClickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp),
        color = if (destructive) Color.Red else if (dark) Color.White else Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun UnavailableChatInputBar(modifier: Modifier = Modifier) = ChatComposerInfoPill(
    icon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(15.dp)) },
    text = stringResource(R.string.chat_input_unavailable),
    modifier = modifier,
)

@Composable
fun BlockedByMeChatInputBar(onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clip(RoundedCornerShape(28.dp)).momentsChromeGlass(RoundedCornerShape(28.dp), interactive = false).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.chat_blocked_by_me_input), modifier = Modifier.weight(1f), color = if (dark) Color.White.copy(.66f) else Color.Black.copy(.58f), fontSize = 13.sp, maxLines = 2)
        Text(stringResource(R.string.chat_blocked_by_me_unblock), modifier = Modifier.clip(CircleShape).momentsChromeGlass(CircleShape, interactive = true).combinedClickable(onClick = onUnblock).padding(horizontal = 12.dp, vertical = 8.dp), color = if (dark) Color.White else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChatComposerInfoPill(icon: @Composable () -> Unit, text: String, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clip(RoundedCornerShape(28.dp)).momentsChromeGlass(RoundedCornerShape(28.dp), interactive = false).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
        Text(text, color = if (dark) Color.White.copy(.62f) else Color.Black.copy(.54f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

data class ChatMessageRendererCallbacks(
    val otherParticipantName: String,
    val otherParticipantId: String? = null,
    val isOtherParticipantUnavailable: Boolean = false,
    val shouldShowAvatar: (EnhancedMessage, List<EnhancedMessage>) -> Boolean = { _, _ -> false },
    val groupPosition: (EnhancedMessage, List<EnhancedMessage>) -> com.moments.android.views.messaging.components.ChatMessageGroupPosition = { _, _ -> com.moments.android.views.messaging.components.ChatMessageGroupPosition.SINGLE },
    val onReply: (EnhancedMessage) -> Unit = {},
    val onAvatarTap: () -> Unit = {},
    val onReplyTap: (String) -> Unit = {},
    val onOpenMedia: (EnhancedMessage) -> Unit = {},
    val onOpenCluster: (List<EnhancedMessage>) -> Unit = {},
    val onMomentNavigation: (EnhancedMessage) -> Unit = {},
    val onStoryNavigation: (EnhancedMessage) -> Unit = {},
    val onViewOnceOpen: (EnhancedMessage, Boolean) -> Unit = { _, _ -> },
    val onHydrateMedia: (EnhancedMessage) -> Unit = {},
    val onStopLiveLocation: (String) -> Unit = {},
    val onLongPress: (EnhancedMessage, String, List<EnhancedMessage>?) -> Unit = { _, _, _ -> },
    val onChangeVanishTimer: () -> Unit = {},
    val onTurnOnVanish: () -> Unit = {},
)

@Stable
class ChatMessagePresentationState {
    var menuSelection by mutableStateOf<ChatMessageMenuSelection?>(null)
    var flashingMessageIds by mutableStateOf(emptySet<String>())

    fun presentMessageOptions(message: EnhancedMessage, rowId: String, currentUserId: String, cluster: List<EnhancedMessage>? = null) {
        menuSelection = ChatMessageMenuSelection(rowId, message, isOutgoing = message.senderId == currentUserId, clusterMessages = cluster)
    }

    fun clearMessageOptions() { menuSelection = null }
    fun isBubbleFlashing(messageId: String): Boolean = messageId in flashingMessageIds
    fun beginBubbleFlash(messageId: String) { flashingMessageIds = flashingMessageIds + messageId }
    fun endBubbleFlash(messageId: String) { flashingMessageIds = flashingMessageIds - messageId }
    fun isMessageItemHighlighted(item: MessageItem): Boolean = when (item) {
        is MessageItem.Single -> isBubbleFlashing(item.message.id)
        is MessageItem.MediaCluster -> item.messages.any { isBubbleFlashing(it.id) }
    }
}

@Composable
fun rememberChatMessagePresentationState() = remember { ChatMessagePresentationState() }

fun lastOutgoingMessageId(messages: List<EnhancedMessage>, currentUserId: String): String? = messages.lastOrNull { it.senderId == currentUserId }?.id
fun shouldShowSeenLabel(messageId: String, status: MessageStatus, messages: List<EnhancedMessage>, currentUserId: String): Boolean = status == MessageStatus.READ && messageId == lastOutgoingMessageId(messages, currentUserId)
fun reactionToken(messageId: String, viewModel: EnhancedChatViewModel): String = viewModel.displayReactions(messageId)?.takeIf { it.isNotEmpty() }?.entries?.map { "${it.key}:${it.value.size}" }?.sorted()?.joinToString(",").orEmpty()
fun reactionIdentitySuffix(item: MessageItem, viewModel: EnhancedChatViewModel): String = when (item) {
    is MessageItem.Single -> reactionToken(item.message.id, viewModel)
    is MessageItem.MediaCluster -> item.messages.joinToString("|") { reactionToken(it.id, viewModel) }
}

@Composable
fun GlassmorphicChatMessageItem(
    item: MessageItem,
    messages: List<EnhancedMessage>,
    viewModel: EnhancedChatViewModel,
    presentationState: ChatMessagePresentationState,
    callbacks: ChatMessageRendererCallbacks,
    quickReactionEmoji: String,
    modifier: Modifier = Modifier,
) {
    val rowId = "row:message:${item.id}"
    val menuSelected = presentationState.menuSelection?.rowId == rowId
    val scope = rememberCoroutineScope()
    fun pulse(messageId: String) {
        presentationState.beginBubbleFlash(messageId)
        scope.launch {
            delay(ChatBubbleAnchorMetrics.highlightDurationMillis)
            presentationState.endBubbleFlash(messageId)
        }
    }
    val live = { message: EnhancedMessage -> viewModel.messagesById[message.id] ?: message }
    when (item) {
        is MessageItem.Single -> {
            val message = live(item.message)
            if (message.type == MessageType.CHAT_NOTICE) {
                ChatNoticeTimelineRow(message.content.orEmpty(), message.senderId, viewModel.currentUserId, callbacks.otherParticipantName, callbacks.onChangeVanishTimer, callbacks.onTurnOnVanish, modifier)
            } else {
                GlassmorphicMessageRow(
                    message = message,
                    displayReactions = if (menuSelected) null else viewModel.displayReactions(message.id),
                    isCurrentUser = message.senderId == viewModel.currentUserId,
                    showAvatar = callbacks.shouldShowAvatar(message, messages),
                    groupPosition = callbacks.groupPosition(message, messages),
                    otherUserId = callbacks.otherParticipantId,
                    isOtherParticipantUnavailable = callbacks.isOtherParticipantUnavailable,
                    otherParticipantName = callbacks.otherParticipantName,
                    repliedMessage = message.replyTo?.let(viewModel.messagesById::get),
                    isMenuSelected = menuSelected,
                    isBubbleFlashing = presentationState.isBubbleFlashing(message.id),
                    progress = viewModel.uploadProgress.value[message.id],
                    downloadProgress = viewModel.downloadProgress.value[message.id],
                    isDownloadingMedia = viewModel.isDownloadingMedia(message.id),
                    showSeenLabel = shouldShowSeenLabel(message.id, message.status, messages, viewModel.currentUserId),
                    callbacks = ChatMessageBubbleCallbacks(
                        onReply = { callbacks.onReply(message) },
                        onReaction = { emoji -> viewModel.addReaction(message, emoji); pulse(message.id) },
                        onAvatarTap = callbacks.onAvatarTap,
                        onReplyTap = callbacks.onReplyTap,
                        onMomentNavigation = callbacks.onMomentNavigation,
                        onStoryNavigation = callbacks.onStoryNavigation,
                        onOpenMedia = callbacks.onOpenMedia,
                        onStopLiveLocation = callbacks.onStopLiveLocation,
                        onHydrateMedia = callbacks.onHydrateMedia,
                        onMessageViewed = { messageId ->
                            viewModel.messagesById[messageId]?.let(viewModel::markEphemeralAsViewed)
                        },
                        onLongPress = {
                            presentationState.presentMessageOptions(message, rowId, viewModel.currentUserId)
                            callbacks.onLongPress(message, rowId, null)
                        },
                        onViewOnceOpen = callbacks.onViewOnceOpen,
                    ),
                    modifier = modifier.pointerInput(message.id) {
                        detectTapGestures(onDoubleTap = {
                            viewModel.addReaction(message, quickReactionEmoji)
                            pulse(message.id)
                            HapticManager.shared.lightImpact()
                        })
                    },
                )
            }
        }
        is MessageItem.MediaCluster -> {
            val cluster = item.messages.map(live)
            GlassmorphicClusterRow(
                messages = cluster,
                isCurrentUser = cluster.firstOrNull()?.senderId == viewModel.currentUserId,
                uploadProgress = viewModel.uploadProgress.value,
                onOpenCluster = callbacks.onOpenCluster,
                onHydrateMedia = callbacks.onHydrateMedia,
                modifier = modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        cluster.firstOrNull()?.let {
                            presentationState.presentMessageOptions(it, rowId, viewModel.currentUserId, cluster.takeIf { messages -> messages.size > 1 })
                            callbacks.onLongPress(it, rowId, cluster.takeIf { messages -> messages.size > 1 })
                        }
                    },
                ).pointerInput(item.id) {
                    detectTapGestures(onDoubleTap = {
                        cluster.firstOrNull()?.let { message -> viewModel.addReaction(message, quickReactionEmoji); pulse(message.id); HapticManager.shared.lightImpact() }
                    })
                },
            )
        }
    }
}

fun chatMediaLayoutSignature(messages: List<EnhancedMessage>): String = messages.takeLast(6).joinToString(";") { "${it.id}|${it.mediaUrl.orEmpty()}|${it.thumbnailUrl.orEmpty()}|${it.type.name}" }

data class ChatPendingReplay(val conversationId: String, val messageId: String)

@Stable
class ChatComposerChromeLifecycle(
    private val viewModel: EnhancedChatViewModel,
    private val onReloadNotificationIntent: () -> Unit = {},
    private val onReconcileScroll: () -> Unit = {},
    private val onConsumeDeferredJump: () -> Unit = {},
    private val onConfigureInitialScroll: () -> Unit = {},
    private val onSyncPendingIncoming: () -> Unit = {},
    private val onSetupOnlineStatus: () -> Unit = {},
    private val onRefreshOtherParticipant: () -> Unit = {},
    private val onCheckStories: () -> Unit = {},
    private val onInstallScreenshotObserver: () -> Unit = {},
    private val onRemoveScreenshotObserver: () -> Unit = {},
    private val onCleanupConsumedViewOnce: (String) -> Unit = {},
    private val pendingReplays: (String) -> List<ChatPendingReplay> = { emptyList() },
    private val onConsumeAbandonedReplay: (ChatPendingReplay) -> Unit = {},
) {
    var didHydrateScrollStateOnce by mutableStateOf(false)
        private set
    var hasCompletedInitialScroll by mutableStateOf(false)
    var isPinnedToBottom by mutableStateOf(true)
    var listIsAtBottom by mutableStateOf(true)

    fun restoreScrollUIState() {
        if (didHydrateScrollStateOnce) return
        didHydrateScrollStateOnce = true
        hasCompletedInitialScroll = false
        isPinnedToBottom = true
        listIsAtBottom = true
    }

    fun onAppear(conversationId: String) {
        restoreScrollUIState()
        onReloadNotificationIntent()
        onReconcileScroll()
        onConsumeDeferredJump()
        if (conversationId.isNotBlank()) {
            ChatSessionEngine.activate(conversationId)
            onCleanupConsumedViewOnce(conversationId)
        }
        onConfigureInitialScroll()
        onSyncPendingIncoming()
        onSetupOnlineStatus()
        onRefreshOtherParticipant()
        onCheckStories()
        onInstallScreenshotObserver()
    }

    fun onDisappear(conversationId: String, preservingChat: Boolean) {
        if (preservingChat) return
        viewModel.markVisibleConversationAsRead()
        didHydrateScrollStateOnce = false
        hasCompletedInitialScroll = false
        if (conversationId.isNotBlank()) {
            pendingReplays(conversationId).forEach(onConsumeAbandonedReplay)
            ChatSessionEngine.deactivate(conversationId)
        }
        viewModel.handleChatDismissedForVanishMode()
        onRemoveScreenshotObserver()
    }
}
