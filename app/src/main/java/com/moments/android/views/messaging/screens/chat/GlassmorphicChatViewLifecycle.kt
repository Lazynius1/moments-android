package com.moments.android.views.messaging.screens.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.services.firestore.checkPublicProfileAvailability
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.views.messaging.components.ChatMessageGroupPosition
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Port de `GlassmorphicChatView+Lifecycle.swift`.
 *
 * Los envíos de view-once y el replay store viven todavía en sus fuentes Swift propias;
 * sus contratos están aquí para que el ciclo de la pantalla no pierda estados al portarlos.
 */
enum class ChatCameraCapturedMediaType { IMAGE, VIDEO }

data class ViewOnceViewerPresentation(
    val message: EnhancedMessage,
    val authorName: String,
    val isReplaySession: Boolean,
)

data class ChatViewOnceSessionOperations(
    val markReplayAvailable: (EnhancedMessage, String) -> Unit = { _, _ -> },
    val markReplayConsumed: (EnhancedMessage, String) -> Unit = { _, _ -> },
    val markViewed: (conversationId: String, messageId: String, viewerId: String) -> Unit = { _, _, _ -> },
)

data class ChatCameraCaptureOperations(
    val sendViewOnce: (data: ByteArray, mediaType: ChatCameraCapturedMediaType, allowReplay: Boolean, replyTo: String?, overlayPayload: Any?) -> Unit = { _, _, _, _, _ -> },
    val sendImage: (data: ByteArray, replyTo: String?) -> Unit = { _, _ -> },
    val sendVideo: (data: ByteArray, replyTo: String?) -> Unit = { _, _ -> },
)

