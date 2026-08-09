package com.moments.android.views.messaging.screens.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.OnlineStatus
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.services.firestore.checkPublicProfileAvailability
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.views.messaging.components.ChatMessageGroupPosition
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.PresenceDisplay
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Port de `GlassmorphicChatView+Lifecycle.swift`.
 *
 * View-once (mark viewed / replay store) y cámara se cablean desde el host vía
 * [ChatCameraCaptureOperations] / [ChatViewOnceSessionOperations].
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
    private val onlineStatusService: OnlineStatusService = OnlineStatusService.shared,
    private val onObserveOnlineStatus: ((String, (OnlineStatus, Date?) -> Unit) -> (() -> Unit))? = null,
    private val onStoriesDisabled: () -> Unit = {},
    private val onStoriesRefresh: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var removeStatusObserver: (() -> Unit)? = null

    var otherUserStatus by mutableStateOf<OnlineStatus?>(null)
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
    var storyRing by mutableStateOf(
        StoryRingSnapshot(
            hasStory = false,
            hasUnseenStory = false,
            storyCount = 0,
            storyViewedStatus = emptyList(),
            storyAudiences = emptyList(),
        ),
    )
        private set

    val hasStory: Boolean get() = storyRing.hasStory
    val presenceDisplay: PresenceDisplay?
        get() {
            val status = otherUserStatus ?: return null
            return onlineStatusService.presenceDisplay(status, otherUserLastSeen)
        }

    fun setupOnlineStatusObserver() {
        removeStatusObserver?.invoke()
        val otherUserId = viewModel.conversation.otherParticipantId
        if (otherUserId.isBlank()) return
        val observe = onObserveOnlineStatus ?: onlineStatusService::observeUserStatus
        removeStatusObserver = observe(otherUserId) { status, lastSeen ->
            otherUserStatus = status
            otherUserLastSeen = lastSeen
        }
    }

    /** ≡ `checkUserStories()` en ComposerAndChrome. */
    fun checkUserStories() {
        val authorId = viewModel.conversation.otherParticipantId.trim()
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val empty = StoryRingSnapshot(
            hasStory = false,
            hasUnseenStory = false,
            storyCount = 0,
            storyViewedStatus = emptyList(),
            storyAudiences = emptyList(),
        )
        if (authorId.isEmpty() || viewerId.isEmpty()) {
            storyRing = empty
            return
        }
        scope.launch {
            val snapshot = runCatching {
                StoryRingResolverService.resolve(viewerId = viewerId, authorId = authorId)
            }.getOrElse { empty }
            if (viewModel.conversation.otherParticipantId.trim() == authorId) {
                storyRing = snapshot
            }
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
        // ≡ iOS: unavailable → cierra cámara; sin conversationId → return sin cerrar
        if (isOtherParticipantUnavailable) {
            shouldShowCamera = false
            return
        }
        if (viewModel.conversation.id.isNullOrBlank()) return

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

    // ≡ iOS: delay 0.35s para no competir con el dismiss del visor view-once
    fun openCameraForReply(messageId: String) {
        pendingCameraReplyToMessageId = messageId
        scope.launch {
            kotlinx.coroutines.delay(350)
            shouldShowCamera = true
        }
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
        val uid = viewModel.currentUserId
        val live = viewModel.messagesById[message.id] ?: message
        // ≡ iOS openViewOnceMessage / openReplay guards — no reabrir si ya se consumió.
        if (!isReplaySession) {
            val alreadyViewed = live.isViewed ||
                live.hasBeenViewedBy(uid) ||
                live.replayAvailableInCurrentChatSession
            if (alreadyViewed) return
        } else {
            val canReplay = live.replayAvailableInCurrentChatSession || live.canReplayViewOnce(uid)
            if (!canReplay || live.replayConsumedInCurrentChatSession || live.hasBeenReplayedBy(uid)) return
        }
        val hasMedia = !live.mediaUrl.isNullOrBlank() || !live.mediaObjectPath.isNullOrBlank()
        if (!hasMedia) return

        viewOnceViewerPresentation = ViewOnceViewerPresentation(
            message = live,
            authorName = if (live.senderId == viewModel.currentUserId) currentUserName else otherParticipantDisplayName,
            isReplaySession = isReplaySession,
        )
    }

    fun dismissViewOnceViewer() {
        viewOnceViewerPresentation = null
    }

    fun handleViewOnceViewerViewed(presentation: ViewOnceViewerPresentation) {
        val message = viewModel.messagesById[presentation.message.id] ?: presentation.message
        val viewerId = viewModel.currentUserId
        val viewedBy = (message.viewedBy.orEmpty() + viewerId).distinct()
        // iOS muta EnhancedMessage (@ObservedObject) al instante; Compose necesita replace en el StateFlow.
        if (message.allowReplay == true && !presentation.isReplaySession) {
            viewOnceOperations.markReplayAvailable(message, viewerId)
            viewModel.appendOrReplaceMessage(
                message.copy(
                    isViewed = true,
                    viewedBy = viewedBy,
                    replayAvailableInCurrentChatSession = true,
                    replayConsumedInCurrentChatSession = false,
                ),
            )
        } else {
            viewModel.appendOrReplaceMessage(
                message.copy(isViewed = true, viewedBy = viewedBy),
            )
        }
        viewOnceOperations.markViewed(message.conversationId, message.id, viewerId)
    }

    fun handleViewOnceReplayConsumed(presentation: ViewOnceViewerPresentation) {
        val message = viewModel.messagesById[presentation.message.id] ?: presentation.message
        val viewerId = viewModel.currentUserId
        viewOnceOperations.markReplayConsumed(message, viewerId)
        viewModel.appendOrReplaceMessage(
            message.copy(
                replayAvailableInCurrentChatSession = false,
                replayConsumedInCurrentChatSession = true,
                replayedBy = (message.replayedBy.orEmpty() + viewerId).distinct(),
            ),
        )
    }

    /** Tras CF `consumeViewOnceMessage` OK — limpia media local (Firestore ya no trae paths). */
    fun handleViewOnceMediaConsumed(messageId: String) {
        val message = viewModel.messagesById[messageId] ?: return
        viewModel.appendOrReplaceMessage(
            message.copy(
                isViewed = true,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaObjectPath = null,
                thumbnailObjectPath = null,
                mediaEncryption = null,
                thumbnailEncryption = null,
                textOverlayLive = null,
                textOverlays = null,
                stickers = null,
                drawingData = null,
                replayAvailableInCurrentChatSession = false,
                replayConsumedInCurrentChatSession = message.allowReplay == true ||
                    message.replayConsumedInCurrentChatSession,
            ),
        )
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
        disableUnavailableParticipantStories()
    }

    /** ≡ iOS `disableUnavailableParticipantStories()`. */
    fun disableUnavailableParticipantStories() {
        storyRing = StoryRingSnapshot(
            hasStory = false,
            hasUnseenStory = false,
            storyCount = 0,
            storyViewedStatus = emptyList(),
            storyAudiences = emptyList(),
        )
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
                // ≡ iOS checkUserStories()
                checkUserStories()
                onStoriesRefresh()
            }
        }
    }

    fun shouldShowAvatar(message: EnhancedMessage, messages: List<EnhancedMessage> = viewModel.messages.value): Boolean {
        // ≡ iOS: usa messageIndexById + viewModel.messages (ignora el param `in` como fuente)
        val source = viewModel.messages.value
        val index = viewModel.messageIndexById[message.id] ?: return true
        if (index >= source.size) return true
        if (index == source.lastIndex) return true
        return source[index + 1].senderId != message.senderId
    }

    fun messageGroupPosition(message: EnhancedMessage, messages: List<EnhancedMessage> = viewModel.messages.value): ChatMessageGroupPosition {
        val source = viewModel.messages.value
        val index = viewModel.messageIndexById[message.id] ?: return ChatMessageGroupPosition.SINGLE
        if (index >= source.size) return ChatMessageGroupPosition.SINGLE
        val previousSameSender = index > 0 && source[index - 1].senderId == message.senderId
        val nextSameSender = index < source.lastIndex && source[index + 1].senderId == message.senderId
        return when {
            !previousSameSender && !nextSameSender -> ChatMessageGroupPosition.SINGLE
            !previousSameSender -> ChatMessageGroupPosition.FIRST
            nextSameSender -> ChatMessageGroupPosition.MIDDLE
            else -> ChatMessageGroupPosition.LAST
        }
    }
}
