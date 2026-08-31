package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.components.momentRefresh
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.views.profile.core.sections.ProfileBentoTileAssigner
import com.moments.android.views.profile.core.sections.ProfileHeaderCollapseMetrics
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.profile.core.sections.ProfileMomentsBentoGrid
import com.moments.android.views.profile.core.sections.ProfileMomentsGridSkeletonView
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyIcon
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyState
import com.moments.android.views.profile.core.sections.ProfileStickyChromeContainer
import com.moments.android.views.profile.highlights.ProfileHighlightsView
import com.moments.android.views.profile.userprofile.UserProfileFloatingTabBar
import com.moments.android.views.profile.userprofile.UserProfilePillTabs
import com.moments.android.views.profile.userprofile.UserProfileTabType
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.delay

/**
 * Port de `UserModernPublicProfileView` (`UserProfilePublicProfileView.swift`).
 *
 * Sticky chrome + floating tabs vía `ProfileStickyChromeContainer` (tabsMinY con
 * `onGloballyPositioned`, como PreferenceKey iOS). Pull-to-refresh ≡ `.momentRefresh`.
 * Navegación (QR/report/social/chat/zoom) por callbacks del host.
 */
@Composable
fun UserModernPublicProfileView(
    viewModel: UserProfileViewModel,
    selectedTab: UserProfileTabType,
    onSelectTab: (UserProfileTabType) -> Unit,
    safeAreaBottom: Dp,
    onFollowAction: () -> Unit,
    onDismiss: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenMessage: () -> Unit,
    onShowProfileImageFullscreen: () -> Unit,
    onShowQrCode: () -> Unit,
    onAvatarBoundsChange: (Rect) -> Unit = {},
    hideAvatarForFlip: Boolean = false,
    onShowReport: () -> Unit,
    onOpenSocial: (SocialConnectionTab) -> Unit,
    onOpenMoment: (moments: List<com.moments.android.models.Moment>, index: Int) -> Unit,
    onMomentLongPress: (moment: com.moments.android.models.Moment, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // ≡ ProfileShellComponents: statusTop + topContentInset (chrome sticky bajo safe area)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var highlightsRefreshToken by remember { mutableIntStateOf(0) }
    var storyRingRefreshToken by remember { mutableIntStateOf(0) }
    var tabsMinY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    val collapseProgress = ProfileHeaderCollapseMetrics.progress(tabsMinY)
    val tabsArePinned = ProfileHeaderCollapseMetrics.tabsArePinned(tabsMinY)
    val pinnedAlpha by animateFloatAsState(
        targetValue = if (tabsArePinned) 1f else 0f,
        label = "pinnedTabs",
    )

    LaunchedEffect(selectedTab) {
        if (selectedTab == UserProfileTabType.TAGGED &&
            viewModel.taggedMoments.isEmpty() &&
            !viewModel.isLoadingTagged
        ) {
            viewModel.fetchTaggedMoments()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(ProfileMomentZoomNavigation.canvasBackground(colors.isDark))
            .momentRefresh {
                highlightsRefreshToken += 1
                storyRingRefreshToken += 1
                viewModel.refreshProfile()
                // ≡ Timer iOS: esperar a que termine `isRefreshing`.
                while (viewModel.isRefreshing) delay(100)
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = safeAreaBottom + 120.dp),
        ) {
            Spacer(Modifier.height(statusTop + ProfileHeaderCollapseMetrics.topContentInset))

            UserModernProfileHeader(
                viewModel = viewModel,
                storyRingRefreshTrigger = storyRingRefreshToken,
                usernameCollapseProgress = collapseProgress,
                onFollowAction = onFollowAction,
                onOpenStories = onOpenStories,
                onShowProfileImageFullscreen = onShowProfileImageFullscreen,
                onOpenMessage = onOpenMessage,
                onAvatarBoundsChange = onAvatarBoundsChange,
                hideAvatarForFlip = hideAvatarForFlip,
                modifier = Modifier
                    .padding(top = ProfileHeaderCollapseMetrics.headerTopPadding)
                    .padding(bottom = 4.dp),
            )

            UserProfileOverviewSection(
                viewModel = viewModel,
                interests = viewModel.userProfile?.interests ?: emptyList(),
                onOpenSocial = onOpenSocial,
                onSelectMoments = { onSelectTab(UserProfileTabType.MOMENTS) },
                modifier = Modifier.padding(bottom = 4.dp),
            )

            viewModel.userProfile?.id?.let { profileId ->
                ProfileHighlightsView(
                    userId = profileId,
                    isOwnProfile = false,
                    isCompact = true,
                    refreshTrigger = highlightsRefreshToken,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (viewModel.isRefreshing) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    UserModernRefreshIndicator()
                }
            }

            Column(Modifier.fillMaxWidth()) {
                UserProfilePillTabs(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 4.dp)
                        .onGloballyPositioned { coords ->
                            // PreferenceKey iOS usa puntos; Convertir px → dp para `tabsPinY` / `tabsFadeLead`.
                            tabsMinY = with(density) { coords.positionInRoot().y.toDp().value }
                        }
                        .alpha(if (tabsArePinned) 0f else 1f),
                )

                when (selectedTab) {
                    UserProfileTabType.MOMENTS -> {
                        if (viewModel.moments.isEmpty()) {
                            if (viewModel.isLoadingMoments) {
                                ProfileMomentsGridSkeletonView()
                            } else {
                                UserModernEmptyMomentsView(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                )
                            }
                        } else {
                            ProfileMomentsBentoGrid(moments = viewModel.moments) { moment, unitWidth, index, descriptor ->
                                ScreenshotProtectedView(
                                    isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                                ) {
                                    UserModernMomentThumbnail(
                                        moment = moment,
                                        size = unitWidth,
                                        zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index),
                                        onTap = { onOpenMoment(viewModel.moments, index) },
                                        onLongPress = { onMomentLongPress(moment, index) },
                                        gridIndex = index,
                                        descriptor = descriptor,
                                    )
                                }
                            }
                        }
                    }

                    UserProfileTabType.TAGGED -> {
                        when {
                            viewModel.isLoadingTagged -> {
                                Box(
                                    Modifier.fillMaxWidth().height(400.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MomentsCircularProgressIndicator()
                                }
                            }
                            viewModel.taggedMoments.isEmpty() -> {
                                ProfileSectionEmptyState(
                                    icon = ProfileSectionEmptyIcon.TAGGED,
                                    title = R.string.profile_tagged_empty_title,
                                    subtitle = R.string.profile_tagged_empty_description,
                                )
                            }
                            else -> {
                                ProfileMomentsBentoGrid(
                                    moments = viewModel.taggedMoments,
                                    descriptors = ProfileBentoTileAssigner.simple(viewModel.taggedMoments),
                                ) { moment, unitWidth, index, descriptor ->
                                    ScreenshotProtectedView(
                                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                                    ) {
                                        UserModernMomentThumbnail(
                                            moment = moment,
                                            size = unitWidth,
                                            zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index),
                                            onTap = { onOpenMoment(viewModel.taggedMoments, index) },
                                            onLongPress = { onMomentLongPress(moment, index) },
                                            gridIndex = index,
                                            descriptor = descriptor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ProfileStickyChromeContainer(
            blurProgress = collapseProgress,
            tabsArePinned = tabsArePinned,
            chrome = {
                ProfileVisitorPinnedTopChrome(
                    viewModel = viewModel,
                    collapseProgress = collapseProgress,
                    onDismiss = onDismiss,
                    onShowQrCode = onShowQrCode,
                    onShowReport = onShowReport,
                )
            },
            pinnedTabs = {
                if (pinnedAlpha > 0.01f) {
                    Box(Modifier.alpha(pinnedAlpha).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        UserProfileFloatingTabBar(
                            selectedTab = selectedTab,
                            onSelectTab = onSelectTab,
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}
