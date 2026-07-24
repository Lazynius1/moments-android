package com.moments.android.views.profile.core.sections

import com.moments.android.views.profile.core.ProfilePillTabs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.models.VisitsView
import com.moments.android.views.profile.core.ProfileTabType
import com.moments.android.views.profile.core.UserListRowAction
import com.moments.android.views.profile.core.UserListView
import com.moments.android.views.profile.core.ProfileViewModel
import com.moments.android.views.profile.highlights.ProfileHighlightsView
import com.moments.android.views.story.StoryViewModel

/** Destinos que abren las stats del perfil, equivalentes a `SocialConnectionsRoute` de iOS. */
enum class ProfileConnectionsRoute(val titleRes: Int) {
    VISITS(R.string.profile_header_visits),
    FOLLOWERS(R.string.profile_header_followers),
    FOLLOWING(R.string.profile_header_following),
    MUTUALS(R.string.profile_header_mutuals),
}

/** Port de `ModernBackgroundView`; el theme Firestore conserva su base de color. */
@Composable
fun ModernBackgroundView(profileImagePath: String?, scrollOffset: Float, profileTheme: String? = null, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val base = if (dark) listOf(Color(0xFF0B1215), Color(0xFF121B20)) else listOf(Color(0xFFFAF9F6), Color(0xFFEAF0F2))
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(base))) {
        if (!profileImagePath.isNullOrBlank()) AsyncImage(profileImagePath, null, Modifier.fillMaxSize().graphicsLayer { translationY = scrollOffset * .2f }.blur(30.dp), contentScale = ContentScale.Crop, alpha = if (dark) .15f else .08f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if (dark) listOf(Color.Black.copy(.3f), Color.Black.copy(.7f)) else listOf(Color.White.copy(.2f), Color.White.copy(.6f)))))
    }
}

/** Orquestador de contenido equivalente al shell SwiftUI; ownership de navegación queda en el caller Android. */
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
    onEditProfileNote: () -> Unit,
    onShowNotifications: () -> Unit,
    onShowQr: () -> Unit,
    onShowIncognito: () -> Unit,
    onShowSettings: () -> Unit,
    isIncognitoActive: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        viewModel.isLoading -> Column(modifier.fillMaxWidth()) { ProfileHeaderSkeletonView(); ProfileMomentsGridSkeletonView(Modifier.padding(top = 20.dp)) }
        viewModel.errorMessage != null -> ModernErrorView(viewModel.errorMessage.orEmpty(), viewModel::refreshProfile, modifier)
        else -> Box(modifier.fillMaxSize()) {
            // Destino abierto desde las stats (visitas / seguidores / seguidos / mutuos).
            var connectionsRoute by remember { mutableStateOf<ProfileConnectionsRoute?>(null) }

            ModernBackgroundView(viewModel.userProfile?.profileImagePath, 0f, viewModel.userProfile?.selectedProfileTheme)
            Column(Modifier.fillMaxSize()) {
                ModernProfileHeader(viewModel, storyViewModel, 0f, onEditProfile, onShowStory, onShowProfileImage, onEditProfileNote, Modifier.padding(top = 4.dp, bottom = 4.dp))
                ProfileOverviewCard(
                    viewModel,
                    viewModel.userProfile?.interests.orEmpty(),
                    onOpenVisits = { connectionsRoute = ProfileConnectionsRoute.VISITS },
                    onOpenFollowers = { connectionsRoute = ProfileConnectionsRoute.FOLLOWERS },
                    onOpenFollowing = { connectionsRoute = ProfileConnectionsRoute.FOLLOWING },
                    onOpenMutuals = { connectionsRoute = ProfileConnectionsRoute.MUTUALS },
                )
                // Destacadas compactas tras el bloque social, como en iOS.
                viewModel.userProfile?.id?.takeIf { it.isNotBlank() }?.let { profileId ->
                    ProfileHighlightsView(
                        userId = profileId,
                        isOwnProfile = profileId == FirebaseAuth.getInstance().currentUser?.uid,
                        isCompact = true,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                ProfilePillTabs(selectedTab, onSelectTab)
                when (selectedTab) {
                    ProfileTabType.MOMENTS -> ProfileMomentsTab(viewModel.moments, viewModel.isLoadingMoments, ProfileBentoTileAssigner.assign(viewModel.moments)) { moments, index -> onOpenMoment(moments, index, ProfileMomentZoomFeedKind.OWN_MOMENTS) }
                    ProfileTabType.SAVED -> ProfileSavedContent(savedState, onOpenSavedManager, { moments, index -> onOpenMoment(moments, index, ProfileMomentZoomFeedKind.SAVED_MOMENTS) }, onRefreshSavedVisibility, onRemoveSaved)
                    ProfileTabType.TAGGED -> {
                        LaunchedEffect(viewModel.userProfile?.id) { if (viewModel.taggedMoments.isEmpty()) viewModel.userProfile?.id?.let(viewModel::fetchTaggedMoments) }
                        ProfileMomentsTab(viewModel.taggedMoments, viewModel.isLoadingTagged, ProfileBentoTileAssigner.simple(viewModel.taggedMoments)) { moments, index -> onOpenMoment(moments, index, ProfileMomentZoomFeedKind.TAGGED_MOMENTS) }
                    }
                }
            }
            ProfileStickyChromeContainer(
                0f,
                false,
                chrome = {
                    ProfileOwnPinnedTopChrome(viewModel.userProfile?.username ?: stringResource(R.string.profile_default_username), viewModel.userProfile?.isVerified == true, 0f, isIncognitoActive, onShowNotifications, onShowQr, onShowIncognito, onShowSettings)
                },
                modifier = Modifier.statusBarsPadding(),
            )

            connectionsRoute?.let { route ->
                Dialog(
                    onDismissRequest = { connectionsRoute = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    if (route == ProfileConnectionsRoute.VISITS) {
                        VisitsView(onDismiss = { connectionsRoute = null })
                    } else {
                        UserListView(
                            title = stringResource(route.titleRes),
                            users = when (route) {
                                ProfileConnectionsRoute.FOLLOWERS -> viewModel.followers
                                ProfileConnectionsRoute.FOLLOWING -> viewModel.following
                                else -> viewModel.mutuals
                            },
                            visitTimestamps = viewModel.visitTimestamps,
                            viewModel = viewModel,
                            onDismiss = { connectionsRoute = null },
                            rowAction = if (route == ProfileConnectionsRoute.FOLLOWING) {
                                UserListRowAction.UNFOLLOW
                            } else {
                                UserListRowAction.NONE
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMomentsTab(moments: List<Moment>, loading: Boolean, descriptors: List<ProfileGridTileDescriptor>, onOpen: (List<Moment>, Int) -> Unit) {
    when {
        loading -> ProfileMomentsGridSkeletonView()
        moments.isEmpty() -> ModernEmptyMomentsView()
        else -> ProfileMomentsBentoGrid(moments, descriptors) { moment, width, index, descriptor -> ModernMomentThumbnail(moment, width, zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index), onTap = { onOpen(moments, index) }, onLongPress = {}, usesDiscreetAudienceIcon = true, showsAudienceBadge = false, gridIndex = index, descriptor = descriptor) }
    }
}

@Composable
fun ModernRefreshIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "profileRefresh")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Restart), label = "profileRefreshTurn")
    androidx.compose.foundation.layout.Row(modifier.background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.08f) else Color.Black.copy(.06f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Refresh, null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn })
        Text(stringResource(R.string.profile_shell_updating), color = profileShellSecondary(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun profileShellPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
@Composable private fun profileShellSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.64f) else Color(0xFF52626A)
