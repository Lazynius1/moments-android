package com.moments.android.coordinators

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.notifications.services.FCMTokenService
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.screens.NotificationsScreen
import com.moments.android.services.auth.AuthService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.feed.core.FeedView
import com.moments.android.views.creator.CreatorView
import com.moments.android.views.messaging.screens.MessagingView
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Pestañas principales — paridad iOS AppTab (home=0, nova=1, create=2, explore=3, profile=4). */
enum class AppTab {
    HOME, NOVA, CREATE, EXPLORE, PROFILE;

    companion object {
        fun fromIndex(index: Int): AppTab = when (index) {
            0 -> HOME
            1 -> NOVA
            2 -> CREATE
            3 -> EXPLORE
            4 -> PROFILE
            else -> HOME
        }

        fun toIndex(tab: AppTab): Int = when (tab) {
            HOME -> 0
            NOVA -> 1
            CREATE -> 2
            EXPLORE -> 3
            PROFILE -> 4
        }
    }
}

/**
 * Shell principal autenticado — port de TabBarView.swift.
 */
@Composable
fun TabBarScreen(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val mainViewModel = remember { MainViewModel.shared }
    val hasNewFeedContent by mainViewModel.hasNewFeedContent.collectAsState()
    val hasUnreadNotifications by mainViewModel.hasUnreadNotifications.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var previousSelectedTab by remember { mutableIntStateOf(0) }
    var showCreatorView by remember { mutableStateOf(false) }
    var isCreatingStory by remember { mutableStateOf(false) }
    var openCreatorInStoryMode by remember { mutableStateOf(false) }
    var hasPreloadedExplore by remember { mutableStateOf(false) }
    var showEchoInvitation by remember { mutableStateOf(false) }
    var pendingEchoId by remember { mutableStateOf("") }
    var showEchoViewer by remember { mutableStateOf(false) }
    var echoInvitationRoute by remember { mutableStateOf<String?>(null) }
    var showNotificationsOverlay by remember { mutableStateOf(false) }
    var showMessagesOverlay by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    val routerContext = remember {
        AppRouterTabBarContext(
            setSelectedTab = { selectedTab = it },
            setShowCreatorView = { showCreatorView = it },
            setPendingEchoId = { pendingEchoId = it },
            setShowEchoInvitation = { showEchoInvitation = it },
            setShowEchoViewer = { showEchoViewer = it },
            onEchoInvitationRoute = { echoId -> echoInvitationRoute = echoId },
            postDelayed = { block ->
                scope.launch {
                    delay(100)
                    block()
                }
            },
        )
    }

    DisposableEffect(Unit) {
        LegacyNavigationBridge.wireMentionNavigation()
        InAppNotificationService.startListening()
        onDispose { InAppNotificationService.stopListening() }
    }

    LaunchedEffect(Unit) {
        FCMTokenService.updateFCMToken()
        previousSelectedTab = selectedTab
        if (FirebaseAuth.getInstance().currentUser?.uid != null) {
            delay(2000)
            if (!hasPreloadedExplore) {
                hasPreloadedExplore = true
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.toIndex(AppTab.EXPLORE) && !hasPreloadedExplore) {
            hasPreloadedExplore = true
        }
        if (selectedTab == AppTab.toIndex(AppTab.CREATE)) {
            showCreatorView = true
            isCreatingStory = true
            selectedTab = previousSelectedTab
        } else {
            previousSelectedTab = selectedTab
        }
        if (selectedTab == AppTab.toIndex(AppTab.HOME)) {
            mainViewModel.markFeedAsSeen()
        }
        if (selectedTab == AppTab.toIndex(AppTab.PROFILE)) {
            mainViewModel.markNotificationsAsSeen()
        }
    }

    LaunchedEffect(Unit) {
        AppRouter.pending.collectLatest { pending ->
            if (pending != null) {
                AppRouter.dispatchPending(routerContext)
            }
        }
    }

    LaunchedEffect(Unit) {
        NavigationEventBus.events.collectLatest { event ->
            when (event) {
                is CoordinatorNavigationEvent.ShowExploreView,
                -> selectedTab = AppTab.toIndex(AppTab.EXPLORE)
                is CoordinatorNavigationEvent.NavigateToMoment,
                is CoordinatorNavigationEvent.NavigateToProfile,
                is CoordinatorNavigationEvent.NavigateToStoryInFeed,
                is CoordinatorNavigationEvent.NavigateToStoryChain,
                is CoordinatorNavigationEvent.NavigateToNotifications,
                is CoordinatorNavigationEvent.ShowStories,
                is CoordinatorNavigationEvent.NavigateToUserProfileInFeed,
                -> selectedTab = AppTab.toIndex(AppTab.HOME)
                is CoordinatorNavigationEvent.NavigateToFollowRequests,
                is CoordinatorNavigationEvent.ShowProfileVisits,
                -> selectedTab = AppTab.toIndex(AppTab.PROFILE)
                is CoordinatorNavigationEvent.ShowUserProfile -> {
                    selectedTab = AppTab.toIndex(AppTab.HOME)
                    scope.launch {
                        delay(500)
                        NavigationEventBus.emit(
                            CoordinatorNavigationEvent.NavigateToUserProfileInFeed(event.userId),
                        )
                    }
                }
                is CoordinatorNavigationEvent.ShowNotifications -> showNotificationsOverlay = true
                is CoordinatorNavigationEvent.ShowMessages -> showMessagesOverlay = true
                is CoordinatorNavigationEvent.ScrollFeedToTop -> Unit
                is CoordinatorNavigationEvent.ReturnToFeedAfterMomentPublish -> {
                    previousSelectedTab = AppTab.toIndex(AppTab.HOME)
                    selectedTab = AppTab.toIndex(AppTab.HOME)
                }
                is CoordinatorNavigationEvent.NavigateToOwnProfileTab -> selectedTab = AppTab.toIndex(AppTab.PROFILE)
                is CoordinatorNavigationEvent.NavigateToUserProfile -> {
                    AppRouter.navigate(AppRouter.Destination.Profile(event.userId))
                }
                is CoordinatorNavigationEvent.OpenCreatorForChain -> {
                    showCreatorView = true
                    isCreatingStory = true
                    scope.launch {
                        delay(1000)
                        NavigationEventBus.emit(CoordinatorNavigationEvent.SetContentType("story"))
                        NavigationEventBus.emit(
                            CoordinatorNavigationEvent.SetChainContext(
                                event.chainId,
                                event.chainTitle,
                                event.chainPosition,
                            ),
                        )
                    }
                }
                is CoordinatorNavigationEvent.NavigateToStoryChainInFeed -> {
                    selectedTab = AppTab.toIndex(AppTab.HOME)
                    scope.launch {
                        delay(500)
                        NavigationEventBus.emit(
                            CoordinatorNavigationEvent.NavigateToStoryChain(event.chainId, event.chainTitle),
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        TabBarDeepLinkHandler.handle(uri, firestoreService) { onDeepLinkHandled() }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                MomentsCustomTabBar(
                    selectedTab = selectedTab,
                    onSelectTab = { index ->
                        if (index == AppTab.toIndex(AppTab.HOME) && selectedTab == index) {
                            HapticManager.shared.lightImpact()
                            NavigationEventBus.emit(CoordinatorNavigationEvent.ScrollFeedToTop)
                        } else if (index == AppTab.toIndex(AppTab.CREATE)) {
                            HapticManager.shared.mediumImpact()
                            showCreatorView = true
                            isCreatingStory = true
                        } else {
                            HapticManager.shared.selection()
                            selectedTab = index
                        }
                    },
                    onOpenCreator = {
                        HapticManager.shared.mediumImpact()
                        showCreatorView = true
                    },
                    showFeedBadge = hasNewFeedContent,
                    showProfileBadge = hasUnreadNotifications,
                )
            },
        ) { padding ->
            TabContent(
                selectedTab = selectedTab,
                padding = padding,
                showCreatorView = showCreatorView,
                onShowCreatorViewChange = { showCreatorView = it },
            )
        }

        echoInvitationRoute?.let { echoId ->
            EchoInvitationPlaceholder(
                echoId = echoId,
                onDismiss = {
                    echoInvitationRoute = null
                    showEchoInvitation = false
                    pendingEchoId = ""
                },
                onAccept = { acceptedId ->
                    pendingEchoId = acceptedId
                    showEchoViewer = true
                },
            )
        }

        if (showCreatorView) {
            Dialog(
                onDismissRequest = {
                    showCreatorView = false
                    openCreatorInStoryMode = false
                    isCreatingStory = false
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                // Fondo edge-to-edge; el contenido respeta safe drawing (status + nav).
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        CreatorView(
                            showCreatorView = true,
                            onShowCreatorViewChange = {
                                showCreatorView = it
                                if (!it) {
                                    openCreatorInStoryMode = false
                                    isCreatingStory = false
                                }
                            },
                            isCreatingStory = isCreatingStory,
                            onIsCreatingStoryChange = { isCreatingStory = it },
                            openInStoryMode = openCreatorInStoryMode,
                        )
                    }
                }
            }
        }

        if (showEchoViewer && pendingEchoId.isNotEmpty()) {
            EchoViewerPlaceholder(
                echoId = pendingEchoId,
                onDismiss = {
                    showEchoViewer = false
                    pendingEchoId = ""
                },
            )
        }

        if (showNotificationsOverlay) {
            Dialog(
                onDismissRequest = {
                    showNotificationsOverlay = false
                    mainViewModel.markNotificationsAsSeen()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    NotificationsScreen(onBack = {
                        showNotificationsOverlay = false
                        mainViewModel.markNotificationsAsSeen()
                    })
                }
            }
        }

        if (showMessagesOverlay) {
            Dialog(
                onDismissRequest = { showMessagesOverlay = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    MessagingView(onDismiss = { showMessagesOverlay = false })
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    padding: PaddingValues,
    showCreatorView: Boolean,
    onShowCreatorViewChange: (Boolean) -> Unit,
) {
    when (AppTab.fromIndex(selectedTab)) {
        AppTab.HOME -> FeedView(
            padding = padding,
            showCreatorView = showCreatorView,
            onShowCreatorViewChange = onShowCreatorViewChange,
        )
        AppTab.NOVA -> CoordinatorPlaceholderScreen(
            title = stringResource(R.string.tab_bar_nova),
            padding = padding,
        )
        AppTab.CREATE -> Box(Modifier.fillMaxSize())
        AppTab.EXPLORE -> ExploreView(
            contentPadding = padding,
        )
        AppTab.PROFILE -> {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                UserProfileView(
                    userId = uid,
                    onDismiss = {},
                    showBack = false,
                    modifier = Modifier.padding(padding),
                )
            } else {
                CoordinatorPlaceholderScreen(
                    title = stringResource(R.string.tab_bar_profile),
                    padding = padding,
                )
            }
        }
    }
}

@Composable
private fun MomentsCustomTabBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenCreator: () -> Unit,
    showFeedBadge: Boolean,
    showProfileBadge: Boolean,
) {
    val isDark = isSystemInDarkTheme()
    val activeColor = if (isDark) Color.White else Color(0xFF0B1215)
    val inactiveColor = activeColor.copy(alpha = 0.62f)
    val chromeTint = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = chromeTint,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabBarItem(
                icon = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                title = stringResource(R.string.tab_bar_home),
                isSelected = selectedTab == 0,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                showBadge = showFeedBadge,
                onClick = { onSelectTab(0) },
            )
            TabBarItem(
                icon = null,
                title = stringResource(R.string.tab_bar_nova),
                isSelected = selectedTab == 1,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                isNova = true,
                onClick = { onSelectTab(1) },
            )
            CreateTabButton(
                isSelected = selectedTab == 2,
                isDark = isDark,
                onClick = onOpenCreator,
            )
            TabBarItem(
                icon = if (selectedTab == 3) Icons.Filled.Search else Icons.Outlined.Search,
                title = stringResource(R.string.tab_bar_explore),
                isSelected = selectedTab == 3,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onSelectTab(3) },
            )
            TabBarItem(
                icon = if (selectedTab == 4) Icons.Filled.Person else Icons.Outlined.Person,
                title = stringResource(R.string.tab_bar_profile),
                isSelected = selectedTab == 4,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                showBadge = showProfileBadge,
                onClick = { onSelectTab(4) },
            )
        }
    }
}

@Composable
private fun RowScope.TabBarItem(
    icon: ImageVector?,
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    isNova: Boolean = false,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            if (isNova) {
                NovaTabGlyph(
                    size = 22.dp,
                    color = if (isSelected) activeColor else inactiveColor,
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                )
            }
        }
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) activeColor else inactiveColor,
        )
    }
}

