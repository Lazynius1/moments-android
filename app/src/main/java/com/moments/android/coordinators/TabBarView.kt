package com.moments.android.coordinators

import android.net.Uri
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import com.moments.android.coordinators.nav3.MomentsDeepLinkParser
import com.moments.android.coordinators.nav3.MomentsNavKey
import com.moments.android.coordinators.nav3.MomentsTabNavHost
import com.moments.android.coordinators.nav3.MomentsTabNavKey
import com.moments.android.coordinators.nav3.rememberMomentsTabNavigationState
import com.moments.android.coordinators.nav3.rememberMomentsTabNavigator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import com.moments.android.icons.MomentsIcons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassButtonTint
import com.moments.android.notifications.services.FCMTokenService
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.services.auth.AuthService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.views.shared.OfflineBannerOverlay
import com.moments.android.views.components.InAppBannerView
import com.moments.android.utilities.HapticManager
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
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
    deepLinkFromNewTask: Boolean = false,
    onDeepLinkHandled: () -> Unit = {},
) {
    val mainViewModel = remember { MainViewModel.shared }
    val hasNewFeedContent by mainViewModel.hasNewFeedContent.collectAsState()
    val hasUnreadNotifications by mainViewModel.hasUnreadNotifications.collectAsState()

    // Nav3 fase 2a/2b: back stacks por tab + DialogSceneStrategy overlays.
    val tabNavigationState = rememberMomentsTabNavigationState()
    val tabNavigator = rememberMomentsTabNavigator(tabNavigationState)
    val selectedTab = tabNavigationState.selectedTabIndex
    var isCreatingStory by remember { mutableStateOf(false) }
    var openCreatorInStoryMode by remember { mutableStateOf(false) }
    var hasPreloadedExplore by remember { mutableStateOf(false) }
    var showEchoInvitation by remember { mutableStateOf(false) }
    var pendingEchoId by remember { mutableStateOf("") }
    var showEchoViewer by remember { mutableStateOf(false) }
    var echoInvitationRoute by remember { mutableStateOf<String?>(null) }
    // ≡ iOS `.toolbar(.hidden, for: .tabBar)` desde Settings / edit / moment zoom
    var suppressTabBar by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    val routerContext = remember(tabNavigator) {
        AppRouterTabBarContext(
            setSelectedTab = { index ->
                if (index == AppTab.toIndex(AppTab.CREATE)) {
                    isCreatingStory = true
                    tabNavigator.push(MomentsNavKey.Creator)
                } else {
                    tabNavigator.selectTabIndex(index)
                }
            },
            setShowCreatorView = { visible ->
                if (visible) tabNavigator.push(MomentsNavKey.Creator)
                else tabNavigator.popIfTop(MomentsNavKey.Creator)
            },
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
                -> tabNavigator.selectTab(MomentsTabNavKey.Explore)
                is CoordinatorNavigationEvent.NavigateToUserProfileInFeed,
                -> tabNavigator.selectTab(MomentsTabNavKey.Feed)
                // AppRouter / deep link → host Nav3 (stack Feed + Back al root).
                is CoordinatorNavigationEvent.NavigateToProfile ->
                    tabNavigator.openProfile(event.userId)
                is CoordinatorNavigationEvent.ShowUserProfile ->
                    tabNavigator.openProfile(event.userId)
                is CoordinatorNavigationEvent.NavigateToMoment -> {
                    val momentId = event.momentId.trim()
                    if (momentId.isEmpty()) return@collectLatest
                    scope.launch {
                        val author = event.userId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: firestoreService.fetchMomentAuthorId(momentId)
                        if (!author.isNullOrEmpty()) {
                            tabNavigator.openMoment(momentId, author)
                        }
                    }
                }
                is CoordinatorNavigationEvent.NavigateToConversation ->
                    tabNavigator.openConversation(event.conversationId)
                is CoordinatorNavigationEvent.ShowStories ->
                    tabNavigator.openStories()
                is CoordinatorNavigationEvent.ShowStoriesStartingAt ->
                    tabNavigator.openStories(startAtUserId = event.userId)
                is CoordinatorNavigationEvent.NavigateToStoryInFeed ->
                    tabNavigator.openStory(event.storyId, event.authorId)
                is CoordinatorNavigationEvent.NavigateToStoryChain ->
                    tabNavigator.openStoryChain(event.chainId, event.chainTitle)
                is CoordinatorNavigationEvent.NavigateToStoryChainInFeed ->
                    tabNavigator.openStoryChain(event.chainId, event.chainTitle)
                is CoordinatorNavigationEvent.NavigateToNotifications -> {
                    tabNavigator.selectTab(MomentsTabNavKey.Feed)
                    tabNavigator.push(MomentsNavKey.ShowNotifications)
                }
                is CoordinatorNavigationEvent.NavigateToFollowRequests,
                is CoordinatorNavigationEvent.ShowProfileVisits,
                -> tabNavigator.selectTab(MomentsTabNavKey.Profile)
                // Un solo host Nav3 (DialogSceneStrategy). Feed no monta otro Dialog en paralelo.
                CoordinatorNavigationEvent.ShowNotifications,
                CoordinatorNavigationEvent.OpenNotifications,
                -> tabNavigator.push(MomentsNavKey.ShowNotifications)
                is CoordinatorNavigationEvent.ShowMessages ->
                    tabNavigator.push(MomentsNavKey.ShowMessages)
                is CoordinatorNavigationEvent.ScrollFeedToTop -> Unit
                is CoordinatorNavigationEvent.ReturnToFeedAfterMomentPublish -> {
                    tabNavigator.selectTab(MomentsTabNavKey.Feed)
                }
                is CoordinatorNavigationEvent.NavigateToOwnProfileTab ->
                    tabNavigator.selectTab(MomentsTabNavKey.Profile)
                is CoordinatorNavigationEvent.NavigateToUserProfile ->
                    tabNavigator.openProfile(event.userId)
                is CoordinatorNavigationEvent.OpenCreatorForChain -> {
                    isCreatingStory = true
                    tabNavigator.push(MomentsNavKey.Creator)
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
                else -> Unit
            }
        }
    }

    LaunchedEffect(deepLinkUri, deepLinkFromNewTask) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        TabBarDeepLinkHandler.handle(
            uri = uri,
            firestoreService = firestoreService,
            fromNewTask = deepLinkFromNewTask,
            openDeepLink = { key, newTask -> tabNavigator.openDeepLink(key, newTask) },
            onHandled = onDeepLinkHandled,
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Skill edge-to-edge: bottom insets los consume el tab bar (navigationBarsPadding).
        // Top/horizontal → contentPadding del Scaffold; no doble-padear el dock.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
            ),
            bottomBar = {
                // Push Profile (Nav3) oculta dock; overlays DialogScene ya cubren encima.
                if (!suppressTabBar && !tabNavigator.shouldHideTabBarForPush()) {
                    MomentsCustomTabBar(
                        selectedTab = selectedTab,
                        onSelectTab = { index ->
                            if (index == AppTab.toIndex(AppTab.HOME) && selectedTab == index) {
                                HapticManager.shared.lightImpact()
                                NavigationEventBus.emit(CoordinatorNavigationEvent.ScrollFeedToTop)
                            } else if (index == AppTab.toIndex(AppTab.CREATE)) {
                                HapticManager.shared.mediumImpact()
                                isCreatingStory = true
                                tabNavigator.push(MomentsNavKey.Creator)
                            } else {
                                HapticManager.shared.selection()
                                tabNavigator.selectTabIndex(index)
                            }
                        },
                        onOpenCreator = {
                            HapticManager.shared.mediumImpact()
                            tabNavigator.push(MomentsNavKey.Creator)
                        },
                        showFeedBadge = hasNewFeedContent,
                        showProfileBadge = hasUnreadNotifications,
                    )
                }
            },
        ) { padding ->
            MomentsTabNavHost(
                navigationState = tabNavigationState,
                navigator = tabNavigator,
                padding = padding,
                isCreatingStory = isCreatingStory,
                onIsCreatingStoryChange = { isCreatingStory = it },
                openCreatorInStoryMode = openCreatorInStoryMode,
                onOpenCreatorInStoryModeChange = { openCreatorInStoryMode = it },
                onSuppressTabBarChange = { suppressTabBar = it },
            )
        }

        InAppBannerView(Modifier.align(Alignment.TopCenter))

        OfflineBannerOverlay(Modifier.align(Alignment.TopCenter))

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

        if (showEchoViewer && pendingEchoId.isNotEmpty()) {
            com.moments.android.views.echoes.EchoViewerUI(
                echoId = pendingEchoId,
                onDismiss = {
                    showEchoViewer = false
                    pendingEchoId = ""
                },
            )
        }
    }
}