@Stable
class GlassmorphicChatLifecycleController(
    private val viewModel: EnhancedChatViewModel,
    private val firestoreService: FirestoreService = FirestoreService(),
    private val cameraOperations: ChatCameraCaptureOperations = ChatCameraCaptureOperations(),
    private val viewOnceOperations: ChatViewOnceSessionOperations = ChatViewOnceSessionOperations(),
    private val onObserveOnlineStatus: (String, (String?, Date?) -> Unit) -> (() -> Unit)? = { _, _ -> null },
    private val onStoriesDisabled: () -> Unit = {},
    private val onStoriesRefresh: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var removeStatusObserver: (() -> Unit)? = null

    var otherUserStatus by mutableStateOf<String?>(null)
        private set
    var otherUserLastSeen by mutableStateOf<Date?>(null)
        private set
    var liveOtherParticipantUsername by mutableStateOf("")
        private set
    var isOtherParticipantUnavailable by mutableStateOf(false)
        private set
    var isOtherParticipantBlockedByCurrentUser by mutableStateOf(false)
        private set
    var pendingCameraReplyToMessageId by mutableStateOf<String?>(null)
        private set
    var shouldShowCamera by mutableStateOf(false)
        private set
    var viewOnceViewerPresentation by mutableStateOf<ViewOnceViewerPresentation?>(null)
        private set

    fun setupOnlineStatusObserver() {
        removeStatusObserver?.invoke()
        val otherUserId = viewModel.conversation.otherParticipantId
        if (otherUserId.isBlank()) return
        removeStatusObserver = onObserveOnlineStatus(otherUserId) { status, lastSeen ->
            otherUserStatus = status
            otherUserLastSeen = lastSeen
        }
    }

    fun dispose() {
        removeStatusObserver?.invoke()
        removeStatusObserver = null
    }

    fun handleCameraCapture(
        data: ByteArray,
        mediaType: ChatCameraCapturedMediaType,
        mode: com.moments.android.views.messaging.media.ChatMediaSendMode,
        overlayPayload: Any? = null,
    ) {
        if (isOtherParticipantUnavailable || viewModel.conversation.id.isNullOrBlank()) {
            shouldShowCamera = false
            return
        }
        val replyTo = pendingCameraReplyToMessageId
        pendingCameraReplyToMessageId = null
        when (mode) {
            com.moments.android.views.messaging.media.ChatMediaSendMode.VIEW_ONCE -> cameraOperations.sendViewOnce(data, mediaType, false, replyTo, overlayPayload)
            com.moments.android.views.messaging.media.ChatMediaSendMode.ALLOW_REPLAY -> cameraOperations.sendViewOnce(data, mediaType, true, replyTo, overlayPayload)
            com.moments.android.views.messaging.media.ChatMediaSendMode.KEEP_IN_CHAT -> when (mediaType) {
                ChatCameraCapturedMediaType.IMAGE -> cameraOperations.sendImage(data, replyTo)
                ChatCameraCapturedMediaType.VIDEO -> cameraOperations.sendVideo(data, replyTo)
            }
        }
        shouldShowCamera = false
    }

    fun openCameraForReply(messageId: String) {
        pendingCameraReplyToMessageId = messageId
        shouldShowCamera = true
    }

    fun openCamera() {
        pendingCameraReplyToMessageId = null
        shouldShowCamera = true
    }

    fun dismissCamera() {
        pendingCameraReplyToMessageId = null
        shouldShowCamera = false
    }

    fun presentViewOnceViewer(message: EnhancedMessage, isReplaySession: Boolean, otherParticipantDisplayName: String, currentUserName: String) {
        viewOnceViewerPresentation = ViewOnceViewerPresentation(
            message = message,
            authorName = if (message.senderId == viewModel.currentUserId) currentUserName else otherParticipantDisplayName,
            isReplaySession = isReplaySession,
        )
    }

    fun dismissViewOnceViewer() {
        viewOnceViewerPresentation = null
    }

    fun handleViewOnceViewerViewed(presentation: ViewOnceViewerPresentation) {
        val message = presentation.message
        val viewerId = viewModel.currentUserId
        if (message.allowReplay == true && !presentation.isReplaySession) {
            viewOnceOperations.markReplayAvailable(message, viewerId)
        }
        viewOnceOperations.markViewed(message.conversationId, message.id, viewerId)
    }

    fun handleViewOnceReplayConsumed(presentation: ViewOnceViewerPresentation) {
        viewOnceOperations.markReplayConsumed(presentation.message, viewModel.currentUserId)
    }

    fun refreshOtherParticipantUsername() {
        val userId = viewModel.conversation.otherParticipantId.trim()
        if (userId.isEmpty()) {
            liveOtherParticipantUsername = ""
            return
        }
        UserCacheService.refreshUser(userId) { user ->
            if (viewModel.conversation.otherParticipantId.trim() == userId) {
                liveOtherParticipantUsername = user?.username?.trim().orEmpty()
            }
        }
    }

    fun refreshOtherParticipantAvailability() {
        val userId = viewModel.conversation.otherParticipantId.trim()
        if (userId.isEmpty() || !NetworkMonitor.isConnected) return
        scope.launch {
            val availability = firestoreService.checkPublicProfileAvailability(userId)
            if (viewModel.conversation.otherParticipantId.trim() != userId) return@launch
            if (availability == PublicProfileAvailability.UNAVAILABLE) markOtherParticipantUnavailable(clearLiveUsername = true)
            else refreshOtherParticipantBlockAvailability(userId)
        }
    }

    fun refreshOtherParticipantBlockAvailability(userId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            val result = firestoreService.checkIfBlocked(currentUserId, userId)
            if (viewModel.conversation.otherParticipantId.trim() != userId) return@launch
            if (result.isBlockedByCurrentUser || result.isCurrentUserBlocked) {
                isOtherParticipantBlockedByCurrentUser = result.isBlockedByCurrentUser
                markOtherParticipantUnavailable(clearLiveUsername = false)
            } else {
                isOtherParticipantBlockedByCurrentUser = false
                isOtherParticipantUnavailable = false
                refreshOtherParticipantUsername()
            }
        }
    }

    fun markOtherParticipantUnavailable(clearLiveUsername: Boolean) {
        isOtherParticipantUnavailable = true
        if (clearLiveUsername) {
            liveOtherParticipantUsername = ""
            isOtherParticipantBlockedByCurrentUser = false
        }
        onStoriesDisabled()
    }

    fun unblockOtherParticipantFromChat() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val otherUserId = viewModel.conversation.otherParticipantId.trim()
        if (otherUserId.isEmpty()) return
        scope.launch {
            runCatching { firestoreService.unblockUser(currentUserId, otherUserId) }.onSuccess {
                isOtherParticipantBlockedByCurrentUser = false
                isOtherParticipantUnavailable = false
                refreshOtherParticipantUsername()
                onStoriesRefresh()
            }
        }
    }

    fun shouldShowAvatar(message: EnhancedMessage, messages: List<EnhancedMessage> = viewModel.messages.value): Boolean {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0 || index == messages.lastIndex) return true
        return messages[index + 1].senderId != message.senderId
    }

    fun messageGroupPosition(message: EnhancedMessage, messages: List<EnhancedMessage> = viewModel.messages.value): ChatMessageGroupPosition {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) return ChatMessageGroupPosition.SINGLE
        val previousSameSender = index > 0 && messages[index - 1].senderId == message.senderId
        val nextSameSender = index < messages.lastIndex && messages[index + 1].senderId == message.senderId
        return when {
            !previousSameSender && !nextSameSender -> ChatMessageGroupPosition.SINGLE
            !previousSameSender -> ChatMessageGroupPosition.FIRST
            nextSameSender -> ChatMessageGroupPosition.MIDDLE
            else -> ChatMessageGroupPosition.LAST
        }
    }
}
