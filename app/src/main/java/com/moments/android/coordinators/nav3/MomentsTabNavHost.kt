package com.moments.android.coordinators.nav3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.MainViewModel
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.notifications.screens.NotificationsScreen
import com.moments.android.views.creator.CreatorView
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.feed.core.FeedView
import com.moments.android.views.feed.core.sections.FeedMomentDetailRoute
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.screens.MessagingView
import com.moments.android.views.nova.NovaView
import com.moments.android.views.profile.core.ProfileView
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.story.StoriesView
import com.moments.android.views.story.StoryChainView
import com.moments.android.adaptive.AdaptiveContentWidths

/**
 * Host Nav3 del dock — skill `navigation-3` fase 2a/2b.
 *
 * - Tabs = [MomentsTabNavKey] roots
 * - Overlays DialogScene: creator / notifications / messages / moment / conversation / stories / chain
 * - Push SinglePane: Profile (Back → Feed root)
 */
@Composable
fun MomentsTabNavHost(
    navigationState: MomentsTabNavigationState,
    navigator: MomentsTabNavigator,
    padding: PaddingValues,
    isCreatingStory: Boolean,
    onIsCreatingStoryChange: (Boolean) -> Unit,
    openCreatorInStoryMode: Boolean,
    onOpenCreatorInStoryModeChange: (Boolean) -> Unit,
    onSuppressTabBarChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
    val fullScreenDialog = remember {
        DialogSceneStrategy.dialog(
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        )
    }
    val showCreatorView = navigator.contains(MomentsNavKey.Creator)

    val provider: (NavKey) -> NavEntry<NavKey> = { key ->
        when (key) {
            MomentsTabNavKey.Feed -> NavEntry(key) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    Box(
                        Modifier
                            .widthIn(max = AdaptiveContentWidths.FeedMax)
                            .fillMaxSize(),
                    ) {
                        FeedView(
                            padding = padding,
                            showCreatorView = showCreatorView,
                            onSuppressTabBarChange = onSuppressTabBarChange,
                            onShowCreatorViewChange = { visible ->
                                if (visible) {
                                    onIsCreatingStoryChange(true)
                                    navigator.push(MomentsNavKey.Creator)
                                } else {
                                    navigator.popIfTop(MomentsNavKey.Creator)
                                    onOpenCreatorInStoryModeChange(false)
                                    onIsCreatingStoryChange(false)
                                }
                            },
                        )
                    }
                }
            }
            MomentsTabNavKey.Messages -> NavEntry(key) {
                // Solo bottom: el toolbar ya aplica statusBarsPadding (evitar doble hueco).
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                ) {
                    // ≡ iOS tab MessagingView(onDismiss: nil) — sin chevron de cierre.
                    MessagingView(
                        onDismiss = {},
                        embeddedInTab = true,
                        onSuppressTabBarChange = onSuppressTabBarChange,
                    )
                }
            }
            MomentsTabNavKey.Explore -> NavEntry(key) {
                ExploreView(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                )
            }
            MomentsTabNavKey.Profile -> NavEntry(key) {
                ProfileView(
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onSuppressTabBarChange = onSuppressTabBarChange,
                )
            }
            MomentsNavKey.Creator -> NavEntry(key, metadata = fullScreenDialog) {
                val creatorSurface = rememberAdaptiveColors().surfaceBackground
                Box(Modifier.fillMaxSize().background(creatorSurface)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
                    ) {
                        CreatorView(
                            showCreatorView = true,
                            onShowCreatorViewChange = { visible ->
                                if (!visible) {
                                    navigator.navigateUp()
                                    onOpenCreatorInStoryModeChange(false)
                                    onIsCreatingStoryChange(false)
                                }
                            },
                            isCreatingStory = isCreatingStory,
                            onIsCreatingStoryChange = onIsCreatingStoryChange,
                            openInStoryMode = openCreatorInStoryMode,
                        )
                    }
                }
            }
            MomentsNavKey.ShowNotifications,
            is MomentsNavKey.Notifications,
            -> NavEntry(key, metadata = fullScreenDialog) {
                Surface(Modifier.fillMaxSize()) {
                    NotificationsScreen(
                        onBack = {
                            navigator.navigateUp()
                            MainViewModel.shared.markNotificationsAsSeen()
                        },
                        onNotificationsCleared = {
                            NavigationEventBus.emit(CoordinatorNavigationEvent.NotificationsCleared)
                        },
                    )
                }
            }
            MomentsNavKey.ShowMessages -> NavEntry(key, metadata = fullScreenDialog) {
                // Legacy overlay — preferir tab Messages; se mantiene por deep links antiguos.
                Surface(modifier.fillMaxSize(), color = Color.Transparent) {
                    MessagingView(
                        onDismiss = { navigator.navigateUp() },
                    )
                }
            }
            MomentsNavKey.ShowNova -> NavEntry(key, metadata = fullScreenDialog) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    // ≡ iOS navigationDestination(NovaView) — Back del sistema cierra.
                    androidx.activity.compose.BackHandler { navigator.navigateUp() }
                    NovaView()
                }
            }
            is MomentsNavKey.Conversation -> NavEntry(key, metadata = fullScreenDialog) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    MessagingView(
                        targetConversationId = key.id,
                        // Up sintético: Conversation → ShowMessages → Feed
                        onDismiss = { navigator.navigateUp() },
                    )
                }
            }
            is MomentsNavKey.Moment -> NavEntry(key, metadata = fullScreenDialog) {
                Surface(Modifier.fillMaxSize()) {
                    FeedMomentDetailRoute(
                        momentId = key.id,
                        authorId = key.authorId,
                        onDismiss = { navigator.navigateUp() },
                    )
                }
            }
            MomentsNavKey.ShowStories -> NavEntry(key, metadata = fullScreenDialog) {
                StoriesView(
                    onDismiss = { navigator.navigateUp() },
                )
            }
            is MomentsNavKey.Story -> NavEntry(key, metadata = fullScreenDialog) {
                StoriesView(
                    startAtUserId = key.authorId,
                    onDismiss = { navigator.navigateUp() },
                )
            }
            is MomentsNavKey.StoryChain -> NavEntry(key, metadata = fullScreenDialog) {
                Surface(Modifier.fillMaxSize()) {
                    StoryChainView(
                        chainId = key.chainId,
                        chainTitle = key.title,
                        canContinueChain = true,
                        onDismiss = { navigator.navigateUp() },
                        onContinueChain = { chainId, chainTitle, position ->
                            navigator.navigateUp()
                            NavigationEventBus.emit(
                                CoordinatorNavigationEvent.OpenCreatorForChain(
                                    chainId = chainId,
                                    chainTitle = chainTitle,
                                    chainPosition = position,
                                ),
                            )
                        },
                    )
                }
            }
            is MomentsNavKey.Profile,
            is MomentsNavKey.ShowUserProfile,
            is MomentsNavKey.UserProfileInFeed,
            -> {
                val userId = when (key) {
                    is MomentsNavKey.Profile -> key.userId
                    is MomentsNavKey.ShowUserProfile -> key.userId
                    is MomentsNavKey.UserProfileInFeed -> key.userId
                    else -> ""
                }
                NavEntry(key) {
                    Surface(Modifier.fillMaxSize()) {
                        UserProfileView(
                            userId = userId,
                            onDismiss = { navigator.navigateUp() },
                        )
                    }
                }
            }
            else -> error("Unknown tab/overlay route: $key")
        }
    }

    NavDisplay(
        entries = navigationState.toDecoratedEntries(provider),
        onBack = {
            val top = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
            when (top) {
                MomentsNavKey.ShowNotifications,
                is MomentsNavKey.Notifications,
                -> MainViewModel.shared.markNotificationsAsSeen()
                MomentsNavKey.Creator -> {
                    onOpenCreatorInStoryModeChange(false)
                    onIsCreatingStoryChange(false)
                }
                else -> Unit
            }
            // Stack sintético: Back hace pop hacia el root (Feed/Profile/…).
            // Up explícito (toolbars) → [MomentsTabNavigator.navigateUp] /
            // [createDeepLinkUpTaskStack] si hace falta reiniciar Task.
            navigator.goBack()
        },
        sceneStrategies = listOf(dialogStrategy),
        modifier = modifier.fillMaxSize(),
    )
}
