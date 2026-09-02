package com.moments.android.views.messaging.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Forum
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.OnlineStatus
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.services.messaging.displayName
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.components.MomentRefreshOverlayHost
import com.moments.android.views.components.momentRefresh
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ChatRecoveryGateView
import com.moments.android.views.messaging.components.ConversationContextMenuInsets
import com.moments.android.views.messaging.components.ConversationContextMenuOverlay
import com.moments.android.views.messaging.components.ConversationListInteraction
import com.moments.android.views.messaging.components.ConversationMenuData
import com.moments.android.views.messaging.components.ConversationMenuSelection
import com.moments.android.views.messaging.components.MessagingActionToast
import com.moments.android.views.messaging.components.OnlineStatusSelectorView
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.ChatTextMarkup
import com.moments.android.views.messaging.core.GlobalMessageSearchResult
import com.moments.android.views.messaging.core.MessageRequest
import com.moments.android.views.messaging.core.MessagingPresentationRoute
import com.moments.android.views.messaging.core.MessagingViewModel
import com.moments.android.views.messaging.core.PendingChatContext
import com.moments.android.views.messaging.core.PendingChatContextFactory
import com.moments.android.views.messaging.core.ProfileMessagePresentation
import com.moments.android.views.messaging.core.consumeProfileMessagePresentation
import com.moments.android.views.messaging.screens.chat.ChatStoryRoute
import com.moments.android.views.messaging.screens.chat.GlassmorphicChatView
import com.moments.android.views.messaging.services.ChatDraftEvent
import com.moments.android.views.messaging.services.ChatDraftEvents
import com.moments.android.views.messaging.services.ConversationMuteEvents
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.story.StoriesView
import com.moments.android.adaptive.LocalAdaptiveWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Port de `MessagingView.swift` — bandeja + destinations (chat / new / archived / requests / pending).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagingView(
    targetConversationId: String? = null,
    onTargetConversationIdConsumed: () -> Unit = {},
    onDismiss: () -> Unit = {},
    /** ≡ iOS `onDismiss == nil`: sin chevron; compose a la izquierda. */
    embeddedInTab: Boolean = false,
    /** ≡ iOS `momentsFloatingTabBarHidden` en GlassmorphicChatView. */
    onSuppressTabBarChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** ≡ EnvironmentObject iOS: el feed reutiliza el mismo VM para `presentationRoute`. */
    messagingViewModel: MessagingViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val viewModel = messagingViewModel ?: remember { MessagingViewModel() }
    val requestService = remember { MessageRequestService() }
    val onlineStatusService = remember { OnlineStatusService.shared }
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val adaptiveWindow = LocalAdaptiveWindowState.current

    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showingNewConversation by remember { mutableStateOf(false) }
    var showingRequests by remember { mutableStateOf(false) }
    var showingArchived by remember { mutableStateOf(false) }
    var showingStatusSelector by remember { mutableStateOf(false) }
    var pendingChatContext by remember { mutableStateOf<PendingChatContext?>(null) }
    var conversationMenuSelection by remember { mutableStateOf<ConversationMenuSelection?>(null) }
    var conversationRowFrames by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var actionToastMessage by remember { mutableStateOf<String?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var storyUserId by remember { mutableStateOf<String?>(null) }
    // ≡ iOS MessagingView.profileRoute → UserProfileView
    var profileUserId by remember { mutableStateOf<String?>(null) }

    fun openConversationProfile(userId: String) {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return
        profileUserId = trimmed
    }

    val pendingRequests by requestService.pendingRequests.collectAsState()
    val outgoingPending by requestService.outgoingPendingRequests.collectAsState()
    val currentStatus by onlineStatusService.currentUserStatus.collectAsState()
    val pendingRequestCount = pendingRequests.size

    // ≡ iOS: lista de conversaciones muestra tab bar; chat individual la oculta.
    val suppressTabBar =
        (viewModel.selectedConversation != null && !adaptiveWindow.supportsTwoPanes) ||
            pendingChatContext != null
    LaunchedEffect(suppressTabBar) {
        onSuppressTabBarChange(suppressTabBar)
    }
    DisposableEffect(Unit) {
        onDispose { onSuppressTabBarChange(false) }
    }

    LaunchedEffect(pendingRequestCount) {
        // ≡ MessagingView.updatePendingRequestCount → widget_pending_message_requests
        com.moments.android.widget.MomentsWidgetStore.putInt(
            com.moments.android.widget.MomentsWidgetStore.KEY_PENDING_MESSAGE_REQUESTS,
            pendingRequestCount,
        )
    }

    fun showToast(message: String) {
        actionToastMessage = message
        scope.launch {
            delay(2200)
            if (actionToastMessage == message) actionToastMessage = null
        }
    }

    fun consumePresentationRoute() {
        val route = viewModel.presentationRoute ?: return
        viewModel.presentationRoute = null
        showingNewConversation = false
        when (route) {
            is MessagingPresentationRoute.Conversation -> viewModel.openConversation(route.conversation)
            is MessagingPresentationRoute.PendingChat -> pendingChatContext = route.context
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start(targetConversationId)
        uid?.let {
            requestService.listenToPendingRequests(it)
            requestService.listenToOutgoingPendingRequests(it)
        }
        consumePresentationRoute()
    }
    LaunchedEffect(viewModel.presentationRoute) {
        consumePresentationRoute()
    }
    LaunchedEffect(targetConversationId) {
        if (!targetConversationId.isNullOrBlank()) {
            viewModel.onTargetConversationId(targetConversationId)
            onTargetConversationIdConsumed()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300_000)
            viewModel.refreshVisibleUsers()
        }
    }
    LaunchedEffect(Unit) {
        ChatDraftEvents.events.collectLatest { event ->
            when (event) {
                is ChatDraftEvent.Changed -> viewModel.refreshDraftOrdering()
                is ChatDraftEvent.VanishModeChanged ->
                    viewModel.updateVanishMode(event.conversationId, event.vanishModeActive)
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        ConversationMuteEvents.events.collectLatest { event ->
            viewModel.applyLocalConversationState(
                conversationId = event.conversationId,
                isMuted = event.isMuted,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            actionToastMessage = null
            viewModel.stopListening()
            requestService.removeAllListeners()
        }
    }

    // ≡ iOS .navigationDestination(item: $profileRoute) — overlay sin destruir el chat/settings
    profileUserId?.let { userId ->
        Dialog(
            onDismissRequest = { profileUserId = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                UserProfileView(
                    userId = userId,
                    onDismiss = { profileUserId = null },
                )
            }
        }
    }

    when {
        storyUserId != null -> {
            BackHandler { storyUserId = null }
            StoriesView(startWithUserId = storyUserId, onDismiss = { storyUserId = null })
            return
        }
        showingNewConversation -> {
            BackHandler { showingNewConversation = false }
            GlassmorphicNewConversationView(
                viewModel = viewModel,
                onDismiss = { showingNewConversation = false },
                onConversationReady = { conversation ->
                    showingNewConversation = false
                    viewModel.openConversation(conversation)
                },
                onNeedsRequest = { context ->
                    pendingChatContext = context
                    showingNewConversation = false
                },
            )
            return
        }
        showingRequests -> {
            BackHandler { showingRequests = false }
            Column(Modifier.fillMaxSize().background(colors.surfaceBackground).statusBarsPadding()) {
                MessagingDestinationHeader(
                    title = stringResource(R.string.message_requests_title),
                    onBack = { showingRequests = false },
                )
                MessageRequestsView(
                    service = requestService,
                    onOpenRequest = { request ->
                        showingRequests = false
                        val fallback = context.getString(R.string.messaging_user_default)
                        pendingChatContext = PendingChatContext.incoming(request, fallback)
                    },
                )
            }
            return
        }
        showingArchived -> {
            BackHandler { showingArchived = false }
            ArchivedConversationsView(
                viewModel = viewModel,
                onBack = { showingArchived = false },
                onOpenConversation = {
                    showingArchived = false
                    viewModel.openConversation(it)
                },
                onOpenProfile = { openConversationProfile(it) },
                onOpenStory = { storyUserId = it },
                onMarkUnread = { viewModel.markConversationAsUnread(it) },
                onPin = { viewModel.togglePinned(it) },
                onMute = { viewModel.toggleMuted(it) },
                onUnarchive = { viewModel.unarchiveConversation(it) },
                onDelete = { viewModel.deleteConversation(it) },
            )
            return
        }
        pendingChatContext != null -> {
            val ctx = pendingChatContext!!
            Box(Modifier.fillMaxSize().background(colors.chatBackground.first())) {
                GlassmorphicChatView(
                    conversation = ctx.syntheticConversation(uid.orEmpty()),
                    pendingChatContext = ctx,
                    onBack = { pendingChatContext = null },
                    onProfile = { openConversationProfile(it) },
                    onPendingChatAccepted = { conversationId ->
                        val currentUserId = uid.orEmpty()
                        if (currentUserId.isEmpty()) {
                            pendingChatContext = null
                            return@GlassmorphicChatView
                        }
                        val accepted = Conversation(
                            id = conversationId,
                            participants = listOf(currentUserId, ctx.otherUserId).sorted(),
                            lastMessage = ctx.initialText,
                            timestamp = java.util.Date(),
                            readStatus = mapOf(currentUserId to true, ctx.otherUserId to true),
                            otherParticipantId = ctx.otherUserId,
                            otherParticipantUsername = ctx.otherUsername,
                            otherParticipantProfileImagePath = ctx.otherProfileImagePath,
                        )
                        pendingChatContext = null
                        viewModel.openConversation(accepted)
                        viewModel.fetchConversations(currentUserId)
                    },
                    onPendingChatDismissed = { pendingChatContext = null },
                )
            }
            return
        }
    }

    @Composable
    fun ConversationListPane() {
        Column(Modifier.fillMaxSize().background(colors.surfaceBackground)) {
            MessagingToolbar(
                onDismiss = onDismiss,
                embeddedInTab = embeddedInTab,
                onCompose = { showingNewConversation = true },
                onRequests = { showingRequests = true },
                onStatus = { showingStatusSelector = true },
                pendingRequestCount = pendingRequestCount,
                currentStatus = currentStatus,
            )
            val showSearch =
                viewModel.conversations.isNotEmpty() ||
                    viewModel.archivedConversations.isNotEmpty() ||
                    outgoingPending.isNotEmpty() ||
                    isSearching
            if (showSearch) {
                MessagingSearchBar(
                    searchText = searchText,
                    isSearchFocused = isSearchFocused,
                    onSearchTextChange = { value ->
                        searchText = value
                        isSearching = value.isNotEmpty()
                        if (value.isNotEmpty()) viewModel.searchConversationsAndUsers(value)
                        else viewModel.clearSearch()
                    },
                    onFocusChange = { isSearchFocused = it },
                    onCancel = {
                        searchText = ""
                        isSearching = false
                        isSearchFocused = false
                        viewModel.clearSearch()
                    },
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .momentRefresh {
                        uid?.takeIf { it.isNotBlank() }?.let { viewModel.fetchConversations(it) }
                        kotlinx.coroutines.delay(700)
                    },
            ) {
                MessagingConversationList(
                    viewModel = viewModel,
                    outgoingPending = outgoingPending,
                    isSearching = isSearching,
                    searchText = searchText,
                    conversationMenuSelection = conversationMenuSelection,
                    onOpenConversation = { viewModel.openConversation(it) },
                    onOpenArchived = { showingArchived = true },
                    onOpenOutgoing = { user ->
                        scope.launch {
                            val current = uid ?: return@launch
                            pendingChatContext = PendingChatContextFactory.outgoing(user, current)
                        }
                    },
                    onOpenStory = { storyUserId = it },
                    onCompose = { showingNewConversation = true },
                    onLongPressConversation = { conv ->
                        val id = conv.id ?: return@MessagingConversationList
                        val frame = conversationRowFrames[id] ?: return@MessagingConversationList
                        if (frame.width <= 0f || frame.height <= 0f) return@MessagingConversationList
                        conversationMenuSelection = ConversationMenuSelection(
                            item = ConversationMenuData(
                                conversation = conv,
                                unreadCount = conv.unreadCount(uid.orEmpty()),
                                isPinned = conv.isPinned(uid),
                                isMuted = conv.isMuted(uid),
                                isArchived = false,
                            ),
                            rowFrame = frame,
                        )
                    },
                    onRowFrame = { id, rect ->
                        conversationRowFrames = conversationRowFrames + (id to rect)
                    },
                    onOpenSearchMessage = { result ->
                        viewModel.openConversation(result.conversation)
                        searchText = ""
                        isSearching = false
                        viewModel.clearSearch()
                    },
                    onStartDraftWithUser = { user ->
                        searchText = ""
                        isSearching = false
                        viewModel.clearSearch()
                        val current = uid ?: return@MessagingConversationList
                        viewModel.startConversation(user = user, fromUserId = current) { conversation ->
                            scope.launch {
                                val presentation = viewModel.consumeProfileMessagePresentation(
                                    conversation = conversation,
                                    user = user,
                                    currentUserId = current,
                                ) ?: return@launch
                                when (val destination = presentation.destination) {
                                    is ProfileMessagePresentation.Destination.OpenConversation ->
                                        viewModel.openConversation(destination.conversation)
                                    is ProfileMessagePresentation.Destination.PendingChat ->
                                        pendingChatContext = destination.context
                                }
                            }
                        }
                    },
                )
                MomentRefreshOverlayHost(Modifier.align(Alignment.TopCenter))
            }
        }
    }

    ChatRecoveryGateView(onCancel = onDismiss) {
        val selected = viewModel.selectedConversation
        Box(
            modifier
                .fillMaxSize()
                // Chat: mismo canvas hasta el status bar (no surface blanco encima).
                .background(
                    if (selected != null) colors.chatBackground.first() else colors.surfaceBackground,
                )
                .padding(contentPadding)
                .onSizeChanged { containerSize = it },
        ) {
            if (selected != null && adaptiveWindow.supportsTwoPanes) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val listPaneWidth = if (maxWidth < 720.dp) {
                        (maxWidth * 0.40f).coerceIn(220.dp, 300.dp)
                    } else {
                        360.dp
                    }
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(listPaneWidth).fillMaxHeight()) {
                            ConversationListPane()
                        }
                        VerticalDivider(color = colors.primary.copy(alpha = 0.10f))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            GlassmorphicChatView(
                                conversation = selected,
                                onBack = { viewModel.closeChat() },
                                showBackButton = false,
                                onProfile = { openConversationProfile(it) },
                                onStory = { route ->
                                    when (route) {
                                        is ChatStoryRoute.UserStories -> storyUserId = route.userId
                                        is ChatStoryRoute.SharedStory -> Unit
                                    }
                                },
                            )
                        }
                    }
                }
            } else if (selected != null) {
                GlassmorphicChatView(
                    conversation = selected,
                    onBack = { viewModel.closeChat() },
                    onProfile = { openConversationProfile(it) },
                    onStory = { route ->
                        when (route) {
                            is ChatStoryRoute.UserStories -> storyUserId = route.userId
                            is ChatStoryRoute.SharedStory -> Unit // cubierto dentro del chat
                        }
                    },
                )
            } else {
                ConversationListPane()
            }

            ConversationContextMenuOverlay(
                selection = conversationMenuSelection,
                containerSize = containerSize,
                safeAreaInsets = ConversationContextMenuInsets(),
                onDismiss = { conversationMenuSelection = null },
                onMarkUnread = {
                    viewModel.markConversationAsUnread(it)
                    conversationMenuSelection = null
                    showToast(context.getString(R.string.messaging_menu_mark_unread))
                },
                onPin = {
                    viewModel.togglePinned(it)
                    conversationMenuSelection = null
                },
                onMute = {
                    viewModel.toggleMuted(it)
                    conversationMenuSelection = null
                },
                onArchive = {
                    viewModel.archiveConversation(it)
                    conversationMenuSelection = null
                    showToast(context.getString(R.string.messaging_menu_archive))
                },
                onUnarchive = {
                    viewModel.unarchiveConversation(it)
                    conversationMenuSelection = null
                },
                onDelete = {
                    viewModel.deleteConversation(it)
                    conversationMenuSelection = null
                },
            )

            actionToastMessage?.let { msg ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    MessagingActionToast(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    if (showingStatusSelector) {
        OnlineStatusSelectorView(
            currentStatus = currentStatus,
            onStatusSelected = { onlineStatusService.setStatus(it) },
            onDismiss = { showingStatusSelector = false },
        )
    }
}

@Composable
private fun MessagingDestinationHeader(title: String, onBack: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = colors.primary)
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.primary)
    }
}

@Composable
private fun MessagingToolbar(
    onDismiss: () -> Unit,
    embeddedInTab: Boolean = false,
    onCompose: () -> Unit,
    onRequests: () -> Unit,
    onStatus: () -> Unit,
    pendingRequestCount: Int,
    currentStatus: OnlineStatus,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ≡ iOS: tab → compose leading; overlay → chevron dismiss.
        if (embeddedInTab) {
            IconButton(onClick = onCompose) {
                Icon(Icons.Filled.Create, stringResource(R.string.messaging_new_conversation), tint = colors.primary)
            }
        } else {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = colors.primary)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.messaging_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = colors.primary,
                maxLines = 1,
            )
            Row(
                Modifier.clickable(onClick = onStatus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(onlineStatusColor(currentStatus)),
                )
                Text(
                    currentStatus.displayName(context),
                    fontSize = 11.sp,
                    color = colors.secondary,
                    maxLines = 1,
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = colors.secondary, modifier = Modifier.size(10.dp))
            }
        }
        if (!embeddedInTab) {
            IconButton(onClick = onCompose) {
                Icon(Icons.Filled.Create, stringResource(R.string.messaging_new_conversation), tint = colors.primary)
            }
        }
        Box {
            IconButton(onClick = onRequests) {
                Icon(Icons.Outlined.Forum, stringResource(R.string.message_requests_title), tint = colors.primary)
            }
            if (pendingRequestCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$pendingRequestCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun onlineStatusColor(status: OnlineStatus): Color = when (status) {
    OnlineStatus.ONLINE -> Color(0xFF34C759)
    OnlineStatus.AWAY -> Color(0xFFFF9500)
    OnlineStatus.BUSY -> Color(0xFFFF3B30)
    OnlineStatus.OFFLINE -> Color.Gray
    OnlineStatus.INVISIBLE -> Color(0xFF8E8E93)
}

@Composable
private fun MessagingSearchBar(
    searchText: String,
    isSearchFocused: Boolean,
    onSearchTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val focusRequester = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.messaging_search_placeholder),
                            color = colors.secondary,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    tint = colors.secondary,
                    modifier = Modifier.size(16.dp).clickable(onClick = onCancel),
                )
            }
        }
        if (isSearchFocused) {
            Text(
                stringResource(R.string.common_cancel),
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
    }
}

