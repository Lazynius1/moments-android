package com.moments.android.views.messaging.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.models.PendingChatContext
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ChatAttachmentMediaAsset
import com.moments.android.views.messaging.components.ChatAttachmentMediaSheetOverlay
import com.moments.android.views.messaging.components.ChatAttachmentMenuPopover
import com.moments.android.views.messaging.components.ChatAttachmentPickerSheet
import com.moments.android.views.messaging.components.ChatAttachmentSheetKind
import com.moments.android.views.messaging.components.ChatBuzzTimelineEventRow
import com.moments.android.views.messaging.components.ChatFloatingNavigationOverlay
import com.moments.android.views.messaging.components.ChatFloatingNavigationState
import com.moments.android.views.messaging.components.GlassmorphicTypingIndicator
import com.moments.android.views.messaging.components.GlassmorphicUnreadDivider
import com.moments.android.views.messaging.components.rememberChatMessageListController
import com.moments.android.views.messaging.media.CameraPickerMediaType
import com.moments.android.views.messaging.media.ChatCameraView
import com.moments.android.views.messaging.media.ViewOnceImmersiveViewer
import com.moments.android.views.messaging.core.MessageItem
import com.moments.android.views.messaging.services.ChatDraftStore
import com.moments.android.views.messaging.services.ChatScrollTarget
import com.moments.android.views.messaging.services.ViewOnceReplaySessionStore
import java.util.UUID

/** Port de `Views/Messaging/Screens/Chat/GlassmorphicChatView.swift`. */
data class ChatStoryRoute(val userId: String)