/**
 * Tab bar docked estilo IG (full-width, sin labels).
 * Edge-to-edge: [Surface] pinta bajo la gesture/nav bar; iconos en [navigationBarsPadding].
 * TODO(adaptive): NavigationSuiteScaffold / rail en tablet — skill `adaptive` + Nav3.
 */
@Composable
private fun MomentsCustomTabBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenCreator: () -> Unit,
    showFeedBadge: Boolean,
    showProfileBadge: Boolean,
) {
    val isDark = isSystemInDarkTheme()
    val activeColor = if (isDark) Color.White else MomentsGlassButtonTint.dark
    val inactiveColor = activeColor.copy(alpha = 0.55f)
    val chromeFill = MaterialTheme.colorScheme.background
    val hairline = Color.Black.copy(alpha = if (isDark) 0.28f else 0.10f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = chromeFill,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(hairline),
            )
            // Altura de iconos fija; el padding de nav bars va fuera para que el
            // fondo del Surface se extienda edge-to-edge bajo la gesture bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .padding(horizontal = 8.dp),
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
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
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
                // ≡ iOS MomentsFloatingTabBar perfil = foto + StorySegmentedRing
                ProfileTabBarItem(
                    title = stringResource(R.string.tab_bar_profile),
                    isSelected = selectedTab == 4,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    showBadge = showProfileBadge,
                    onClick = { onSelectTab(4) },
                )
            }
            // Skill: inset size modifier — chrome del dock bajo la gesture/nav bar.
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
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
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            if (isNova) {
                NovaTabGlyph(
                    size = 26.dp,
                    color = if (isSelected) activeColor else inactiveColor,
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(26.dp),
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
    }
}