@Composable
private fun NovaTabGlyph(size: androidx.compose.ui.unit.Dp, color: Color) {
    // iOS NovaTabIcon (template) — tintamos el PNG importado de Assets.xcassets
    Image(
        painter = painterResource(R.drawable.nova_tab_icon),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
private fun RowScope.CreateTabButton(
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (isSelected) 1.1f else 1f, label = "createScale")
    val gradient = if (isDark) {
        listOf(Color(0xFF6B73FF), Color(0xFF9B59B6))
    } else {
        listOf(Color(0xFF007AFF), Color(0xFF5856D6))
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .scale(scale)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Create" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 44.dp, height = 32.dp)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CoordinatorPlaceholderScreen(
    title: String,
    padding: PaddingValues,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.coordinator_placeholder_body, title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CoordinatorPlaceholderDialog(title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.coordinator_placeholder_body, title),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}



@Composable
private fun EchoInvitationPlaceholder(
    echoId: String,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.coordinator_echo_invitation), fontWeight = FontWeight.Bold)
                Text("ID: $echoId", modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.login_close),
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                    Text(
                        "Accept",
                        modifier = Modifier.clickable { onAccept(echoId) },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EchoViewerPlaceholder(echoId: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.coordinator_echo_viewer), style = MaterialTheme.typography.headlineSmall)
                Text("ID: $echoId", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Deep links — port de handleDeepLink / handleCustomScheme / handleUniversalLink. */
object TabBarDeepLinkHandler {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )

    fun handle(uri: Uri, firestoreService: FirestoreService, onHandled: () -> Unit = {}) {
        when (uri.scheme?.lowercase()) {
            "moments", "glowsy" -> handleCustomScheme(uri, firestoreService)
            "https" -> handleUniversalLink(uri)
        }
        onHandled()
    }

    private fun handleCustomScheme(uri: Uri, firestoreService: FirestoreService) {
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()

        when {
            host == "moment" && uri.pathSegments.size > 1 -> {
                AppRouter.navigate(AppRouter.Destination.Moment(uri.pathSegments[1], ""))
            }
            host == "story" && path == "/create" -> {
                AppRouter.navigate(AppRouter.Destination.Creator)
            }
            host == "profile" && path == "/visits" -> {
                AppRouter.navigate(AppRouter.Destination.OwnProfileTab)
                scope.launch {
                    delay(500)
                    AppRouter.navigate(AppRouter.Destination.ShowProfileVisits)
                }
            }
            host == "profile" && uri.pathSegments.size > 1 -> {
                val username = uri.lastPathSegment.orEmpty()
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { firestoreService.fetchUserByUsername(username) }
                        .onSuccess { user ->
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                AppRouter.navigate(AppRouter.Destination.ShowUserProfile(user.id))
                            }
                        }
                }
            }
            host == "messages" -> AppRouter.navigate(AppRouter.Destination.ShowMessages)
            host == "notifications" -> AppRouter.navigate(AppRouter.Destination.ShowNotifications)
            host == "stories" -> AppRouter.navigate(AppRouter.Destination.ShowStories)
        }
    }

    private fun handleUniversalLink(uri: Uri) {
        val host = uri.host?.lowercase().orEmpty()
        val supported = setOf(
            "moments.app", "www.moments.app",
            "momentsapp.app", "www.momentsapp.app",
        )
        if (host !in supported) return
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0] == "moment") {
            scope.launch {
                delay(500)
                AppRouter.navigate(AppRouter.Destination.Moment(segments[1], ""))
            }
        }
    }
}
