package com.moments.android.views.messaging.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.reportes.ReportBottomSheet
import com.moments.android.reportes.ReportTarget
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.sharing.SharedStoryAccessDenialReason
import com.moments.android.views.messaging.components.chatBuzzShakeEffect
import com.moments.android.views.shared.momentdetail.MomentDetailContainerView
import com.moments.android.views.shared.momentdetail.MomentDetailContext
import com.moments.android.views.story.StoriesView
import com.moments.android.views.messaging.screens.ConversationFullScreenMediaView
import com.moments.android.views.messaging.screens.SharedMedia
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.services.ChatBuzzProcessedStore
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.views.messaging.services.ChatSessionEngine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.views.messaging.components.GlassmorphicDateHeader
import com.moments.android.views.messaging.components.chatMenuDimmedUnlessSelected
import com.moments.android.views.messaging.components.chatMenuDimmedWhenOpen
import com.moments.android.R
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.creator.EmojiPickerView
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.core.PendingChatContext
import com.moments.android.views.messaging.core.PendingChatContextFactory
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AudioRecordingManager
import com.moments.android.views.messaging.components.ChatAttachmentMediaAsset
import com.moments.android.views.messaging.components.ChatAttachmentMediaSheetOverlay
import com.moments.android.views.messaging.components.ChatAttachmentMenuPopover
import com.moments.android.views.messaging.components.ChatAttachmentPickerSheet
import com.moments.android.views.messaging.components.ChatAttachmentSheetKind
import com.moments.android.views.messaging.components.GlassmorphicMediaSelectionSheet
import com.moments.android.views.messaging.components.ClusterGalleryView
import com.moments.android.views.messaging.components.ClusterGallerySelection
import com.moments.android.views.messaging.components.ClusterGalleryScope
import com.moments.android.views.messaging.components.ClusterGalleryPresentation
import com.moments.android.views.messaging.components.ChatBuzzTimelineEventRow
import com.moments.android.views.messaging.components.ChatComposerChromeMetrics
import com.moments.android.views.messaging.components.ChatFloatingNavigationOverlay
import com.moments.android.views.messaging.components.ChatFloatingNavigationState
import com.moments.android.views.messaging.components.ChatMessageForwardSheet
import com.moments.android.views.messaging.components.ChatVanishTimerSheet
import com.moments.android.views.messaging.components.GlassmorphicTypingIndicator
import com.moments.android.views.messaging.components.GlassmorphicUnreadDivider
import com.moments.android.views.messaging.components.VoiceRecordingBlobOverlay
import com.moments.android.views.messaging.components.VoiceRecordingFloatingControlHost
import com.moments.android.views.messaging.components.VoiceRecordingGestureState
import com.moments.android.views.messaging.components.rememberChatMessageListController
import com.moments.android.views.messaging.components.userAccentColor
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.views.messaging.media.CameraPickerMediaType
import kotlinx.coroutines.delay
import com.moments.android.views.messaging.media.ChatCameraView
import com.moments.android.views.messaging.media.ViewOnceImmersiveViewer
import com.moments.android.views.messaging.core.MessageItem
import com.moments.android.views.messaging.screens.ConversationSettingsView
import com.moments.android.views.messaging.services.ChatDraftStore
import com.moments.android.views.messaging.services.ChatScrollTarget
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ViewOnceConsumptionReason
import com.moments.android.views.messaging.services.ViewOnceConsumptionService
import com.moments.android.views.messaging.services.ViewOnceReplaySessionStore
import com.moments.android.views.messaging.services.cleanupConsumedViewOnceMessages
import com.moments.android.views.messaging.services.markViewOnceAsViewed
import com.moments.android.views.shared.MomentsModalSheet
import java.util.Date
import java.util.UUID
import android.content.ClipData
import android.content.ClipboardManager
import kotlinx.coroutines.launch

/**
 * Port de `ChatStoryRoute` en `GlassmorphicChatView.swift`.
 * - [UserStories]: toolbar / anillo de stories del otro participante
 * - [SharedStory]: tap en burbuja de story compartida
 */
sealed class ChatStoryRoute {
    abstract val id: String

    data class UserStories(val userId: String) : ChatStoryRoute() {
        override val id: String get() = userId
    }

    data class SharedStory(val story: Story) : ChatStoryRoute() {
        override val id: String get() = story.id?.takeIf { it.isNotBlank() } ?: story.hashCode().toString()
    }
}

