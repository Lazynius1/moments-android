package com.moments.android.views.profile.userprofile.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.profile.core.sections.ProfileMomentsBentoGrid
import com.moments.android.views.profile.core.sections.ProfileMomentsGridSkeletonView
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyIcon
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyState
import com.moments.android.views.profile.highlights.ProfileHighlightsView
import com.moments.android.views.profile.userprofile.UserProfilePillTabs
import com.moments.android.views.profile.userprofile.UserProfileTabType
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.shared.ScreenshotProtectedView

/**
 * Port de `UserProfilePublicProfileView.swift` — la composición del perfil público visitado:
 * chrome fijado, cabecera, resumen, destacadas, pestañas (momentos / etiquetados) y sus grids bento.
 *
 * Puentes conscientes respecto a iOS:
 * - `PreferenceKey` + `coordinateSpace` de SwiftUI → `ScrollState.value` de Compose para calcular el
 *   progreso de colapso del username.
 * - Los `@Binding` de navegación (chat, QR, report, ruta de conexiones sociales, destino de zoom) se
 *   exponen como callbacks; el host mantiene el ownership, igual que en las otras Sections portadas.
 * - `ProfileMomentsBentoGrid` de Kotlin ya calcula su altura con `BoxWithConstraints`, así que no se
 *   usan aquí `calculateBentoGridHeight`/`calculateTaggedGridHeight` (siguen portados por paridad).
 * - El pull-to-refresh (`momentRefresh` en iOS) queda en el host: aquí solo se pinta el indicador
 *   cuando `viewModel.isRefreshing`.
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
    onShowReport: () -> Unit,
    onOpenSocial: (SocialConnectionTab) -> Unit,
    onOpenMoment: (moments: List<com.moments.android.models.Moment>, index: Int) -> Unit,
    onMomentLongPress: (moment: com.moments.android.models.Moment, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val scrollState = rememberScrollState()
    val highlightsRefreshToken by remember { mutableIntStateOf(0) }
    val storyRingRefreshToken by remember { mutableIntStateOf(0) }

    // Equivalente a `ProfileHeaderCollapseMetrics.progress(forTabsMinY:)`: el username del chrome
    // aparece a medida que la cabecera se desplaza fuera de pantalla.
    val collapseProgress by remember {
        derivedStateOf { (scrollState.value / 220f).coerceIn(0f, 1f) }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == UserProfileTabType.TAGGED &&
            viewModel.taggedMoments.isEmpty() &&
            !viewModel.isLoadingTagged
        ) {
            viewModel.fetchTaggedMoments()
        }
    }

    Box(modifier.fillMaxSize().background(ProfileMomentZoomNavigation.canvasBackground(colors.isDark))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = safeAreaBottom + 120.dp),
        ) {
            Box(Modifier.height(56.dp))

            UserModernProfileHeader(
                viewModel = viewModel,
                storyRingRefreshTrigger = storyRingRefreshToken,
                usernameCollapseProgress = collapseProgress,
                onFollowAction = onFollowAction,
                onOpenStories = onOpenStories,
                onShowProfileImageFullscreen = onShowProfileImageFullscreen,
                onOpenMessage = onOpenMessage,
                modifier = Modifier.padding(bottom = 4.dp),
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
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                )

                when (selectedTab) {
                    UserProfileTabType.MOMENTS -> {
                        if (viewModel.moments.isEmpty()) {
                            if (viewModel.isLoadingMoments) {
                                ProfileMomentsGridSkeletonView()
                            } else {
                                UserModernEmptyMomentsView(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
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
                                Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = colors.primary)
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
                                    descriptors = com.moments.android.views.profile.core.sections.ProfileBentoTileAssigner.simple(viewModel.taggedMoments),
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

        ProfileVisitorPinnedTopChrome(
            viewModel = viewModel,
            collapseProgress = collapseProgress,
            onDismiss = onDismiss,
            onShowQrCode = onShowQrCode,
            onShowReport = onShowReport,
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// `UserProfilePillTabs` pertenece a `UserProfileView.swift` → se consume desde el paquete
// `userprofile`, no se redefine aquí (paridad 1 archivo Swift = 1 archivo Kotlin).
