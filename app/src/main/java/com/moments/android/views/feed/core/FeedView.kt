package com.moments.android.views.feed.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.coordinators.AppRouter
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.Echo
import com.moments.android.models.MediaItem
import com.moments.android.notifications.screens.NotificationSummaryService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.network.OfflineSyncService
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.controls.FeedType
import com.moments.android.views.feed.controls.FeedTypePreferences
import com.moments.android.views.feed.controls.rememberFeedType
import com.moments.android.views.feed.core.sections.FeedFloatingSelector
import com.moments.android.views.feed.core.sections.FeedHeaderSection
import com.moments.android.views.feed.core.sections.FeedListSection
import com.moments.android.views.feed.core.sections.FeedOverlaysSection
import com.moments.android.views.feed.stories.FeedStoryRingCoordinator
import com.moments.android.views.feed.uploads.FloatingMomentUploadOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private val FeedHeaderHeight = 88.dp
private val FeedSelectorHeight = 35.dp

/**
 * Port 1:1 de `FeedView.swift` — misma superficie de estado, ciclo de vida,
 * helpers (load/refresh/profile/echoes) y capas ZStack.
 * Liquid Glass refresh chip → omitido (no existe en Android).
 */
@Composable
fun FeedView(
    padding: PaddingValues,
    showCreatorView: Boolean = false,
    onShowCreatorViewChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    // MARK: - Services (equivalente @StateObject / @ObservedObject iOS)
    val viewModel = remember {
        FeedViewModel().also { it.attachContext(context) }
    }
    val storyRingCoordinator = remember { FeedStoryRingCoordinator(appContext = context.applicationContext) }
    val firestoreService = remember { FirestoreService() }
    val uploadService = BackgroundMomentUploadService
    val networkMonitor = NetworkMonitor
    val badgeService = NotificationBadgeService
    val notificationSummaryService = NotificationSummaryService

    val unreadNotifications by badgeService.unreadNotificationsCount.collectAsState()
    val unreadMessages by badgeService.unreadMessagesCount.collectAsState()
    val isConnected by networkMonitor.isConnectedFlow.collectAsState()

    // MARK: - Feed type + layout insets
    val (selectedFeedType, setFeedType) = rememberFeedType()
    var isFeedHeaderHidden by remember { mutableStateOf(false) }
    var isManualRefreshing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Altura real del header (incluye status bar). La pill se ancla justo debajo.
    var headerHeightDp by remember { mutableStateOf(statusBarTop + FeedHeaderHeight) }
    val floatingSelectorTopInsetTarget: Dp =
        if (isFeedHeaderHidden) statusBarTop + 18.dp else headerHeightDp
    val floatingSelectorTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = floatingSelectorTopInsetTarget,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 400f),
        label = "feedFloatingInset",
    )
    // iOS: floatingSelectorTopInset + feedSelectorHeight + 25
    val feedContentTopInset: Dp =
        floatingSelectorTopInset + FeedSelectorHeight + 25.dp
    val headerHideTranslationPx = with(density) { -(headerHeightDp + 20.dp).toPx() }
    val headerAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFeedHeaderHidden) 0f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 400f),
        label = "feedHeaderAlpha",
    )
    val headerTranslationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFeedHeaderHidden) headerHideTranslationPx else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 400f),
        label = "feedHeaderTy",
    )
    // iOS modernBackgroundView = AdaptiveColors.surfaceBackground (0B1215 / FAF9F6)
    val surface = rememberAdaptiveColors().surfaceBackground

    // MARK: - Navigation / presentation flags (espejo iOS @State)
    var showNotifications by remember { mutableStateOf(false) }
    var showMessages by remember { mutableStateOf(false) }
    var showStories by remember { mutableStateOf(false) }
    var selectedMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var suspendedMomentForComments by remember { mutableStateOf<FeedMoment?>(null) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showingLocationMap by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLocationLatitude by remember { mutableStateOf<Double?>(null) }
    var selectedLocationLongitude by remember { mutableStateOf<Double?>(null) }
    var selectedUserId by remember { mutableStateOf("") }
    var selectedProfileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }

    var showStoryChain by remember { mutableStateOf(false) }
    var selectedChainId by remember { mutableStateOf("") }
    var selectedChainTitle by remember { mutableStateOf("") }
    var hasLoadedInitialData by remember { mutableStateOf(false) }
    var hasUnreadMessages by remember { mutableStateOf(false) }

    var selectedStoryRoute by remember { mutableStateOf<StoryUserPresentationRoute?>(null) }
    var storyRingNavigationUserIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedHashtag by remember { mutableStateOf("") }
    var showExploreWithHashtag by remember { mutableStateOf(false) }
    var showGlobalContextMenu by remember { mutableStateOf(false) }
    var selectedMomentForMenu by remember { mutableStateOf<FeedMoment?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    // LONG PRESS PEEK
    var peekImageURL by remember { mutableStateOf<String?>(null) }
    var peekAspectRatio by remember { mutableFloatStateOf(1f) }
    var isPeeking by remember { mutableStateOf(false) }
    var peekIsProtected by remember { mutableStateOf(false) }

    var targetConversationId by remember { mutableStateOf<String?>(null) }
    var targetMomentId by remember { mutableStateOf<String?>(null) }
    var showMomentDetail by remember { mutableStateOf(false) }
    var targetMomentUserId by remember { mutableStateOf<String?>(null) }
    var showExplore by remember { mutableStateOf(false) }

    // ECHOES
    var pendingEchoes by remember { mutableStateOf<List<Echo>>(emptyList()) }
    var showEchoHistory by remember { mutableStateOf(false) }
    var showPendingEchoInvitation by remember { mutableStateOf(false) }
    var selectedPendingEchoId by remember { mutableStateOf("") }
    var pendingEchoInvitationRoute by remember { mutableStateOf<FeedEchoInvitationRoute?>(null) }
    var pendingEchoesListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    var shareMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var didScheduleNotificationPrompt by remember { mutableStateOf(false) }
    var showNotificationSummary by remember { mutableStateOf(false) }

    // --- Helpers (paridad funciones privadas iOS) ---

    fun syncStoryRingNavigationOrder() {
        storyRingNavigationUserIds = storyRingCoordinator.ringNavigationUserIds
    }

    fun openStoryViewer(userId: String) {
        if (userId.isEmpty()) return
        syncStoryRingNavigationOrder()
        selectedStoryRoute = StoryUserPresentationRoute(userId)
    }

    fun openUserProfile(userId: String) {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return
        if (trimmed == viewModel.viewerId) {
            selectedMoment = null
            selectedUserId = ""
            selectedProfileRoute = null
            LegacyNavigationBridge.ownProfileTab()
            return
        }
        selectedUserId = trimmed
        val current = selectedMoment
        if (current != null) {
            suspendedMomentForComments = current
            selectedMoment = null
            scope.launch {
                delay(450)
                selectedProfileRoute = FeedProfileSheetRoute(trimmed)
            }
        } else {
            selectedProfileRoute = FeedProfileSheetRoute(trimmed)
        }
    }

    fun setupServiceConnections() {
        uploadService.setFeedViewModel(viewModel)
    }

    fun setupPendingEchoesListener() {
        val userId = viewModel.viewerId ?: return
        pendingEchoesListener?.remove()
        pendingEchoesListener = EchoService.fetchPendingEchoes(userId) { echoes ->
            pendingEchoes = echoes
        }
    }

    fun prefetchImages() {
        // iOS: Kingfisher preload + VideoPreloader desde los primeros momentos
        viewModel.moments.take(12).let { slice ->
            val urls = slice.asSequence()
                .flatMap { it.visibleMediaItems.asSequence() }
                .map { it.url }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            if (urls.isNotEmpty()) {
                com.moments.android.services.cache.VideoPreloader.preloadAssets(
                    urls.filter { url ->
                        slice.any { m ->
                            m.visibleMediaItems.any {
                                it.url == url && it.type.equals("video", true)
                            }
                        }
                    },
                )
            }
        }
    }

    fun loadInitialData() {
        val userId = viewModel.viewerId ?: return
        if (hasLoadedInitialData) {
            viewModel.fetchUserData(scope, userId)
            return
        }
        storyRingCoordinator.clearCacheIfNeeded()
        val preferred = FeedTypePreferences.load(context)
        setFeedType(preferred)
        scope.launch {
            firestoreService.loadSavedMoments(userId)
            viewModel.fetchMoments(scope, userId, preferred)
            viewModel.fetchUserData(scope, userId)
            storyRingCoordinator.loadStoryUsers(scope, userId)
            prefetchImages()
            hasLoadedInitialData = true
        }
    }

    fun forceRefresh() {
        hasLoadedInitialData = false
        storyRingCoordinator.resetCache()
        loadInitialData()
    }

    suspend fun refreshFeed(userId: String) {
        viewModel.refreshMoments(userId)
        storyRingCoordinator.loadStoryUsers(scope, userId, allowInstantCache = false)
        prefetchImages()
    }

    suspend fun performManualRefresh(userId: String) {
        isManualRefreshing = true
        refreshFeed(userId)
        delay(250)
        isManualRefreshing = false
    }

    fun deleteMoment(moment: FeedMoment) {
        isDeleting = true
        scope.launch {
            runCatching {
                firestoreService.deleteMoment(userId = moment.authorId, momentId = moment.id)
            }.onSuccess {
                viewModel.removeMoment(moment.id)
                showGlobalContextMenu = false
                selectedMomentForMenu = null
            }
            isDeleting = false
        }
    }

    fun updateMoment(moment: FeedMoment, payload: EditMomentPayload) {
        scope.launch {
            runCatching {
                val coord = if (payload.locationLatitude != null && payload.locationLongitude != null) {
                    com.moments.android.models.Moment.LocationCoordinate(
                        latitude = payload.locationLatitude,
                        longitude = payload.locationLongitude,
                    )
                } else {
                    null
                }
                firestoreService.updateMomentDetails(
                    userId = moment.authorId,
                    momentId = moment.id,
                    content = payload.content,
                    audience = payload.audience,
                    customListId = payload.customListId,
                    customViewers = payload.customViewers,
                    taggedUsers = payload.taggedUsers,
                    mentionedUsers = payload.mentionedUsers,
                    location = payload.locationName.ifEmpty { null },
                    locationCoordinate = coord,
                    mediaItems = payload.mediaItems,
                )
            }
            showEditSheet = false
        }
    }

    // --- Lifecycle (onAppear / onDisappear) ---

    LaunchedEffect(Unit) {
        loadInitialData()
        storyRingCoordinator.prefetchTopStoryUsers(excluding = viewModel.viewerId, scope = scope)
        setupServiceConnections()
        badgeService.setupListeners()
        setupPendingEchoesListener()
        delay(1000)
        notificationSummaryService.checkShouldShowSummary(
            context = context,
            unreadNotifications = unreadNotifications,
            unreadMessages = unreadMessages,
            onShow = { showNotificationSummary = true },
        )
    }

    // Timer de currentTime cada 60s (startTimeUpdate)
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    // Paridad iOS `onChange(of: selectedFeedType)` — NO switch en el primer composition
    // (loadInitialData ya hace fetch; switch al montar cancela el job y racea e2e).
    var didObserveFeedType by remember { mutableStateOf(false) }
    LaunchedEffect(selectedFeedType) {
        FeedTypePreferences.save(context, selectedFeedType)
        if (!didObserveFeedType) {
            didObserveFeedType = true
            return@LaunchedEffect
        }
        viewModel.switchFeedType(scope, selectedFeedType, viewModel.viewerId)
    }

    LaunchedEffect(selectedProfileRoute) {
        if (selectedProfileRoute == null) {
            selectedUserId = ""
            val suspended = suspendedMomentForComments
            if (suspended != null) {
                delay(400)
                selectedMoment = suspended
                suspendedMomentForComments = null
            }
        }
    }

    LaunchedEffect(showingLocationMap) {
        // onChange mirror — side effects viven en presentaciones
        yield()
    }

    LaunchedEffect(unreadMessages) {
        hasUnreadMessages = unreadMessages > 0
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingEchoesListener?.remove()
            pendingEchoesListener = null
            viewModel.shutdown()
            notificationSummaryService.markAppClosed(context)
        }
    }

    FeedNotificationRoutingEffect(
        context = context,
        scope = scope,
        setShowMessages = { showMessages = it },
        setShowNotifications = { showNotifications = it },
        setShowCreatorView = onShowCreatorViewChange,
        setShowExplore = { showExplore = it },
        setShowMomentDetail = { showMomentDetail = it },
        setTargetConversationId = { targetConversationId = it },
        setTargetMomentId = { targetMomentId = it },
        setTargetMomentUserId = { targetMomentUserId = it },
        setShowNotificationSummary = { showNotificationSummary = it },
        notificationSummaryService = notificationSummaryService,
        badgeService = badgeService,
        storyRingCoordinator = storyRingCoordinator,
        firestoreService = firestoreService,
        onOpenUserProfile = { userId -> openUserProfile(userId) },
        onOpenStory = { _, authorId ->
            syncStoryRingNavigationOrder()
            if (!authorId.isNullOrBlank()) {
                selectedStoryRoute = StoryUserPresentationRoute(authorId)
            } else {
                showStories = true
            }
        },
        onOpenStoryChain = { chainId, chainTitle ->
            selectedChainId = chainId
            selectedChainTitle = chainTitle
            showStoryChain = true
        },
    )

    // forceFeedRefresh — iOS NotificationCenter.forceFeedRefresh
    LaunchedEffect(Unit) {
        NavigationEventBus.events.collectLatest { event ->
            when (event) {
                is CoordinatorNavigationEvent.ForceFeedRefresh,
                is CoordinatorNavigationEvent.ReturnToFeedAfterMomentPublish,
                -> {
                    val uid = viewModel.viewerId ?: return@collectLatest
                    performManualRefresh(uid)
                    OfflineSyncService.retryFromUserAction()
                    if (NetworkMonitor.isConnected) {
                        HapticManager.shared.success()
                    } else {
                        HapticManager.shared.warning()
                    }
                }
                is CoordinatorNavigationEvent.ShowStories -> {
                    syncStoryRingNavigationOrder()
                    showStories = true
                }
                else -> Unit
            }
        }
    }

    FeedPresentations(
        showNotifications = showNotifications,
        onShowNotificationsChange = { showNotifications = it },
        showMessages = showMessages,
        onShowMessagesChange = { showMessages = it },
        selectedStoryRoute = selectedStoryRoute,
        onSelectedStoryRouteChange = { selectedStoryRoute = it },
        storyRingNavigationUserIds = storyRingNavigationUserIds,
        showStories = showStories,
        onShowStoriesChange = { showStories = it },
        selectedMoment = selectedMoment,
        onSelectedMomentChange = { selectedMoment = it },
        showExploreWithHashtag = showExploreWithHashtag,
        onShowExploreWithHashtagChange = { showExploreWithHashtag = it },
        selectedHashtag = selectedHashtag,
        showExplore = showExplore,
        onShowExploreChange = { showExplore = it },
        showingLocationMap = showingLocationMap,
        onShowingLocationMapChange = { showingLocationMap = it },
        selectedLocationName = selectedLocationName,
        selectedLocationLatitude = selectedLocationLatitude,
        selectedLocationLongitude = selectedLocationLongitude,
        showMomentDetail = showMomentDetail,
        onShowMomentDetailChange = { showMomentDetail = it },
        targetMomentId = targetMomentId,
        onTargetMomentIdChange = { targetMomentId = it },
        targetMomentUserId = targetMomentUserId,
        onTargetMomentUserIdChange = { targetMomentUserId = it },
        showEditSheet = showEditSheet,
        onShowEditSheetChange = { showEditSheet = it },
        showDeleteAlert = showDeleteAlert,
        onShowDeleteAlertChange = { showDeleteAlert = it },
        selectedMomentForMenu = selectedMomentForMenu,
        selectedProfileRoute = selectedProfileRoute,
        onSelectedProfileRouteChange = { selectedProfileRoute = it },
        onSelectedUserIdChange = { selectedUserId = it },
        showEchoHistory = showEchoHistory,
        onShowEchoHistoryChange = { showEchoHistory = it },
        targetConversationId = targetConversationId,
        onTargetConversationIdChange = { targetConversationId = it },
        firestoreService = firestoreService,
        updateMoment = { moment, payload -> updateMoment(moment, payload) },
        deleteMoment = { deleteMoment(it) },
    ) {
        // MARK: - body ZStack
        // iOS: fondo under status bar; header con padding.top -8 (sin doble inset).
        // Scaffold ya mete top inset → solo aplicamos bottom (tab bar) + laterales.
        val layoutDir = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .background(surface)
                .padding(
                    start = padding.calculateStartPadding(layoutDir),
                    end = padding.calculateEndPadding(layoutDir),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            // modernBackground ya aplicado vía surface

            // mainContent
            Box(Modifier.fillMaxSize()) {
                FeedListSection(
                    viewModel = viewModel,
                    selectedFeedType = selectedFeedType,
                    contentTopPadding = PaddingValues(top = feedContentTopInset, bottom = 22.dp),
                    onRefresh = {
                        val uid = viewModel.viewerId ?: return@FeedListSection
                        scope.launch {
                            performManualRefresh(uid)
                            OfflineSyncService.retryFromUserAction()
                            if (NetworkMonitor.isConnected) {
                                HapticManager.shared.success()
                            } else {
                                HapticManager.shared.warning()
                            }
                        }
                    },
                    onLoadMore = {
                        viewModel.viewerId?.let { viewModel.loadMoreMoments(scope, it) }
                    },
                    onOpenUserProfile = { openUserProfile(it) },
                    onOpenHashtag = { tag ->
                        // iOS handleFeedHashtagTap: selectedHashtag = "#\(hashtag)"
                        selectedHashtag = if (tag.startsWith("#")) tag else "#$tag"
                        showExploreWithHashtag = true
                        LegacyNavigationBridge.showExplore()
                    },
                    onOpenLocation = { name, coordinate ->
                        selectedLocationName = name
                        selectedLocationLatitude = coordinate?.latitude
                        selectedLocationLongitude = coordinate?.longitude
                        showingLocationMap = true
                    },
                    onOpenComments = { moment -> selectedMoment = moment },
                    onShare = { moment ->
                        shareMoment = moment
                        selectedMomentForMenu = moment
                        showShareSheet = true
                    },
                    isFeedHeaderHidden = isFeedHeaderHidden,
                    onHeaderHiddenChange = { isFeedHeaderHidden = it },
                    onContextMenu = { moment ->
                        selectedMomentForMenu = moment
                        showGlobalContextMenu = true
                    },
                    onAuthorAvatarTap = { authorId, hasStory ->
                        // iOS ModernPostCardView.handleAuthorAvatarTap
                        if (hasStory) {
                            syncStoryRingNavigationOrder()
                            selectedStoryRoute = StoryUserPresentationRoute(authorId)
                        } else {
                            openUserProfile(authorId)
                        }
                    },
                    onPeek = { url, ratio, pressing ->
                        if (pressing && url.isNotBlank()) {
                            peekImageURL = url
                            peekAspectRatio = ratio
                            val owner = viewModel.moments.firstOrNull { m ->
                                m.visibleMediaItems.any { it.url == url } ||
                                    m.mediaItems.any { it.url == url }
                            }
                            peekIsProtected =
                                (owner?.audience?.lowercase() ?: "") != "everyone"
                            isPeeking = true
                        } else {
                            isPeeking = false
                            peekImageURL = null
                            peekIsProtected = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                FeedHeaderSection(
                    storyUsers = storyRingCoordinator.storyUsers,
                    isLoadingStories = storyRingCoordinator.isLoadingStories,
                    isLoadingMoreRing = storyRingCoordinator.isLoadingMoreRing,
                    pendingEchoes = pendingEchoes,
                    ownStoryProfileImageUrl = viewModel.userProfileImage,
                    ownStoryCount = storyRingCoordinator.storyUsers
                        .firstOrNull { it.userId == viewModel.viewerId }
                        ?.storyCount ?: 0,
                    currentUserId = viewModel.viewerId,
                    onCreateStory = { onShowCreatorViewChange(true) },
                    onOpenStory = { user -> openStoryViewer(user.userId) },
                    onOpenActivity = {
                        showNotifications = true
                        LegacyNavigationBridge.showNotifications()
                    },
                    onOpenMessages = {
                        showMessages = true
                        LegacyNavigationBridge.showMessages()
                    },
                    onOpenEchoHistory = { showEchoHistory = true },
                    onOpenEchoInvitation = { echoId ->
                        selectedPendingEchoId = echoId
                        showPendingEchoInvitation = true
                        pendingEchoInvitationRoute = FeedEchoInvitationRoute(echoId)
                    },
                    onLoadMoreRing = {
                        viewModel.viewerId?.let { storyRingCoordinator.loadMoreRing(scope, it) }
                    },
                    // iOS: header en safe area; fondo ignoresSafeArea(.top).
                    // Medimos altura real → la pill se coloca justo debajo (sin statusBarsPadding extra).
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onGloballyPositioned { coords ->
                            val h = with(density) { coords.size.height.toDp() }
                            if (h > 0.dp && h != headerHeightDp) headerHeightDp = h
                        }
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = headerTranslationY
                        }
                        .zIndex(10f),
                )
            }

            FloatingMomentUploadOverlay(
                topInset = if (isFeedHeaderHidden) {
                    statusBarTop.value + 18f
                } else {
                    headerHeightDp.value + 12f
                },
            )

            FeedFloatingSelector(
                selectedFeedType = selectedFeedType,
                onSelectFeedType = { type ->
                    setFeedType(type)
                    // switch solo vía LaunchedEffect(selectedFeedType) — paridad iOS onChange
                },
                isManualRefreshing = isManualRefreshing,
                floatingSelectorTopInset = floatingSelectorTopInset,
                isFeedHeaderHidden = isFeedHeaderHidden,
                pendingEchoesCount = pendingEchoes.size,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(998f),
            )

            // SlowConnectionBanner + AppErrorBanner
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .fillMaxWidth()
                    .zIndex(999f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isConnected || networkMonitor.isSlowConnection) {
                    SlowConnectionBanner(
                        isOffline = !isConnected,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                viewModel.errorMessage?.let { msg ->
                    AppErrorBanner(
                        message = if (msg == "feed_error") stringResource(R.string.feed_error) else msg,
                        onRetry = { forceRefresh() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            FeedOverlaysSection(
                isPeeking = isPeeking,
                peekImageUrl = peekImageURL,
                peekAspectRatio = peekAspectRatio,
                peekIsProtected = peekIsProtected,
                showShareSheet = showShareSheet,
                showContextMenu = showGlobalContextMenu,
                selectedMoment = selectedMomentForMenu ?: shareMoment,
                pendingEchoInvitationRoute = pendingEchoInvitationRoute,
                showNotificationSummary = showNotificationSummary,
                onDismissPeek = {
                    isPeeking = false
                    peekImageURL = null
                },
                onDismissShare = { showShareSheet = false },
                onDismissContextMenu = {
                    showGlobalContextMenu = false
                    selectedMomentForMenu = null
                },
                onEdit = {
                    showEditSheet = true
                    showGlobalContextMenu = false
                },
                onDelete = { showDeleteAlert = true },
                onDismissEchoInvitation = {
                    pendingEchoInvitationRoute = null
                    showPendingEchoInvitation = false
                    selectedPendingEchoId = ""
                },
                onAcceptEchoInvitation = { echoId ->
                    // Paridad iOS Overlays: AppRouter.navigate(.echo(echoId:))
                    AppRouter.navigate(AppRouter.Destination.Echo(echoId))
                    pendingEchoInvitationRoute = null
                    showPendingEchoInvitation = false
                    selectedPendingEchoId = ""
                },
                onDismissNotificationSummary = { showNotificationSummary = false },
            )

            // Silence unused flags until Sections/presentations wire them
            @Suppress("UNUSED_VARIABLE")
            val keepAlive = listOf(
                showCreatorView, showReportSheet, editedContent, isDeleting,
                peekAspectRatio, peekIsProtected, showStoryChain, selectedChainId, selectedChainTitle,
                showPendingEchoInvitation, selectedPendingEchoId,
                currentTimeMillis, showNotificationSummary, didScheduleNotificationPrompt,
            )
        }
    }
}

@Composable
private fun SlowConnectionBanner(isOffline: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            if (isOffline) R.string.feed_offline_banner else R.string.feed_slow_connection_banner,
        ),
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFCC5500), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
internal fun AppErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFB00020), RoundedCornerShape(10.dp))
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(message, color = Color.White)
        Text(stringResource(R.string.feed_error_retry), color = Color.White.copy(alpha = 0.85f))
    }
}