@Composable
fun GlassmorphicChatView(
    conversation: Conversation,
    session: MomentsChatViewModel = remember(conversation.id) {
        MomentsChatViewModel(conversation, FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    val messages by session.messages.collectAsState()
    val searching by session.isSearchingHistory.collectAsState()
    val canLoadMore by session.canLoadMore.collectAsState()
    val vanishModeActive by session.vanishModeActive.collectAsState()
    val listController = rememberChatMessageListController()
    val messagePresentation = rememberChatMessagePresentationState()
    val listPresentation = rememberChatMessageListPresentation()
    val unreadDivider = remember(session) { ChatUnreadDividerController(session) }
    val voice = remember(session) { GlassmorphicChatVoiceController(session) }
    val composer = rememberChatComposerAndChromeController(pendingChatContext)
    var messageText by remember(conversation.id) { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<EnhancedMessage?>(null) }
    var editingMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var attachmentSheet by remember { mutableStateOf<ChatAttachmentSheetKind?>(null) }

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
            ),
        )
    }
    val search = remember(session, scroll) { GlassmorphicChatSearchController(session, scroll) }
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
            ),
        )
    }
    val displayName = lifecycle.liveOtherParticipantUsername.ifBlank {
        conversation.otherParticipantUsername.orEmpty()
    }
    val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

    fun sendAssets(assets: List<ChatAttachmentMediaAsset>) {
        val batchId = UUID.randomUUID().toString()
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

    LaunchedEffect(messages) {
        session.syncMessagePresentation()
        unreadDivider.initialize()
        scroll.routeInitialScroll()
    }
    LaunchedEffect(conversation.id) {
        messageText = ChatDraftStore.draft(context, conversation.id.orEmpty())
        session.activateChatSession()
        session.markVisibleConversationAsRead()
        lifecycle.setupOnlineStatusObserver()
        lifecycle.refreshOtherParticipantUsername()
        lifecycle.refreshOtherParticipantAvailability()
        scroll.configureListInitialScrollPolicy()
    }
    DisposableEffect(session, lifecycle) {
        onDispose {
            voice.resetVoiceRecordingInteraction()
            lifecycle.dispose()
            session.deactivateChatSession()
        }
    }

    val rows = remember(session.chatRenderRows, pendingChatContext, canLoadMore, messages, scroll.hasCompletedInitialScroll, session.typingUsers.value) {
        chatListRows(
            baseRows = session.chatRenderRows,
            pendingChatContext = pendingChatContext,
            conversationIntroContext = null,
            pendingTimelineMessage = composer.pendingChatTimelineMessage,
            canLoadMore = canLoadMore,
            hasCompletedInitialScroll = scroll.hasCompletedInitialScroll,
            hasTypingUsers = session.typingUsers.value.isNotEmpty(),
        )
    }
    val transaction = chatListTransaction(session, rows) { id -> rows.firstOrNull { id in rowMessageIds(it) }?.id }
    val renderer = ChatMessageRendererCallbacks(
        otherParticipantName = displayName,
        otherParticipantId = conversation.otherParticipantId,
        isOtherParticipantUnavailable = lifecycle.isOtherParticipantUnavailable,
        shouldShowAvatar = lifecycle::shouldShowAvatar,
        groupPosition = lifecycle::messageGroupPosition,
        onReply = { message -> replyingTo = message; editingMessage = null },
        onAvatarTap = { onProfile(conversation.otherParticipantId) },
        onOpenMedia = onOpenMedia,
        onOpenCluster = onOpenCluster,
        onMomentNavigation = onMomentNavigation,
        onStoryNavigation = onStoryNavigation,
        onViewOnceOpen = { message, replay -> lifecycle.presentViewOnceViewer(message, replay, displayName, currentUserName) },
        onHydrateMedia = session::hydrateMediaIfNeeded,
        onChangeVanishTimer = {},
        onTurnOnVanish = { session.toggleVanishMode() },
    )

    GlassmorphicChatRootContent(
        adaptiveColors = colors,
        viewModel = session,
        messagePresentation = messagePresentation,
        buzzToastText = null,
        isSearchVisible = search.isSearchVisible,
        composerHeight = scroll.lastComposerHeight ?: 68.dp,
        callbacks = ChatMessageRenderingCallbacks(
            renderer = renderer,
            buzzText = { event ->
                if (event.senderId == session.currentUserId) context.getString(R.string.chat_buzz_sent)
                else context.getString(R.string.chat_buzz_received, displayName)
            },
            onReplyCancelled = { replyingTo = null },
            onEditCancelled = { editingMessage = null },
            onEditingStarted = { message -> editingMessage = message; replyingTo = null; messageText = message.content.orEmpty() },
        ),
        modifier = modifier,
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
                    callbacks = ChatMessageListCallbacks(
                        loadOlderHistory = scroll::loadOlderHistoryIfNeeded,
                        retryHistoryLoad = scroll::loadOlderHistoryIfNeeded,
                        onRowsChanged = scroll::routeInitialScroll,
                        onContentExtentChanged = scroll::updateContentExtent,
                        renderMessage = { item ->
                            if (unreadDivider.shouldShowBefore(messageIds(item), canLoadMore)) {
                                GlassmorphicUnreadDivider(unreadDivider.dividerCount)
                            }
                            GlassmorphicChatMessageItem(item, messages, session, messagePresentation, renderer, "❤️")
                        },
                        renderHeader = { header -> GlassmorphicDateHeader(header.date) },
                        renderBuzz = { buzz ->
                            ChatBuzzTimelineEventRow(
                                text = if (buzz.event.senderId == session.currentUserId) stringResource(R.string.chat_buzz_sent) else stringResource(R.string.chat_buzz_received, displayName),
                                isOutgoing = buzz.event.senderId == session.currentUserId,
                            )
                        },
                        renderTyping = { GlassmorphicTypingIndicator(reduceMotion = false, modifier = Modifier.padding(horizontal = 16.dp)) },
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
                    canSearchGoUp = search.currentSearchMatchId != null,
                    canSearchGoDown = search.currentSearchMatchId != null,
                    pendingIncomingCount = scroll.pendingIncomingMessages,
                    accentColor = colors.accent,
                    badgeTextColor = colors.surfaceBackground,
                    reduceMotion = false,
                    onSearchPrevious = { search.moveSearchSelection(-1) },
                    onSearchNext = search::advanceSearchSelection,
                    onScrollToBottom = scroll::scrollToBottomFromUserAction,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        },
        composer = {
            ChatComposerChrome(
                controller = composer,
                messageText = messageText,
                onMessageTextChange = { text ->
                    messageText = text
                    ChatDraftStore.setDraft(context, text, conversation.id.orEmpty())
                },
                isOtherParticipantBlockedByCurrentUser = lifecycle.isOtherParticipantBlockedByCurrentUser,
                isOtherParticipantUnavailable = lifecycle.isOtherParticipantUnavailable,
                otherParticipantDisplayName = displayName,
                vanishModeActive = vanishModeActive,
                isRecordingVoice = voice.isRecording,
                isVoiceRecordingLocked = voice.isLocked,
                recordingSeconds = voice.recordingTime.toLong(),
                editingMessage = editingMessage,
                replyingTo = replyingTo,
                onEditingFinished = { editingMessage = null },
                onReplyingFinished = { replyingTo = null },
                onUnblock = lifecycle::unblockOtherParticipantFromChat,
                onOpenAttachments = { attachmentSheet = ChatAttachmentSheetKind.MENU },
                onStartVoiceRecording = {
                    context.findActivity()?.let { voice.startVoiceRecording(it, UUID.randomUUID().toString(), false) }
                },
                onFinishVoiceRecording = { send ->
                    voice.interactionId?.let { id ->
                        voice.finishVoiceRecording(id, if (send) com.moments.android.views.messaging.components.VoiceRecordingFinishAction.SEND else com.moments.android.views.messaging.components.VoiceRecordingFinishAction.CANCEL)
                    }
                },
                onReport = onReport,
                viewModel = session,
            )
        },
    )

    GlassmorphicChatToolbar(
        displayName = displayName,
        userId = conversation.otherParticipantId,
        profileImagePath = conversation.otherParticipantProfileImagePath,
        adaptiveColors = colors,
        isUnavailable = lifecycle.isOtherParticipantUnavailable,
        isBlockedByMe = lifecycle.isOtherParticipantBlockedByCurrentUser,
        hasStory = false,
        hasTypingUsers = session.typingUsers.value.isNotEmpty(),
        presence = null,
        callbacks = ChatToolbarCallbacks(
            onBack = onBack,
            onProfile = { onProfile(conversation.otherParticipantId) },
            onStory = { onStory(ChatStoryRoute(conversation.otherParticipantId)) },
            onSettings = onSettings,
            onSearchClose = search::toggleChatSearch,
            onSearchClear = { search.updateSearchQuery("") },
            onSearchSubmit = search::scrollToCurrentSearchMatch,
        ),
    )
    if (search.isSearchVisible) {
        GlassmorphicChatSearchHeader(
            query = search.searchQuery,
            onQueryChange = search::updateSearchQuery,
            isSearching = searching,
            adaptiveColors = colors,
            callbacks = ChatToolbarCallbacks(
                onSearchClose = search::toggleChatSearch,
                onSearchClear = { search.updateSearchQuery("") },
                onSearchSubmit = search::scrollToCurrentSearchMatch,
            ),
        )
    }

    ChatAttachmentMenuPopover(
        isPresented = attachmentSheet,
        anchorBounds = chatAttachmentMenuAnchor(),
        canSendBuzz = session.canSendBuzz,
        onDismiss = { attachmentSheet = null },
        onOpenCamera = lifecycle::openCamera,
        onSendBuzz = { session.sendBuzz(); attachmentSheet = null },
        onSheetSelected = { attachmentSheet = it },
    )
    ChatAttachmentMediaSheetOverlay(
        activeSheet = attachmentSheet,
        accentColor = colors.accent,
        onPickerUris = { uris ->
            sendAssets(uris.map { uri -> ChatAttachmentMediaAsset(uri.toString(), uri, false, 0) })
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
            onSendReply = { session.sendTextMessage(it, presentation.message.id) },
            onSendReaction = { session.sendTextMessage(it, presentation.message.id) },
            onOpenCameraReply = { lifecycle.openCameraForReply(presentation.message.id) },
            onDismiss = lifecycle::dismissViewOnceViewer,
        )
    }
}

@Composable
private fun chatAttachmentMenuAnchor(): IntRect {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val height = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val top = with(density) { (configuration.screenHeightDp.dp - 64.dp).roundToPx() }
    return IntRect(left = 16, top = top.coerceAtLeast(0), right = 184, bottom = height)
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
