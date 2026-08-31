package com.moments.android.views.profile.core.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.views.components.momentRefresh
import com.moments.android.views.profile.core.ProfileFloatingTabBar
import com.moments.android.views.profile.core.ProfilePillTabs
import com.moments.android.views.profile.core.ProfileTabType
import com.moments.android.views.profile.core.ProfileViewModel
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.views.profile.core.SocialConnectionsRoute
import com.moments.android.views.profile.core.SocialConnectionsScreen
import com.moments.android.views.profile.highlights.ProfileHighlightsView
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryViewModel
import kotlinx.coroutines.delay

/**
 * Port de `ProfileShellComponents.swift`.
 *
 * Puentes conscientes:
 * - Temas de perfil / blur de fondo 🚫 → canvas AdaptiveColors (`#0B1215` / `#FAF9F6`).
 * - Grid hero / menú largo / pin / preview editor → host vía [onMomentLongPress] (post-paridad).
 * - Zoom detail destination vive en `ProfileView` (como navigationDestination iOS).
 */

/** Destinos que abren las stats del perfil, equivalentes a `SocialConnectionsRoute` de iOS. */
enum class ProfileConnectionsRoute {
    VISITS,
    FOLLOWERS,
    FOLLOWING,
    MUTUALS,
}

private fun ProfileConnectionsRoute.toSocialTab(): SocialConnectionTab = when (this) {
    ProfileConnectionsRoute.VISITS -> SocialConnectionTab.VISITS
    ProfileConnectionsRoute.FOLLOWERS -> SocialConnectionTab.FOLLOWERS
    ProfileConnectionsRoute.FOLLOWING -> SocialConnectionTab.FOLLOWING
    ProfileConnectionsRoute.MUTUALS -> SocialConnectionTab.MUTUALS
}

/**
 * ≡ `ModernBackgroundView` iOS — en Android solo canvas (sin tema/blur/material).
 * [profileImagePath] / [scrollOffset] / [profileTheme] se conservan por firma iOS.
 */
@Composable
fun ModernBackgroundView(
    profileImagePath: String?,
    scrollOffset: Float,
    profileTheme: String? = null,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedImage = profileImagePath
    @Suppress("UNUSED_PARAMETER")
    val unusedScroll = scrollOffset
    @Suppress("UNUSED_PARAMETER")
    val unusedTheme = profileTheme
    val dark = isSystemInDarkTheme()
    Box(
        modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
    )
}

/**
 * Orquestador ≡ `ModernProfileContentView`.
 * Ownership de sheets/zoom queda en el caller (`ProfileView`); aquí collapse + tabs + grids.
 */