private sealed class MergedListRow {
    abstract val timestamp: Date
    data class ConversationItem(val conversation: Conversation) : MergedListRow() {
        override val timestamp: Date get() = conversation.timestamp
    }
    data class OutgoingRequestItem(val request: MessageRequest) : MergedListRow() {
        override val timestamp: Date get() = request.timestamp
    }
}

@Composable
private fun MessagingConversationList(
    viewModel: MessagingViewModel,
    outgoingPending: List<MessageRequest>,
    isSearching: Boolean,
    searchText: String,
    conversationMenuSelection: ConversationMenuSelection?,
    onOpenConversation: (Conversation) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenOutgoing: (AppUser) -> Unit,
    onOpenStory: (String) -> Unit,
    onCompose: () -> Unit,
    onLongPressConversation: (Conversation) -> Unit,
    onRowFrame: (String, Rect) -> Unit,
    onOpenSearchMessage: (GlobalMessageSearchResult) -> Unit,
    onStartDraftWithUser: (AppUser) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val error = viewModel.errorMessage
    val emptyInbox =
        viewModel.conversations.isEmpty() &&
            viewModel.archivedConversations.isEmpty() &&
            outgoingPending.isEmpty() &&
            !isSearching

    when {
        viewModel.isLoading && viewModel.conversations.isEmpty() && !isSearching -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MomentsCircularProgressIndicator()
            }
        }
        error != null && viewModel.conversations.isEmpty() && !isSearching -> {
            MessagingErrorState(
                message = error,
                onRetry = {
                    if (uid.isNotBlank()) viewModel.fetchConversations(uid) else viewModel.start(null)
                },
            )
        }
        emptyInbox -> MessagingEmptyState(onCompose = onCompose)
        isSearching -> MessagingSearchResults(
            viewModel = viewModel,
            searchText = searchText,
            onOpenConversation = onOpenConversation,
            onOpenStory = onOpenStory,
            onOpenSearchMessage = onOpenSearchMessage,
            onStartDraftWithUser = onStartDraftWithUser,
        )
        else -> {
            val merged = remember(viewModel.conversations, outgoingPending) {
                val convRows = viewModel.conversations
                    .filter { !it.id.isNullOrBlank() }
                    .map { MergedListRow.ConversationItem(it) }
                val reqRows = outgoingPending.map { MergedListRow.OutgoingRequestItem(it) }
                (convRows + reqRows).sortedByDescending { it.timestamp.time }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                userScrollEnabled = conversationMenuSelection == null,
            ) {
                if (viewModel.archivedConversations.isNotEmpty()) {
                    item(key = "archived-entry") {
                        ArchivedEntryRow(
                            unreadCount = viewModel.archivedUnreadCount(uid),
                            onClick = onOpenArchived,
                        )
                    }
                }
                items(
                    merged,
                    key = {
                        when (it) {
                            is MergedListRow.ConversationItem -> "c:${it.conversation.id}"
                            is MergedListRow.OutgoingRequestItem -> "r:${it.request.id ?: it.request.receiverId}"
                        }
                    },
                ) { row ->
                    when (row) {
                        is MergedListRow.ConversationItem -> {
                            val conv = row.conversation
                            val id = conv.id.orEmpty()
                            val selected = conversationMenuSelection?.item?.conversation?.id == id
                            GlassmorphicConversationRow(
                                conversation = conv,
                                onOpenProfile = { /* profile destination stub */ },
                                onTap = { onOpenConversation(conv) },
                                onOpenStory = onOpenStory,
                                isMenuSelected = selected,
                                listInteraction = ConversationListInteraction(
                                    onTap = { onOpenConversation(conv) },
                                    onLongPress = { onLongPressConversation(conv) },
                                    onPressingChanged = {},
                                ),
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    if (id.isNotBlank()) onRowFrame(id, coords.boundsInRoot())
                                },
                            )
                        }
                        is MergedListRow.OutgoingRequestItem -> {
                            OutgoingSentRequestRow(request = row.request, onOpen = onOpenOutgoing)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagingErrorState(message: String, onRetry: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxSize()
            .momentsEmptyStateAppear()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, null, tint = colors.primary, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(message, color = colors.secondary, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .clickable(onClick = onRetry)
                .padding(horizontal = 22.dp)
                .height(50.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.messaging_retry),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun MessagingEmptyState(onCompose: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxSize()
            .momentsEmptyStateAppear()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Forum, null, tint = colors.primary, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.messaging_no_conversations_title),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.messaging_no_conversations_subtitle),
            color = colors.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .clickable(onClick = onCompose)
                .padding(horizontal = 22.dp)
                .height(50.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.messaging_new_conversation),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun ArchivedEntryRow(unreadCount: Int, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Archive, null, tint = colors.primary.copy(0.85f), modifier = Modifier.size(22.dp))
        Text(
            if (unreadCount > 0) {
                stringResource(R.string.messaging_section_archived_with_unread, unreadCount)
            } else {
                stringResource(R.string.messaging_section_archived)
            },
            color = colors.primary.copy(0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colors.primary.copy(0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OutgoingSentRequestRow(
    request: MessageRequest,
    onOpen: (AppUser) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val firestore = remember { FirestoreService() }
    var receiver by remember(request.receiverId) { mutableStateOf<AppUser?>(null) }
    LaunchedEffect(request.receiverId) {
        receiver = runCatching { firestore.fetchUser(request.receiverId) }.getOrNull()
            ?: runCatching {
                firestore.fetchUsers(listOf(request.receiverId)).firstOrNull()
            }.getOrNull()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = receiver != null) { receiver?.let(onOpen) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncProfileImageView(request.receiverId, Modifier.size(48.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                receiver?.username ?: " ",
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Create, null, tint = colors.secondary, modifier = Modifier.size(12.dp))
                Text(
                    stringResource(R.string.messaging_sent_request_badge),
                    color = colors.secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colors.secondary.copy(0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MessagingSearchResults(
    viewModel: MessagingViewModel,
    searchText: String,
    onOpenConversation: (Conversation) -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenSearchMessage: (GlobalMessageSearchResult) -> Unit,
    onStartDraftWithUser: (AppUser) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val empty =
        viewModel.filteredConversations.isEmpty() &&
            viewModel.searchedUsers.isEmpty() &&
            viewModel.searchedMessages.isEmpty() &&
            searchText.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        if (viewModel.filteredConversations.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.messaging_conversations),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = colors.secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            items(viewModel.filteredConversations, key = { "fc:${it.id}" }) { conversation ->
                GlassmorphicConversationRow(
                    conversation = conversation,
                    onOpenProfile = {},
                    onTap = { onOpenConversation(conversation) },
                    onOpenStory = onOpenStory,
                )
            }
        }
        if (viewModel.searchedUsers.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.messaging_users),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = colors.secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            items(viewModel.searchedUsers, key = { "su:${it.id}" }) { user ->
                SearchUserRow(user = user, onSelect = { onStartDraftWithUser(user) })
            }
        }
        if (viewModel.searchedMessages.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.messaging_messages_section),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = colors.secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            items(viewModel.searchedMessages, key = { "sm:${it.id}" }) { result ->
                SearchMessageResultRow(result = result, onTap = { onOpenSearchMessage(result) })
            }
        }
        if (empty) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.messaging_search_empty), color = colors.secondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchUserRow(user: AppUser, onSelect: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.secondary.copy(0.2f)),
        )
        Text(user.username, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun SearchMessageResultRow(result: GlobalMessageSearchResult, onTap: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            result.conversation.otherParticipantUsername ?: stringResource(R.string.messaging_user_default),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(
            ChatTextMarkup.plainText(result.message.content.orEmpty(), hidesSpoilers = true),
            color = colors.secondary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GlassmorphicNewConversationView(
    viewModel: MessagingViewModel,
    onDismiss: () -> Unit,
    onConversationReady: (Conversation) -> Unit,
    onNeedsRequest: (PendingChatContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var searchText by remember { mutableStateOf("") }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.searchUsers("") }
    LaunchedEffect(searchText) { viewModel.searchUsers(searchText) }

    Column(modifier.fillMaxSize().background(colors.surfaceBackground).statusBarsPadding()) {
        MessagingDestinationHeader(
            title = stringResource(R.string.messaging_new_title),
            onBack = onDismiss,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.messaging_new_to),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = colors.primary,
            )
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.primary),
                singleLine = true,
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.messaging_new_search_placeholder),
                            color = colors.secondary,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (searchText.isBlank()) {
            Text(
                stringResource(R.string.messaging_new_suggestions),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        val users = viewModel.suggestedUsers
        when {
            users.isEmpty() && searchText.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MomentsCircularProgressIndicator()
            }
            users.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.messaging_no_results), color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(users, key = { it.id }) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val currentUserId = uid ?: return@clickable
                                viewModel.startConversation(user = user, fromUserId = currentUserId) { conversation ->
                                    scope.launch {
                                        val presentation = viewModel.consumeProfileMessagePresentation(
                                            conversation = conversation,
                                            user = user,
                                            currentUserId = currentUserId,
                                        ) ?: return@launch
                                        when (val destination = presentation.destination) {
                                            is ProfileMessagePresentation.Destination.OpenConversation ->
                                                onConversationReady(destination.conversation)
                                            is ProfileMessagePresentation.Destination.PendingChat ->
                                                onNeedsRequest(destination.context)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(
                            model = user.profileImagePath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.secondary.copy(0.2f)),
                        )
                        Text(
                            user.username,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