/**
 * ≡ iOS `FloatingTabProfileSegmentRenderer` en el tab Perfil:
 * foto + [StoryRingAvatarView] (mismos aros/audiencias que el feed).
 * Refresh al [CoordinatorNavigationEvent.StoryUploaded] (paridad `StoryUploaded`).
 */
@Composable
private fun RowScope.ProfileTabBarItem(
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    showBadge: Boolean,
    onClick: () -> Unit,
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var ringRefreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(uid) {
        NavigationEventBus.events
            .filterIsInstance<CoordinatorNavigationEvent.StoryUploaded>()
            .collect { ringRefreshTrigger += 1 }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (uid.isNotEmpty()) {
                // iOS tab: avatar 30 / line 2.2; Android iconos ~26 → 28 + aro cabe en 49dp.
                StoryRingAvatarView(
                    userId = uid,
                    size = 28.dp,
                    lineWidth = 2.2.dp,
                    refreshTrigger = ringRefreshTrigger,
                    isOwnStory = true,
                    hapticsEnabled = false,
                )
            } else {
                Icon(
                    if (isSelected) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = null,
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(26.dp),
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
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
) {
    // ≡ iOS modern Tab `camera.aperture`: icono suelto (sin pill/gradiente), un poco mayor
    // que house/person (26) para que lea como el simbólico de captura.
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "Create" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MomentsIcons.CameraAperture,
            contentDescription = null,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(30.dp),
        )
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

/**
 * Deep links — port de handleDeepLink / handleCustomScheme / handleUniversalLink.
 *
 * Sync: [MomentsDeepLinkParser] → [MomentsNavKey] → [MomentsTabNavigator.openDeepLink]
 * (stack sintético; skill navigation-3 / deeplink-guide).
 */
object TabBarDeepLinkHandler {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )

    fun handle(
        uri: Uri,
        firestoreService: FirestoreService,
        fromNewTask: Boolean = false,
        openDeepLink: (MomentsNavKey, fromNewTask: Boolean) -> Unit,
        onHandled: () -> Unit = {},
    ) {
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        val scheme = uri.scheme?.lowercase()

        // profile/visits: padre sintético = tab Profile
        if (scheme in setOf("moments", "glowsy") && host == "profile" && path == "/visits") {
            openDeepLink(MomentsNavKey.ShowProfileVisits, fromNewTask)
            onHandled()
            return
        }

        // profile/{username}: resolución async Firestore → push Profile sintético
        if (scheme in setOf("moments", "glowsy") && host == "profile" && uri.pathSegments.size > 1) {
            val username = uri.lastPathSegment.orEmpty()
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { firestoreService.fetchUserByUsername(username) }
                    .onSuccess { user ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            openDeepLink(MomentsNavKey.Profile(user.id), fromNewTask)
                        }
                    }
            }
            onHandled()
            return
        }

        // https universal moment
        if (scheme == "https") {
            val key = MomentsDeepLinkParser.parse(uri)
            if (key is MomentsNavKey.Moment) {
                scope.launch {
                    delay(500)
                    openDeepLink(key, fromNewTask)
                }
                onHandled()
                return
            }
        }

        val parsed = MomentsDeepLinkParser.parse(uri)
        when {
            parsed is MomentsNavKey.Moment && parsed.authorId.isBlank() -> {
                scope.launch {
                    val author = firestoreService.fetchMomentAuthorId(parsed.id)
                    if (!author.isNullOrEmpty()) {
                        openDeepLink(MomentsNavKey.Moment(parsed.id, author), fromNewTask)
                    }
                }
            }
            parsed != null -> openDeepLink(parsed, fromNewTask)
        }
        onHandled()
    }
}