@Composable
fun GlassmorphicChatView(
    conversation: Conversation,
    // Como iOS (`ChatSessionEngine.shared.session(for:)`): la sesión sale del caché, así que
    // reabrir la conversación reutiliza mensajes, listeners y scroll en vez de reconstruirlos.
    session: MomentsChatViewModel = remember(conversation.id) {
        ChatSessionEngine.session(conversation)
    },
    pendingChatContext: PendingChatContext? = null,
    onBack: () -> Unit,
    onProfile: (String) -> Unit = {},
    onStory: (ChatStoryRoute) -> Unit = {},
    onSettings: () -> Unit = {},
    onReport: () -> Unit = {},
    onOpenMedia: (EnhancedMessage) -> Unit = {},
    onOpenCluster: (List<EnhancedMessage>) -> Unit = {},
    onMomentNavigation: (EnhancedMessage) -> Unit = {},
    onStoryNavigation: (EnhancedMessage) -> Unit = {},
    /** ≡ iOS `onPendingChatAccepted`. */
    onPendingChatAccepted: (String) -> Unit = {},
    /** ≡ iOS `onPendingChatDismissed`. */
    onPendingChatDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    val messages by session.messages.collectAsState()
    val timelineMutation by session.chatTimelineMutation.collectAsState()
    val downloadProgress by session.downloadProgress.collectAsState()
    val searching by session.isSearchingHistory.collectAsState()
    val canLoadMore by session.canLoadMore.collectAsState()
    val vanishModeActive by session.vanishModeActive.collectAsState()
    val searchResults by session.searchResults.collectAsState()
    val typingUsers by session.typingUsers.collectAsState()
    val listController = rememberChatMessageListController()
    val messagePresentation = rememberChatMessagePresentationState()
    messagePresentation.rowFrameProvider = { listController.frameInWindow(it) }
    val listPresentation = rememberChatMessageListPresentation()
    val unreadDivider = remember(session) { ChatUnreadDividerController(session) }
    val voiceGestureState = remember { VoiceRecordingGestureState() }
    val audioPower by AudioRecordingManager.shared.audioPower.collectAsState()
    var messageText by remember(conversation.id) { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<EnhancedMessage?>(null) }
    var editingMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var attachmentSheet by remember { mutableStateOf<ChatAttachmentSheetKind?>(null) }
    var plusButtonAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var voiceButtonAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var lastBuzzSentAt by remember { mutableStateOf<Long?>(null) }
    var buzzToastText by remember { mutableStateOf<String?>(null) }
    var buzzShakeProgress by remember { mutableFloatStateOf(0f) }
    val voice = remember(session) {
        GlassmorphicChatVoiceController(
            viewModel = session,
            onError = { resId -> buzzToastText = context.getString(resId) },
        )
    }
    var showingConversationSettings by remember { mutableStateOf(false) }
    var showVanishTimerSheet by remember { mutableStateOf(false) }
    var showingUserReportSheet by remember { mutableStateOf(false) }
    var selectedChatMedia by remember { mutableStateOf<SharedMedia?>(null) }
    var selectedChatMediaItems by remember { mutableStateOf<List<SharedMedia>>(emptyList()) }
    var clusterForReply by remember { mutableStateOf<List<EnhancedMessage>?>(null) }
    var clusterGallerySelection by remember { mutableStateOf<ClusterGallerySelection?>(null) }
    var deferredJumpToMessageId by remember { mutableStateOf<String?>(null) }
    var reactionPickerMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var forwardingMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var starredMessageIds by remember(conversation.id) { mutableStateOf(emptySet<String>()) }
    // ≡ iOS selectedMoment / showingMomentDetail / storyUnavailable
    // chatStoryRoute declarado arriba (holder para onStoriesDisabled)
    var selectedMoment by remember { mutableStateOf<Moment?>(null) }
    var showingMomentError by remember { mutableStateOf(false) }
    var storyUnavailableReason by remember { mutableStateOf<SharedStoryAccessDenialReason?>(null) }
    val vanishMessageTimer by session.vanishMessageTimer.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // ≡ iOS onChange(of: activeAttachmentSheet) → isTextFieldFocused = false
    LaunchedEffect(attachmentSheet) {
        if (attachmentSheet != null) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // ≡ iOS draftStorageKey: conversation.id ?? "pending:\(otherParticipantId)"
    val draftStorageKey = conversation.id?.takeIf { it.isNotBlank() }
        ?: "pending:${conversation.otherParticipantId}"
    // ≡ iOS @State conversationIntroContext
    var conversationIntroContext by remember(conversation.id, conversation.otherParticipantId) {
        mutableStateOf<PendingChatContext?>(null)
    }

    val messageRequestService = remember { MessageRequestService() }
    val requestLoading by messageRequestService.isLoading.collectAsState()
    val pendingContextRef = remember { mutableStateOf(pendingChatContext) }
    val requestOperations = remember(messageRequestService) {
        fun resolveRequest(requestId: String) =
            pendingContextRef.value?.request?.takeIf { it.id == requestId }

        PendingMessageRequestOperations(
            send = { receiverId, text, completion ->
                messageRequestService.sendMessageRequest(receiverId, text, onComplete = completion)
            },
            accept = { requestId, completion ->
                val request = resolveRequest(requestId)
                if (request == null) {
                    completion(Result.failure(IllegalStateException("Missing message request")))
                } else {
                    messageRequestService.acceptRequest(request, completion)
                }
            },
            cancel = { requestId, completion ->
                val request = resolveRequest(requestId)
                if (request == null) {
                    completion(Result.failure(IllegalStateException("Missing message request")))
                } else {
                    messageRequestService.cancelRequest(request, completion)
                }
            },
            reject = { requestId, completion ->
                val request = resolveRequest(requestId)
                if (request == null) {
                    completion(Result.failure(IllegalStateException("Missing message request")))
                } else {
                    messageRequestService.rejectRequest(request, completion)
                }
            },
            block = { requestId, completion ->
                val request = resolveRequest(requestId)
                if (request == null) {
                    completion(Result.failure(IllegalStateException("Missing message request")))
                } else {
                    messageRequestService.blockUser(request, completion)
                }
            },
        )
    }
    val composer = rememberChatComposerAndChromeController(
        pendingChatContext = pendingChatContext,
        requestOperations = requestOperations,
        onDraftCleared = { ChatDraftStore.clearDraft(context, draftStorageKey) },
        onAccepted = onPendingChatAccepted,
        onDismissed = onPendingChatDismissed,
        onError = { error ->
            buzzToastText = error.message.takeUnless { it.isNullOrBlank() }
                ?: context.getString(R.string.common_error)
        },
    )
    composer.isRequestLoading = requestLoading
    composer.currentUserId = session.currentUserId
    // Request ops leen el contexto enriquecido del composer (no el param estático).
    pendingContextRef.value = composer.pendingChatContext ?: pendingChatContext

    // ≡ iOS .task { enrichPendingChatContextIfNeeded(); loadConversationIntroContextIfNeeded() }
    LaunchedEffect(pendingChatContext, conversation.id, conversation.otherParticipantId, session.currentUserId) {
        val viewerId = session.currentUserId
        val pending = composer.pendingChatContext ?: pendingChatContext
        val incomingRequest = pending?.request
        if (pending != null &&
            pending.direction == PendingChatContext.Direction.INCOMING &&
            pending.viewerFollowsOther == null &&
            incomingRequest != null &&
            viewerId.isNotBlank()
        ) {
            val enriched = PendingChatContextFactory.incoming(incomingRequest, viewerId)
            val current = composer.pendingChatContext ?: pendingChatContext
            if (current?.request?.id == incomingRequest.id &&
                current?.status == PendingChatContext.Status.INCOMING_REQUEST_PENDING
            ) {
                composer.updatePendingContext(enriched)
            }
        }

        if ((composer.pendingChatContext ?: pendingChatContext) == null &&
            conversationIntroContext == null &&
            viewerId.isNotBlank()
        ) {
            val intro = PendingChatContextFactory.conversationIntro(conversation, viewerId)
            if ((composer.pendingChatContext ?: pendingChatContext) == null) {
                conversationIntroContext = intro
            }
        }
    }


    LaunchedEffect(messages, session.currentUserId) {
        starredMessageIds = messages.mapNotNull { msg ->
            msg.id.takeIf { session.currentUserId in (msg.starredBy.orEmpty()) || session.isStarred(msg.id) }
        }.toSet()
    }

    val scroll = remember(session, listController) {
        GlassmorphicChatScrollController(
            viewModel = session,
            listController = listController,
            callbacks = ChatScrollCallbacks(
                rowsReady = { session.chatRenderRows.isNotEmpty() },
                resolveInitialTarget = {
                    session.messages.value.firstOrNull { !it.isRead && it.senderId != session.currentUserId }
                        ?.let { ChatScrollTarget.FirstUnread(it.id) }
                        ?: session.messages.value.lastOrNull()?.let { ChatScrollTarget.Bottom(it.id) }
                },
                messageRowReady = { id -> session.chatRenderRows.any { id in rowMessageIds(it) } },
                onPrefetchMedia = session::prefetchUnresolvedMediaIfNeeded,
                onVanishToggle = { session.toggleVanishMode() },
                // ≡ presentVanishTimerPickerIfFirstActivation()
                onPresentVanishTimer = {
                    val conversationId = conversation.id.orEmpty()
                    if (conversationId.isNotEmpty()) {
                        val prefs = context.getSharedPreferences("moments_chat_prefs", Context.MODE_PRIVATE)
                        val key = "chat.vanish.timerPicker.shown.$conversationId"
                        if (!prefs.getBoolean(key, false)) {
                            prefs.edit().putBoolean(key, true).apply()
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                delay(450L)
                                showVanishTimerSheet = true
                            }
                        }
                    }
                },
            ),
        )
    }
    val search = remember(session, scroll) { GlassmorphicChatSearchController(session, scroll) }
    // Holder antes del lifecycle para que `onStoriesDisabled` limpie storyRoute (≡ iOS)
    val chatStoryRouteHolder = remember { mutableStateOf<ChatStoryRoute?>(null) }
    var chatStoryRoute by chatStoryRouteHolder
    val lifecycle = remember(session) {
        GlassmorphicChatLifecycleController(
            viewModel = session,
            cameraOperations = ChatCameraCaptureOperations(
                sendViewOnce = { data, mediaType, allowReplay, replyTo, payload ->
                    session.sendViewOnceMessage(
                        data = data,
                        mediaType = if (mediaType == ChatCameraCapturedMediaType.IMAGE) CameraPickerMediaType.IMAGE else CameraPickerMediaType.VIDEO,
                        allowReplay = allowReplay,
                        replyTo = replyTo,
                        overlayPayload = payload as? com.moments.android.views.messaging.media.ChatMediaOverlayPayload,
                    )
                },
                sendImage = session::sendImageMessageForReply,
                sendVideo = { data, replyTo -> session.sendMediaMessage(data, MessageType.VIDEO, replyTo = replyTo) },
            ),
            viewOnceOperations = ChatViewOnceSessionOperations(
                markReplayAvailable = ViewOnceReplaySessionStore::markAvailable,
                markReplayConsumed = ViewOnceReplaySessionStore::markConsumed,
                markViewed = { conversationId, messageId, viewerId ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        ChatService.markViewOnceAsViewed(conversationId, messageId, viewerId)
                            .onFailure {
                                android.util.Log.e(
                                    "ViewOnceConsume",
                                    "markViewOnceAsViewed failed msg=$messageId",
                                    it,
                                )
                            }
                    }
                },
            ),
            onStoriesDisabled = { chatStoryRouteHolder.value = null },
        )
    }
    val displayName = lifecycle.liveOtherParticipantUsername.ifBlank {
        conversation.otherParticipantUsername.orEmpty()
    }
    // ≡ NSLocalizedString("chat.reply.you") — no Auth.displayName
    val currentUserName = stringResource(R.string.chat_reply_you)
    var didRunConsumedViewOnceCleanup by remember(conversation.id) { mutableStateOf(false) }

    fun sendAssets(assets: List<ChatAttachmentMediaAsset>) {
        val batchId = assets.takeIf { it.size > 1 }?.let { UUID.randomUUID().toString() }
        assets.forEach { asset ->
            val data = context.contentResolver.openInputStream(asset.uri)?.use { it.readBytes() } ?: return@forEach
            session.sendMediaMessage(
                data = data,
                type = if (asset.isVideo) MessageType.VIDEO else MessageType.IMAGE,
                fileName = asset.uri.lastPathSegment,
                mediaBatchId = batchId,
                replyTo = replyingTo?.id,
            )
        }
        replyingTo = null
        attachmentSheet = null
    }


    fun triggerBuzzEffect(text: String, isLocal: Boolean, showsToast: Boolean = true) {
        if (showsToast) buzzToastText = text
        if (isLocal) return
        HapticManager.shared.chatBuzzReceived(MotionPolicy.reduceMotion)
        HapticManager.shared.playBuzzReceivedSound()
        if (MotionPolicy.reduceMotion) return
        scope.launch {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(durationMillis = 1120, easing = LinearEasing)) {
                buzzShakeProgress = value
            }
            buzzShakeProgress = 0f
        }
    }

    fun activateReply(message: EnhancedMessage) {
        replyingTo = message
        editingMessage = null
        scope.launch { messagePresentation.pulseBubbleHighlight(message.id) }
        if (scroll.hasCompletedInitialScroll && !scroll.isPinnedToBottom) {
            scroll.scrollToTarget(ChatScrollTarget.HighlightedMessage(message.id), animated = !MotionPolicy.reduceMotion)
        }
    }

    fun openChatMedia(message: EnhancedMessage) {
        session.hydrateMediaIfNeeded(message)
        val selected = sharedMediaFrom(message) ?: return
        selectedChatMediaItems = sharedMediaItemsForOverlay(messages, message)
        selectedChatMedia = selected
    }

    fun sendReplyToOpenedMedia(media: SharedMedia, text: String, completion: (Result<Unit>) -> Unit) {
        sendReplyToSharedMedia(session, media, text, completion)
    }

    fun sendBuzzFromAttachmentMenu() {
        val now = System.currentTimeMillis()
        if (lastBuzzSentAt != null && now - lastBuzzSentAt!! < 45_000L) {
            buzzToastText = context.getString(R.string.chat_buzz_cooldown)
            return
        }
        lastBuzzSentAt = now
        attachmentSheet = null
        session.sendBuzz { result ->
            result.onSuccess {
                HapticManager.shared.playBuzzSentSound()
                triggerBuzzEffect(
                    text = context.getString(R.string.chat_buzz_sent),
                    isLocal = true,
                    showsToast = false,
                )
            }.onFailure { error ->
                lastBuzzSentAt = null
                buzzToastText = error.message.takeUnless { it.isNullOrBlank() }
                    ?: context.getString(R.string.chat_buzz_sent)
            }
        }
    }

    LaunchedEffect(buzzToastText) {
        if (buzzToastText == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_900)
        buzzToastText = null
    }

    LaunchedEffect(messages) {
        session.syncMessagePresentation()
        unreadDivider.initialize()
        scroll.routeInitialScroll()
    }
    // ≡ handleLastMessageScrollChangeInList — badge de entrantes si no estás al fondo
    val lastMessageId = messages.lastOrNull()?.id
    var previousLastMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(lastMessageId) {
        val mine = messages.lastOrNull()?.senderId == session.currentUserId
        // ≡ dismissUnreadDividerOnUserReply
        if (mine && previousLastMessageId != null && previousLastMessageId != lastMessageId) {
            if (unreadDivider.dividerMessageId != null || session.unreadIncomingCount > 0) {
                unreadDivider.clear()
                scroll.clearPendingIncoming()
                session.markVisibleConversationAsRead()
            }
        }
        scroll.handleLastMessageChange(previousLastMessageId, lastMessageId)
        previousLastMessageId = lastMessageId
    }
    LaunchedEffect(scroll.hasCompletedInitialScroll) {
        listPresentation.hasCompletedInitialScroll = scroll.hasCompletedInitialScroll
    }
    LaunchedEffect(conversation.id, conversation.otherParticipantId) {
        messageText = ChatDraftStore.draft(context, draftStorageKey)
        session.activateChatSession()
        session.markVisibleConversationAsRead()
        lifecycle.setupOnlineStatusObserver()
        lifecycle.refreshOtherParticipantUsername()
        lifecycle.refreshOtherParticipantAvailability()
        lifecycle.checkUserStories()
        scroll.configureListInitialScrollPolicy()
        // ≡ cleanupConsumedViewOnceMessagesIfNeeded()
        val conversationId = conversation.id.orEmpty()
        if (conversationId.isNotBlank() && !didRunConsumedViewOnceCleanup) {
            didRunConsumedViewOnceCleanup = true
            ChatService.cleanupConsumedViewOnceMessages(conversationId)
        }
    }
    // ≡ onChange(isSearchVisible) → restoreLayoutAfterClosingSearch only on close
    var wasSearchVisible by remember { mutableStateOf(false) }
    LaunchedEffect(search.isSearchVisible) {
        if (wasSearchVisible && !search.isSearchVisible) {
            search.restoreLayoutAfterClosingSearch()
        }
        wasSearchVisible = search.isSearchVisible
    }
    // ≡ onChange(searchResults / messages) → syncSearchMatchesFromViewModel
    LaunchedEffect(searchResults, messages, search.searchQuery, search.isSearchVisible) {
        if (search.isSearchVisible && search.searchQuery.trim().isNotEmpty()) {
            search.syncSearchMatchesFromViewModel()
        }
    }
    DisposableEffect(session, lifecycle, search) {
        onDispose {
            // ≡ onDisappearActions: drainAvailable → ABANDON_REPLAY
            val conversationId = session.conversationId
            if (conversationId.isNotBlank()) {
                ViewOnceReplaySessionStore.drainAvailable(conversationId).forEach { pending ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        // Misma carrera que replay: CF exige first view en Firestore.
                        ChatService.markViewOnceAsViewed(
                            pending.conversationId,
                            pending.messageId,
                            pending.viewerId,
                        )
                        val error = ViewOnceConsumptionService.consumeAwait(
                            pending.conversationId,
                            pending.messageId,
                            ViewOnceConsumptionReason.ABANDON_REPLAY,
                        )
                        if (error == null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                lifecycle.handleViewOnceMediaConsumed(pending.messageId)
                            }
                        }
                    }
                }
            }
            voice.resetVoiceRecordingInteraction()
            search.dispose()
            lifecycle.dispose()
            session.deactivateChatSession()
        }
    }

    val effectivePendingContext = composer.pendingChatContext ?: pendingChatContext
    val rows = remember(
        session.chatRenderRows,
        effectivePendingContext,
        conversationIntroContext,
        composer.pendingChatTimelineMessage,
        canLoadMore,
        messages,
        scroll.hasCompletedInitialScroll,
        typingUsers,
    ) {
        chatListRows(
            baseRows = session.chatRenderRows,
            pendingChatContext = effectivePendingContext,
            conversationIntroContext = conversationIntroContext,
            pendingTimelineMessage = composer.pendingChatTimelineMessage,
            canLoadMore = canLoadMore,
            hasCompletedInitialScroll = scroll.hasCompletedInitialScroll,
            hasTypingUsers = typingUsers.isNotEmpty(),
        )
    }
    val transaction = remember(rows, timelineMutation) {
        chatListTransaction(session, rows) { id -> rows.firstOrNull { id in rowMessageIds(it) }?.id }
    }
    val renderer = ChatMessageRendererCallbacks(
        otherParticipantName = displayName,
        otherParticipantId = conversation.otherParticipantId,
        isOtherParticipantUnavailable = lifecycle.isOtherParticipantUnavailable,
        shouldShowAvatar = lifecycle::shouldShowAvatar,
        groupPosition = lifecycle::messageGroupPosition,
        onReply = ::activateReply,
        onClusterReply = { cluster -> clusterForReply = cluster },
        onAvatarTap = { onProfile(conversation.otherParticipantId) },
        onReplyTap = { messageId ->
            scope.launch { messagePresentation.pulseBubbleHighlight(messageId) }
            scroll.scrollToTarget(ChatScrollTarget.HighlightedMessage(messageId), animated = !MotionPolicy.reduceMotion)
        },
        onOpenMedia = { message ->
            openChatMedia(message)
            onOpenMedia(message)
        },
        onOpenCluster = { cluster ->
            val ids = cluster.map { it.id }
            val anchorId = ids.firstOrNull()
            if (anchorId != null) {
                clusterGallerySelection = ClusterGallerySelection(anchorId, ids)
            }
            onOpenCluster(cluster)
        },
        onMomentNavigation = { message ->
            onMomentNavigation(message)
            scope.launch {
                when (val result = GlassmorphicChatSharedContentNavigation.handleMomentNavigationFromChat(message)) {
                    is ChatMomentNavigationResult.Open -> selectedMoment = result.moment
                    ChatMomentNavigationResult.Failed -> showingMomentError = true
                    ChatMomentNavigationResult.Ignored -> Unit
                }
            }
        },
        onStoryNavigation = { message ->
            onStoryNavigation(message)
            scope.launch {
                when (val result = GlassmorphicChatSharedContentNavigation.handleStoryNavigationFromChat(message)) {
                    is ChatStoryNavigationResult.Open -> {
                        chatStoryRoute = ChatStoryRoute.SharedStory(result.story)
                    }
                    is ChatStoryNavigationResult.Unavailable -> {
                        storyUnavailableReason = result.reason
                    }
                    ChatStoryNavigationResult.Ignored -> Unit
                }
            }
        },
        onViewOnceOpen = { message, replay -> lifecycle.presentViewOnceViewer(message, replay, displayName, currentUserName) },
        onHydrateMedia = session::hydrateMediaIfNeeded,
        onStopLiveLocation = session::stopLiveLocation,
        onChangeVanishTimer = { showVanishTimerSheet = true },
        onTurnOnVanish = { session.toggleVanishMode() },
    )

    // Port de `handleIncomingBuzzToastIfNeeded()`: al llegar un zumbido ajeno se muestra el aviso
    // 1,9 s y se marca como procesado para no repetirlo al reabrir el chat.
    val latestBuzzEvent by session.latestBuzzEvent.collectAsState()
    val buzzEvents by session.buzzEvents.collectAsState()
    val chatError by session.error.collectAsState()

    // ≡ viewModel.error → toast breve (mismo canal que buzz)
    LaunchedEffect(chatError) {
        val message = chatError?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        buzzToastText = message
        session.clearError()
    }

    // Abrir desde el banner/push de un zumbido: `ChatNavigationIntentStore.enqueueBuzz` deja la
    // intención pendiente y aquí se consume, reproduciéndolo una vez (port de
    // `resolvePendingBuzzEventForReplay`). Sin esto la intención se encolaba y no la leía nadie.
    LaunchedEffect(session.conversationId, buzzEvents.size) {
        val conversationId = session.conversationId.ifBlank { return@LaunchedEffect }
        val intent = ChatNavigationIntentStore.peek(conversationId) ?: return@LaunchedEffect
        if (!intent.playBuzzOnOpen) return@LaunchedEffect
        val replayWindowStart = Date(System.currentTimeMillis() - ChatBuzzProcessedStore.replayWindowMillis)
        val event = intent.buzzEventId?.let { id -> buzzEvents.firstOrNull { it.id == id } }
            ?: buzzEvents.filter { it.senderId != session.currentUserId && it.createdAt >= replayWindowStart }
                .maxByOrNull { it.createdAt }
            ?: return@LaunchedEffect
        ChatNavigationIntentStore.clearBuzz(conversationId)
        triggerBuzzEffect(context.getString(R.string.chat_buzz_received, displayName), isLocal = false)
        ChatBuzzProcessedStore.markProcessed(context, event.id, conversationId)
    }
    LaunchedEffect(latestBuzzEvent?.id) {
        val event = latestBuzzEvent ?: return@LaunchedEffect
        if (event.senderId == session.currentUserId) return@LaunchedEffect
        if (ChatBuzzProcessedStore.isProcessed(context, event.id, session.conversationId)) return@LaunchedEffect
        triggerBuzzEffect(context.getString(R.string.chat_buzz_received, displayName), isLocal = false)
        ChatBuzzProcessedStore.markProcessed(context, event.id, session.conversationId)
        session.clearLatestBuzzEvent()
    }

    // Back físico: cerrar overlays locales antes de salir al inbox (no al Feed).
    // Último registrado gana; handlers disabled se saltan → onBack queda de fallback.
    // ConversationFullScreenMediaView / ChatCamera / ViewOnce registran los suyos encima.
    BackHandler(onBack = onBack)
    BackHandler(enabled = showingConversationSettings) {
        showingConversationSettings = false
        session.refreshTypingIndicatorPreference()
        session.refreshForwardingPreference()
        session.refreshBuzzPreference()
        deferredJumpToMessageId?.let { messageId ->
            deferredJumpToMessageId = null
            scroll.consumeDeferredJumpToMessage(messageId)
        }
    }
    BackHandler(enabled = clusterGallerySelection != null) {
        clusterGallerySelection = null
    }
    BackHandler(enabled = selectedMoment != null) {
        selectedMoment = null
    }
    BackHandler(enabled = clusterForReply != null) {
        clusterForReply = null
    }

    GlassmorphicChatRootContent(
        adaptiveColors = colors,
        viewModel = session,
        messagePresentation = messagePresentation,
        buzzToastText = buzzToastText,
        isSearchVisible = search.isSearchVisible,
        composerHeight = scroll.lastComposerHeight ?: ChatComposerChromeMetrics.estimatedComposerChromeHeight,
        onComposerHeightChange = scroll::handleComposerHeightChange,
        callbacks = ChatMessageRenderingCallbacks(
            renderer = renderer,
            buzzText = { event ->
                if (event.senderId == session.currentUserId) context.getString(R.string.chat_buzz_sent)
                else context.getString(R.string.chat_buzz_received, displayName)
            },
            onReplyCancelled = { replyingTo = null },
            onEditCancelled = { editingMessage = null; messageText = "" },
            onEditingStarted = { message ->
                editingMessage = message
                replyingTo = null
                messageText = message.content.orEmpty()
            },
            onCopy = { message ->
                val text = message.content.orEmpty()
                if (text.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
                }
            },
            onForward = { message -> forwardingMessage = message },
            onMoreReactions = { message -> reactionPickerMessage = message },
            onToggleStar = { message ->
                starredMessageIds = if (message.id in starredMessageIds) {
                    starredMessageIds - message.id
                } else {
                    starredMessageIds + message.id
                }
            },
            starredMessageIds = starredMessageIds,
        ),
        modifier = modifier.chatBuzzShakeEffect(buzzShakeProgress, 24.dp),
        content = {
            Box(Modifier.fillMaxSize()) {
                GlassmorphicChatMessageList(
                    transaction = transaction,
                    listController = listController,
                    presentation = listPresentation,
                    viewModel = session,
                    adaptiveColors = colors,
                    fallbackName = displayName,
                    fallbackUserId = conversation.otherParticipantId,
                    composerChromeHeight = scroll.lastComposerHeight
                        ?: ChatComposerChromeMetrics.estimatedComposerChromeHeight,
                    isVanishGestureEnabled = scroll.hasCompletedInitialScroll && !search.isSearchVisible,
                    searchHighlightTerm = search.activeSearchHighlightTerm,
                    searchActiveMessageId = search.currentSearchMatchId,
                    elevatedRowId = messagePresentation.menuSelection?.rowId,
                    callbacks = ChatMessageListCallbacks(
                        loadOlderHistory = scroll::loadOlderHistoryIfNeeded,
                        retryHistoryLoad = scroll::loadOlderHistoryIfNeeded,
                        // ≡ iOS onPrependFinished → endHistoryScrollRestoration()
                        onPrependFinished = session::endHistoryScrollRestoration,
                        onRowsChanged = scroll::routeInitialScroll,
                        onContentExtentChanged = scroll::updateContentExtent,
                        onAtBottomChanged = scroll::handleListAtBottomChange,
                        onVanishPullReleased = scroll::handleVanishPullReleased,
                        renderMessage = { item ->
                            // ≡ iOS chatRenderRow(.message): divider + burbuja en VStack.
                            // Sin Column, LazyColumn mete ambos en un Box → se solapan.
                            // ≡ iOS `.chatMenuDimmedUnlessSelected` + `.zIndex(100)` — la burbuja
                            // seleccionada se “levanta” sobre el resto atenuado.
                            val rowId = "row:message:${item.id}"
                            val menuOpen = messagePresentation.menuSelection != null
                            val selected = messagePresentation.menuSelection?.rowId == rowId
                            val highlighted = messagePresentation.isMessageItemHighlighted(item)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (selected || highlighted) 100f else 0f)
                                    .chatMenuDimmedUnlessSelected(selected, menuOpen),
                            ) {
                                if (unreadDivider.shouldShowBefore(messageIds(item), canLoadMore)) {
                                    GlassmorphicUnreadDivider(
                                        unreadDivider.dividerCount,
                                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                    )
                                }
                                GlassmorphicChatMessageItem(
                                    item,
                                    messages,
                                    session,
                                    messagePresentation,
                                    renderer,
                                    "❤️",
                                    listController.timestampRevealState,
                                )
                            }
                        },
                        renderHeader = { header ->
                            GlassmorphicDateHeader(
                                header.date,
                                Modifier
                                    .chatMenuDimmedWhenOpen(messagePresentation.menuSelection != null)
                                    .padding(vertical = 10.dp),
                            )
                        },
                        renderBuzz = { buzz ->
                            ChatBuzzTimelineEventRow(
                                text = if (buzz.event.senderId == session.currentUserId) stringResource(R.string.chat_buzz_sent) else stringResource(R.string.chat_buzz_received, displayName),
                                isOutgoing = buzz.event.senderId == session.currentUserId,
                                modifier = Modifier.chatMenuDimmedWhenOpen(messagePresentation.menuSelection != null),
                            )
                        },
                        renderTyping = {
                            GlassmorphicTypingIndicator(
                                reduceMotion = MotionPolicy.reduceMotion,
                                modifier = Modifier
                                    .chatMenuDimmedWhenOpen(messagePresentation.menuSelection != null)
                                    .padding(horizontal = 16.dp),
                            )
                        },
                    ),
                )
                val navigation = ChatFloatingNavigationState.resolve(
                    hasCompletedInitialScroll = scroll.hasCompletedInitialScroll,
                    isSearchVisible = search.isSearchVisible,
                    isSearchingHistory = searching,
                    hasSearchQuery = search.searchQuery.isNotBlank(),
                    isPinnedToBottom = scroll.isPinnedToBottom,
                )
                ChatFloatingNavigationOverlay(
                    state = navigation,
                    isSearching = searching,
                    canSearchGoUp = search.canSearchGoUp,
                    canSearchGoDown = search.canSearchGoDown,
                    pendingIncomingCount = scroll.pendingIncomingMessages,
                    accentColor = colors.userAccentColor,
                    badgeTextColor = colors.surfaceBackground,
                    reduceMotion = MotionPolicy.reduceMotion,
                    onSearchPrevious = { search.moveSearchSelection(-1) },
                    onSearchNext = search::advanceSearchSelection,
                    onScrollToBottom = scroll::scrollToBottomFromUserAction,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 8.dp,
                            bottom = ChatComposerChromeMetrics.floatingControlBottomInset(
                                scroll.lastComposerHeight
                                    ?: ChatComposerChromeMetrics.estimatedComposerChromeHeight,
                            ) + 20.dp,
                        ),
                )
                VoiceRecordingFloatingControlHost(
                    isRecording = voice.isRecording,
                    isLocked = voice.isLocked,
                    isPreparing = voice.isPreparingPreview,
                    hasDraft = voice.draft != null,
                    hasActiveInteraction = voice.interactionId != null,
                    gestureState = voiceGestureState,
                    primaryTint = colors.primary,
                    accentTint = colors.userAccentColor,
                    onPause = voice::pauseVoiceRecording,
                    onResume = {
                        context.findActivity()?.let(voice::resumeVoiceRecording)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = ChatComposerChromeMetrics.floatingControlBottomInset(
                                scroll.lastComposerHeight
                                    ?: ChatComposerChromeMetrics.estimatedComposerChromeHeight,
                            ) + 20.dp,
                        ),
                )
            }
        },
        composer = {
            ChatComposerChrome(
                controller = composer,
                messageText = messageText,
                onMessageTextChange = { text ->
                    messageText = text
                    // ≡ iOS onChange(messageText): no persistir draft mientras editas
                    if (editingMessage == null) {
                        ChatDraftStore.setDraft(context, text, draftStorageKey)
                    }
                    session.setTyping(text.isNotBlank())
                },
                isOtherParticipantBlockedByCurrentUser = lifecycle.isOtherParticipantBlockedByCurrentUser,
                isOtherParticipantUnavailable = lifecycle.isOtherParticipantUnavailable,
                otherParticipantDisplayName = displayName,
                vanishModeActive = vanishModeActive,
                isRecordingVoice = voice.isRecording,
                isVoiceRecordingLocked = voice.isLocked,
                recordingSeconds = voice.recordingTime,
                recordingInteractionId = voice.interactionId,
                voiceRecordingDraft = voice.draft,
                isPreparingVoiceRecordingPreview = voice.isPreparingPreview,
                voiceGestureState = voiceGestureState,
                editingMessage = editingMessage,
                replyingTo = replyingTo,
                onEditingFinished = { editingMessage = null },
                onReplyingFinished = { replyingTo = null },
                onUnblock = lifecycle::unblockOtherParticipantFromChat,
                isAttachmentMenuOpen = attachmentSheet == ChatAttachmentSheetKind.MENU,
                onOpenAttachments = {
                    attachmentSheet = if (attachmentSheet == ChatAttachmentSheetKind.MENU) null else ChatAttachmentSheetKind.MENU
                },
                onAttachmentPlusAnchorBoundsChanged = { plusButtonAnchorBounds = it },
                onVoiceButtonAnchorBoundsChanged = { voiceButtonAnchorBounds = it },
                onStartVoiceRecording = { id, locked ->
                    context.findActivity()?.let { voice.startVoiceRecording(it, id, locked) }
                },
                onFinishVoiceRecording = { id, action ->
                    voice.finishVoiceRecording(id, action)
                },
                onVoiceRecordingTrimChanged = voice::updateVoiceRecordingTrimRange,
                onLockChanged = voice::setVoiceRecordingLocked,
                onReport = {
                    showingUserReportSheet = true
                    onReport()
                },
                onPendingRequestSent = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onReplyAfterAcceptance = { conversationId, text ->
                    val uid = session.currentUserId
                    val ctx = composer.pendingChatContext ?: pendingChatContext
                    if (uid != null && ctx != null) {
                        val accepted = Conversation(
                            id = conversationId,
                            participants = listOf(uid, ctx.otherUserId).sorted(),
                            lastMessage = ctx.initialText,
                            timestamp = Date(),
                            readStatus = mapOf(uid to true, ctx.otherUserId to true),
                            otherParticipantId = ctx.otherUserId,
                            otherParticipantUsername = ctx.otherUsername,
                            otherParticipantProfileImagePath = ctx.otherProfileImagePath,
                        )
                        ChatSessionEngine.session(accepted).sendTextMessage(text, replyTo = null)
                    }
                },
                viewModel = session,
            )
        },
    )

    if (search.isSearchVisible) {
        GlassmorphicChatSearchHeader(
            query = search.searchQuery,
            onQueryChange = search::updateSearchQuery,
            isSearching = searching,
            adaptiveColors = colors,
            callbacks = ChatToolbarCallbacks(
                onSearchClose = search::toggleChatSearch,
                onSearchClear = search::clearSearchQueryKeepingMode,
                onSearchSubmit = search::scrollToCurrentSearchMatch,
            ),
        )
    } else {
        GlassmorphicChatToolbar(
            displayName = displayName,
            userId = conversation.otherParticipantId,
            profileImagePath = conversation.otherParticipantProfileImagePath,
            adaptiveColors = colors,
            isUnavailable = lifecycle.isOtherParticipantUnavailable,
            isBlockedByMe = lifecycle.isOtherParticipantBlockedByCurrentUser,
            storyRing = lifecycle.storyRing,
            hasTypingUsers = typingUsers.isNotEmpty(),
            presence = lifecycle.presenceDisplay,
            callbacks = ChatToolbarCallbacks(
                onBack = onBack,
                onProfile = { onProfile(conversation.otherParticipantId) },
                onStory = {
                    val uid = conversation.otherParticipantId
                    if (uid.isNotBlank()) {
                        // ≡ iOS fullScreenCover sobre el chat (no reemplaza MessagingView)
                        chatStoryRoute = ChatStoryRoute.UserStories(uid)
                    }
                },
                onSettings = { showingConversationSettings = true },
                onSearchClose = search::toggleChatSearch,
                onSearchClear = search::clearSearchQueryKeepingMode,
                onSearchSubmit = search::scrollToCurrentSearchMatch,
            ),
        )
    }

    // ≡ navigationDestination(showingConversationSettings)
    if (showingConversationSettings) {
        ConversationSettingsView(
            conversation = conversation,
            onBack = {
                showingConversationSettings = false
                session.refreshTypingIndicatorPreference()
                session.refreshForwardingPreference()
                // Buzz se aplica en vivo vía ConversationBuzzPreferenceEvents; re-sync local por si acaso
                session.refreshBuzzPreference()
                // Vanish: sync en vivo desde ConversationSettingsView.updateVanish → sesión
                deferredJumpToMessageId?.let { messageId ->
                    deferredJumpToMessageId = null
                    // ≡ consumeDeferredJumpToMessageIfNeeded (delay 0.35s)
                    scroll.consumeDeferredJumpToMessage(messageId)
                }
            },
            onJumpToMessage = { messageId ->
                deferredJumpToMessageId = messageId
            },
            onSearchRequested = {
                showingConversationSettings = false
                search.toggleChatSearch()
            },
            onProfile = onProfile,
            onReport = { /* Report sheet hosted inside ConversationSettingsView */ },
            modifier = Modifier.fillMaxSize(),
        )
    }

    VoiceRecordingBlobOverlay(
        anchorBounds = voiceButtonAnchorBounds,
        audioPower = audioPower,
        gestureState = voiceGestureState,
        isRecording = voice.isRecording && !voice.isLocked,
    )
    ChatAttachmentMenuPopover(
        isPresented = attachmentSheet,
        anchorBounds = plusButtonAnchorBounds,
        canSendBuzz = session.canSendBuzz,
        onDismiss = { attachmentSheet = null },
        onOpenCamera = {
            attachmentSheet = null
            lifecycle.openCamera()
        },
        onSendBuzz = ::sendBuzzFromAttachmentMenu,
        onSheetSelected = { attachmentSheet = it },
    )
    ChatAttachmentMediaSheetOverlay(
        activeSheet = attachmentSheet,
        accentColor = colors.accent,
        onPickerUris = { uris ->
            sendAssets(
                uris.map { uri ->
                    ChatAttachmentMediaAsset(
                        id = uri.toString(),
                        uri = uri,
                        isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true,
                        durationMillis = 0,
                    )
                },
            )
        },
        onConfirmAssets = ::sendAssets,
        onDismiss = { attachmentSheet = null },
    )
    attachmentSheet?.takeIf { it.isPickerSheet }?.let { picker ->
        ChatAttachmentPickerSheet(
            kind = picker,
            accentColor = colors.accent,
            onDismiss = { attachmentSheet = null },
            onSelectGif = { session.sendGif(it, replyingTo?.id); replyingTo = null },
            onSelectSticker = { session.sendSticker(context, it, replyingTo?.id); replyingTo = null },
            onSendStaticLocation = { latitude, longitude, name, address -> session.sendStaticLocation(latitude, longitude, name, address) },
            onStartLive = { duration -> session.startLiveLocation(context, duration) },
        )
    }
    if (lifecycle.shouldShowCamera) {
        ChatCameraView(
            otherUserId = conversation.otherParticipantId,
            otherUsername = displayName,
            onSend = { data, type, mode, payload ->
                lifecycle.handleCameraCapture(
                    data = data,
                    mediaType = if (type == CameraPickerMediaType.IMAGE) ChatCameraCapturedMediaType.IMAGE else ChatCameraCapturedMediaType.VIDEO,
                    mode = mode,
                    overlayPayload = payload,
                )
            },
            onDismiss = lifecycle::dismissCamera,
        )
    }
    lifecycle.viewOnceViewerPresentation?.let { presentation ->
        ViewOnceImmersiveViewer(
            message = presentation.message,
            authorName = presentation.authorName,
            onViewed = { lifecycle.handleViewOnceViewerViewed(presentation) },
            isReplaySession = presentation.isReplaySession,
            onReplayConsumed = { lifecycle.handleViewOnceReplayConsumed(presentation) },
            onMediaConsumed = { lifecycle.handleViewOnceMediaConsumed(presentation.message.id) },
            onSendReply = { session.sendTextMessage(it, presentation.message.id) },
            onSendReaction = { session.sendTextMessage(it, presentation.message.id) },
            onOpenCameraReply = { lifecycle.openCameraForReply(presentation.message.id) },
            onDismiss = lifecycle::dismissViewOnceViewer,
        )
    }

    // ≡ showingReactionEmojiPicker
    if (reactionPickerMessage != null) {
        MomentsModalSheet(
            onDismissRequest = { reactionPickerMessage = null },
            largeOnly = false,
        ) {
            EmojiPickerView(
                onDismiss = { reactionPickerMessage = null },
                onSelect = { emoji ->
                    reactionPickerMessage?.let { session.addReaction(it, emoji) }
                    reactionPickerMessage = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ≡ forwardingMessage sheet
    forwardingMessage?.let { message ->
        MomentsModalSheet(
            onDismissRequest = { forwardingMessage = null },
            largeOnly = false,
        ) {
            ChatMessageForwardSheet(
                message = message,
                onDismiss = { forwardingMessage = null },
                onForward = { userIds ->
                    session.forwardTextMessage(message, userIds)
                    forwardingMessage = null
                },
            )
        }
    }


    // ≡ selectedChatMedia / ConversationFullScreenMediaView
    selectedChatMedia?.let { media ->
        ConversationFullScreenMediaView(
            media = media,
            mediaItems = selectedChatMediaItems,
            currentUserId = session.currentUserId,
            otherParticipantName = displayName,
            displayReactions = session::displayReactions,
            onReaction = { messageId, emoji ->
                messages.firstOrNull { it.id == messageId }?.let { session.addReaction(it, emoji) }
            },
            onMoreReactions = { messageId ->
                reactionPickerMessage = messages.firstOrNull { it.id == messageId }
            },
            onClose = {
                selectedChatMedia = null
                selectedChatMediaItems = emptyList()
            },
            onSendReply = { shared, text, completion -> sendReplyToOpenedMedia(shared, text, completion) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // ≡ showingReportSheet for pending request Report
    if (showingUserReportSheet) {
        val otherId = conversation.otherParticipantId.orEmpty()
        if (otherId.isNotBlank()) {
            ReportBottomSheet(
                target = ReportTarget.UserTarget(otherId, displayName),
                onDismiss = { showingUserReportSheet = false },
            )
        } else {
            showingUserReportSheet = false
        }
    }


    // ≡ sheet(item: clusterForReply) → GlassmorphicMediaSelectionSheet
    clusterForReply?.let { cluster ->
        MomentsModalSheet(
            onDismissRequest = { clusterForReply = null },
            largeOnly = false,
        ) {
            GlassmorphicMediaSelectionSheet(
                messages = cluster,
                onSelect = { selected ->
                    activateReply(selected)
                    clusterForReply = null
                },
                onCancel = { clusterForReply = null },
            )
        }
    }

    // ≡ navigationDestination(clusterGallerySelection) → ClusterGalleryView
    clusterGallerySelection?.let { selection ->
        val liveCluster = selection.messageIds.mapNotNull { id ->
            messages.firstOrNull { it.id == id && !it.isDeleted }
        }
        LaunchedEffect(selection.id, liveCluster.map { it.id }) {
            if (liveCluster.isNotEmpty()) session.prefetchClusterGalleryMedia(liveCluster)
        }
        ClusterGalleryView(
            messages = liveCluster,
            currentUserId = session.currentUserId,
            scope = ClusterGalleryScope.CLUSTER,
            presentation = ClusterGalleryPresentation.PUSHED,
            onClose = { clusterGallerySelection = null },
            onOpenMedia = { message ->
                // Solo descarga cuando no hay detail host listo (fallback).
                session.openMediaForViewing(message) { /* download only; user taps again */ }
            },
            onPrepareDownload = { message ->
                session.openMediaForViewing(message) { /* download only; user taps again */ }
            },
            onHydrateMedia = session::hydrateMediaIfNeeded,
            isDownloadingMedia = session::isDownloadingMedia,
            downloadProgress = { downloadProgress[it] },
            onDeleteForMe = { items -> items.forEach(session::deleteMessageForMe) },
            onDeleteForEveryone = { items -> items.forEach(session::deleteMessageForEveryone) },
            detail = { selectedMessage, dismissDetail ->
                // ≡ clusterGalleryDetailView / ClusterGalleryDetailHost
                val media = sharedMediaFrom(selectedMessage)
                if (media != null) {
                    ConversationFullScreenMediaView(
                        media = media,
                        mediaItems = sharedMediaItemsForOverlay(liveCluster, selectedMessage).ifEmpty { listOf(media) },
                        currentUserId = session.currentUserId,
                        otherParticipantName = displayName,
                        displayReactions = session::displayReactions,
                        onReaction = { messageId, emoji ->
                            messages.firstOrNull { it.id == messageId }?.let { session.addReaction(it, emoji) }
                        },
                        onMoreReactions = { messageId ->
                            reactionPickerMessage = messages.firstOrNull { it.id == messageId }
                        },
                        onClose = dismissDetail,
                        onSendReply = { shared, text, completion -> sendReplyToOpenedMedia(shared, text, completion) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // ≡ showVanishTimerSheet / ChatVanishTimerSheet
    if (showVanishTimerSheet) {
        ChatVanishTimerSheet(
            selectedTimer = vanishMessageTimer,
            onSelect = { timer -> session.setVanishMessageTimer(timer) },
            onDismiss = { showVanishTimerSheet = false },
        )
    }

    // ≡ fullScreenCover(item: $storyRoute)
    chatStoryRoute?.let { route ->
        Dialog(
            onDismissRequest = { chatStoryRoute = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            when (route) {
                is ChatStoryRoute.UserStories -> StoriesView(
                    startWithUserId = route.userId,
                    onDismiss = { chatStoryRoute = null },
                )
                is ChatStoryRoute.SharedStory -> StoriesView(
                    explicitStories = listOf(route.story),
                    startAtIndex = 0,
                    onDismiss = { chatStoryRoute = null },
                )
            }
        }
    }

    // ≡ sheet(isPresented: $showingMomentDetail)
    selectedMoment?.let { moment ->
        MomentsModalSheet(
            onDismissRequest = { selectedMoment = null },
            largeOnly = true,
        ) {
            MomentDetailContainerView(
                context = MomentDetailContext.Single(moment.toExploreFeedMoment()),
                onDismiss = { selectedMoment = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ≡ alert chat.moment.loadError
    if (showingMomentError) {
        AlertDialog(
            onDismissRequest = { showingMomentError = false },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(stringResource(R.string.chat_moment_load_error)) },
            confirmButton = {
                TextButton(onClick = { showingMomentError = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    // ≡ alert showingStoryUnavailable
    storyUnavailableReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { storyUnavailableReason = null },
            title = { Text(stringResource(reason.titleRes)) },
            text = { Text(stringResource(reason.messageRes)) },
            confirmButton = {
                TextButton(onClick = { storyUnavailableReason = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun messageIds(item: MessageItem): Set<String> = when (item) {
    is MessageItem.Single -> setOf(item.message.id)
    is MessageItem.MediaCluster -> item.messages.mapTo(linkedSetOf()) { it.id }
}