@Composable
fun ModernProfileContentView(
    viewModel: ProfileViewModel,
    storyViewModel: StoryViewModel,
    selectedTab: ProfileTabType,
    onSelectTab: (ProfileTabType) -> Unit,
    savedState: ProfileSavedContentState,
    onOpenSavedManager: () -> Unit,
    onOpenMoment: (List<Moment>, Int, ProfileMomentZoomFeedKind) -> Unit,
    onRefreshSavedVisibility: (Moment, (Boolean) -> Unit) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onEditProfile: () -> Unit,
    onShowStory: () -> Unit,
    onShowProfileImage: () -> Unit,
    onShowNotifications: () -> Unit,
    onShowQr: () -> Unit,
    onShowIncognito: () -> Unit,
    onShowSettings: () -> Unit,
    isIncognitoActive: Boolean,
    onAvatarBoundsChange: (Rect) -> Unit = {},
    onMenuBoundsChange: (Rect) -> Unit = {},
    hideAvatarForFlip: Boolean = false,
    hideMenuForFlip: Boolean = false,
    onMomentLongPress: (Moment, Int, ProfileMomentZoomFeedKind) -> Unit = { _, _, _ -> },
    onRefreshSaved: () -> Unit = {},
    openConnectionsRoute: ProfileConnectionsRoute? = null,
    onOpenConnectionsRouteConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dark = isSystemInDarkTheme()

    when {
        viewModel.isLoading -> {
            Column(modifier.fillMaxWidth()) {
                Spacer(Modifier.height(statusTop + ProfileHeaderCollapseMetrics.topContentInset))
                ProfileHeaderSkeletonView()
                ProfileMomentsGridSkeletonView(Modifier.padding(top = 20.dp))
            }
        }

        viewModel.errorMessage != null -> {
            ModernErrorView(
                errorMessage = viewModel.errorMessage.orEmpty(),
                onRetry = viewModel::refreshProfile,
                modifier = modifier,
            )
        }

        else -> {
            val density = LocalDensity.current
            val scrollState = rememberScrollState()
            var tabsMinY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
            var connectionsRoute by remember { mutableStateOf<ProfileConnectionsRoute?>(null) }
            var openedProfileId by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(openConnectionsRoute) {
                openConnectionsRoute?.let { route ->
                    connectionsRoute = route
                    onOpenConnectionsRouteConsumed()
                }
            }

            val collapseProgress = ProfileHeaderCollapseMetrics.progress(tabsMinY)
            val tabsArePinned = ProfileHeaderCollapseMetrics.tabsArePinned(tabsMinY)
            val pinnedAlpha by animateFloatAsState(
                targetValue = if (tabsArePinned) 1f else 0f,
                label = "ownPinnedTabs",
            )

            LaunchedEffect(selectedTab, viewModel.userProfile?.id) {
                if (selectedTab == ProfileTabType.TAGGED &&
                    viewModel.taggedMoments.isEmpty() &&
                    !viewModel.isLoadingTagged
                ) {
                    viewModel.userProfile?.id?.let(viewModel::fetchTaggedMoments)
                }
            }

            Box(
                modifier
                    .fillMaxSize()
                    .background(ProfileMomentZoomNavigation.canvasBackground(dark))
                    .momentRefresh {
                        viewModel.refreshProfile()
                        if (selectedTab == ProfileTabType.SAVED) onRefreshSaved()
                        while (viewModel.isRefreshing) delay(100)
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = safeBottom + 100.dp),
                ) {
                    Spacer(Modifier.height(statusTop + ProfileHeaderCollapseMetrics.topContentInset))

                    ModernProfileHeader(
                        viewModel = viewModel,
                        storyViewModel = storyViewModel,
                        usernameCollapseProgress = collapseProgress,
                        onEditProfile = onEditProfile,
                        onShowStoryViewer = onShowStory,
                        onShowProfileImage = onShowProfileImage,
                        onAvatarBoundsChange = onAvatarBoundsChange,
                        hideAvatarForFlip = hideAvatarForFlip,
                        modifier = Modifier.padding(
                            top = ProfileHeaderCollapseMetrics.headerTopPadding,
                            bottom = 4.dp,
                        ),
                    )

                    ProfileOverviewCard(
                        viewModel = viewModel,
                        interests = viewModel.userProfile?.interests.orEmpty(),
                        onOpenVisits = { connectionsRoute = ProfileConnectionsRoute.VISITS },
                        onOpenFollowers = { connectionsRoute = ProfileConnectionsRoute.FOLLOWERS },
                        onOpenFollowing = { connectionsRoute = ProfileConnectionsRoute.FOLLOWING },
                        onOpenMutuals = { connectionsRoute = ProfileConnectionsRoute.MUTUALS },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    viewModel.userProfile?.id?.takeIf { it.isNotBlank() }?.let { profileId ->
                        ProfileHighlightsView(
                            userId = profileId,
                            isOwnProfile = profileId == FirebaseAuth.getInstance().currentUser?.uid,
                            isCompact = true,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    if (viewModel.isRefreshing) {
                        Box(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ModernRefreshIndicator()
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        ProfilePillTabs(
                            selectedTab = selectedTab,
                            onSelect = onSelectTab,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    tabsMinY = with(density) { coords.positionInRoot().y.toDp().value }
                                }
                                .alpha(if (tabsArePinned) 0f else 1f),
                        )

                        when (selectedTab) {
                            ProfileTabType.MOMENTS -> ProfileOwnMomentsTab(
                                moments = viewModel.moments,
                                loading = viewModel.isLoadingMoments,
                                customListNamesById = viewModel.customListNamesById,
                                onOpen = { moments, index ->
                                    onOpenMoment(moments, index, ProfileMomentZoomFeedKind.OWN_MOMENTS)
                                },
                                onLongPress = { moment, index ->
                                    onMomentLongPress(moment, index, ProfileMomentZoomFeedKind.OWN_MOMENTS)
                                },
                            )

                            ProfileTabType.SAVED -> ProfileSavedContent(
                                state = savedState,
                                onOpenSavedManager = onOpenSavedManager,
                                onOpenDetail = { moments, index ->
                                    onOpenMoment(moments, index, ProfileMomentZoomFeedKind.SAVED_MOMENTS)
                                },
                                onRefreshVisibility = onRefreshSavedVisibility,
                                onRemoveMoment = onRemoveSaved,
                            )

                            ProfileTabType.TAGGED -> ProfileOwnTaggedTab(
                                moments = viewModel.taggedMoments,
                                loading = viewModel.isLoadingTagged,
                                customListNamesById = viewModel.customListNamesById,
                                onOpen = { moments, index ->
                                    onOpenMoment(moments, index, ProfileMomentZoomFeedKind.TAGGED_MOMENTS)
                                },
                                onLongPress = { moment, index ->
                                    onMomentLongPress(moment, index, ProfileMomentZoomFeedKind.TAGGED_MOMENTS)
                                },
                            )
                        }
                    }
                }

                ProfileStickyChromeContainer(
                    blurProgress = collapseProgress,
                    tabsArePinned = tabsArePinned,
                    chrome = {
                        ProfileOwnPinnedTopChrome(
                            username = viewModel.userProfile?.username
                                ?: stringResource(R.string.profile_default_username),
                            isVerified = viewModel.userProfile?.isVerified == true,
                            collapseProgress = collapseProgress,
                            isIncognitoActive = isIncognitoActive,
                            onNotifications = onShowNotifications,
                            onShowQrCode = onShowQr,
                            onShowIncognito = onShowIncognito,
                            onShowSettings = onShowSettings,
                            onMenuBoundsChange = onMenuBoundsChange,
                            hideMenuForFlip = hideMenuForFlip,
                        )
                    },
                    pinnedTabs = {
                        if (pinnedAlpha > 0.01f) {
                            Box(
                                Modifier.alpha(pinnedAlpha).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                ProfileFloatingTabBar(
                                    selectedTab = selectedTab,
                                    onSelect = onSelectTab,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )

                connectionsRoute?.let { route ->
                    Dialog(
                        onDismissRequest = { connectionsRoute = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        SocialConnectionsScreen(
                            route = SocialConnectionsRoute(initialTab = route.toSocialTab()),
                            username = viewModel.userProfile?.username.orEmpty(),
                            availableTabs = SocialConnectionTab.ownProfileTabs,
                            includesVisits = true,
                            isOwnProfile = true,
                            currentUser = viewModel.userProfile,
                            inCommonUsers = emptyList(),
                            followers = viewModel.followers,
                            following = viewModel.following,
                            mutuals = viewModel.mutuals,
                            suggestedUsers = emptyList(),
                            visitTimestamps = viewModel.visitTimestamps,
                            listViewModel = viewModel,
                            viewerInterests = viewModel.userProfile?.interests.orEmpty(),
                            onDismiss = { connectionsRoute = null },
                            onOpenProfile = { openedProfileId = it },
                            onOpenStories = {},
                            onOpenChat = {},
                            onOpenMoment = {},
                        )
                    }
                }

                openedProfileId?.let { nested ->
                    Dialog(
                        onDismissRequest = { openedProfileId = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        UserProfileView(
                            userId = nested,
                            onDismiss = { openedProfileId = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOwnMomentsTab(
    moments: List<Moment>,
    loading: Boolean,
    customListNamesById: Map<String, String>,
    onOpen: (List<Moment>, Int) -> Unit,
    onLongPress: (Moment, Int) -> Unit,
) {
    when {
        moments.isEmpty() && loading -> ProfileMomentsGridSkeletonView()
        moments.isEmpty() -> ModernEmptyMomentsView(Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        else -> ProfileMomentsBentoGrid(
            moments = moments,
            descriptors = ProfileBentoTileAssigner.assign(moments),
        ) { moment, unitWidth, index, descriptor ->
            ScreenshotProtectedView(
                isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
            ) {
                ModernMomentThumbnail(
                    moment = moment,
                    size = unitWidth,
                    customListNamesById = customListNamesById,
                    zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index),
                    onTap = { onOpen(moments, index) },
                    onLongPress = { onLongPress(moment, index) },
                    usesDiscreetAudienceIcon = true,
                    showsAudienceBadge = false,
                    gridIndex = index,
                    descriptor = descriptor,
                )
            }
        }
    }
}

@Composable
private fun ProfileOwnTaggedTab(
    moments: List<Moment>,
    loading: Boolean,
    customListNamesById: Map<String, String>,
    onOpen: (List<Moment>, Int) -> Unit,
    onLongPress: (Moment, Int) -> Unit,
) {
    when {
        loading -> {
            Box(
                Modifier.fillMaxWidth().height(400.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = if (isSystemInDarkTheme()) Color.White else Color(0xFF0B1215))
            }
        }
        moments.isEmpty() -> {
            ProfileSectionEmptyState(
                icon = ProfileSectionEmptyIcon.TAGGED,
                title = R.string.profile_tagged_empty_title,
                subtitle = R.string.profile_tagged_empty_description,
                modifier = Modifier.height(400.dp),
            )
        }
        else -> ProfileMomentsBentoGrid(
            moments = moments,
            descriptors = ProfileBentoTileAssigner.simple(moments),
        ) { moment, unitWidth, index, descriptor ->
            ScreenshotProtectedView(
                isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
            ) {
                ModernMomentThumbnail(
                    moment = moment,
                    size = unitWidth,
                    customListNamesById = customListNamesById,
                    zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index),
                    onTap = { onOpen(moments, index) },
                    onLongPress = { onLongPress(moment, index) },
                    showsAudienceBadge = false,
                    gridIndex = index,
                    descriptor = descriptor,
                )
            }
        }
    }
}

/** Port de `ModernRefreshIndicator`. */
@Composable
fun ModernRefreshIndicator(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "profileRefresh")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Restart),
        label = "profileRefreshTurn",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1_000), RepeatMode.Reverse),
        label = "profileRefreshPulse",
    )
    val material = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(material)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(material),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color(0xFF007AFF),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        rotationZ = turn
                        scaleX = pulse
                        scaleY = pulse
                    },
            )
        }
        Text(
            stringResource(R.string.profile_shell_updating),
            color = if (dark) Color.White.copy(0.64f) else Color(0xFF52626A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
